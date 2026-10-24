package it.snel.kaartscan

import org.jsoup.Jsoup
import org.jsoup.nodes.Element

data class Message(val title: String, val text: String, val isError: Boolean) {
    override fun toString(): String = if (title.isEmpty()) text else "$title: $text"
}

/** What is still read from HTML pages: the login page and the session messages. */
data class Page(val isLoginPage: Boolean, val notice: Message?, val error: Message?) {
    val message: Message? get() = error ?: notice
}

/**
 * Reads the messages html_start() shows ($_SESSION['notice'] / ['err']).
 * Needed for the reason a login failed (only the login page shows it) and
 * as fallback when api.php has no 'melding' action yet.
 */
object Html {
    fun parse(html: String): Page {
        val doc = Jsoup.parse(html)
        return Page(
            isLoginPage = doc.title().contains("Loginpagina"),
            notice = message(doc.selectFirst("div.ui-state-highlight"), isError = false),
            error = message(doc.selectFirst("div.ui-state-error"), isError = true),
        )
    }

    fun isLoginPage(html: String): Boolean = html.contains("Loginpagina</title>")

    /** <div class="ui-state-..."><p><span/> <strong>Title:</strong> text</div> */
    private fun message(div: Element?, isError: Boolean): Message? {
        if (div == null) return null
        val strong = div.selectFirst("strong")?.text() ?: ""
        val full = div.text()
        val text = if (strong.isNotEmpty() && full.contains(strong)) full.substringAfter(strong).trim() else full.trim()
        return Message(strong.trim().removeSuffix(":").trim(), text, isError)
    }
}
