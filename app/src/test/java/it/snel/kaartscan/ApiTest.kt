package it.snel.kaartscan

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** JSON as produced by api.php. */
class ApiTest {
    private val base = "https://srv-ovc-linux.ovc.nl/kaartscan/"

    @Test
    fun whoami() {
        val me = Api.me(JSONObject("""
            {"username": "r.snel", "orig_username": "r.snel", "type": "personeel", "ppl_id": 1, "admin": true,
             "feest": {"feest_id": 8, "naam": "Halloweenfeest 2026"}, "introducee_toegestaan": false}
        """))
        assertEquals(Me("r.snel", "Halloweenfeest 2026", false), me)
    }

    @Test
    fun whoamiIntroduceeInFeestOrMissing() {
        val inFeest = Api.me(JSONObject("""{"username": "a", "feest": {"naam": "Gala", "introducee_toegestaan": 1}}"""))
        assertTrue(inFeest.introduceeToegestaan)
        val off = Api.me(JSONObject("""{"username": "a", "feest": {"naam": "Gala", "introducee_toegestaan": "0"}}"""))
        assertFalse(off.introduceeToegestaan)
        val missing = Api.me(JSONObject("""{"username": "a", "feest": {"naam": null}}"""))
        assertTrue(missing.introduceeToegestaan)
        assertNull(missing.feestNaam)
    }

    @Test
    fun aantallen() {
        val a = Api.aantallen(JSONObject("""
            {"verkocht": {"totaal": 4, "leerlingen": 3, "introducees": 1},
             "binnen": {"totaal": 3, "leerlingen": 2, "introducees": 1}}
        """))
        assertEquals(Counts("4", "3", "1"), a.verkocht)
        assertEquals(Counts("3", "2", "1"), a.binnen)
    }

    @Test
    fun kaartjes() {
        val list = Api.kaartjes(JSONObject("""
            {"kaartjes": [
              {"kaartverkoop_id": 38, "barcode": "213874",
               "gast": {"ppl_id": 10, "naam": "Sanne Bakker", "voornaam": "Sanne", "tussenvoegsel": "", "achternaam": "Bakker", "klas": "6V2"},
               "introducee": true, "bron": "scan", "tijdstip": "2026-10-31 19:42:10",
               "door": {"username": "r.snel", "orig_username": "s.pieneman"}, "verwijderd": null,
               "binnen": {"kaartcontrole_id": 12, "tijdstip": "2026-10-31 21:15:03", "door": {"username": "a.faber", "orig_username": "a.faber"}}},
              {"kaartverkoop_id": 41, "barcode": "208120",
               "gast": {"ppl_id": 13, "naam": "Milan de Boer", "voornaam": "Milan", "tussenvoegsel": "de", "achternaam": "Boer", "klas": null},
               "introducee": false, "bron": null, "tijdstip": "2026-10-31 19:30:12",
               "door": {"username": "r.snel", "orig_username": "r.snel"},
               "verwijderd": {"tijdstip": "2026-10-31 19:31:00", "door": {"username": "a.faber", "orig_username": "a.faber"}}},
              {"kaartverkoop_id": 42, "barcode": "0888049978040401221026", "gast": null,
               "introducee": false, "bron": "scan", "tijdstip": "2026-10-31 19:20:00",
               "door": {"username": "r.snel", "orig_username": "r.snel"}, "verwijderd": null}
            ]}
        """), base)
        assertEquals(3, list.size)
        val (sanne, milan, onbekend) = list
        assertEquals("Sanne Bakker (6V2)", sanne.gast)
        assertTrue(sanne.introducee)
        assertEquals("r.snel/s.pieneman", sanne.door)
        assertEquals("scan", sanne.bron)
        assertNull(sanne.verwijderd)
        assertEquals("${base}do_delete.php?kaartverkoop_id=38", sanne.deleteLink)
        assertEquals("binnen om 21:15:03 (door a.faber)", sanne.binnen)
        assertEquals("${base}do_delete_kaartcontrole.php?kaartcontrole_id=12", sanne.exitLink)

        assertEquals("Milan de Boer (KLAS?)", milan.gast)
        assertNull(milan.bron)
        assertEquals("verwijderd door a.faber op 2026-10-31 19:31:00", milan.verwijderd)
        assertNull(milan.deleteLink)
        assertNull(milan.binnen)
        assertNull(milan.exitLink)

        assertNull(onbekend.gast)
        assertEquals("0888049978040401221026", onbekend.barcode)
    }

    @Test
    fun controles() {
        val list = Api.controles(JSONObject("""
            {"controles": [
              {"kaartcontrole_id": 12, "kaartverkoop_id": 38, "barcode": "213874",
               "gast": {"ppl_id": 10, "naam": "Sanne Bakker", "klas": "6V2"},
               "introducee": true, "tijdstip": "2026-10-31 21:15:03",
               "door": {"username": "r.snel", "orig_username": "r.snel"}, "verwijderd": null}
            ]}
        """), base)
        assertEquals(12, list[0].id)
        assertEquals("r.snel", list[0].door)
        assertEquals("${base}do_delete_kaartcontrole.php?kaartcontrole_id=12", list[0].deleteLink)
    }

    @Test
    fun aantallenInList() {
        val o = JSONObject("""
            {"aantallen": {"verkocht": {"totaal": 4, "leerlingen": 3, "introducees": 1},
                           "binnen": {"totaal": 3, "leerlingen": 2, "introducees": 1}},
             "controles": []}
        """)
        assertEquals(Aantallen(Counts("4", "3", "1"), Counts("3", "2", "1")), Api.aantallenIn(o))
        assertNull(Api.aantallenIn(JSONObject("""{"controles": []}""")))
    }

    @Test
    fun melding() {
        assertEquals(
            Message("Dubbel", "Barcode 213874 staat al in het systeem", true),
            Api.melding(JSONObject("""{"melding": {"title": "Dubbel", "text": "Barcode 213874 staat al in het systeem", "error": true}}""")),
        )
        assertNull(Api.melding(JSONObject("""{"melding": null}""")))
        assertEquals(
            Message("Success", "Barcode 212000 stored in database", false),
            Api.melding(JSONObject("""{"melding": {"title": "Success", "text": "Barcode 212000  stored in database", "error": false}}""")),
        )
    }
}
