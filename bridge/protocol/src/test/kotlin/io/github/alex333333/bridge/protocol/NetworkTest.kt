package io.github.alex333333.bridge.protocol

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class NetworkTest {
    private fun ip(a: Int, b: Int, c: Int, d: Int) = byteArrayOf(a.toByte(), b.toByte(), c.toByte(), d.toByte())

    @Test
    fun `a slash 24 network broadcasts to the last octet`() {
        assertArrayEquals(ip(192, 168, 1, 255), broadcastOf(ip(192, 168, 1, 37), 24))
    }

    @Test
    fun `a slash 16 network broadcasts to the last two octets`() {
        assertArrayEquals(ip(10, 1, 255, 255), broadcastOf(ip(10, 1, 2, 3), 16))
    }

    @Test
    fun `a slash 32 network is the address itself`() {
        assertArrayEquals(ip(10, 1, 2, 3), broadcastOf(ip(10, 1, 2, 3), 32))
    }

    @Test
    fun `a slash 0 network is the limited broadcast`() {
        assertArrayEquals(ip(255, 255, 255, 255), broadcastOf(ip(10, 1, 2, 3), 0))
    }

    @Test
    fun `a prefix that is not a whole number of octets works`() {
        assertArrayEquals(ip(172, 16, 15, 255), broadcastOf(ip(172, 16, 3, 9), 20))
    }

    @Test
    fun `high address bytes are treated as unsigned`() {
        assertArrayEquals(ip(200, 200, 200, 255), broadcastOf(ip(200, 200, 200, 7), 24))
    }

    @Test
    fun `the input is not modified`() {
        val address = ip(192, 168, 1, 37)
        broadcastOf(address, 24)
        assertArrayEquals(ip(192, 168, 1, 37), address)
    }

    @Test
    fun `a prefix outside 0 to 32 is rejected`() {
        assertThrows(IllegalArgumentException::class.java) { broadcastOf(ip(1, 2, 3, 4), -1) }
        assertThrows(IllegalArgumentException::class.java) { broadcastOf(ip(1, 2, 3, 4), 33) }
    }

    @Test
    fun `an address that is not four bytes is rejected`() {
        assertThrows(IllegalArgumentException::class.java) { broadcastOf(ByteArray(16), 24) }
        assertThrows(IllegalArgumentException::class.java) { broadcastOf(ByteArray(3), 24) }
    }
}
