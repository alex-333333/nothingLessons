package io.github.alex333333.bridge.pc

import io.github.alex333333.bridge.protocol.Api
import io.github.alex333333.bridge.protocol.BridgeJson
import io.github.alex333333.bridge.protocol.ClipText
import io.github.alex333333.bridge.protocol.FileInfo
import io.github.alex333333.bridge.protocol.Fingerprint
import io.github.alex333333.bridge.protocol.Hello
import io.github.alex333333.bridge.protocol.InputEvent
import io.github.alex333333.bridge.protocol.MediaAction
import io.github.alex333333.bridge.protocol.MouseButton
import io.github.alex333333.bridge.protocol.Note
import io.github.alex333333.bridge.protocol.PairRequest
import io.github.alex333333.bridge.protocol.PairResponse
import io.github.alex333333.bridge.protocol.PowerAction
import io.github.alex333333.bridge.protocol.ServerEvent
import io.github.alex333333.bridge.protocol.Signer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encodeToString
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.http.WebSocket
import java.nio.file.Files
import java.nio.file.Path
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.Random
import java.util.concurrent.CompletionStage
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLEngine
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManager
import javax.net.ssl.X509ExtendedTrustManager

class ServerTest {
    @get:Rule val tmp = TemporaryFolder()

    private lateinit var dataDir: Path
    private lateinit var downloads: Path
    private lateinit var platform: FakePlatform
    private lateinit var server: BridgeServer
    private lateinit var http: HttpClient
    private lateinit var ssl: SSLContext
    private var port = 0

    private val clock = AtomicLong(System.currentTimeMillis())
    private val peerOk = AtomicBoolean(true)
    @Volatile private var macs = listOf("AA-BB-CC-DD-EE-FF")
    private val servers = mutableListOf<BridgeServer>()
    private val sockets = mutableListOf<WebSocket>()

    private val deviceId = "phone-1"
    private var token = ""

    @Before fun setUp() {
        dataDir = tmp.newFolder("data").toPath()
        downloads = tmp.newFolder("downloads").toPath()
        platform = FakePlatform()
        server = newServer()
        port = server.start(0)
        ssl = pinnedContext(server.tlsFingerprintHex)
        http = HttpClient.newBuilder().sslContext(ssl).version(HttpClient.Version.HTTP_1_1).build()
    }

    @After fun tearDown() {
        sockets.forEach { runCatching { it.abort() } }
        servers.forEach { runCatching { it.stop() } }
    }

    private fun newServer(): BridgeServer {
        val s = BridgeServer(
            dataDir, downloads, platform, "Рабочий ПК", { macs },
            now = { clock.get() }, peerAllowed = { peerOk.get() },
        )
        servers += s
        return s
    }

    // ---- client plumbing -------------------------------------------------------------------------------------

    private class Resp(val status: Int, val body: ByteArray) {
        val text: String get() = String(body, Charsets.UTF_8)
    }

