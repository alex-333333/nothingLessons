package io.github.alex333333.bridge.protocol.client

import io.github.alex333333.bridge.protocol.Api
import io.github.alex333333.bridge.protocol.BridgeApi
import io.github.alex333333.bridge.protocol.BridgeChannel
import io.github.alex333333.bridge.protocol.BridgeJson
import io.github.alex333333.bridge.protocol.ClipText
import io.github.alex333333.bridge.protocol.FileInfo
import io.github.alex333333.bridge.protocol.Hello
import io.github.alex333333.bridge.protocol.InputEvent
import io.github.alex333333.bridge.protocol.Note
import io.github.alex333333.bridge.protocol.NotPairedException
import io.github.alex333333.bridge.protocol.PairRequest
import io.github.alex333333.bridge.protocol.PairResponse
import io.github.alex333333.bridge.protocol.PairResult
import io.github.alex333333.bridge.protocol.ServerEvent
import io.github.alex333333.bridge.protocol.Signer
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import okhttp3.Callback
import okhttp3.Dispatcher
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.BufferedSink
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLSocket
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private fun rejectExtraBytes(input: InputStream) {
    if (input.read() != -1) throw IOException("The file is longer than the announced size")
}

/** The PC answered with a status the call does not accept (for example 403 for a wrong pairing code). */
class BridgeHttpException(val status: Int, message: String) : IOException(message)

/**
 * OkHttp client for the PC server.
 *
 * Trust: the PC certificate is pinned by [pinnedFingerprintHex]. Without a fingerprint, any certificate is accepted
 * for [hello] and [pair] only (on a separate connection pool), and [pair] returns the fingerprint it saw so that the
 * caller can store it. Every other call needs the pin and the token; without them it throws [NotPairedException]
 * before anything is sent. A 401 answer to a signed request is [NotPairedException] too.
 *
 * Other failures: [IOException] (including `SSLException` for a certificate that is not the pinned one and
 * [BridgeHttpException] for unexpected statuses).
 */
