package io.github.alex333333.bridge.pc

import io.github.alex333333.bridge.protocol.Api
import io.github.alex333333.bridge.protocol.BridgeJson
import io.github.alex333333.bridge.protocol.ClipText
import io.github.alex333333.bridge.protocol.FileInfo
import io.github.alex333333.bridge.protocol.Fingerprint
import io.github.alex333333.bridge.protocol.Hello
import io.github.alex333333.bridge.protocol.InputEvent
import io.github.alex333333.bridge.protocol.Note
import io.github.alex333333.bridge.protocol.PairRequest
import io.github.alex333333.bridge.protocol.PairResponse
import io.github.alex333333.bridge.protocol.PairingSession
import io.github.alex333333.bridge.protocol.PowerAction
import io.github.alex333333.bridge.protocol.ServerEvent
import io.github.alex333333.bridge.protocol.Signer
import io.github.alex333333.bridge.protocol.isLocalAddress
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.withCharset
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.engine.sslConnector
import io.ktor.server.http.content.LocalFileContent
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.origin
import io.ktor.server.request.contentLength
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.server.request.receiveChannel
import io.ktor.server.request.uri
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.routing
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.util.AttributeKey
import io.ktor.utils.io.discard
import io.ktor.utils.io.readRemaining
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.io.readByteArray
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import java.io.IOException
import java.net.InetAddress
import java.nio.file.Path
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/** What the phone shows after the PC announces a fresh code: the digits to type and the certificate check value. */
data class PairingCode(val code: String, val fingerprintShort: String)

/**
 * The Bridge HTTPS + WebSocket server.
 *
 * Request pipeline, in this order, for every request:
 *  1. [peerAllowed] on the remote address (403), before anything else, even for /hello;
 *  2. only `GET /hello` and `POST /pair` continue without credentials; everything else, including unknown paths,
 *     must carry a valid HMAC signature of a paired device (401), checked before any handler or WebSocket upgrade runs.
 */
