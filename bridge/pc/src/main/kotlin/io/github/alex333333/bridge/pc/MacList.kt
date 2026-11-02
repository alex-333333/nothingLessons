package io.github.alex333333.bridge.pc

import io.github.alex333333.bridge.protocol.isLocalAddress
import java.net.Inet4Address
import java.net.NetworkInterface

/** The network interfaces that are up and have an IPv4 address in a local range. */
internal object LocalInterfaces {
    fun upWithLocalIpv4(): List<Pair<NetworkInterface, Inet4Address>> {
        val result = mutableListOf<Pair<NetworkInterface, Inet4Address>>()
        val all = try {
            NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
        } catch (_: java.net.SocketException) {
            emptyList()
        }
        for (nic in all) {
            try {
                if (!nic.isUp || nic.isLoopback || nic.isVirtual) continue
                val address = nic.inetAddresses.toList().filterIsInstance<Inet4Address>()
                    .firstOrNull { !it.isLoopbackAddress && isLocalAddress(it) } ?: continue
                result += nic to address
            } catch (_: java.net.SocketException) {
                // The interface vanished while being inspected; skip it.
            }
        }
        return result
    }
}

object MacList {
    /** MAC addresses ("AA-BB-CC-DD-EE-FF") of the running, non-loopback interfaces with a local IPv4 address. */
    fun current(): List<String> =
        LocalInterfaces.upWithLocalIpv4()
            .mapNotNull { (nic, _) -> nic.hardwareAddress?.takeIf { it.size == 6 }?.let(::format) }
            .distinct()

    internal fun format(bytes: ByteArray): String = bytes.joinToString("-") { "%02X".format(it.toInt() and 0xFF) }
}
