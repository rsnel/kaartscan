package it.snel.kaartscan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Fixtures reproduce what phplib's html_start() prints. */
class HtmlTest {
    private fun page(title: String, header: String) = """
<!DOCTYPE html>
<html translate="no">
<head>
<meta charset="UTF-8">
<title>Kaartscan OVC - $title</title>
</head><body>
<div id="header">
$header</div><div id="content"><h3>Kaartscan</h3>
</div>
</body></html>
"""

    private fun box(cls: String, title: String, msg: String) = """
<div class="ui-widget">
<div class="$cls ui-corner-all" style="padding: 0 .7em;"> 
<p><span class="ui-icon ui-icon-alert" style="float: left; margin-right: .3em;"></span> 
<strong>
$title:
</strong>
$msg
</div>
</div>
"""

    @Test
    fun notice() {
        val p = Html.parse(page("Kaartverkoop", box("ui-state-highlight", "Success", "Barcode 123456  stored in database")))
        assertFalse(p.isLoginPage)
        assertEquals(Message("Success", "Barcode 123456 stored in database", false), p.notice)
        assertNull(p.error)
        assertEquals(p.notice, p.message)
    }

    @Test
    fun error() {
        val p = Html.parse(page("Kaartcontrole", box("ui-state-error", "Dubbel", "Barcode 123456 is al binnen")))
        assertEquals(Message("Dubbel", "Barcode 123456 is al binnen", true), p.error)
        assertEquals(p.error, p.message)
    }

    @Test
    fun loginPageWithError() {
        val html = page(
            "Loginpagina",
            box("ui-state-error", "Ongeldige combi gebruikersnaam/wachtwoord", "Leerlingen loggen in met hun leerlingnummer."),
        )
        assertTrue(Html.isLoginPage(html))
        val p = Html.parse(html)
        assertTrue(p.isLoginPage)
        assertEquals(
            "Ongeldige combi gebruikersnaam/wachtwoord: Leerlingen loggen in met hun leerlingnummer.",
            p.error.toString(),
        )
    }

    @Test
    fun barcodes() {
        assertEquals("123456", Barcodes.normalize(" ~123456\n"))
        assertTrue(Barcodes.isValid("123456"))
        assertFalse(Barcodes.isValid("12345"))
        assertFalse(Barcodes.isValid("12345a"))
        assertFalse(Barcodes.isValid("abc123456"))
    }
}
