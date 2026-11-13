package com.mosquednd.updater

import org.junit.Assert.*
import org.junit.Test

class SemVerTest {

    @Test
    fun testSemVerParsing() {
        val v1 = SemVer.parse("v1.0.12")
        assertNotNull(v1)
        assertEquals(1, v1?.major)
        assertEquals(0, v1?.minor)
        assertEquals(12, v1?.patch)

        val v2 = SemVer.parse("1.2.3")
        assertNotNull(v2)
        assertEquals(1, v2?.major)
        assertEquals(2, v2?.minor)
        assertEquals(3, v2?.patch)

        val v3 = SemVer.parse("V2.1.0-rc1")
        assertNotNull(v3)
        assertEquals(2, v3?.major)
        assertEquals(1, v3?.minor)
        assertEquals(0, v3?.patch)
        assertEquals("rc1", v3?.preRelease)

        assertNull(SemVer.parse("invalid"))
        assertNull(SemVer.parse(""))
        assertNull(SemVer.parse(null))
    }

    @Test
    fun testSemVerComparison() {
        val v1 = SemVer.parse("1.0.2")!!
        val v2 = SemVer.parse("v1.0.12")!!
        val v3 = SemVer.parse("1.1.0")!!
        val v4 = SemVer.parse("2.0.0")!!
        val v5 = SemVer.parse("1.0.2-rc1")!!

        assertTrue(v2 > v1)
        assertTrue(v3 > v2)
        assertTrue(v4 > v3)
        assertTrue(v1 > v5) // version without pre-release is higher than with pre-release
        assertEquals(0, v1.compareTo(SemVer.parse("v1.0.2")!!))
    }
}
