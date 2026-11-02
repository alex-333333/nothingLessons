package io.github.alex333333.bridge.data

import android.content.Context
import android.os.Build
import io.github.alex333333.bridge.protocol.BridgeApi
import io.github.alex333333.bridge.protocol.BridgeChannel
import io.github.alex333333.bridge.protocol.NotPairedException
import io.github.alex333333.bridge.protocol.PcRecord
import io.github.alex333333.bridge.protocol.ServerEvent
import io.github.alex333333.bridge.protocol.client.BridgeClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * The only door from the app to the PC. It builds a [BridgeClient] from the stored [PcRecord] and keeps it while
 * the record does not change. Everything here runs on [Dispatchers.IO]; callers may be on the main thread.
 */
class BridgeSession private constructor(context: Context) {
    val store = BridgeStore(context)

    /** The paired PC, or null. Starts as null until the store has been read. */
    val pc: StateFlow<PcRecord?> = store.pc.stateIn(
        CoroutineScope(SupervisorJob() + Dispatchers.Default),
        SharingStarted.Eagerly,
        null,
    )

    private val lock = Mutex()
    private var cachedFor: PcRecord? = null
    private var cached: BridgeClient? = null

    /** The client for the stored PC. Throws [NotPairedException] when the phone is not paired. */
    suspend fun client(): BridgeApi = lock.withLock {
        val record = store.pc.first() ?: throw NotPairedException("Телефон не привязан к ПК")
        if (record != cachedFor) {
            cached?.close()
            cached = BridgeClient(record.host, record.port, record.deviceId, record.token, record.fingerprintHex)
            cachedFor = record
        }
        cached!!
    }

    /** Runs [block] with the client on the IO dispatcher. */
    suspend fun <T> call(block: suspend (BridgeApi) -> T): T = withContext(Dispatchers.IO) { block(client()) }

    /** Pairs with the PC at [host]:[port] using the 6-digit [code], and stores the result. */
    suspend fun pair(host: String, port: Int, code: String): PcRecord = withContext(Dispatchers.IO) {
        val deviceId = store.deviceId()
        val fresh = BridgeClient(host, port, deviceId, null, null)
        try {
            val result = fresh.pair(code, Build.MODEL)
            val record = PcRecord(result.pcName, host, port, result.token, deviceId, result.fingerprintHex, result.macs)
            store.savePc(record)
            record
        } finally {
            fresh.close()
        }
    }

    /** True if the PC answers /hello. */
    suspend fun isOnline(): Boolean = try {
        call { it.hello() }
        true
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        false
    }

    /** Forgets the PC on this phone. */
    suspend fun unpair() = withContext(Dispatchers.IO) {
        try {
            client().unpair()
        } catch (_: NotPairedException) {
        }
        lock.withLock {
            cached?.close()
            cached = null
            cachedFor = null
        }
        store.clearPc()
    }

    /**
     * Events from the PC over one WebSocket, reconnecting every few seconds when it drops. Ends when the phone is
     * not paired. Collect it only while the app is in the foreground.
     */
    fun events(): Flow<ServerEvent> = flow {
        while (true) {
            val channel: BridgeChannel = try {
                call { it.openChannel() }
            } catch (e: CancellationException) {
                throw e
            } catch (_: NotPairedException) {
                return@flow
            } catch (_: Exception) {
                delay(RECONNECT_MILLIS)
                continue
            }
            try {
                emitAll(channel.events.catch { })
            } finally {
                channel.close()
            }
            delay(RECONNECT_MILLIS)
        }
    }

    companion object {
        private const val RECONNECT_MILLIS = 3_000L

        @Volatile
        private var instance: BridgeSession? = null

        fun get(context: Context): BridgeSession =
            instance ?: synchronized(this) {
                instance ?: BridgeSession(context.applicationContext).also { instance = it }
            }
    }
}