    /** Trusts exactly one certificate, identified by its SHA-256 fingerprint (what the phone does). */
    private fun pinnedContext(fingerprintHex: String): SSLContext {
        val tm = object : X509ExtendedTrustManager() {
            private fun check(chain: Array<X509Certificate>) {
                if (Fingerprint.sha256Hex(chain[0].encoded) != fingerprintHex) throw CertificateException("fingerprint mismatch")
            }
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) = check(chain)
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String, socket: java.net.Socket) = check(chain)
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String, engine: SSLEngine) = check(chain)
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) = throw CertificateException()
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String, socket: java.net.Socket) = throw CertificateException()
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String, engine: SSLEngine) = throw CertificateException()
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        }
        return SSLContext.getInstance("TLS").apply { init(null, arrayOf<TrustManager>(tm), SecureRandom()) }
    }

    private fun sigHeaders(
        method: String, path: String, body: ByteArray?, device: String? = deviceId, tok: String = token,
        time: Long = clock.get(), bodyHash: String? = null,
    ): Map<String, String> {
        val hash = bodyHash ?: Signer.sha256Hex(body ?: ByteArray(0))
        val h = linkedMapOf<String, String>()
        if (device != null) h[Api.HDR_DEVICE] = device
        h[Api.HDR_TIME] = time.toString()
        h[Api.HDR_SIG] = Signer.sign(tok, method, path, time, hash)
        return h
    }

    private fun send(
        method: String, path: String, body: ByteArray? = null, signed: Boolean = true,
        headers: Map<String, String>? = null, publisher: HttpRequest.BodyPublisher? = null,
    ): Resp {
        val pub = publisher ?: if (body == null) HttpRequest.BodyPublishers.noBody() else HttpRequest.BodyPublishers.ofByteArray(body)
        val builder = HttpRequest.newBuilder(URI("https://localhost:$port$path")).method(method, pub)
        val hs = headers ?: if (signed) sigHeaders(method, path, body) else emptyMap()
        hs.forEach { (k, v) -> builder.header(k, v) }
        val r = http.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray())
        return Resp(r.statusCode(), r.body())
    }

    private fun json(method: String, path: String, jsonBody: String, headers: Map<String, String>? = null): Resp =
        send(method, path, jsonBody.toByteArray(Charsets.UTF_8), headers = headers)

    private fun pairRequest(code: String, device: String = deviceId, name: String = "Phone"): Resp =
        send("POST", Api.PAIR, BridgeJson.encodeToString(PairRequest(code, device, name)).toByteArray(), signed = false)

    /** Pairs [deviceId] and keeps its token in [token]. */
    private fun pairDevice(): String {
        val r = pairRequest(server.newPairingCode().code)
        assertEquals(r.text, 200, r.status)
        token = BridgeJson.decodeFromString<PairResponse>(r.text).token
        return token
    }

    private fun await(what: String, timeoutMs: Long = 10_000, condition: () -> Boolean) {
        val deadline = System.nanoTime() + timeoutMs * 1_000_000
        while (!condition()) {
            if (System.nanoTime() > deadline) fail("Timed out waiting for $what")
            Thread.sleep(10)
        }
    }

    private fun files(dir: Path): List<String> = Files.list(dir).use { s -> s.map { it.fileName.toString() }.sorted().toList() }

    // ---- hello and access control -----------------------------------------------------------------------------

    @Test fun helloAnswersWithoutSignature() {
        val r = send("GET", Api.HELLO, signed = false)
        assertEquals(200, r.status)
        val hello = BridgeJson.decodeFromString<Hello>(r.text)
        assertEquals("Рабочий ПК", hello.name)
        assertEquals("bridge", hello.app)
        assertTrue(hello.pcId.isNotBlank())
    }

    @Test fun pcIdIsStableAcrossRestarts() {
        val first = BridgeJson.decodeFromString<Hello>(send("GET", Api.HELLO, signed = false).text).pcId
        server.stop()
        val second = newServer()
        port = second.start(0)
        assertEquals(first, BridgeJson.decodeFromString<Hello>(send("GET", Api.HELLO, signed = false).text).pcId)
    }

    @Test fun everyOtherRouteNeedsASignature() {
        pairDevice()
        val unsignedRoutes = listOf(
            "GET" to Api.NOTES, "PUT" to "${Api.NOTES}/n1", "DELETE" to "${Api.NOTES}/n1", "GET" to Api.CLIPBOARD,
            "PUT" to Api.CLIPBOARD, "POST" to "${Api.FILES}?name=a.txt", "GET" to Api.OUTBOX, "GET" to "${Api.OUTBOX}/x",
            "DELETE" to "${Api.OUTBOX}/x", "GET" to Api.WS, "GET" to "/nothing-here", "POST" to Api.HELLO, "GET" to Api.PAIR,
        )
        for ((m, p) in unsignedRoutes) assertEquals("$m $p", 401, send(m, p, if (m == "GET" || m == "DELETE") null else byteArrayOf(1), signed = false).status)
        assertEquals(200, send("GET", Api.NOTES).status) // the same request, signed, is fine
    }

    @Test fun wrongTokenWrongDeviceAndTamperingAreRejected() {
        pairDevice()
        val path = Api.NOTES
        assertEquals("wrong token", 401, send("GET", path, headers = sigHeaders("GET", path, null, tok = "not-the-token")).status)
        assertEquals("unknown device", 401, send("GET", path, headers = sigHeaders("GET", path, null, device = "someone-else")).status)
        assertEquals("empty device", 401, send("GET", path, headers = sigHeaders("GET", path, null, device = "")).status)
        assertEquals("no device header", 401, send("GET", path, headers = sigHeaders("GET", path, null, device = null)).status)
        assertEquals("signed another path", 401, send("GET", path, headers = sigHeaders("GET", Api.CLIPBOARD, null)).status)
        assertEquals("signed another method", 401, send("GET", path, headers = sigHeaders("DELETE", path, null)).status)
        val body = BridgeJson.encodeToString(ClipText("a")).toByteArray()
        assertEquals("signed another body", 401, send("PUT", Api.CLIPBOARD, body, headers = sigHeaders("PUT", Api.CLIPBOARD, "other".toByteArray())).status)
        assertEquals("garbage time", 401, send("GET", path, headers = sigHeaders("GET", path, null) + (Api.HDR_TIME to "yesterday")).status)
        assertEquals("garbage signature", 401, send("GET", path, headers = sigHeaders("GET", path, null) + (Api.HDR_SIG to "AAAA")).status)
        assertEquals("empty signature", 401, send("GET", path, headers = sigHeaders("GET", path, null) + (Api.HDR_SIG to "")).status)
    }

    @Test fun staleAndFutureTimestampsAreRejected() {
        pairDevice()
        assertEquals(200, send("GET", Api.NOTES, headers = sigHeaders("GET", Api.NOTES, null, time = clock.get() - 59_000)).status)
        assertEquals(401, send("GET", Api.NOTES, headers = sigHeaders("GET", Api.NOTES, null, time = clock.get() - 61_000)).status)
        assertEquals(401, send("GET", Api.NOTES, headers = sigHeaders("GET", Api.NOTES, null, time = clock.get() + 61_000)).status)
        clock.addAndGet(120_000) // the server clock moves on: a signature made earlier is now stale
        val old = sigHeaders("GET", Api.NOTES, null, time = clock.get() - 120_000)
        assertEquals(401, send("GET", Api.NOTES, headers = old).status)
    }

    @Test fun disallowedPeersGet403EvenOnHello() {
        pairDevice()
        peerOk.set(false)
        assertEquals(403, send("GET", Api.HELLO, signed = false).status)
        assertEquals(403, pairRequest("123456").status)
        assertEquals(403, send("GET", Api.NOTES).status) // valid signature, still refused
        assertEquals(403, send("GET", "/anything", signed = false).status)
        peerOk.set(true)
        assertEquals(200, send("GET", Api.HELLO, signed = false).status)
    }

    @Test fun aClientThatDoesNotMatchThePinnedFingerprintCannotConnect() {
        val wrong = HttpClient.newBuilder().sslContext(pinnedContext("00".repeat(32))).version(HttpClient.Version.HTTP_1_1).build()
        try {
            wrong.send(HttpRequest.newBuilder(URI("https://localhost:$port/hello")).build(), HttpResponse.BodyHandlers.discarding())
            fail("The handshake must fail")
        } catch (_: java.io.IOException) {
        }
    }

    /** A client that offers h2 through ALPN (the default of java.net.http and of OkHttp) must still get answers. */
    @Test fun bodilessSignedRequestsAnswerForClientsThatOfferHttp2() {
        pairDevice()
        val h2Capable = HttpClient.newBuilder().sslContext(ssl).build() // default version: HTTP_2, falls back to 1.1
        fun call(method: String, path: String): HttpResponse<ByteArray> {
            val req = HttpRequest.newBuilder(URI("https://localhost:$port$path")).timeout(java.time.Duration.ofSeconds(5))
                .method(method, HttpRequest.BodyPublishers.noBody())
            sigHeaders(method, path, null).forEach { (k, v) -> req.header(k, v) }
            return h2Capable.send(req.build(), HttpResponse.BodyHandlers.ofByteArray())
        }
        for ((method, path) in listOf("GET" to Api.NOTES, "GET" to Api.CLIPBOARD, "GET" to Api.OUTBOX, "DELETE" to "${Api.NOTES}/none")) {
            val r = call(method, path)
            assertEquals("$method $path", if (method == "DELETE") 404 else 200, r.statusCode())
            assertEquals("$method $path", HttpClient.Version.HTTP_1_1, r.version())
        }
    }

    @Test fun certificateIsStoredAndReusedAcrossRestarts() {
        val before = server.tlsFingerprintHex
        assertTrue(Files.exists(dataDir.resolve("bridge.p12")))
        assertTrue(Regex("^[0-9a-f]{64}$").matches(before))
        server.stop()
        assertEquals(before, newServer().tlsFingerprintHex)
    }

    // ---- pairing ----------------------------------------------------------------------------------------------

    @Test fun wrongCodeIs403() {
        val code = server.newPairingCode().code
        val wrong = if (code == "000000") "000001" else "000000"
        assertEquals(403, pairRequest(wrong).status)
    }

    @Test fun rightCodeReturnsTokenAndMacsAndNotifies() {
        val pc = server.newPairingCode()
        assertTrue(Regex("^[0-9]{6}$").matches(pc.code))
        assertEquals(Fingerprint.short(server.tlsFingerprintHex), pc.fingerprintShort)
        val r = pairRequest(pc.code, name = "Pixel")
        assertEquals(200, r.status)
        val resp = BridgeJson.decodeFromString<PairResponse>(r.text)
        assertEquals("Рабочий ПК", resp.pcName)
        assertEquals(listOf("AA-BB-CC-DD-EE-FF"), resp.macs)
        assertTrue(Regex("^[A-Za-z0-9_-]{43}$").matches(resp.token)) // 32 random bytes, base64url without padding
        assertTrue(platform.notifications.any { "Pixel" in it })
    }

    @Test fun macsAreAskedAgainForEveryPairing() {
        macs = listOf("AA-BB-CC-DD-EE-FF")
        val first = pairRequest(server.newPairingCode().code, device = "phone-a")
        assertEquals(listOf("AA-BB-CC-DD-EE-FF"), BridgeJson.decodeFromString<PairResponse>(first.text).macs)
        macs = listOf("11-22-33-44-55-66", "77-88-99-AA-BB-CC")
        val second = pairRequest(server.newPairingCode().code, device = "phone-b")
        assertEquals(listOf("11-22-33-44-55-66", "77-88-99-AA-BB-CC"), BridgeJson.decodeFromString<PairResponse>(second.text).macs)
    }

    @Test fun pairingBalloonIsRussian() {
        pairRequest(server.newPairingCode().code, name = "Pixel")
        assertEquals(listOf("Телефон привязан: Pixel"), platform.notifications.toList())
    }

    @Test fun theSameCodeCannotBeUsedTwice() {
        val code = server.newPairingCode().code
        assertEquals(200, pairRequest(code).status)
        assertEquals(403, pairRequest(code, device = "phone-2").status)
    }

    @Test fun afterFiveWrongCodesTheRightOneIsRefusedToo() {
        val code = server.newPairingCode().code
        val wrong = if (code == "000000") "000001" else "000000"
        repeat(5) { assertEquals(403, pairRequest(wrong).status) }
        assertEquals(403, pairRequest(code).status)
    }

    @Test fun aNewCodeReplacesTheOldOne() {
        val old = server.newPairingCode().code
        var fresh = server.newPairingCode().code
        while (fresh == old) fresh = server.newPairingCode().code // 1 in a million
        assertEquals(403, pairRequest(old).status)
        assertEquals(200, pairRequest(fresh).status)
    }

    @Test fun aCodeExpiresAfterTwoMinutes() {
        val code = server.newPairingCode().code
        clock.addAndGet(121_000)
        assertEquals(403, pairRequest(code).status)
    }

    @Test fun pairingWithNoCodeIssuedIs403() {
        assertEquals(403, pairRequest("123456").status)
    }

    @Test fun pairingRejectsBadRequestsWithoutBurningTheCode() {
        val code = server.newPairingCode().code
        assertEquals(400, send("POST", Api.PAIR, "{not json".toByteArray(), signed = false).status)
        assertEquals(400, pairRequest(code, device = "").status)
        assertEquals(400, pairRequest(code, device = "bad id\r\nX-Evil: 1").status)
        assertEquals(413, send("POST", Api.PAIR, ByteArray(100_000) { 'a'.code.toByte() }, signed = false).status)
        assertEquals(200, pairRequest(code).status)
    }

    @Test fun twoDevicesCanBePairedAndHaveSeparateTokens() {
        val a = BridgeJson.decodeFromString<PairResponse>(pairRequest(server.newPairingCode().code, "dev-a").text).token
        val b = BridgeJson.decodeFromString<PairResponse>(pairRequest(server.newPairingCode().code, "dev-b").text).token
        assertNotEquals(a, b)
        assertEquals(200, send("GET", Api.NOTES, headers = sigHeaders("GET", Api.NOTES, null, device = "dev-a", tok = a)).status)
        assertEquals(200, send("GET", Api.NOTES, headers = sigHeaders("GET", Api.NOTES, null, device = "dev-b", tok = b)).status)
        assertEquals(401, send("GET", Api.NOTES, headers = sigHeaders("GET", Api.NOTES, null, device = "dev-a", tok = b)).status)
    }

    @Test fun pairingSurvivesARestart() {
        pairDevice()
        server.stop()
        port = newServer().start(0)
        assertEquals(200, send("GET", Api.NOTES).status)
    }

    // ---- notes ------------------------------------------------------------------------------------------------

    @Test fun notesPutGetDelete() {
        pairDevice()
        assertEquals("[]", send("GET", Api.NOTES).text)
        val n1 = Note("n1", "первая", 1)
        val n2 = Note("n2", "second", 2)
        assertEquals(200, json("PUT", "${Api.NOTES}/n1", BridgeJson.encodeToString(n1)).status)
        assertEquals(200, json("PUT", "${Api.NOTES}/n2", BridgeJson.encodeToString(n2)).status)
        val listed = BridgeJson.decodeFromString(ListSerializer(Note.serializer()), send("GET", Api.NOTES).text)
        assertEquals(setOf(n1, n2), listed.toSet())
        val updated = n1.copy(text = "правка", updatedAt = 5)
        assertEquals(200, json("PUT", "${Api.NOTES}/n1", BridgeJson.encodeToString(updated)).status)
        assertEquals(setOf(updated, n2), BridgeJson.decodeFromString(ListSerializer(Note.serializer()), send("GET", Api.NOTES).text).toSet())
        assertEquals(200, send("DELETE", "${Api.NOTES}/n1").status)
        assertEquals(listOf(n2), BridgeJson.decodeFromString(ListSerializer(Note.serializer()), send("GET", Api.NOTES).text))
        assertEquals(404, send("DELETE", "${Api.NOTES}/n1").status)
    }

    @Test fun aHugeNoteWithCyrillicEmojiAndNewlinesComesBackEqual() {
        pairDevice()
        val unit = "Привет, мир! 😀 строка\nвторая\r\n\t\"кавычки\" \\ слэш "
        val text = buildString { while (length < 100_000) append(unit) }.take(100_000)
        val note = Note("big", text, 42)
        assertEquals(200, json("PUT", "${Api.NOTES}/big", BridgeJson.encodeToString(note)).status)
        val r = send("GET", Api.NOTES)
        assertTrue(r.text.length > 100_000)
        assertEquals(listOf(note), BridgeJson.decodeFromString(ListSerializer(Note.serializer()), r.text))
    }

    @Test fun notesSurviveARestart() {
        pairDevice()
        val note = Note("n1", "жив", 1)
        json("PUT", "${Api.NOTES}/n1", BridgeJson.encodeToString(note))
        server.stop()
        port = newServer().start(0)
        assertEquals(listOf(note), BridgeJson.decodeFromString(ListSerializer(Note.serializer()), send("GET", Api.NOTES).text))
    }

    @Test fun noteIdMustMatchThePathAndBeSane() {
        pairDevice()
        assertEquals(400, json("PUT", "${Api.NOTES}/n1", BridgeJson.encodeToString(Note("other", "x", 1))).status)
        assertEquals(400, json("PUT", "${Api.NOTES}/n1", "{broken").status)
        val longId = "x".repeat(300)
        assertEquals(400, json("PUT", "${Api.NOTES}/$longId", BridgeJson.encodeToString(Note(longId, "x", 1))).status)
        assertEquals("[]", send("GET", Api.NOTES).text)
    }

    @Test fun bodiesOverOneMebibyteAreRefusedWith413() {
        pairDevice()
        val huge = BridgeJson.encodeToString(ClipText("a".repeat(1_048_576))).toByteArray()
        assertEquals(413, send("PUT", Api.CLIPBOARD, huge).status)
        assertEquals("", platform.clipboard)
        val hugeNote = BridgeJson.encodeToString(Note("n", "a".repeat(1_048_576), 1)).toByteArray()
        assertEquals(413, send("PUT", "${Api.NOTES}/n", hugeNote).status)
        // Just under the cap is accepted.
        val fits = BridgeJson.encodeToString(ClipText("a".repeat(1_048_000))).toByteArray()
        assertTrue(fits.size <= 1_048_576)
        assertEquals(200, send("PUT", Api.CLIPBOARD, fits).status)
    }

    // ---- clipboard --------------------------------------------------------------------------------------------

    @Test fun clipboardPutWritesAndGetReads() {
        pairDevice()
        val text = "из телефона 😀\nстрока"
        assertEquals(200, json("PUT", Api.CLIPBOARD, BridgeJson.encodeToString(ClipText(text))).status)
        assertEquals(text, platform.clipboard)
        platform.clipboard = "с компьютера\nвторая"
        val r = send("GET", Api.CLIPBOARD)
        assertEquals(200, r.status)
        assertEquals(ClipText("с компьютера\nвторая"), BridgeJson.decodeFromString<ClipText>(r.text))
    }

    // ---- file upload ------------------------------------------------------------------------------------------

    private fun upload(rawName: String, content: ByteArray, signedPath: String? = null): Resp {
        val path = "${Api.FILES}?name=${URLEncoder.encode(rawName, Charsets.UTF_8)}"
        return send("POST", path, content, headers = sigHeaders("POST", signedPath ?: path, content, bodyHash = Api.UNSIGNED_BODY))
    }

    @Test fun anEightMegabyteUploadLandsInDownloadsIntact() {
        pairDevice()
        val data = ByteArray(8 * 1024 * 1024).also { Random(7).nextBytes(it) }
        val r = upload("big.bin", data)
        assertEquals(r.text, 200, r.status)
        val info = BridgeJson.decodeFromString<FileInfo>(r.text)
        assertEquals("big.bin", info.name)
        assertEquals(data.size.toLong(), info.size)
        assertEquals(listOf("big.bin"), files(downloads))
        assertArrayEquals(data, Files.readAllBytes(downloads.resolve("big.bin")))
    }

    @Test fun uploadNamesAreSanitisedAndStayInsideTheFolder() {
        pairDevice()
        assertEquals(200, upload("..\\..\\evil.exe", byteArrayOf(1, 2, 3)).status)
        assertEquals(200, upload("../../etc/passwd", byteArrayOf(4)).status)
        assertEquals(200, upload("..", byteArrayOf(5)).status)
        assertEquals(200, upload("C:\\Windows\\system32\\x.dll", byteArrayOf(6)).status)
        assertEquals(listOf("evil.exe", "file", "passwd", "x.dll"), files(downloads))
        // Nothing escaped next to or above the downloads folder.
        assertEquals(listOf("data", "downloads"), files(tmp.root.toPath()))
        assertEquals(listOf("bridge.p12", "devices.json"), files(dataDir)) // uploads never go to the data folder
    }

    @Test fun aSecondUploadWithTheSameNameGetsANumber() {
        pairDevice()
        val first = BridgeJson.decodeFromString<FileInfo>(upload("evil.exe", byteArrayOf(1)).text)
        val second = BridgeJson.decodeFromString<FileInfo>(upload("evil.exe", byteArrayOf(2)).text)
        assertEquals("evil.exe", first.name)
        assertEquals("evil (1).exe", second.name)
        assertEquals(listOf("evil (1).exe", "evil.exe"), files(downloads))
        assertArrayEquals(byteArrayOf(1), Files.readAllBytes(downloads.resolve("evil.exe")))
        assertArrayEquals(byteArrayOf(2), Files.readAllBytes(downloads.resolve("evil (1).exe")))
    }

    @Test fun anEmptyUploadIs400AndLeavesNothing() {
        pairDevice()
        assertEquals(400, upload("empty.txt", ByteArray(0)).status)
        assertEquals(emptyList<String>(), files(downloads))
    }

    @Test fun anUnsignedOrBadlySignedUploadIsRefusedAndLeavesNothing() {
        pairDevice()
        val path = "${Api.FILES}?name=a.txt"
        assertEquals(401, send("POST", path, ByteArray(10), signed = false).status)
        assertEquals(401, send("POST", path, ByteArray(10), headers = sigHeaders("POST", path, null, tok = "bad", bodyHash = Api.UNSIGNED_BODY)).status)
        // The query string is part of the signed path, so the name cannot be swapped.
        assertEquals(401, upload("b.txt", ByteArray(10), signedPath = path).status)
        // A normal body hash is not what the server expects for streamed files.
        assertEquals(401, send("POST", path, ByteArray(10)).status)
        assertEquals(emptyList<String>(), files(downloads))
    }

    private fun rawUploadThenAbort(reset: Boolean) {
        pairDevice()
        val path = "${Api.FILES}?name=abort.bin"
        val h = sigHeaders("POST", path, null, bodyHash = Api.UNSIGNED_BODY)
        val total = 8 * 1024 * 1024
        val piece = ByteArray(1024 * 1024) { 7 }
        val socket = ssl.socketFactory.createSocket("localhost", port) as SSLSocket
        try {
            val out = socket.getOutputStream()
            val head = buildString {
                append("POST $path HTTP/1.1\r\nHost: localhost\r\n")
                h.forEach { (k, v) -> append("$k: $v\r\n") }
                append("Content-Type: application/octet-stream\r\n")
                append("Content-Length: $total\r\n\r\n")
            }
            out.write(head.toByteArray())
            out.write(piece)
            out.flush()
            await("the partial file to appear") { files(downloads).any { it.endsWith(".part") } }
            if (reset) socket.setSoLinger(true, 0)
        } finally {
            socket.close()
        }
        try {
            await("the partial file to be removed") { files(downloads).isEmpty() }
        } catch (e: AssertionError) {
            throw AssertionError("${e.message}; left in the folder: ${files(downloads)}")
        }
        assertEquals(emptyList<String>(), files(downloads))
    }

    @Test fun aClientThatClosesMidUploadLeavesNoFileAndNoPartFile() = rawUploadThenAbort(reset = false)

    @Test fun aClientThatResetsMidUploadLeavesNoFileAndNoPartFile() = rawUploadThenAbort(reset = true)

    /** A chunked body cannot prove it was complete when the connection drops, so uploads must declare their length. */
    @Test fun anUploadWithoutContentLengthIsRefused() {
        pairDevice()
        val path = "${Api.FILES}?name=chunked.bin"
        val h = sigHeaders("POST", path, null, bodyHash = Api.UNSIGNED_BODY)
        val socket = ssl.socketFactory.createSocket("localhost", port) as SSLSocket
        socket.use {
            val head = buildString {
                append("POST $path HTTP/1.1\r\nHost: localhost\r\n")
                h.forEach { (k, v) -> append("$k: $v\r\n") }
                append("Transfer-Encoding: chunked\r\n\r\n5\r\nhello\r\n0\r\n\r\n")
            }
            it.getOutputStream().write(head.toByteArray())
            it.getOutputStream().flush()
            val statusLine = it.getInputStream().bufferedReader().readLine()
            assertTrue(statusLine, statusLine.contains(" 411"))
        }
        assertEquals(emptyList<String>(), files(downloads))
    }

    // ---- outbox -----------------------------------------------------------------------------------------------

    @Test fun outboxListsDownloadsAndDeletes() {
        pairDevice()
        val data = ByteArray(300_000).also { Random(3).nextBytes(it) }
        val src = tmp.newFolder("src").toPath().resolve("report v2.pdf")
        Files.write(src, data)
        assertEquals("[]", send("GET", Api.OUTBOX).text)
        server.sendToPhone(src)
        val list = BridgeJson.decodeFromString(ListSerializer(FileInfo.serializer()), send("GET", Api.OUTBOX).text)
        assertEquals(1, list.size)
        assertEquals("report v2.pdf", list[0].name)
        assertEquals(data.size.toLong(), list[0].size)
        val id = list[0].id
        val dl = send("GET", "${Api.OUTBOX}/$id")
        assertEquals(200, dl.status)
        assertArrayEquals(data, dl.body)
        // The client may sign a streamed download with the UNSIGNED marker instead of the empty-body hash.
        val p = "${Api.OUTBOX}/$id"
        assertEquals(200, send("GET", p, headers = sigHeaders("GET", p, null, bodyHash = Api.UNSIGNED_BODY)).status)
        assertEquals(200, send("DELETE", "${Api.OUTBOX}/$id").status)
        assertEquals("[]", send("GET", Api.OUTBOX).text)
        assertEquals(404, send("GET", "${Api.OUTBOX}/$id").status)
        assertEquals(404, send("DELETE", "${Api.OUTBOX}/$id").status)
        assertArrayEquals(data, Files.readAllBytes(src)) // removing it from the outbox never deletes the user's file
    }

    @Test fun outboxIdsAreNeverPaths() {
        pairDevice()
        val secret = tmp.newFile("secret.txt").toPath().also { Files.writeString(it, "secret") }
        for (id in listOf("..%2F..%2Fsecret.txt", secret.toString().replace("/", "%2F"), "..")) {
            val r = send("GET", "${Api.OUTBOX}/$id")
            assertTrue("$id -> ${r.status}", r.status == 404 || r.status == 400)
            assertFalse(r.text.contains("secret"))
        }
    }

    @Test fun outboxSurvivesARestart() {
        pairDevice()
        val src = tmp.newFolder("src").toPath().resolve("a.txt").also { Files.writeString(it, "hello") }
        server.sendToPhone(src)
        server.stop()
        port = newServer().start(0)
        val list = BridgeJson.decodeFromString(ListSerializer(FileInfo.serializer()), send("GET", Api.OUTBOX).text)
        assertEquals(listOf("a.txt"), list.map { it.name })
    }

    // ---- WebSocket --------------------------------------------------------------------------------------------

    private inner class Ws(val socket: WebSocket, val messages: LinkedBlockingQueue<String>, val closed: LinkedBlockingQueue<Int>) {
        fun send(event: InputEvent) {
            socket.sendText(BridgeJson.encodeToString(event), true).get(5, TimeUnit.SECONDS)
        }
        fun nextEvent(): ServerEvent {
            val text = messages.poll(10, TimeUnit.SECONDS) ?: throw AssertionError("No event arrived")
            return BridgeJson.decodeFromString(text)
        }
        fun nextClose(): Int = closed.poll(10, TimeUnit.SECONDS) ?: throw AssertionError("The channel was not closed")
    }

    private fun connectWs(headers: Map<String, String>? = null): Ws {
        val messages = LinkedBlockingQueue<String>()
        val closed = LinkedBlockingQueue<Int>()
        val buffer = StringBuilder()
        val listener = object : WebSocket.Listener {
            override fun onOpen(webSocket: WebSocket) { webSocket.request(1) }
            override fun onText(webSocket: WebSocket, data: CharSequence, last: Boolean): CompletionStage<*>? {
                buffer.append(data)
                if (last) { messages.add(buffer.toString()); buffer.setLength(0) }
                webSocket.request(1)
                return null
            }
            override fun onClose(webSocket: WebSocket, statusCode: Int, reason: String): CompletionStage<*>? {
                closed.add(statusCode)
                return null
            }
        }
        val builder = http.newWebSocketBuilder()
        (headers ?: sigHeaders("GET", Api.WS, null)).forEach { (k, v) -> builder.header(k, v) }
        val socket = builder.buildAsync(URI("wss://localhost:$port${Api.WS}"), listener).get(10, TimeUnit.SECONDS)
        sockets += socket
        return Ws(socket, messages, closed)
    }

    @Test fun webSocketNeedsASignedUpgrade() {
        pairDevice()
        for (bad in listOf(emptyMap(), sigHeaders("GET", Api.WS, null, tok = "bad"), sigHeaders("GET", "/other", null))) {
            try {
                connectWs(bad)
                fail("The upgrade must be refused")
            } catch (e: java.util.concurrent.ExecutionException) {
                val cause = e.cause as? java.net.http.WebSocketHandshakeException ?: throw e
                assertEquals(401, cause.response.statusCode())
            }
        }
        peerOk.set(false)
        try {
            connectWs()
            fail("The upgrade must be refused")
        } catch (e: java.util.concurrent.ExecutionException) {
            assertEquals(403, (e.cause as java.net.http.WebSocketHandshakeException).response.statusCode())
        }
    }

    @Test fun inputEventsReachThePlatform() {
        pairDevice()
        val ws = connectWs()
        ws.send(InputEvent.MouseMove(5, -3))
        ws.send(InputEvent.TypeText("Привет, мир 😀"))
        ws.send(InputEvent.Media(MediaAction.VolumeUp))
        ws.send(InputEvent.Click(MouseButton.Right))
        ws.send(InputEvent.Scroll(-4))
        ws.send(InputEvent.PressKey(io.github.alex333333.bridge.protocol.Key.Enter))
        await("all input events") { platform.calls.size >= 6 }
        assertEquals(
            listOf(
                "mouseMove(5,-3)", "typeText(Привет, мир 😀)", "media(VolumeUp)", "click(Right)", "scroll(-4)", "pressKey(Enter)",
            ),
            platform.calls.toList(),
        )
        assertTrue(platform.notifications.isNotEmpty())
    }

    @Test fun shutdownNeedsConfirmationButLockDoesNot() {
        pairDevice()
        val ws = connectWs()
        ws.send(InputEvent.Power(PowerAction.Shutdown, confirmed = false))
        ws.send(InputEvent.Power(PowerAction.Lock))
        ws.send(InputEvent.Media(MediaAction.Mute)) // events are handled in order, so once this lands the first two are done
        await("the marker event") { platform.calls.contains("media(Mute)") }
        assertEquals(listOf("power(Lock)", "media(Mute)"), platform.calls.toList())
        ws.send(InputEvent.Power(PowerAction.Shutdown, confirmed = true))
        await("confirmed shutdown") { platform.calls.contains("power(Shutdown)") }
        assertEquals(listOf("power(Lock)", "media(Mute)", "power(Shutdown)"), platform.calls.toList())
    }

    @Test fun malformedFramesAreIgnoredAndTheChannelStaysUp() {
        pairDevice()
        val ws = connectWs()
        ws.socket.sendText("not json", true).get(5, TimeUnit.SECONDS)
        ws.socket.sendText("""{"type":"nope"}""", true).get(5, TimeUnit.SECONDS)
        ws.send(InputEvent.Media(MediaAction.Next))
        await("the good event after the bad ones") { platform.calls.contains("media(Next)") }
        assertEquals(listOf("media(Next)"), platform.calls.toList())
    }

    @Test fun sendToPhoneArrivesAsOutboxChanged() {
        pairDevice()
        val ws = connectWs()
        ws.send(InputEvent.Media(MediaAction.Mute)) // proves the server side of the channel is up
        await("channel ready") { platform.calls.isNotEmpty() }
        val src = tmp.newFolder("src").toPath().resolve("f.txt").also { Files.writeString(it, "x") }
        server.sendToPhone(src)
        assertEquals(ServerEvent.OutboxChanged, ws.nextEvent())
    }

    @Test fun noteChangesAreBroadcastToTheChannel() {
        pairDevice()
        val ws = connectWs()
        ws.send(InputEvent.Media(MediaAction.Mute))
        await("channel ready") { platform.calls.isNotEmpty() }
        json("PUT", "${Api.NOTES}/n1", BridgeJson.encodeToString(Note("n1", "a", 1)))
        assertEquals(ServerEvent.NotesChanged, ws.nextEvent())
    }

    private fun connectedBalloons() = platform.notifications.filter { it.startsWith("Телефон подключён") }

    private fun connectAndWaitUntilServed(): Ws {
        val before = platform.calls.size
        val ws = connectWs()
        ws.send(InputEvent.Media(MediaAction.Mute))
        await("channel ready") { platform.calls.size > before }
        return ws
    }

    @Test fun theConnectedBalloonIsRussianAndNamesThePhone() {
        pairRequest(server.newPairingCode().code, name = "Pixel").also { token = BridgeJson.decodeFromString<PairResponse>(it.text).token }
        connectAndWaitUntilServed()
        assertEquals(listOf("Телефон подключён: Pixel"), connectedBalloons())
    }

    @Test fun theConnectedBalloonIsShownOncePerDeviceInTenMinutes() {
        pairDevice()
        connectAndWaitUntilServed()
        assertEquals(1, connectedBalloons().size)
        clock.addAndGet(9 * 60_000L)
        connectAndWaitUntilServed() // a reconnect after 9 minutes: silent
        assertEquals(1, connectedBalloons().size)
        clock.addAndGet(2 * 60_000L) // 11 minutes after the first balloon
        connectAndWaitUntilServed()
        assertEquals(2, connectedBalloons().size)
    }

    @Test fun theConnectedBalloonLimitIsPerDevice() {
        pairDevice()
        connectAndWaitUntilServed()
        val firstToken = token
        val r = pairRequest(server.newPairingCode().code, device = "phone-2", name = "Other")
        token = BridgeJson.decodeFromString<PairResponse>(r.text).token
        val before = platform.calls.size
        val ws = connectWs(sigHeaders("GET", Api.WS, null, device = "phone-2", tok = token))
        ws.send(InputEvent.Media(MediaAction.Mute))
        await("second channel ready") { platform.calls.size > before }
        assertEquals(listOf("Телефон подключён: Phone", "Телефон подключён: Other"), connectedBalloons())
        assertNotEquals(firstToken, token)
    }

    // ---- notes seen from the PC ---------------------------------------------------------------------------------

    @Test fun pcNotesRoundTripWithCyrillicEmojiAndNewlines() {
        assertEquals(emptyList<Note>(), server.listNotes())
        val text = "Привет, мир! 😀\nвторая строка\r\n\t\"кавычки\""
        val created = server.saveNote(null, text)
        assertEquals(text, created.text)
        assertTrue(created.id.isNotBlank())
        assertEquals(clock.get(), created.updatedAt)
        assertEquals(listOf(created), server.listNotes())

        clock.addAndGet(1_000)
        val edited = server.saveNote(created.id, "правка")
        assertEquals(created.id, edited.id)
        assertEquals(clock.get(), edited.updatedAt)
        assertEquals(listOf(edited), server.listNotes())

        // The phone sees the same list over HTTP.
        pairDevice()
        assertEquals(listOf(edited), BridgeJson.decodeFromString(ListSerializer(Note.serializer()), send("GET", Api.NOTES).text))

        server.deleteNote(created.id)
        assertEquals(emptyList<Note>(), server.listNotes())
        server.deleteNote("no-such-note") // harmless
    }

    @Test fun pcNoteChangesAreBroadcastToTheChannelAndListeners() {
        pairDevice()
        val ws = connectAndWaitUntilServed()
        val seen = java.util.concurrent.atomic.AtomicInteger()
        server.onNotesChanged { seen.incrementAndGet() }
        val note = server.saveNote(null, "a")
        assertEquals(ServerEvent.NotesChanged, ws.nextEvent())
        server.deleteNote(note.id)
        assertEquals(ServerEvent.NotesChanged, ws.nextEvent())
        assertEquals(2, seen.get())
    }

    @Test fun aNoteFromThePhoneNotifiesOnlyWhenItIsNew() {
        pairDevice()
        platform.notifications.clear()
        assertEquals(200, json("PUT", "${Api.NOTES}/n1", BridgeJson.encodeToString(Note("n1", "a", 1))).status)
        assertEquals(listOf("Новая заметка с телефона"), platform.notifications.toList())
        assertEquals(200, json("PUT", "${Api.NOTES}/n1", BridgeJson.encodeToString(Note("n1", "b", 2))).status)
        assertEquals(listOf("Новая заметка с телефона"), platform.notifications.toList())
        assertEquals(200, json("PUT", "${Api.NOTES}/n2", BridgeJson.encodeToString(Note("n2", "c", 3))).status)
        assertEquals(2, platform.notifications.size)
    }

    @Test fun aNoteSavedOnThePcDoesNotNotify() {
        platform.notifications.clear()
        server.saveNote(null, "x")
        assertTrue(platform.notifications.isEmpty())
    }

    @Test fun unpairAllRevokesTokensAndNotifiesTheChannel() {
        pairDevice()
        val ws = connectWs()
        ws.send(InputEvent.Media(MediaAction.Mute))
        await("channel ready") { platform.calls.isNotEmpty() }
        assertEquals(200, send("GET", Api.NOTES).status)
        server.unpairAll()
        assertEquals(ServerEvent.Unpaired, ws.nextEvent())
        ws.nextClose()
        assertEquals(401, send("GET", Api.NOTES).status)
        // Pairing again with a fresh code works and gives a different token.
        val old = token
        pairDevice()
        assertNotEquals(old, token)
        assertEquals(200, send("GET", Api.NOTES).status)
    }

    @Test fun unpairAllSurvivesARestart() {
        pairDevice()
        server.unpairAll()
        server.stop()
        port = newServer().start(0)
        assertEquals(401, send("GET", Api.NOTES).status)
    }

    @Test fun aLongTypeTextFrameIsDelivered() {
        pairDevice()
        val ws = connectWs()
        ws.send(InputEvent.TypeText("а".repeat(50_000)))
        await("the long text") { platform.calls.size == 1 }
        assertEquals("typeText(${"а".repeat(50_000)})", platform.calls.single())
    }

}