class BridgeClient(
    private val host: String,
    private val port: Int,
    private val deviceId: String,
    token: String?,
    pinnedFingerprintHex: String?,
    private val now: () -> Long = System::currentTimeMillis,
) : BridgeApi {
    @Volatile private var token: String? = token
    @Volatile private var pinnedFingerprintHex: String? = pinnedFingerprintHex

    private val dispatcher = Dispatcher().apply { maxRequestsPerHost = 16 }
    private var trustAnyClient: OkHttpClient? = null
    private var pinnedClient: Pair<String, OkHttpClient>? = null

    init {
        require(DEVICE_ID.matches(deviceId)) { "Invalid device id" }
    }

    // ---- BridgeApi ---------------------------------------------------------------------------------------------

    override suspend fun hello(): Hello {
        val request = Request.Builder().url(url(Api.HELLO)).get().build()
        return clientForOpenCall().call(request, signed = false) { decode(Hello.serializer(), it) }
    }

    override suspend fun pair(code: String, deviceName: String): PairResult {
        val body = BridgeJson.encodeToString(PairRequest.serializer(), PairRequest(code, deviceId, deviceName))
        val request = Request.Builder().url(url(Api.PAIR)).post(body.toByteArray(Charsets.UTF_8).toRequestBody(JSON)).build()
        val known = pinnedFingerprintHex
        val seenCertificate = SeenCertificate()
        val tagged = request.newBuilder().tag(SeenCertificate::class.java, seenCertificate).build()
        val result = clientForOpenCall().call(tagged, signed = false) { response ->
            // The certificate of the connection that carried this very answer (OkHttp's own Handshake is not usable
            // here: its chain cleaner finds no trust anchor for a self-signed certificate and returns an empty list).
            val seen = seenCertificate.fingerprintHex ?: throw IOException("No TLS handshake information")
            if (known != null && !seen.equals(known, ignoreCase = true)) throw IOException("The PC certificate changed")
            val paired = decode(PairResponse.serializer(), response)
            PairResult(paired.token, paired.pcName, paired.macs, seen)
        }
        pinnedFingerprintHex = result.fingerprintHex
        token = result.token
        dropTrustAnyClient() // nothing opened without a pin is kept
        return result
    }

    override suspend fun notes(): List<Note> {
        val creds = credentials()
        val request = signed("GET", url(Api.NOTES), null, emptyHash(), creds)
        return creds.client.call(request) { decode(ListSerializer(Note.serializer()), it) }
    }

    override suspend fun putNote(note: Note) {
        val creds = credentials()
        val body = jsonBody(Note.serializer(), note)
        val request = signed("PUT", url(Api.NOTES, segment(note.id)), body.second, body.first, creds)
        creds.client.call(request) { }
    }

    override suspend fun deleteNote(id: String) {
        val creds = credentials()
        val request = signed("DELETE", url(Api.NOTES, segment(id)), null, emptyHash(), creds)
        creds.client.call(request, acceptNotFound = true) { }
    }

    override suspend fun clipboard(): String {
        val creds = credentials()
        val request = signed("GET", url(Api.CLIPBOARD), null, emptyHash(), creds)
        return creds.client.call(request) { decode(ClipText.serializer(), it).text }
    }

    override suspend fun setClipboard(text: String) {
        val creds = credentials()
        val body = jsonBody(ClipText.serializer(), ClipText(text))
        val request = signed("PUT", url(Api.CLIPBOARD), body.second, body.first, creds)
        creds.client.call(request) { }
    }

    override suspend fun upload(name: String, size: Long, open: () -> InputStream, onProgress: (Long) -> Unit): FileInfo {
        require(size >= 0) { "Negative size" }
        val creds = credentials()
        val target = HttpUrl.Builder().scheme("https").host(host).port(port).addPathSegments(Api.FILES.trimStart('/'))
            .addEncodedQueryParameter("name", URLEncoder.encode(name, "UTF-8")).build()
        // A streamed body is not hashed; the exact Content-Length is mandatory (the PC answers 411 to chunked uploads).
        val request = signed("POST", target, UploadBody(size, open, onProgress), Api.UNSIGNED_BODY, creds)
        return creds.client.call(request) { decode(FileInfo.serializer(), it) }
    }

    override suspend fun outbox(): List<FileInfo> {
        val creds = credentials()
        val request = signed("GET", url(Api.OUTBOX), null, emptyHash(), creds)
        return creds.client.call(request) { decode(ListSerializer(FileInfo.serializer()), it) }
    }

    override suspend fun download(id: String, sink: OutputStream, onProgress: (Long) -> Unit) {
        val creds = credentials()
        val request = signed("GET", url(Api.OUTBOX, segment(id)), null, Api.UNSIGNED_BODY, creds)
        creds.client.call(request) { response ->
            val input = response.body?.byteStream() ?: throw IOException("Empty answer")
            val buffer = ByteArray(COPY_BUFFER)
            var total = 0L
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                sink.write(buffer, 0, n)
                total += n
                onProgress(total)
            }
        }
    }

    override suspend fun deleteOutbox(id: String) {
        val creds = credentials()
        val request = signed("DELETE", url(Api.OUTBOX, segment(id)), null, emptyHash(), creds)
        creds.client.call(request, acceptNotFound = true) { }
    }

    /** Opens `/ws` right away. Throws [NotPairedException] if there is no token or no pinned certificate. */
    override fun openChannel(): BridgeChannel {
        val creds = credentials()
        val request = signed("GET", url(Api.WS), null, emptyHash(), creds)
        return SocketChannel(creds.client, request)
    }

    /** Forgets the token on this device. The PC has no request for it; it keeps the token until the PC unpairs. */
    override suspend fun unpair() {
        token = null
    }

    /** Releases threads and connections. The client must not be used afterwards. */
    @Synchronized
    fun close() {
        pinnedClient?.second?.connectionPool?.evictAll()
        trustAnyClient?.connectionPool?.evictAll()
        dispatcher.executorService.shutdown()
    }

    // ---- plumbing ----------------------------------------------------------------------------------------------

    /** Filled in by the network interceptor with the SHA-256 fingerprint of the certificate the connection uses. */
    private class SeenCertificate {
        @Volatile var fingerprintHex: String? = null
    }

    private class Credentials(val token: String, val client: OkHttpClient)

    /** Token and pinned client for a signed call; throws [NotPairedException] before anything is sent otherwise. */
    private fun credentials(): Credentials {
        val t = token?.takeIf { it.isNotBlank() } ?: throw NotPairedException("This device is not paired with a PC")
        val pin = pinnedFingerprintHex?.takeIf { it.isNotBlank() }
            ?: throw NotPairedException("The PC certificate is not known yet: pair with the PC first")
        return Credentials(t, pinnedClientFor(pin))
    }

    /** hello() and pair(): the pinned client if a fingerprint is known, otherwise the one that records instead. */
    private fun clientForOpenCall(): OkHttpClient =
        pinnedFingerprintHex?.takeIf { it.isNotBlank() }?.let(::pinnedClientFor) ?: trustAnyClientFor()

    @Synchronized
    private fun trustAnyClientFor(): OkHttpClient =
        trustAnyClient ?: baseClient().applyTls(PinnedTls.acceptAny()).build().also { trustAnyClient = it }

    @Synchronized
    private fun dropTrustAnyClient() {
        trustAnyClient?.connectionPool?.evictAll()
        trustAnyClient = null
    }

    @Synchronized
    private fun pinnedClientFor(pin: String): OkHttpClient {
        pinnedClient?.let { if (it.first == pin) return it.second }
        pinnedClient?.second?.connectionPool?.evictAll()
        val client = baseClient().applyTls(PinnedTls.pinned(pin)).build()
        pinnedClient = pin to client
        return client
    }

    private fun baseClient(): OkHttpClient.Builder = OkHttpClient.Builder()
        .protocols(listOf(Protocol.HTTP_1_1)) // the PC speaks HTTP/1.1 only
        .dispatcher(dispatcher)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .pingInterval(15, TimeUnit.SECONDS) // WebSocket keep-alive and dead-peer detection
        .followRedirects(false)
        .followSslRedirects(false)
        .addNetworkInterceptor { chain ->
            // Noting the certificate of the live TLS session for requests that ask for it (pairing).
            chain.request().tag(SeenCertificate::class.java)?.let { holder ->
                val session = (chain.connection()?.socket() as? SSLSocket)?.session
                holder.fingerprintHex = session?.peerCertificates?.firstOrNull()?.let(PinnedTls::fingerprintOf)
            }
            chain.proceed(chain.request())
        }

    private fun OkHttpClient.Builder.applyTls(tls: PinnedTls): OkHttpClient.Builder =
        sslSocketFactory(tls.socketFactory, tls.trustManager).hostnameVerifier(tls.hostnameVerifier)

    private fun url(vararg segments: String): HttpUrl {
        val builder = HttpUrl.Builder().scheme("https").host(host).port(port)
        for (s in segments) {
            if (s.startsWith("/")) builder.addPathSegments(s.trimStart('/')) else builder.addPathSegment(s)
        }
        return builder.build()
    }

    /** Ids go into the path as one segment; "." and ".." would be resolved away by the URL machinery. */
    private fun segment(id: String): String {
        require(id.isNotEmpty() && id != "." && id != "..") { "Invalid id" }
        return id
    }

    private fun emptyHash(): String = Signer.sha256Hex(ByteArray(0))

    private fun <T> jsonBody(serializer: KSerializer<T>, value: T): Pair<String, RequestBody> {
        val bytes = BridgeJson.encodeToString(serializer, value).toByteArray(Charsets.UTF_8)
        return Signer.sha256Hex(bytes) to bytes.toRequestBody(JSON)
    }

    /** Adds the signature headers. The signed path is the request target exactly as it goes on the wire. */
    private fun signed(method: String, url: HttpUrl, body: RequestBody?, bodyHash: String, creds: Credentials): Request {
        val time = now()
        val target = url.encodedPath + (url.encodedQuery?.let { "?$it" } ?: "")
        return Request.Builder().url(url).method(method, body)
            .header(Api.HDR_DEVICE, deviceId)
            .header(Api.HDR_TIME, time.toString())
            .header(Api.HDR_SIG, Signer.sign(creds.token, method, target, time, bodyHash))
            .build()
    }

    private fun <T> decode(serializer: KSerializer<T>, response: Response): T {
        val text = String(response.body?.bytes() ?: ByteArray(0), Charsets.UTF_8)
        return try {
            BridgeJson.decodeFromString(serializer, text)
        } catch (e: SerializationException) {
            throw IOException("Unexpected answer from the PC", e)
        } catch (e: IllegalArgumentException) {
            throw IOException("Unexpected answer from the PC", e)
        }
    }

    /**
     * Runs [request] and [handle]s a 2xx answer on OkHttp's thread (the body is read there, so cancelling the
     * coroutine cancels the call and aborts a transfer in progress).
     */
    private suspend fun <T> OkHttpClient.call(
        request: Request,
        signed: Boolean = true,
        acceptNotFound: Boolean = false,
        handle: (Response) -> T,
    ): T = suspendCancellableCoroutine { cont ->
        val call = newCall(request)
        cont.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: okhttp3.Call, e: IOException) {
                cont.resumeWithException(e)
            }

            override fun onResponse(call: okhttp3.Call, response: Response) {
                val outcome = runCatching {
                    response.use {
                        when {
                            signed && it.code == 401 -> throw NotPairedException(UNAUTHORIZED_MESSAGE)
                            !it.isSuccessful && !(acceptNotFound && it.code == 404) -> throw BridgeHttpException(it.code, statusMessage(it.code))
                            else -> handle(it)
                        }
                    }
                }
                outcome.fold({ cont.resume(it) }, { cont.resumeWithException(it) })
            }
        })
    }

    private fun statusMessage(code: Int) = when (code) {
        403 -> "The PC refused the request (wrong, expired or already used pairing code, or a network that is not allowed)"
        404 -> "Not found on the PC"
        else -> "The PC answered HTTP $code"
    }

    /** A file body with an exact Content-Length, streamed from [open]; the bytes written must equal [size]. */
    private class UploadBody(
        private val size: Long,
        private val open: () -> InputStream,
        private val onProgress: (Long) -> Unit,
    ) : RequestBody() {
        override fun contentType() = OCTETS
        override fun contentLength() = size
        override fun isOneShot() = true

        override fun writeTo(sink: BufferedSink) {
            try {
                open().use { input ->
                    val buffer = ByteArray(COPY_BUFFER)
                    var sent = 0L
                    if (size == 0L) rejectExtraBytes(input)
                    while (sent < size) {
                        val n = input.read(buffer, 0, minOf(buffer.size.toLong(), size - sent).toInt())
                        if (n < 0) throw IOException("The file ended after $sent of $size bytes")
                        // Nothing of the last chunk is written until the source is known to end here: once the
                        // announced length is on the wire the PC would store a file that is cut short.
                        if (sent + n == size) rejectExtraBytes(input)
                        sink.write(buffer, 0, n)
                        sent += n
                        sink.flush()
                        onProgress(sent)
                    }
                }
            } catch (e: IOException) {
                throw e
            } catch (e: Exception) {
                // OkHttp only understands IOException from a body.
                throw IOException("Cannot read the file: ${e.message}", e)
            }
        }
    }

    private class SocketChannel(client: OkHttpClient, request: Request) : BridgeChannel {
        // Buffered from the moment the socket opens, so no event is lost before the first collector arrives.
        private val incoming = Channel<ServerEvent>(64, BufferOverflow.DROP_OLDEST)

        private val socket: WebSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                val event = try {
                    BridgeJson.decodeFromString(ServerEvent.serializer(), text)
                } catch (_: SerializationException) {
                    return
                } catch (_: IllegalArgumentException) {
                    return
                }
                incoming.trySend(event)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(code, reason)
                incoming.close()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                incoming.close()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                incoming.close(if (response?.code == 401) NotPairedException(UNAUTHORIZED_MESSAGE) else t)
            }
        })

        override val events: Flow<ServerEvent> = incoming.receiveAsFlow()

        override fun send(event: InputEvent) {
            socket.send(BridgeJson.encodeToString(InputEvent.serializer(), event))
        }

        override fun close() {
            socket.close(1000, null)
            incoming.close()
        }
    }

    private companion object {
        const val COPY_BUFFER = 64 * 1024
        const val UNAUTHORIZED_MESSAGE =
            "The PC no longer accepts this device (it was unpaired, or the clocks differ by more than a minute)"
        val JSON = "application/json; charset=utf-8".toMediaType()
        val OCTETS = "application/octet-stream".toMediaType()
        val DEVICE_ID = Regex("^[A-Za-z0-9._-]{1,100}$")
    }
}
