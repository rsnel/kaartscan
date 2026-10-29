package it.snel.kaartscan

/**
 * How picky the camera scanner is. ML Kit has no confidence threshold, so
 * the app filters itself: a barcode only counts when the camera reads the
 * same value [reads] times in a row. A misread caused by noise, glare or
 * blur rarely repeats exactly, so more reads means fewer wrong numbers, at
 * the cost of a slightly slower scan of a worn card.
 */
enum class ScanSensitivity(
    val reads: Int,
    /** silently skip codes that cannot be a leerlingnummer, instead of showing an error */
    val ignoreInvalid: Boolean,
    val label: String,
    val description: String,
) {
    TOLERANT(
        1, false, "Tolerant",
        "Eén keer lezen is genoeg. Het snelst, ook bij een versleten pas, maar bij een slecht beeld " +
            "kan af en toe een verkeerd nummer doorkomen.",
    ),
    NORMAAL(
        2, false, "Normaal",
        "De camera moet de barcode twee keer achter elkaar hetzelfde lezen. Aanbevolen.",
    ),
    STRENG(
        3, true, "Streng",
        "Drie keer achter elkaar hetzelfde, en codes die geen leerlingnummer zijn worden genegeerd " +
            "in plaats van een foutmelding te geven. De minste fouten, iets trager bij een slechte pas.",
    ),
    ;

    companion object {
        val DEFAULT = NORMAAL

        fun fromName(name: String?): ScanSensitivity = entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}

/**
 * Counts identical reads in a row. Frames without a barcode do not reset
 * it; a different value does, and so does a pause longer than [windowMs].
 */
class ScanConfirmer(private val windowMs: Long = 700) {
    private var candidate: String? = null
    private var count = 0
    private var lastAt = 0L

    /** true when [code] has now been read [needed] times in a row */
    fun confirm(code: String, needed: Int, now: Long): Boolean {
        if (code == candidate && now - lastAt <= windowMs) count++
        else {
            candidate = code
            count = 1
        }
        lastAt = now
        if (count < needed) return false
        candidate = null
        count = 0
        return true
    }

    fun reset() {
        candidate = null
        count = 0
    }
}
