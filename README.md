# Kaartscan

Android app for [Kaartscan OVC](https://srv-ovc-linux.ovc.nl/kaartscan/): sell and
check party tickets by scanning the barcode on the student card with the phone's
camera, instead of a laptop with a separate barcode scanner.

The app is a client of the existing PHP web application (source:
`https://snel.it/svn/misc/trunk/kaartscan/`). It reads through `api.php` (JSON) and
writes through the same `do_*.php` scripts the website uses, so validation rules and
messages stay on the server.

User documentation (Dutch): [rsnel.github.io/kaartscan](https://rsnel.github.io/kaartscan/).

## The app

* **Login** with the 'oldstyle' username/password form (`call.php?action=do_login`).
  The PHP session cookie (`kaartscan`) is kept in the app's storage; at startup a
  still valid session skips the login screen. Only users in `$admins` get in, exactly
  as on the website. The password field has a show/hide button.
* **Start screen** with Kaartverkoop, Kaartcontrole, Overzicht, Systeemlog and
  Uitloggen. The top
  bar of every screen shows the party (from `whoami`) and the logged in user.
* **Kaartverkoop**: scan a card or type the leerlingnummer, optionally tick
  *met introducee* first (only shown when `whoami` reports `feest.introducee_toegestaan`;
  it resets after a successful sale). Shows the sold counts and the latest 10 sales.
* **Kaartcontrole**: scan at the door. Shows *toegelaten MET/ZONDER introducee*,
  *al binnen* or *geen kaartje gekocht*, the counts as inside / sold per category,
  and the check-ins (with delete).
* **Overzicht**: all sales with inside / sold counts; order by sale time, arrival
  time or name; who is inside, with arrival time; show deleted; local search on name
  or number. Someone who is inside has a check-out button (deletes the
  kaartcontrole); only after that the bin to delete the sale appears. Someone who
  was checked out and is not inside again shows when and by whom.
* **Systeemlog**: every sale, check-in, check-out and deleted sale of the party,
  newest first, with time and user. Search on a student's name or number; the
  database does the filtering, so older events are found too.
* **Counts**: total, students, introducees. For a party without introducees (and
  none in the data) only the number of students is shown.
* **Result banner**: the server's message, big green or red, with a beep and
  vibration.
* **Status dot** in the top bar, also on the login screen: green when the server
  answers normally, yellow for an unexpected answer (HTTP 5xx, `error_system()`, no
  JSON), red for no connection. Tap for details: last contact, response times,
  request counts, last problem. The app checks `whoami` every 30 s while on screen
  (on the login screen without a session, which api.php answers with a 401).
* **Settings** (gear in the top bar and on the login screen): scanner sensitivity,
  the server address (only changeable when logged out), and updates.
* **Updates**: at startup (unless switched off in settings) and with *Nu controleren
  op updates*, the app looks up the latest GitHub release. A newer version is offered
  in a dialog over any screen: it downloads `kaartscan-<tag>.apk` into the cache and
  hands it to Android's package installer (through a `FileProvider`; Android 8+ asks
  once to allow installing unknown apps for Kaartscan). The latest tag is read from the
  redirect of `/releases/latest`, not the GitHub API, which allows only 60 requests per
  hour per IP address. Versions compare numerically (`1.10` > `1.9`); a build after a
  tag (`1.9-3-gabc1234`) counts as that tag, and a local `dev` build is only offered an
  update when checking by hand.
* Portrait only; the screen stays on while the camera is open.

### Scanning

Continuous scanning with CameraX and ML Kit (bundled model, works offline), with a
torch button. ML Kit has no confidence threshold, so the app filters itself: a code
only counts when it is read the same several times in a row (`ScanConfirmer`). A
different value, or a pause longer than 0.7 s, starts the count over.

| Sensitivity | Identical reads | Non-leerlingnummer codes |
|---|---|---|
| Tolerant | 1 | error message |
| Normaal (default) | 2 | error message |
| Streng | 3 | ignored |

After a submitted code the same code is ignored for 4 s, and scanning pauses for
1.5 s after each result, so a card held in front of the camera counts once. Codes
are validated as on the server: at least 6 digits, leading `~` stripped.

### Writing

| Action | Request |
|---|---|
| Sell | `POST do_kaartverkoop.php` with `barcode` and optionally `introducee=1` |
| Check in | `POST do_kaartcontrole.php` with `barcode` |
| Delete a sale | `GET do_delete.php?kaartverkoop_id=…` |
| Check out / delete a check-in | `GET do_delete_kaartcontrole.php?kaartcontrole_id=…` |

The scripts put their outcome in `$_SESSION['notice']` or `$_SESSION['err']` and
redirect; the app does not follow the redirect but asks `api.php?action=melding`.
Without a session these scripts show the login page, which the app recognises as an
expired session. Why `do_login` refused a login is read from the login page as well
(`Html.kt`), since `error_user()` only puts it in the session.

## api.php

Read-only JSON, next to `kaartverkoop.php` on the server. It sets `$json_api = true`
before including phplib and calls `check_admin()`, so it needs the phplib patch that
makes `$json_api` pages answer errors as JSON instead of the login page or a
redirect:

| Status | When |
|---|---|
| 401 | no valid session (nothing is logged) |
| 403 | logged in, but not an admin; the session is ended, as on the website |
| 400 | bad request, e.g. unknown action or order |
| 500 | `error_system()`, e.g. an SQL error |

Errors look like `{"error": "…", "title": "…"}`. All answers are
`Content-Type: application/json` with `Cache-Control: no-store`.

### Actions

**`?action=whoami`**: the session and the current party.

```json
{
  "username": "r.snel", "orig_username": "r.snel", "type": "personeel",
  "ppl_id": 1, "admin": true,
  "feest": {"feest_id": 8, "naam": "Examengala 2021", "introducee_toegestaan": "1"}
}
```

`feest.introducee_toegestaan` is `"0"` or `"1"` (as it comes from the database); the
app also accepts `0`/`1` and `false`/`true`.

**`?action=aantallen`**: sold and inside, each split up. Deleted sales and check-ins
are not counted; a barcode that is no known student counts as sold but not as a
student.

```json
{
  "verkocht": {"totaal": 143, "leerlingen": 128, "introducees": 15},
  "binnen":   {"totaal": 87,  "leerlingen": 79,  "introducees": 8}
}
```

**`?action=kaartverkoop`**: the sales, with `aantallen` as above.

| Parameter | |
|---|---|
| `order=chron` | sale time, newest first (default) |
| `order=aankomst` | arrival time, latest first; not (yet) inside last, by sale time |
| `order=alph` | surname, first name, prefix; unknown barcodes last |
| `verwijderd=1` | include deleted sales |
| `limit=N` | at most N |

```json
{
  "aantallen": {"verkocht": {…}, "binnen": {…}},
  "kaartjes": [{
    "kaartverkoop_id": 38, "barcode": "213874",
    "gast": {"ppl_id": 10, "naam": "Sanne Bakker", "voornaam": "Sanne",
             "tussenvoegsel": "", "achternaam": "Bakker", "klas": "6V2"},
    "introducee": true, "bron": "scan", "tijdstip": "2021-04-16 19:42:10",
    "door": {"username": "j.devries", "orig_username": "j.devries"},
    "verwijderd": null,
    "binnen": {"kaartcontrole_id": 12, "tijdstip": "2021-04-16 21:14:03",
               "door": {"username": "a.faber", "orig_username": "a.faber"}}
  }]
}
```

`gast` is `null` for a barcode that is no known student; `klas` is the current
stamklas or `null`. `verwijderd` is `null` or `{"tijdstip", "door"}`. `binnen` comes
from a LEFT JOIN on the not deleted `kaartcontrole` and is `null` when the student is
not inside. `uitgecheckt` is the deleted `kaartcontrole` with the highest id
(`{"kaartcontrole_id", "tijdstip", "door"}` with the delete time and user), or
`null`; the app only shows it when `binnen` is `null`. `door` holds `$_SESSION['username']` and `['orig_username']` (they differ
after `do_su`).

**`?action=kaartcontrole`**: the check-ins of the current party, newest first, with
`aantallen`. Parameters `verwijderd=1` and `limit=N` as above.

```json
{
  "aantallen": {"verkocht": {…}, "binnen": {…}},
  "controles": [{
    "kaartcontrole_id": 12, "kaartverkoop_id": 38, "barcode": "213874",
    "gast": {…}, "introducee": true, "tijdstip": "2021-04-16 21:14:03",
    "door": {…}, "verwijderd": null
  }]
}
```

**`?action=barcode&barcode=213874`**: everything about one barcode: the student
(`gast`), the not deleted sale (`kaartje`, as in `kaartverkoop`, or `null`) and the
check-in (`binnen`, as in `kaartcontrole`, or `null`). Not used by the app.

**`?action=log`**: the history of the current party, newest first, as one list of
events. The database merges four queries with `UNION ALL`:

| `soort` | From | `tijdstip` / `door` |
|---|---|---|
| `verkoop` | every `kaartverkoop` row | create |
| `binnen` | every `kaartcontrole` row | create |
| `uitgecheckt` | deleted `kaartcontrole` rows | delete |
| `verkoop_verwijderd` | deleted `kaartverkoop` rows | delete |

Events at the same second are ordered sale, check-in, check-out, deleted sale.
Duplicates that `do_kaartverkoop.php` / `do_kaartcontrole.php` hard-delete right away
never show up.

| Parameter | |
|---|---|
| `zoek=…` | only events of a barcode containing the text, or a student whose full name (first name, prefix, surname) contains it |
| `limit=N` | at most N (the app asks 500) |

```json
{"log": [{
  "soort": "uitgecheckt", "tijdstip": "2021-04-16 21:20:47",
  "door": {"username": "a.faber", "orig_username": "a.faber"},
  "kaartverkoop_id": 40, "kaartcontrole_id": 13, "barcode": "209331",
  "gast": {…}, "introducee": false
}]}
```

`kaartcontrole_id` is `null` for the two sale events. The app skips kinds it does not
know.

**`?action=melding`**: the message a `do_*.php` script left in the session, returned
once and then cleared, as `html_start()` does on the website. An error wins over a
notice.

```json
{"melding": {"title": "Dubbel", "text": "Barcode 213874 is al binnen", "error": true}}
```

`{"melding": null}` when there is none.

## Code

| | |
|---|---|
| `KaartscanClient.kt` | HTTP (OkHttp, persistent cookie jar), login, api.php, do_*.php |
| `Api.kt` | api.php JSON to `Me`, `Entry`, `Aantallen`, `Message` |
| `Html.kt` | messages on the login page (jsoup) |
| `ServerStats.kt` | outcome of every request, for the status dot |
| `ScanSensitivity.kt` | sensitivity levels and `ScanConfirmer` |
| `Updates.kt` | latest release, download, version comparison |
| `MainViewModel.kt` | state, loading, scanning, writing |
| `ui/` | Compose screens: login, start, scan (verkoop/controle), overzicht, systeemlog, settings, camera |

Unit tests cover the JSON parsing, the login page parsing, barcode validation and
`ScanConfirmer` and the version comparison. `ScreenshotTest` renders the screens with example data (Roborazzi on
Robolectric) for the website.

## Building

Open in Android Studio, or `./gradlew assembleDebug`. GitHub Actions builds on every
push; the APKs are artifacts on the workflow run (*kaartscan-debug-apk*,
*kaartscan-release-apk*). The version name comes from `git describe`: `1.9` for a
build of tag `v1.9`, `1.9-3-gabc1234` for later commits (settings shows it with the
build number). The version code is the workflow run number, so each CI
build installs over the previous one.

The website is `docs/`. On every push to `main` CI renders the screenshots into
`docs/screenshots` and commits them when they changed; the `pages` job then publishes
`docs/` (Pages source "GitHub Actions"), only when it changed.

### Release signing

Release builds, and debug builds when the key is available, are signed with the key
in `kaartscan-release.jks` (not in git).

* **CI** takes it from four repository secrets: `KEYSTORE_BASE64` (the keystore,
  base64 encoded), `KEYSTORE_PASSWORD`, `KEY_ALIAS` and `KEY_PASSWORD`. Without them
  the release APK is built unsigned.
* **Locally**, put `kaartscan-release.jks` and a `keystore.properties` in the project
  root (both are gitignored):

  ```
  storeFile=kaartscan-release.jks
  storePassword=...
  keyAlias=kaartscan
  keyPassword=...
  ```

  then `./gradlew assembleRelease`.

To publish a release: `git tag v1.0 && git push origin v1.0`. CI attaches the signed
`kaartscan-v1.0.apk` to a GitHub Release.

Keep a backup of the keystore and its password: Android only installs an update that
is signed with the same key.
