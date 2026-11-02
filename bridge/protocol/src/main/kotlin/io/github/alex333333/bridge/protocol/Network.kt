package io.github.alex333333.bridge.protocol

/**
 * The IPv4 broadcast address of the network that [ip] belongs to: the host bits (all but the first [prefixLength]
 * bits) set to one. /32 gives the address itself, /0 gives 255.255.255.255.
 */
fun broadcastOf(ip: ByteArray, prefixLength: Int): ByteArray {
    require(ip.size == 4) { "An IPv4 address has 4 bytes, got ${ip.size}" }
    require(prefixLength in 0..32) { "Prefix length must be 0..32, got $prefixLength" }
    return ByteArray(4) { i ->
        val hostBits = (prefixLength - i * 8).coerceIn(0, 8) // network bits of this octet
        val hostMask = 0xFF shr hostBits
        (ip[i].toInt() or hostMask).toByte()
    }
}
