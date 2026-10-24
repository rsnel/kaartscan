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
)

/** JSON from api.php; field names as in api.php. */
object Api {
    fun me(o: JSONObject): Me {
        val feest = o.optJSONObject("feest")
        val toegestaan = when {
            o.has("introducee_toegestaan") -> bool(o, "introducee_toegestaan")
            feest != null && feest.has("introducee_toegestaan") -> bool(feest, "introducee_toegestaan")
            else -> true
        }
        return Me(
            username = o.getString("username"),
            feestNaam = feest?.let { str(it, "naam") },
            introduceeToegestaan = toegestaan,
        )
    }

    fun aantallen(o: JSONObject): Aantallen =
        Aantallen(counts(o.getJSONObject("verkocht")), counts(o.getJSONObject("binnen")))

    /** api.php?action=kaartverkoop; [baseUrl] ends with '/' */
    fun kaartjes(o: JSONObject, baseUrl: String): List<Entry> =
        entries(o.getJSONArray("kaartjes")) { k, deleted ->
            val id = k.getInt("kaartverkoop_id")
            entry(k, id, if (deleted) null else "${baseUrl}do_delete.php?kaartverkoop_id=$id")
        }

    /** api.php?action=kaartcontrole */
    fun controles(o: JSONObject, baseUrl: String): List<Entry> =
        entries(o.getJSONArray("controles")) { c, deleted ->
            val id = c.getInt("kaartcontrole_id")
            entry(c, id, if (deleted) null else "${baseUrl}do_delete_kaartcontrole.php?kaartcontrole_id=$id")
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

    private fun entry(o: JSONObject, id: Int, deleteLink: String?): Entry {
        val gast = o.optJSONObject("gast")?.let { g ->
            val naam = str(g, "naam") ?: ""
            val klas = str(g, "klas") ?: "KLAS?"
            "$naam ($klas)"
        }
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
