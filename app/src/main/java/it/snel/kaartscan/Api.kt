package it.snel.kaartscan

import org.json.JSONArray
import org.json.JSONObject

/** api.php?action=whoami */
data class Me(
    val username: String,
    val feestNaam: String?,
    /** false: this party has no introducees, hide the checkbox */
    val introduceeToegestaan: Boolean,
)

data class Counts(val total: String, val leerlingen: String, val introducees: String)

/** api.php?action=aantallen */
data class Aantallen(val verkocht: Counts, val binnen: Counts)

/** A list from api.php with the counts it sends along. */
data class Listing(val entries: List<Entry>, val aantallen: Aantallen)

/**
 * One kaartje (kaartverkoop) or one kaartcontrole, as shown in the lists.
 * [deleteLink] is the do_delete*.php URL, null when already deleted.
 */
data class Entry(
    val id: Int,
    val barcode: String,
    /** "Sanne Bakker (6V2)", null for an unknown barcode */
    val gast: String?,
    val introducee: Boolean,
    val tijdstip: String,
    val door: String,
    val bron: String?,
    /** "verwijderd door r.snel op 2026-10-31 19:31:00", null if not deleted */
    val verwijderd: String?,
    val deleteLink: String?,
    /** kaartjes only: "binnen om 21:15:03 (door r.snel)", null if not (yet) inside */
    val binnen: String? = null,
    /** kaartjes only: do_delete_kaartcontrole.php link that undoes 'binnen' */
    val exitLink: String? = null,
    /** kaartjes only: "uitgecheckt om 22:01:00 (door r.snel)", the latest check-out, if any */
    val uitgecheckt: String? = null,
)

enum class LogSoort(val param: String, val label: String) {
    VERKOOP("verkoop", "Verkocht"),
    BINNEN("binnen", "Binnen"),
    UITGECHECKT("uitgecheckt", "Uitgecheckt"),
    VERKOOP_VERWIJDERD("verkoop_verwijderd", "Verkoop verwijderd"),
}

/** One line of api.php?action=log. */
data class LogEvent(
    val soort: LogSoort,
    val tijdstip: String,
    val door: String,
    val barcode: String,
    /** "Sanne Bakker (6V2)", null for an unknown barcode */
    val gast: String?,
    val introducee: Boolean,
    /** how the ticket was sold: "scan", "wiscollect", or null */
    val bron: String? = null,
)

/** JSON from api.php; field names as in api.php. */
object Api {
    fun me(o: JSONObject): Me {
        val feest = o.getJSONObject("feest")
        return Me(
            username = o.getString("username"),
            feestNaam = str(feest, "naam"),
            // comes from the database as "0" / "1"
            introduceeToegestaan = bool(feest, "introducee_toegestaan"),
        )
    }

    fun aantallen(o: JSONObject): Aantallen =
        Aantallen(counts(o.getJSONObject("verkocht")), counts(o.getJSONObject("binnen")))

    /** the 'aantallen' that kaartverkoop and kaartcontrole include */
    fun aantallenIn(o: JSONObject): Aantallen = aantallen(o.getJSONObject("aantallen"))

    /** api.php?action=kaartverkoop; [baseUrl] ends with '/' */
    fun kaartjes(o: JSONObject, baseUrl: String): List<Entry> =
        entries(o.getJSONArray("kaartjes")) { k, deleted ->
            val id = k.getInt("kaartverkoop_id")
            val binnenId = k.optJSONObject("binnen")?.optInt("kaartcontrole_id", 0)?.takeIf { it > 0 }
            entry(k, id, if (deleted) null else "${baseUrl}do_delete.php?kaartverkoop_id=$id").copy(
                exitLink = binnenId?.let { "${baseUrl}do_delete_kaartcontrole.php?kaartcontrole_id=$it" },
            )
        }

    /** api.php?action=kaartcontrole */
    fun controles(o: JSONObject, baseUrl: String): List<Entry> =
        entries(o.getJSONArray("controles")) { c, deleted ->
            val id = c.getInt("kaartcontrole_id")
            // deleting a check-in is checking out: same button as in Overzicht
            entry(c, id, null).copy(
                exitLink = if (deleted) null else "${baseUrl}do_delete_kaartcontrole.php?kaartcontrole_id=$id",
            )
        }

