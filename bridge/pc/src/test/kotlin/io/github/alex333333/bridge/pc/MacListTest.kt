package io.github.alex333333.bridge.pc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MacListTest {
    private val format = Regex("^[0-9A-F]{2}(-[0-9A-F]{2}){5}$")

    @Test fun formatsBytesAsDashSeparatedUpperCaseHex() {
        assertEquals("00-1A-2B-3C-4D-FF", MacList.format(byteArrayOf(0x00, 0x1A, 0x2B, 0x3C, 0x4D, 0xFF.toByte())))
    }

    @Test fun everyReturnedMacHasTheWakeOnLanFormat() {
        val macs = MacList.current()
        assertTrue(macs.toString(), macs.all { format.matches(it) })
        assertEquals(macs.distinct(), macs)
    }
}
