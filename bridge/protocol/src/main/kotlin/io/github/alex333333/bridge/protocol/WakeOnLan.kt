package io.github.alex333333.bridge.protocol

object WakeOnLan {
    const val PORT = 9

    /** Accepts "AA-BB-CC-DD-EE-FF" or "aa:bb:cc:dd:ee:ff". */
    fun parseMac(text: String): ByteArray {
        val parts = text.trim().split('-', ':')
        require(parts.size == 6) { "MAC address must have 6 parts: $text" }
        return ByteArray(6) { i ->
            val part = parts[i]
            require(part.length == 2 && part.all { it.digitToIntOrNull(16) != null }) { "Bad MAC address: $text" }
            part.toInt(16).toByte()
        }
    }

    /** 6 bytes of 0xFF followed by the MAC repeated 16 times (102 bytes). */
    fun magicPacket(mac: ByteArray): ByteArray {
        require(mac.size == 6) { "MAC address must be 6 bytes" }
        val packet = ByteArray(6 + 16 * 6)
        for (i in 0 until 6) packet[i] = 0xFF.toByte()
        for (rep in 0 until 16) mac.copyInto(packet, 6 + rep * 6)
        return packet
    }
}