class BridgeServer(
    dataDir: Path,
    downloadsDir: Path,
    private val platform: PlatformHooks,
    private val pcName: String,
    /** Asked for every pairing, so the answer reflects the adapters of that moment. */
    private val macs: () -> List<String>,
    private val now: () -> Long = System::currentTimeMillis,
    private val peerAllowed: (InetAddress) -> Boolean = ::isLocalAddress,
) {
    private val identity = TlsIdentity.loadOrCreate(dataDir)
    private val devices = DeviceStore(dataDir)
    private val notes = NotesStore(dataDir)
    private val files = FileStore(downloadsDir, dataDir)

    /** One-way hints to every open channel. Dropping the oldest on overflow is fine: each event just means "look again". */
    private val events = MutableSharedFlow<ServerEvent>(extraBufferCapacity = 64, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    @Volatile private var pairing: PairingSession? = null
    private val notesListeners = CopyOnWriteArrayList<() -> Unit>()

    /** deviceId -> time of the last "connected" balloon, to keep a reconnecting phone quiet. */
    private val connectedNoticeAt = ConcurrentHashMap<String, Long>()
    private var engine: EmbeddedServer<*, *>? = null

    val tlsFingerprintHex: String get() = identity.fingerprintHex

    /** The id other apps see in `/hello` and in the mDNS record. */
    val pcId: String get() = devices.pcId

    @Synchronized
    fun start(port: Int = Api.PORT): Int {
        check(engine == null) { "Already started" }
        val server = embeddedServer(
            Netty,
            configure = {
                // HTTP/1.1 only: reading the (empty) body of a bodiless request never completes over h2 on Netty 3.1.3.
                enableHttp2 = false
                sslConnector(identity.keyStore, identity.alias, { identity.password }, { identity.password }) {
                    this.port = port
                    host = "0.0.0.0"
                }
            },
            module = { module() },
        )
        server.start(wait = false)
        engine = server
        return runBlocking { server.engine.resolvedConnectors().first().port }
    }

    @Synchronized
    fun stop() {
        engine?.stop(gracePeriodMillis = 300, timeoutMillis = 2_000)
        engine = null
    }

    /** Makes a fresh one-shot code; it replaces the previous one. */
    fun newPairingCode(): PairingCode {
        val session = PairingSession(PairingSession.newCode(), now)
        pairing = session
        return PairingCode(session.code, Fingerprint.short(identity.fingerprintHex))
    }

    // ---- notes, as seen from the PC's own window ----------------------------------------------------------------

    fun listNotes(): List<Note> = notes.list()

    /** Creates a note (when [id] is null) or replaces the text of an existing one; connected phones are told. */
    fun saveNote(id: String?, text: String): Note {
        val note = Note(id ?: UUID.randomUUID().toString(), text, now())
        notes.put(note)
        notesChanged()
        return note
    }

    fun deleteNote(id: String) {
        if (notes.delete(id)) notesChanged()
    }

    /** [listener] runs (on an arbitrary thread) after every change of the notes, wherever it came from. */
    fun onNotesChanged(listener: () -> Unit) {
        notesListeners += listener
    }

    private fun notesChanged() {
        events.tryEmit(ServerEvent.NotesChanged)
        for (listener in notesListeners) runCatching(listener)
    }

    /** Offers [file] to the phone (it stays where it is) and tells connected phones. */
    fun sendToPhone(file: Path) {
        files.addToOutbox(file)
        events.tryEmit(ServerEvent.OutboxChanged)
    }

    /** Forgets every token. Connected phones are told and disconnected. */
    fun unpairAll() {
        pairing = null
        devices.clear()
        events.tryEmit(ServerEvent.Unpaired)
    }

    // ---- request gate ------------------------------------------------------------------------------------------

    private class Caller(val deviceId: String, val token: String)

    private companion object {
        val CALLER = AttributeKey<Caller>("bridge.caller")
        val BODY = AttributeKey<ByteArray>("bridge.body")
        const val MAX_JSON_BYTES = 1_048_576
        const val MAX_PAIR_BYTES = 8 * 1_024
        const val MAX_DRAIN_BYTES = 16L * 1_024 * 1_024
        const val MAX_FRAME_BYTES = 1L shl 20
        const val MAX_ID_LENGTH = 100
        const val CONNECTED_NOTICE_INTERVAL_MS = 10 * 60_000L
        val LITERAL_ADDRESS = Regex("^([0-9.]+|[0-9A-Fa-f:.]+(%[0-9A-Za-z_.-]+)?)$")
        val DEVICE_ID = Regex("^[A-Za-z0-9._-]{1,$MAX_ID_LENGTH}$")
        val EMPTY_BODY_HASH = Signer.sha256Hex(ByteArray(0))
        val JSON_UTF8 = ContentType.Application.Json.withCharset(Charsets.UTF_8)
    }

    private fun Application.module() {
        install(WebSockets) {
            pingPeriodMillis = 30_000
            timeoutMillis = 60_000
            maxFrameSize = MAX_FRAME_BYTES
        }

        intercept(ApplicationCallPipeline.Plugins) {
            if (!peerIsAllowed(call)) {
                call.response.header(HttpHeaders.Connection, "close")
                call.respond(HttpStatusCode.Forbidden)
                finish()
                return@intercept
            }
            if (isPublic(call)) return@intercept
            val status = authenticate(call)
            if (status != null) {
                reject(call, status)
                finish()
            }
        }

        routing {
            get(Api.HELLO) { call.respondJson(BridgeJson.encodeToString(Hello.serializer(), Hello(devices.pcId, pcName))) }
            post(Api.PAIR) { handlePair(call) }

            get(Api.NOTES) { call.respondJson(BridgeJson.encodeToString(ListSerializer(Note.serializer()), notes.list())) }
            put("${Api.NOTES}/{id}") { handlePutNote(call) }
            delete("${Api.NOTES}/{id}") {
                val id = call.parameters["id"].orEmpty()
                if (notes.delete(id)) {
                    notesChanged()
                    call.respond(HttpStatusCode.OK)
                } else {
                    call.respond(HttpStatusCode.NotFound)
                }
            }

            get(Api.CLIPBOARD) {
                val text = withContext(Dispatchers.IO) { platform.read() }
                call.respondJson(BridgeJson.encodeToString(ClipText.serializer(), ClipText(text)))
            }
            put(Api.CLIPBOARD) {
                val clip = decodeBody(call, ClipText.serializer()) ?: return@put
                withContext(Dispatchers.IO) { platform.write(clip.text) }
                call.respond(HttpStatusCode.OK)
            }

            post(Api.FILES) { handleUpload(call) }
            get(Api.OUTBOX) { call.respondJson(BridgeJson.encodeToString(ListSerializer(FileInfo.serializer()), files.listOutbox())) }
            get("${Api.OUTBOX}/{id}") {
                val file = files.outboxFile(call.parameters["id"].orEmpty())
                if (file == null) call.respond(HttpStatusCode.NotFound)
                else call.respond(LocalFileContent(file.toFile(), ContentType.Application.OctetStream))
            }
            delete("${Api.OUTBOX}/{id}") {
                if (files.removeFromOutbox(call.parameters["id"].orEmpty())) {
                    events.tryEmit(ServerEvent.OutboxChanged)
                    call.respond(HttpStatusCode.OK)
                } else {
                    call.respond(HttpStatusCode.NotFound)
                }
            }

            webSocket(Api.WS) { handleChannel(call, this) }
        }
    }

    private fun peerIsAllowed(call: ApplicationCall): Boolean = try {
        val host = call.request.origin.remoteAddress
        // Only literal addresses are parsed, so no DNS lookup can ever happen here.
        LITERAL_ADDRESS.matches(host) && peerAllowed(InetAddress.getByName(host))
    } catch (_: Exception) {
        false
    }

    /** Exactly two routes are reachable without credentials. */
    private fun isPublic(call: ApplicationCall): Boolean {
        val path = call.request.path()
        val method = call.request.httpMethod
        return (method == HttpMethod.Get && path == Api.HELLO) || (method == HttpMethod.Post && path == Api.PAIR)
    }

    /**
     * Verifies the signature headers. Returns null on success (the caller is recorded on the call, and for
     * non-streamed routes the already verified body too), or the HTTP status to refuse with.
     */
    private suspend fun authenticate(call: ApplicationCall): HttpStatusCode? {
        val deviceId = call.request.headers[Api.HDR_DEVICE]
        val time = call.request.headers[Api.HDR_TIME]?.toLongOrNull()
        val signature = call.request.headers[Api.HDR_SIG]
        if (deviceId.isNullOrBlank() || time == null || signature.isNullOrBlank()) return HttpStatusCode.Unauthorized
        // A blank token must never reach Signer.verify (it cannot be used as an HMAC key).
        val token = devices.tokenFor(deviceId)?.takeIf { it.isNotBlank() } ?: return HttpStatusCode.Unauthorized

        val method = call.request.httpMethod
        val path = call.request.path()
        val acceptedHashes: List<String> = when {
            // Streamed upload: the body is not hashed, the signature covers method, path+query and time.
            method == HttpMethod.Post && path == Api.FILES -> listOf(Api.UNSIGNED_BODY)
            // Streamed download: has no body, so either marker is accepted.
            method == HttpMethod.Get && path.startsWith("${Api.OUTBOX}/") -> listOf(Api.UNSIGNED_BODY, EMPTY_BODY_HASH)
            else -> {
                val body = readBounded(call, MAX_JSON_BYTES) ?: return HttpStatusCode.PayloadTooLarge
                call.attributes.put(BODY, body)
                listOf(Signer.sha256Hex(body))
            }
        }
        // The signed path is the request target as sent, query string included.
        val target = call.request.uri
        val ok = acceptedHashes.fold(false) { acc, hash ->
            Signer.verify(token, method.value, target, time, hash, signature, now()) or acc
        }
        if (!ok) return HttpStatusCode.Unauthorized
        call.attributes.put(CALLER, Caller(deviceId, token))
        return null
    }

    /** Reads at most [max] bytes; null if the body is larger. */
    private suspend fun readBounded(call: ApplicationCall, max: Int): ByteArray? {
        val declared = call.request.contentLength()
        if (declared != null && declared > max) return null
        val bytes = call.receiveChannel().readRemaining(max + 1L).readByteArray()
        return if (bytes.size > max) null else bytes
    }

    /**
     * Refuses a request. The unread part of a body is drained (bounded) first so that the client, which is probably
     * still sending, can read the answer instead of seeing a reset connection.
     */
    private suspend fun reject(call: ApplicationCall, status: HttpStatusCode) {
        runCatching {
            val declared = call.request.contentLength()
            if (declared == null || declared > 0) {
                withTimeoutOrNull(5_000) { call.receiveChannel().discard(MAX_DRAIN_BYTES) }
            }
        }
        call.response.header(HttpHeaders.Connection, "close")
        call.respond(status)
    }

    private suspend fun ApplicationCall.respondJson(text: String) = respondText(text, JSON_UTF8)

    /** The verified body of the current call (stored by the gate), decoded; answers 400 and returns null if it is not valid. */
    private suspend fun <T> decodeBody(call: ApplicationCall, serializer: KSerializer<T>): T? {
        val body = call.attributes.getOrNull(BODY)
        val value = try {
            body?.let { BridgeJson.decodeFromString(serializer, String(it, Charsets.UTF_8)) }
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
        if (value == null) call.respond(HttpStatusCode.BadRequest)
        return value
    }

    // ---- handlers ----------------------------------------------------------------------------------------------

    private suspend fun handlePair(call: ApplicationCall) {
        val body = readBounded(call, MAX_PAIR_BYTES)
        if (body == null) {
            reject(call, HttpStatusCode.PayloadTooLarge)
            return
        }
        val request = try {
            BridgeJson.decodeFromString(PairRequest.serializer(), String(body, Charsets.UTF_8))
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
        // Malformed requests are refused before they can use up an attempt of the code.
        if (request == null || !DEVICE_ID.matches(request.deviceId)) {
            call.respond(HttpStatusCode.BadRequest)
            return
        }
        val session = pairing
        if (session == null || !session.tryCode(request.code)) {
            call.respond(HttpStatusCode.Forbidden)
            return
        }
        val name = cleanName(request.deviceName)
        val token = devices.pair(request.deviceId, name)
        platform.notify("Телефон привязан: $name")
        call.respondJson(BridgeJson.encodeToString(PairResponse.serializer(), PairResponse(token, pcName, macs())))
    }

    /** Device names end up in tray messages: drop control characters and keep them short. */
    private fun cleanName(raw: String): String {
        val cleaned = raw.filter { it >= ' ' && it != '\u007f' }.trim()
        val short = cleaned.codePoints().limit(64).toArray().let { String(it, 0, it.size) }
        return short.ifBlank { "Phone" }
    }

    private suspend fun handlePutNote(call: ApplicationCall) {
        val id = call.parameters["id"].orEmpty()
        val note = decodeBody(call, Note.serializer()) ?: return
        if (id.isBlank() || id.length > MAX_ID_LENGTH || note.id != id) {
            call.respond(HttpStatusCode.BadRequest)
            return
        }
        val isNew = notes.put(note)
        if (isNew) platform.notify("Новая заметка с телефона")
        notesChanged()
        call.respond(HttpStatusCode.OK)
    }

    private suspend fun handleUpload(call: ApplicationCall) {
        val declared = call.request.contentLength()
        // The length must be declared: with a chunked body a dropped connection looks like a clean end of the file.
        if (declared == null || call.request.headers.contains(HttpHeaders.TransferEncoding)) {
            reject(call, HttpStatusCode.LengthRequired)
            return
        }
        if (declared == 0L) {
            call.respond(HttpStatusCode.BadRequest)
            return
        }
        try {
            val info = files.receive(call.request.queryParameters["name"].orEmpty(), declared, call.receiveChannel())
            call.respondJson(BridgeJson.encodeToString(FileInfo.serializer(), info))
        } catch (e: UploadRejected) {
            reject(call, HttpStatusCode.BadRequest)
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            // The client went away mid-upload (or the disk failed); the partial file is already gone.
            runCatching { call.respond(HttpStatusCode.InternalServerError) }
        }
    }

    /** A phone that reconnects every few seconds must not flood the tray: one balloon per device per 10 minutes. */
    private fun announceConnection(deviceId: String) {
        val time = now()
        var show = false
        connectedNoticeAt.compute(deviceId) { _, last ->
            if (last == null || time - last >= CONNECTED_NOTICE_INTERVAL_MS || time < last) {
                show = true
                time
            } else {
                last
            }
        }
        if (show) platform.notify("Телефон подключён: ${devices.nameFor(deviceId) ?: "Phone"}")
    }

    private suspend fun handleChannel(call: ApplicationCall, session: DefaultWebSocketServerSession) {
        val caller = call.attributes[CALLER]
        announceConnection(caller.deviceId)
        val forwarder = session.launch(start = CoroutineStart.UNDISPATCHED) {
            events.collect { event ->
                session.send(Frame.Text(BridgeJson.encodeToString(ServerEvent.serializer(), event)))
                if (event is ServerEvent.Unpaired) session.close(CloseReason(CloseReason.Codes.NORMAL, "Unpaired"))
            }
        }
        try {
            for (frame in session.incoming) {
                if (frame !is Frame.Text) continue
                // A revoked or replaced token ends the channel at once.
                val current = devices.tokenFor(caller.deviceId)
                if (current == null || !MessageDigest.isEqual(current.toByteArray(), caller.token.toByteArray())) {
                    session.close(CloseReason(CloseReason.Codes.NORMAL, "Unpaired"))
                    break
                }
                handleInput(frame.readText())
            }
        } finally {
            forwarder.cancel()
        }
    }

    private suspend fun handleInput(text: String) {
        val event = try {
            BridgeJson.decodeFromString(InputEvent.serializer(), text)
        } catch (_: SerializationException) {
            return
        } catch (_: IllegalArgumentException) {
            return
        }
        withContext(Dispatchers.IO) {
            try {
                dispatch(event)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // One failing key press must not take the channel down.
            }
        }
    }

    private fun dispatch(event: InputEvent) {
        when (event) {
            is InputEvent.MouseMove -> platform.mouseMove(event.dx.coerceIn(-10_000, 10_000), event.dy.coerceIn(-10_000, 10_000))
            is InputEvent.Click -> platform.click(event.button)
            is InputEvent.Scroll -> platform.scroll(event.dy.coerceIn(-10_000, 10_000))
            is InputEvent.TypeText -> platform.typeText(event.text)
            is InputEvent.PressKey -> platform.pressKey(event.key)
            is InputEvent.Media -> platform.media(event.action)
            is InputEvent.Power -> when (event.action) {
                PowerAction.Lock -> platform.power(PowerAction.Lock)
                // Shutting down is only ever done on an explicit, confirmed request.
                PowerAction.Shutdown -> if (event.confirmed) platform.power(PowerAction.Shutdown)
            }
        }
    }
}
