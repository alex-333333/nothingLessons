package io.github.alex333333.bridge.pc

import java.io.Closeable
import java.io.IOException
import javax.jmdns.JmDNS
import javax.jmdns.ServiceInfo

/**
 * Announces the PC on the local network as `_bridge._tcp.local.` (name, port, TXT `id=<pcId>`) so the phone can find
 * it with NsdManager. One responder per local IPv4 address, so every adapter (Wi-Fi, Ethernet) carries the
 * announcement. Not unit-tested: it needs a real network.
 */
class Discovery(private val pcName: String, private val pcId: String, private val port: Int) : Closeable {
    private val instances = mutableListOf<JmDNS>()

    @Synchronized
    fun start() {
        if (instances.isNotEmpty()) return
        // Bind to the real LAN addresses; the default (the host name's address) is often a virtual adapter on Windows.
        val addresses = LocalInterfaces.upWithLocalIpv4().map { it.second }.distinctBy { it.hostAddress }
        if (addresses.isEmpty()) {
            instances += announce(null)
            return
        }
        var firstFailure: Exception? = null
        for (address in addresses) {
            try {
                instances += announce(address)
            } catch (e: Exception) {
                // One broken adapter must not silence the others.
                System.err.println("mDNS announcement on ${address.hostAddress} failed: $e")
                if (firstFailure == null) firstFailure = e
            }
        }
        if (instances.isEmpty()) throw IOException("mDNS announcement failed on every address", firstFailure)
    }

    /** Creates a responder (for [address], or the default one) and registers the service; closes it again on failure. */
    private fun announce(address: java.net.InetAddress?): JmDNS {
        val instance = if (address != null) JmDNS.create(address) else JmDNS.create()
        try {
            instance.registerService(ServiceInfo.create("_bridge._tcp.local.", pcName, port, 0, 0, mapOf("id" to pcId)))
        } catch (e: Exception) {
            runCatching { instance.close() }
            throw e
        }
        return instance
    }

    @Synchronized
    override fun close() {
        for (instance in instances) {
            runCatching {
                instance.unregisterAllServices()
                instance.close()
            }
        }
        instances.clear()
    }
}
