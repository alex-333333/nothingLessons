package io.github.alex333333.bridge.pc

import java.io.Closeable
import javax.jmdns.JmDNS
import javax.jmdns.ServiceInfo

/**
 * Announces the PC on the local network as `_bridge._tcp.local.` (name, port, TXT `id=<pcId>`) so the phone can find
 * it with NsdManager. Not unit-tested: it needs a real network.
 */
class Discovery(private val pcName: String, private val pcId: String, private val port: Int) : Closeable {
    private var jmdns: JmDNS? = null

    @Synchronized
    fun start() {
        if (jmdns != null) return
        // Bind to a real LAN address; the default (the host name's address) is often a virtual adapter on Windows.
        val address = LocalInterfaces.upWithLocalIpv4().firstOrNull()?.second
        val instance = if (address != null) JmDNS.create(address) else JmDNS.create()
        instance.registerService(ServiceInfo.create("_bridge._tcp.local.", pcName, port, 0, 0, mapOf("id" to pcId)))
        jmdns = instance
    }

    @Synchronized
    override fun close() {
        jmdns?.let {
            it.unregisterAllServices()
            it.close()
        }
        jmdns = null
    }
}
