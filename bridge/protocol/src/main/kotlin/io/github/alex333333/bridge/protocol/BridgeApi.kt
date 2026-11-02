package io.github.alex333333.bridge.protocol

import java.io.Closeable
import java.io.InputStream
import java.io.OutputStream
import kotlinx.coroutines.flow.Flow

/** What a successful pairing yields. [fingerprintHex] is the SHA-256 of the PC certificate to pin. */
data class PairResult(val token: String, val pcName: String, val macs: List<String>, val fingerprintHex: String)

/** Thrown by the client when the PC rejects the saved credentials (the device is no longer paired). */
class NotPairedException(message: String = "Not paired") : Exception(message)

/** Client contract: the Android app is written against this before the real client exists. */
interface BridgeApi {
    suspend fun hello(): Hello
    suspend fun pair(code: String, deviceName: String): PairResult
    suspend fun notes(): List<Note>
    suspend fun putNote(note: Note)
    suspend fun deleteNote(id: String)
    suspend fun clipboard(): String
    suspend fun setClipboard(text: String)
    suspend fun upload(name: String, size: Long, open: () -> InputStream, onProgress: (Long) -> Unit): FileInfo
    suspend fun outbox(): List<FileInfo>
    suspend fun download(id: String, sink: OutputStream, onProgress: (Long) -> Unit)
    suspend fun deleteOutbox(id: String)
    fun openChannel(): BridgeChannel // WebSocket /ws
    suspend fun unpair()
}

interface BridgeChannel : Closeable {
    fun send(event: InputEvent)
    val events: Flow<ServerEvent>
}
