package io.github.alex333333.bridge.net

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import io.github.alex333333.bridge.protocol.Api
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** A PC found on the local network. */
data class DiscoveredPc(val name: String, val host: String, val port: Int)

/** Looks for PCs announcing [Api.SERVICE_TYPE] with NsdManager. */
class Discovery(context: Context) {
    private val nsd = context.applicationContext.getSystemService(Context.NSD_SERVICE) as NsdManager

    /** Emits the current list of found PCs whenever it changes; discovery stops when the collector goes away. */
    fun discover(): Flow<List<DiscoveredPc>> = callbackFlow {
        val browser = Browser(nsd) { trySend(it) }
        browser.start()
        awaitClose { browser.stop() }
    }
}

/** NsdManager resolves one service at a time, so found services wait in a queue. */
private class Browser(
    private val nsd: NsdManager,
    private val onChange: (List<DiscoveredPc>) -> Unit,
) {
    private val lock = Any()
    private val found = LinkedHashMap<String, DiscoveredPc>()
    private val queue = ArrayDeque<NsdServiceInfo>()
    private var resolving = false
    private var stopped = false

    private val listener = object : NsdManager.DiscoveryListener {
        override fun onDiscoveryStarted(serviceType: String) {}
        override fun onDiscoveryStopped(serviceType: String) {}
        override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {}
        override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}

        override fun onServiceFound(serviceInfo: NsdServiceInfo) {
            synchronized(lock) { queue.addLast(serviceInfo) }
            resolveNext()
        }

        override fun onServiceLost(serviceInfo: NsdServiceInfo) {
            synchronized(lock) { found.remove(serviceInfo.serviceName) }
            publish()
        }
    }

    fun start() {
        try {
            nsd.discoverServices(Api.SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
        } catch (_: IllegalArgumentException) {
        }
    }

    fun stop() {
        synchronized(lock) { stopped = true }
        try {
            nsd.stopServiceDiscovery(listener)
        } catch (_: IllegalArgumentException) {
        }
    }

    private fun publish() {
        val list = synchronized(lock) { found.values.toList() }
        onChange(list)
    }

    // resolveService and NsdServiceInfo.host are deprecated from API 34 but still work on every supported version.
    @Suppress("DEPRECATION")
    private fun resolveNext() {
        val next = synchronized(lock) {
            if (stopped || resolving) null else queue.removeFirstOrNull()?.also { resolving = true }
        }
        if (next == null) return
        try {
            nsd.resolveService(next, object : NsdManager.ResolveListener {
                override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                    resolveDone()
                }

                override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                    val address = serviceInfo.host
                    if (address != null && !address.isLinkLocalAddress && serviceInfo.port > 0) {
                        val host = (address.hostAddress ?: "").substringBefore('%')
                        if (host.isNotEmpty()) {
                            synchronized(lock) {
                                found[serviceInfo.serviceName] = DiscoveredPc(serviceInfo.serviceName, host, serviceInfo.port)
                            }
                            publish()
                        }
                    }
                    resolveDone()
                }
            })
        } catch (_: IllegalArgumentException) {
            resolveDone()
        }
    }

    private fun resolveDone() {
        synchronized(lock) { resolving = false }
        resolveNext()
    }
}
