package io.github.alex333333.bridge.protocol

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class WakeOnLanTest {
    private val expected = byteArrayOf(0xAA.toByte(), 0xBB.toByte(), 0xCC.toByte(), 0xDD.toByte(), 0xEE.toByte(), 0xFF.toByte())

    @Test fun parsesDashSeparatedMac() = assertArrayEquals(expected, WakeOnLan.parseMac("AA-BB-CC-DD-EE-FF"))

    @Test fun parsesColonSeparatedLowercaseMac() = assertArrayEquals(expected, WakeOnLan.parseMac("aa:bb:cc:dd:ee:ff"))

    @Test(expected = IllegalArgumentException::class) fun rejectsTooShort() { WakeOnLan.parseMac("AA-BB") }

    @Test(expected = IllegalArgumentException::class) fun rejectsNonHex() { WakeOnLan.parseMac("ZZ-BB-CC-DD-EE-FF") }

    @Test(expected = IllegalArgumentException::class) fun rejectsEmpty() { WakeOnLan.parseMac("") }

    @Test fun magicPacketLayout() {
        val p = WakeOnLan.magicPacket(expected)
        assertEquals(102, p.size)
        for (i in 0 until 6) assertEquals(0xFF.toByte(), p[i])
        for (rep in 0 until 16) assertArrayEquals(expected, p.copyOfRange(6 + rep * 6, 12 + rep * 6))
    }

    @Test fun portIsNine() = assertEquals(9, WakeOnLan.PORT)
}