    /** api.php?action=melding: the notice/err a do_*.php script left in the session */
    fun melding(o: JSONObject): Message? {
        val m = o.optJSONObject("melding") ?: return null
        return Message(clean(str(m, "title")), clean(str(m, "text")), bool(m, "error"))
    }

    /** the server's messages sometimes contain double spaces, HTML collapsed them */
    private fun clean(s: String?): String = s?.trim()?.replace(Regex("\\s+"), " ") ?: ""

    private fun counts(o: JSONObject) =
        Counts(o.optInt("totaal").toString(), o.optInt("leerlingen").toString(), o.optInt("introducees").toString())

    private fun entries(arr: JSONArray, f: (JSONObject, Boolean) -> Entry): List<Entry> =
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            f(o, !o.isNull("verwijderd"))
        }

    /** api.php?action=log; unknown kinds of events are skipped */
    fun log(o: JSONObject): List<LogEvent> {
        val arr = o.getJSONArray("log")
        return (0 until arr.length()).mapNotNull { i ->
            val e = arr.getJSONObject(i)
            val soort = LogSoort.entries.firstOrNull { it.param == str(e, "soort") } ?: return@mapNotNull null
            LogEvent(
                soort = soort,
                tijdstip = str(e, "tijdstip") ?: "",
                door = wie(e.optJSONObject("door")),
                barcode = str(e, "barcode") ?: "",
                gast = gast(e),
                introducee = bool(e, "introducee"),
                bron = str(e, "bron")?.takeIf { it.isNotBlank() },
            )
        }
    }

    /** "Sanne Bakker (6V2)", null for an unknown barcode */
    private fun gast(o: JSONObject): String? = o.optJSONObject("gast")?.let { g ->
        val naam = str(g, "naam") ?: ""
        val klas = str(g, "klas") ?: "KLAS?"
        "$naam ($klas)"
    }

    private fun entry(o: JSONObject, id: Int, deleteLink: String?): Entry {
        val gast = gast(o)
        val verwijderd = o.optJSONObject("verwijderd")?.let { v ->
            "verwijderd door ${wie(v.optJSONObject("door"))} op ${str(v, "tijdstip") ?: "?"}"
        }
        return Entry(
            id = id,
            barcode = str(o, "barcode") ?: "",
            gast = gast,
            introducee = bool(o, "introducee"),
            tijdstip = str(o, "tijdstip") ?: "",
            door = wie(o.optJSONObject("door")),
            bron = str(o, "bron"),
            verwijderd = verwijderd,
            deleteLink = deleteLink,
            binnen = o.optJSONObject("binnen")?.let { b ->
                val t = str(b, "tijdstip") ?: "?"
                "binnen om ${t.substringAfter(' ')} (door ${wie(b.optJSONObject("door"))})"
            },
            uitgecheckt = o.optJSONObject("uitgecheckt")?.let { u ->
                val t = str(u, "tijdstip") ?: "?"
                "uitgecheckt om ${t.substringAfter(' ')} (door ${wie(u.optJSONObject("door"))})"
            },
        )
    }

    /** "r.snel", or "r.snel/s.pieneman" after do_su, like the website */
    private fun wie(o: JSONObject?): String {
        if (o == null) return "?"
        val u = str(o, "username") ?: "?"
        val orig = str(o, "orig_username")
        return if (orig == null || orig == u) u else "$u/$orig"
    }

    private fun str(o: JSONObject, key: String): String? = if (o.isNull(key)) null else o.optString(key)

    /** accepts true/false, 1/0 and "1"/"0" */
    private fun bool(o: JSONObject, key: String): Boolean = when (val v = o.opt(key)) {
        is Boolean -> v
        is Number -> v.toInt() != 0
        is String -> v == "1" || v.equals("true", ignoreCase = true)
        else -> false
    }
}
