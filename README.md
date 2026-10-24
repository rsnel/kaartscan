# Kaartscan

Android app for [Kaartscan OVC](https://srv-ovc-linux.ovc.nl/kaartscan/): sell and
check party tickets by scanning the barcode on the student card with the phone's
camera, instead of a laptop with a separate barcode scanner.

The app is a client of the existing web application (source:
`https://snel.it/svn/misc/trunk/kaartscan/`); nothing changes on the server.

## What it does

* **Login** with the 'oldstyle' username/password form (`call.php?action=do_login`).
  The PHP session cookie (`kaartscan`) is stored; if a stored session is still valid
  the login screen offers to continue with it. Only users in `$admins` get in,
  exactly as on the website.
* **Verkoop** (`kaartverkoop.php` / `do_kaartverkoop.php`): scan a card (or type the
  leerlingnummer), optionally tick *met introducee* first. Shows the server's
  success/error message in a big green/red banner with a beep and vibration, and the
  latest entries with *verwijderen* (`do_delete.php`).
* **Overzicht** (`overzicht.php`): counts, chronological/alphabetical, show deleted,
  plus local search by name or number.
* **Controle** (`kaartcontrole.php` / `do_kaartcontrole.php`): scan at the door;
  shows *toegelaten MET/ZONDER introducee*, *al binnen* or *geen kaartje gekocht*,
  the number of people inside, and the latest entries
  (`do_delete_kaartcontrole.php`).

Scanning is continuous (CameraX + ML Kit, bundled model, works offline). The same
code is ignored for 4 seconds and scanning pauses briefly after each result, so a
card held in front of the camera is submitted once. There is a torch button for dark
venues.

Reading goes through `api.php` (JSON; `whoami`, `aantallen`, `kaartverkoop`,
`kaartcontrole`, `melding`; the two lists include the counts, older versions without
them still work), writing through the same `do_*.php` scripts the website
uses. Their outcome (`$_SESSION['notice']` / `$_SESSION['err']`) comes from
`api.php?action=melding`, or from the redirected page on a server whose `api.php` does
not have that action yet, so validation rules and messages stay on the server. The
checkbox *met introducee* is only shown when `whoami` reports
`introducee_toegestaan`.

User documentation (Dutch): [rsnel.github.io/kaartscan](https://rsnel.github.io/kaartscan/),
from `docs/`. The screenshots there are rendered by `ScreenshotTest` with example data
and committed by CI on every push to `main`.

## Building

Open in Android Studio, or `./gradlew assembleDebug`. GitHub Actions builds on every
push; the APKs are artifacts on the workflow run (*kaartscan-debug-apk*,
*kaartscan-release-apk*). The version code is the workflow run number, so each CI
build installs over the previous one.

### Release signing

Release builds are signed with the key in `kaartscan-release.jks` (not in git).

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
