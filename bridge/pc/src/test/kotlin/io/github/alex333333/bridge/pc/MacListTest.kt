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

    @Test fun currentIsCappedAtEight() {
        assertTrue(MacList.current().size <= 8)
    }

    @Test fun mergeKeepsOrderDropsBadAddressesDuplicatesAndCapsAtEight() {
        fun mac(n: Int) = byteArrayOf(0, 0, 0, 0, 0, n.toByte())
        val running = listOf(mac(1), null, byteArrayOf(1, 2, 3), mac(2))
        val idle = listOf(mac(2), mac(3), ByteArray(6), mac(4), mac(5), mac(6), mac(7), mac(8), mac(9), mac(10))
        val merged = MacList.merge(running, idle)
        assertEquals(
            listOf("00-00-00-00-00-01", "00-00-00-00-00-02", "00-00-00-00-00-03", "00-00-00-00-00-04", "00-00-00-00-00-05", "00-00-00-00-00-06", "00-00-00-00-00-07", "00-00-00-00-00-08"),
            merged,
        )
    }

    @Test fun localIpv4AddressesAreDottedQuadsWithoutDuplicates() {
        val addresses = LocalInterfaces.localIpv4Addresses()
        assertTrue(addresses.toString(), addresses.all { Regex("^\\d{1,3}(\\.\\d{1,3}){3}$").matches(it) })
        assertEquals(addresses.distinct(), addresses)
    }
}
