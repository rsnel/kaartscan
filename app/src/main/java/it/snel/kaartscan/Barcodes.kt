package it.snel.kaartscan

/** Same rule as do_kaartverkoop.php / do_kaartcontrole.php: a leerlingnummer. */
object Barcodes {
    private val leerlingnummer = Regex("^\\d{6,}$")

    /** The server strips leading '~' itself, we do the same so validation matches. */
    fun normalize(raw: String): String = raw.trim().trimStart('~')

    fun isValid(code: String): Boolean = leerlingnummer.matches(code)
}
