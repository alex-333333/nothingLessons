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

private val HOST_CHARS = Regex("^[A-Za-z0-9._-]+$")

/**
 * "192.168.1.5" or "192.168.1.5:1234" (a name works as well) -> host and port; [defaultPort] when no port is given.
 * Null if the text is not a plain host or the port is not 1..65535.
 */
fun parseHostPort(text: String, defaultPort: Int = Api.PORT): Pair<String, Int>? {
    val parts = text.trim().split(':')
    if (parts.size > 2) return null
    val host = parts[0]
    if (!HOST_CHARS.matches(host)) return null
    val port = if (parts.size == 2) parts[1].toIntOrNull() ?: return null else defaultPort
    if (port !in 1..65535) return null
    return host to port
}

/** "1,5 КБ": a file size for people, binary units, one decimal. */
fun formatSize(bytes: Long): String {
    if (bytes < 1024) return "${bytes.coerceAtLeast(0)} Б"
    val units = listOf("КБ", "МБ", "ГБ", "ТБ")
    var value = bytes.toDouble()
    var unit = -1
    while (value >= 1024 && unit < units.lastIndex) {
        value /= 1024
        unit++
    }
    return String.format(java.util.Locale.ROOT, "%.1f", value).replace('.', ',') + " " + units[unit]
}
