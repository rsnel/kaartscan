package it.snel.kaartscan

import org.junit.Assert.assertEquals
import org.junit.Test

class VersionsTest {
    @Test
    fun newer() {
        assertEquals(true, Versions.isNewer("1.10", "1.9"))
        assertEquals(true, Versions.isNewer("v1.10", "1.9"))
        assertEquals(true, Versions.isNewer("2.0", "1.12"))
        assertEquals(true, Versions.isNewer("1.9.1", "1.9"))
        assertEquals(true, Versions.isNewer("1.10", "1.9-3-gabc1234"))
    }

    @Test
    fun notNewer() {
        assertEquals(false, Versions.isNewer("1.9", "1.9"))
        assertEquals(false, Versions.isNewer("1.9", "1.10"))
        assertEquals(false, Versions.isNewer("1.9", "1.9-3-gabc1234")) // built after v1.9
        assertEquals(false, Versions.isNewer("1.9", "1.9-dirty"))
        assertEquals(false, Versions.isNewer("1.9.0", "1.9"))
        assertEquals(false, Versions.isNewer("onzin", "1.9"))
    }

    @Test
    fun unknownCurrent() {
        assertEquals(null, Versions.isNewer("1.10", "dev"))
        assertEquals(null, Versions.isNewer("1.10", "f6a761f")) // describe without tags
        assertEquals(null, Versions.isNewer("1.10", "1234567"))
    }
}
