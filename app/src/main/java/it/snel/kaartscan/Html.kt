package it.snel.kaartscan

import org.jsoup.Jsoup
import org.jsoup.nodes.Element

data class Message(val title: String, val text: String, val isError: Boolean) {
    override fun toString(): String = if (title.isEmpty()) text else "$title: $text"
}

data class Cell(val text: String, val link: String?)

/** One row of an mdb2_res_table(), keyed by column name (tijdstip, doc, barcode, gast, ...). */
data class Row(val cells: Map<String, Cell>) {
    operator fun get(column: String): Cell? = cells[column]
}

data class Counts(val total: String, val leerlingen: String, val introducees: String)

data class Page(
    val isLoginPage: Boolean,
    val heading: String?,
    val notice: Message?,
    val error: Message?,
    val loggedInAs: String?,
    val counts: Counts?,
    val columns: List<String>,
    val rows: List<Row>,
    val hint: String?,
) {
    /** The outcome of a do_*.php action, as shown on the following page. */
    val message: Message? get() = error ?: notice
}

/** Scrapes pages produced by phplib's html_start()/mdb2_res_table()/html_end(). */
object Html {
    private val countsRegex =
        Regex("Aantallen:\\s*(\\d*)\\s*\\(leerlingen:\\s*(\\d*),\\s*introducees:\\s*(\\d*)\\)")
    private val loggedInRegex = Regex("Ingelogd as \\S+ (\\S+)")

    fun parse(html: String, baseUri: String): Page {
        val doc = Jsoup.parse(html, baseUri)
        val content = doc.selectFirst("#content")

        val table = content?.selectFirst("table")
        var columns = emptyList<String>()
        val rows = mutableListOf<Row>()
        if (table != null) {
            val trs = table.select("tr")
            columns = trs.firstOrNull()?.select("th")?.map { it.text() } ?: emptyList()
            for (tr in trs.drop(1)) {
                val tds = tr.select("td")
                if (tds.isEmpty()) continue
                val cells = LinkedHashMap<String, Cell>()
                tds.forEachIndexed { i, td ->
                    val name = columns.getOrNull(i) ?: "kolom${i + 1}"
                    val text = td.text().let { if (it == "NULL" && td.selectFirst("i") != null) "" else it }
                    val link = td.selectFirst("a[href]")?.absUrl("href")?.takeIf { it.isNotEmpty() }
                    cells[name] = Cell(text, link)
                }
                rows.add(Row(cells))
            }
        }

        val contentText = content?.text() ?: ""
        val counts = countsRegex.find(contentText)?.let { m ->
            Counts(
                m.groupValues[1].ifEmpty { "0" },
                m.groupValues[2].ifEmpty { "0" },
                m.groupValues[3].ifEmpty { "0" },
            )
        }

        return Page(
            isLoginPage = doc.title().contains("Loginpagina"),
            heading = content?.selectFirst("h3")?.text(),
            notice = message(doc.selectFirst("div.ui-state-highlight"), isError = false),
            error = message(doc.selectFirst("div.ui-state-error"), isError = true),
            loggedInAs = doc.selectFirst("#header")?.text()?.let { loggedInRegex.find(it)?.groupValues?.get(1) },
            counts = counts,
            columns = columns,
            rows = rows,
            hint = content?.select("p")?.map { it.text() }?.firstOrNull { it.startsWith("De barcode") },
        )
    }

    /** <div class="ui-state-..."><p><span/> <strong>Title:</strong> text</div> */
    private fun message(div: Element?, isError: Boolean): Message? {
        if (div == null) return null
        val strong = div.selectFirst("strong")?.text() ?: ""
        val full = div.text()
        val text = if (strong.isNotEmpty() && full.contains(strong)) full.substringAfter(strong).trim() else full.trim()
        return Message(strong.trim().removeSuffix(":").trim(), text, isError)
    }
}
