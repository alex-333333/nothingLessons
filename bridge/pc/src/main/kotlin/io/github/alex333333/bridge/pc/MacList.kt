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

    /** Dotted-quad IPv4 addresses of [upWithLocalIpv4], without duplicates: what to type on the phone. */
    fun localIpv4Addresses(): List<String> = upWithLocalIpv4().mapNotNull { it.second.hostAddress }.distinct()

    /** Hardware addresses of every non-loopback, non-virtual interface, up or down. */
    fun physicalHardwareAddresses(): List<ByteArray> {
        val all = try {
            NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
        } catch (_: java.net.SocketException) {
            emptyList()
        }
        return all.mapNotNull { nic ->
            try {
                if (nic.isLoopback || nic.isVirtual) null else nic.hardwareAddress?.takeIf { it.size == 6 }
            } catch (_: java.net.SocketException) {
                null
            }
        }
    }
}

object MacList {
    private const val MAX_MACS = 8

    /**
     * MAC addresses ("AA-BB-CC-DD-EE-FF") for Wake-on-LAN: first those of the running interfaces with a local IPv4
     * address, then those of the other physical interfaces (a cable that is unplugged now still wakes the PC later).
     */
    fun current(): List<String> =
        merge(
            LocalInterfaces.upWithLocalIpv4().map { (nic, _) -> nic.hardwareAddress },
            LocalInterfaces.physicalHardwareAddresses(),
        )

    /** Formats, drops anything that is not 6 bytes or all zeros, removes duplicates and keeps the first eight. */
    internal fun merge(primary: List<ByteArray?>, secondary: List<ByteArray?>): List<String> =
        (primary + secondary)
            .filter { it != null && it.size == 6 && it.any { b -> b.toInt() != 0 } }
            .map { format(it!!) }
            .distinct()
            .take(MAX_MACS)

    internal fun format(bytes: ByteArray): String = bytes.joinToString("-") { "%02X".format(it.toInt() and 0xFF) }
}
