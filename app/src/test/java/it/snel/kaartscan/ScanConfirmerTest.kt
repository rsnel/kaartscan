package it.snel.kaartscan

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanConfirmerTest {
    @Test
    fun oneReadIsEnoughForTolerant() {
        val c = ScanConfirmer()
        assertTrue(c.confirm("213874", 1, 0))
    }

    @Test
    fun needsIdenticalReadsInARow() {
        val c = ScanConfirmer()
        assertFalse(c.confirm("213874", 3, 0))
        assertFalse(c.confirm("213874", 3, 50))
        assertTrue(c.confirm("213874", 3, 100))
        // counting starts over after a confirmation
        assertFalse(c.confirm("213874", 3, 150))
    }

    @Test
    fun aDifferentValueResets() {
        val c = ScanConfirmer()
        assertFalse(c.confirm("213874", 2, 0))
        assertFalse(c.confirm("218874", 2, 40)) // misread
        assertFalse(c.confirm("213874", 2, 80))
        assertTrue(c.confirm("213874", 2, 120))
    }

    @Test
    fun aLongPauseResets() {
        val c = ScanConfirmer(windowMs = 700)
        assertFalse(c.confirm("213874", 2, 0))
        assertFalse(c.confirm("213874", 2, 2000))
        assertTrue(c.confirm("213874", 2, 2100))
    }

    @Test
    fun defaultsAndNames() {
        assertTrue(ScanSensitivity.fromName(null) == ScanSensitivity.NORMAAL)
        assertTrue(ScanSensitivity.fromName("STRENG") == ScanSensitivity.STRENG)
        assertTrue(ScanSensitivity.fromName("onzin") == ScanSensitivity.NORMAAL)
    }
}
