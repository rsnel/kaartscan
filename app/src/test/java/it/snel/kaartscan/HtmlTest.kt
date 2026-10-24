package it.snel.kaartscan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Fixtures reproduce what phplib's html_start()/mdb2_res_table()/html_end() print. */
class HtmlTest {
    private val base = "https://srv-ovc-linux.ovc.nl/kaartscan/kaartverkoop.php"

    private fun page(title: String, header: String, content: String) = """
<!DOCTYPE html>
<html translate="no">
<head>
<meta name="google" content="notranslate">
<meta charset="UTF-8">
<title>Kaartscan OVC - $title</title>
</head><body>
<div id="header">
$header</div><div id="content">$content
<p><div id="footer">
Deze webapplicatie &copy; Rik Snel 2022 &lt;rik@snel.it&gt;. Powered by PHP 8.4.27.<br>
</div>
</div>
</body></html>
"""

    private val loggedIn = """
		Ingelogd as docent r.snel (ppl_id=1234)
 met  <a href="admin.php">admin</a> rechten
. <font face="Courier"></font> <a href="/kaartscan/">home</a>
	<a href="call.php?action=do_logout">uitloggen</a>
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
    fun kaartverkoopWithNotice() {
        val html = page(
            "Kaartverkoop",
            loggedIn + box("ui-state-highlight", "Success", "Barcode 123456  stored in database"),
            """<h3>Kaartverkoop Eindfeest 2026</h3><form action="do_kaartverkoop.php" method="POST" accept-charset="UTF-8">
<p>Barcode: <input id="barcode" type="text" name="barcode" value="">
(<input type="checkbox" name="introducee" value="1"> introducee)
<input type="submit" value="Opslaan">
<p>De barcode moet een leerlingnummer zijn van minstens 6 cijfers.
</form>
<h4>De nieuwste mutaties</h4>
<table><tr><th>tijdstip</th><th>bron</th><th>doc</th><th>barcode</th><th>gast</th><th>introducee</th><th>status</th></tr>
<tr><td>2026-10-24 20:01:02</td><td><i>NULL</i></td><td>r.snel</td><td><b>123456</b></td><td>Jan de Vries (6V2)</td><td>ja</td><td><a href="do_delete.php?kaartverkoop_id=42">verwijderen</a></td></tr>
<tr><td>2026-10-24 19:58:00</td><td>web</td><td>r.snel/s.pieneman</td><td><b>207719</b></td><td>Piet  Jansen (5H1)</td><td>nee</td><td>verwijderd door r.snel op 2026-10-24 20:00:00</td></tr>
</table>
""",
        )
        val p = Html.parse(html, base)
        assertFalse(p.isLoginPage)
        assertEquals("Kaartverkoop Eindfeest 2026", p.heading)
        assertEquals("r.snel", p.loggedInAs)
        assertEquals(Message("Success", "Barcode 123456 stored in database", false), p.notice)
        assertNull(p.error)
        assertEquals(p.notice, p.message)
        assertTrue(p.hint!!.startsWith("De barcode moet"))
        assertEquals(listOf("tijdstip", "bron", "doc", "barcode", "gast", "introducee", "status"), p.columns)
        assertEquals(2, p.rows.size)
        val r0 = p.rows[0]
        assertEquals("", r0["bron"]!!.text)
        assertEquals("123456", r0["barcode"]!!.text)
        assertEquals("Jan de Vries (6V2)", r0["gast"]!!.text)
        assertEquals("https://srv-ovc-linux.ovc.nl/kaartscan/do_delete.php?kaartverkoop_id=42", r0["status"]!!.link)
        val r1 = p.rows[1]
        assertNull(r1["status"]!!.link)
        assertEquals("verwijderd door r.snel op 2026-10-24 20:00:00", r1["status"]!!.text)
        assertNull(p.counts)
    }

    @Test
    fun kaartcontroleWithErrorAndCounts() {
        val html = page(
            "Kaartcontrole",
            loggedIn + box("ui-state-error", "Dubbel", "Barcode 123456 is al binnen"),
            """
<h3>Overzocht lln die binnen zijn</h3>
        Aantallen: 3 (leerlingen: 3, introducees: )

<form action="do_kaartcontrole.php" accept-charset="utf-8" method="POST">
<p>Barcode: <input id="barcode" type="text" name="barcode">
<input type="submit" value="Controleren">
</form>
<h4>De nieuwste mutaties</h4>
<table><tr><th>tijdstip</th><th>doc</th><th>barcode</th><th>gast</th><th>introducee</th><th>status</th></tr>
<tr><td>2026-10-24 22:00:00</td><td>a.faber</td><td><b>123456</b></td><td><i>onbekend</i></td><td>nee</td><td><a href="do_delete_kaartcontrole.php?kaartcontrole_id=7">verwijderen</a></td></tr>
</table>
""",
        )
        val p = Html.parse(html, "https://srv-ovc-linux.ovc.nl/kaartscan/kaartcontrole.php")
        assertEquals(Message("Dubbel", "Barcode 123456 is al binnen", true), p.error)
        assertEquals(p.error, p.message)
        assertEquals(Counts("3", "3", "0"), p.counts)
        assertEquals("onbekend", p.rows[0]["gast"]!!.text)
        assertEquals(
            "https://srv-ovc-linux.ovc.nl/kaartscan/do_delete_kaartcontrole.php?kaartcontrole_id=7",
            p.rows[0]["status"]!!.link,
        )
    }

    @Test
    fun loginPageWithError() {
        val html = page(
            "Loginpagina",
            box("ui-state-error", "Ongeldige combi gebruikersnaam/wachtwoord", "Leerlingen loggen in met hun leerlingnummer."),
            """<h3>Kaartscan OVC</h3>
<h4>Inloggen met 'single-sign-on'</h4>
<form method="GET" accept-charset="UTF-8" action="https://srv-ovc-linux.ovc.nl/kaartscan/call.php">
Login(*): <input type="text" id="login" name="username" value="">
</form>""",
        )
        val p = Html.parse(html, base)
        assertTrue(p.isLoginPage)
        assertNull(p.loggedInAs)
        assertNotNull(p.error)
        assertEquals("Ongeldige combi gebruikersnaam/wachtwoord", p.error!!.title)
        assertEquals(
            "Ongeldige combi gebruikersnaam/wachtwoord: Leerlingen loggen in met hun leerlingnummer.",
            p.error.toString(),
        )
        assertTrue(p.rows.isEmpty())
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
