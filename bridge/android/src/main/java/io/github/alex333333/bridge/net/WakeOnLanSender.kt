package io.github.alex333333.bridge.net

import android.content.Context
import android.net.ConnectivityManager
import io.github.alex333333.bridge.protocol.WakeOnLan
import io.github.alex333333.bridge.protocol.broadcastOf
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Sends Wake-on-LAN magic packets as UDP broadcasts. */
object WakeOnLanSender {
    /**
     * Sends a magic packet for every MAC in [macs] to 255.255.255.255:9 and, when the phone's Wi-Fi network is known,
     * to the broadcast address of that subnet. Returns the number of packets that left the phone.
     */
    suspend fun send(context: Context, macs: List<String>): Int = withContext(Dispatchers.IO) {
        val packets = macs.mapNotNull { text ->
            try {
                WakeOnLan.magicPacket(WakeOnLan.parseMac(text))
            } catch (_: IllegalArgumentException) {
                null
            }
        }
        if (packets.isEmpty()) return@withContext 0
        val targets = listOfNotNull(InetAddress.getByAddress(byteArrayOf(-1, -1, -1, -1)), subnetBroadcast(context))
        var sent = 0
        try {
            DatagramSocket().use { socket ->
                socket.setBroadcast(true)
                for (packet in packets) {
                    for (target in targets) {
                        try {
                            socket.send(DatagramPacket(packet, packet.size, target, WakeOnLan.PORT))
                            sent++
                        } catch (_: IOException) {
                            // This target is unreachable (for example no route); the others may still work.
                        }
                    }
                }
            }
        } catch (_: IOException) {
        }
        sent
    }

    /** The broadcast address of the active network's IPv4 subnet, or null if it cannot be found. */
    private fun subnetBroadcast(context: Context): InetAddress? {
        return try {
            val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = manager.activeNetwork ?: return null
            val properties = manager.getLinkProperties(network) ?: return null
            val link = properties.linkAddresses.firstOrNull { it.address is Inet4Address } ?: return null
            InetAddress.getByAddress(broadcastOf(link.address.address, link.prefixLength))
        } catch (_: Exception) {
            null
        }
    }
}
