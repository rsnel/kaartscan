package it.snel.kaartscan

/** Same rules as do_kaartverkoop.php / do_kaartcontrole.php. */
object Barcodes {
    private val leerlingnummer = Regex("^\\d{6,}$")
    private val galaCode = Regex("^\\*20\\d\\dGALA\\d\\d\\d\\*$")
    private val galaCodeBare = Regex("^20\\d\\dGALA\\d\\d\\d$")

    /**
     * The hand scanner sends the Code 39 start/stop characters of the gala
     * introducee codes (*2023GALA123*); a camera decoder strips them, and
     * someone typing the code will probably leave them out, so add them back.
     * The server strips leading '~' itself, we do the same so validation matches.
     */
    fun normalize(raw: String): String {
        var s = raw.trim().trimStart('~')
        if (galaCodeBare.matches(s)) s = "*$s*"
        return s
    }

    fun isValid(code: String): Boolean = leerlingnummer.matches(code) || galaCode.matches(code)
}
