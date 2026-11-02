package io.github.alex333333.bridge.pc

import io.github.alex333333.bridge.protocol.BridgeApi
import io.github.alex333333.bridge.protocol.BridgeChannel
import io.github.alex333333.bridge.protocol.InputEvent
import io.github.alex333333.bridge.protocol.MouseButton
import io.github.alex333333.bridge.protocol.Note
import io.github.alex333333.bridge.protocol.NotPairedException
import io.github.alex333333.bridge.protocol.ServerEvent
import io.github.alex333333.bridge.protocol.client.BridgeClient
import io.github.alex333333.bridge.protocol.client.BridgeHttpException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.Random
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException

/** The real [BridgeClient] against the real [BridgeServer] (port 0, loopback, fake platform). */
class ClientAgainstServerTest {
    @get:Rule val tmp = TemporaryFolder()

    private lateinit var dataDir: Path
    private lateinit var downloads: Path
    private lateinit var platform: FakePlatform
    private lateinit var server: BridgeServer
    private var port = 0

    private val servers = mutableListOf<BridgeServer>()
    private val clients = CopyOnWriteArrayList<BridgeClient>()
    private val channels = CopyOnWriteArrayList<BridgeChannel>()

    private val deviceId = "phone-1"
    private var lastToken = "" // the token of the client that pairedClient() made last

    @Before fun setUp() {
        dataDir = tmp.newFolder("data").toPath()
        downloads = tmp.newFolder("downloads").toPath()
        platform = FakePlatform()
        server = newServer(dataDir, downloads, platform)
        port = server.start(0)
    }

    @After fun tearDown() {
        channels.forEach { runCatching { it.close() } }
        clients.forEach { runCatching { it.close() } }
        servers.forEach { runCatching { it.stop() } }
    }

    private fun newServer(data: Path, dl: Path, hooks: FakePlatform): BridgeServer {
        val s = BridgeServer(data, dl, hooks, "Рабочий ПК", listOf("AA-BB-CC-DD-EE-FF"))
        servers += s
        return s
    }

    private fun client(
        token: String? = null,
        pin: String? = server.tlsFingerprintHex,
        port: Int = this.port,
        now: () -> Long = System::currentTimeMillis,
    ): BridgeClient = BridgeClient("127.0.0.1", port, deviceId, token, pin, now).also { clients += it }

    /** A client that went through the real pairing handshake and trusts the PC certificate only. */
    private fun pairedClient(): BridgeClient {
        val c = client(pin = null)
        val result = runBlocking { c.pair(server.newPairingCode().code, "Pixel") }
        assertEquals(server.tlsFingerprintHex, result.fingerprintHex)
        lastToken = result.token
        return c
    }

    private fun await(what: String, timeoutMs: Long = 10_000, condition: () -> Boolean) {
        val deadline = System.nanoTime() + timeoutMs * 1_000_000
        while (!condition()) {
            if (System.nanoTime() > deadline) fail("Timed out waiting for $what")
            Thread.sleep(10)
        }
    }

    private inline fun <reified T : Throwable> failsWith(what: String, noinline block: suspend () -> Unit): T {
        try {
            runBlocking { withTimeout(15_000) { block() } }
        } catch (e: Throwable) {
            if (e is T) return e
            throw AssertionError("$what: expected ${T::class.simpleName} but got $e", e)
        }
        throw AssertionError("$what: expected ${T::class.simpleName} but nothing was thrown")
    }

    private fun randomBytes(n: Int, seed: Long = 1): ByteArray = ByteArray(n).also { Random(seed).nextBytes(it) }

    private fun files(dir: Path): List<String> = Files.list(dir).use { s -> s.map { it.fileName.toString() }.sorted().toList() }

    // ---- hello and pairing -------------------------------------------------------------------------------------

    @Test fun helloWorksWithAPinnedFingerprint() = runBlocking {
        val hello = client().hello()
        assertEquals("Рабочий ПК", hello.name)
        assertEquals("bridge", hello.app)
        assertEquals(server.pcId, hello.pcId)
    }

    @Test fun helloWorksWithoutAFingerprintAndTrustsNothingElse() = runBlocking {
        val c = client(pin = null)
        assertEquals(server.pcId, c.hello().pcId)
        // No pin recorded by hello(): every other call still needs one.
        failsWith<NotPairedException>("notes without a pin") { c.notes() }
        Unit
    }

    @Test fun pairWithTheWrongCodeFails() {
        val code = server.newPairingCode().code
        val wrong = if (code == "000000") "000001" else "000000"
        val c = client(pin = null)
        val e = failsWith<BridgeHttpException>("wrong code") { c.pair(wrong, "Pixel") }
        assertEquals(403, e.status)
        failsWith<NotPairedException>("still not paired") { c.notes() }
    }

    @Test fun pairWithTheRightCodeReturnsTheFingerprintOfTheCertificate() = runBlocking {
        val pc = server.newPairingCode()
        val c = client(pin = null)
        val result = c.pair(pc.code, "Pixel")
        assertEquals(server.tlsFingerprintHex, result.fingerprintHex)
        assertEquals("Рабочий ПК", result.pcName)
        assertEquals(listOf("AA-BB-CC-DD-EE-FF"), result.macs)
        assertTrue(result.token.isNotBlank())
        assertTrue(platform.notifications.any { "Pixel" in it })
        // The same client is now paired and pinned: no further set-up is needed.
        assertEquals(emptyList<Note>(), c.notes())
    }

    @Test fun pairingAClientThatAlreadyKnowsTheCertificateStillWorks() = runBlocking {
        val c = client() // pinned from the start
        val result = c.pair(server.newPairingCode().code, "Pixel")
        assertEquals(server.tlsFingerprintHex, result.fingerprintHex)
        assertEquals(emptyList<Note>(), c.notes())
    }

    @Test fun aClientBuiltFromTheSavedTokenAndFingerprintWorks() = runBlocking {
        val first = client(pin = null)
        val result = first.pair(server.newPairingCode().code, "Pixel")
        val later = client(token = result.token, pin = result.fingerprintHex)
        later.putNote(Note("n1", "hello", 1))
        assertEquals(listOf(Note("n1", "hello", 1)), first.notes())
    }

    // ---- notes and clipboard -----------------------------------------------------------------------------------

    @Test fun notesRoundTrip() = runBlocking {
        val c = pairedClient()
        assertEquals(emptyList<Note>(), c.notes())
        val a = Note("n1", "первая заметка 😀 \"quoted\" \\ \n second line", 100)
        val b = Note("id with space/slash?and#odd%chars", "две", 200)
        c.putNote(a)
        c.putNote(b)
        assertEquals(setOf(a, b), c.notes().toSet())
        c.putNote(a.copy(text = "changed", updatedAt = 300))
        assertEquals("changed", c.notes().first { it.id == "n1" }.text)
        c.deleteNote("n1")
        assertEquals(listOf(b), c.notes())
        c.deleteNote(b.id)
        assertEquals(emptyList<Note>(), c.notes())
    }

    @Test fun deletingAMissingNoteIsNotAnError() = runBlocking {
        pairedClient().deleteNote("nothing-here")
    }

    @Test fun clipboardRoundTrip() = runBlocking {
        val c = pairedClient()
        platform.clipboard = "from the PC — текст"
        assertEquals("from the PC — текст", c.clipboard())
        c.setClipboard("from the phone 😀 \"q\" \n line 2")
        assertEquals("from the phone 😀 \"q\" \n line 2", platform.clipboard)
        c.setClipboard("")
        assertEquals("", platform.clipboard)
    }

    // ---- files -------------------------------------------------------------------------------------------------

    @Test fun uploadOf8MiBStreamsWithProgress() = runBlocking {
        val c = pairedClient()
        val data = randomBytes(8 * 1024 * 1024)
        val progress = CopyOnWriteArrayList<Long>()
        val info = c.upload("big file+1&2.bin", data.size.toLong(), { ByteArrayInputStream(data) }, { progress += it })
        assertEquals(data.size.toLong(), info.size)
        assertEquals("big file+1&2.bin", info.name)
        assertTrue(progress.isNotEmpty())
        assertEquals(data.size.toLong(), progress.last())
        assertEquals(progress.sorted(), progress.toList()) // never goes backwards
        assertTrue("progress is reported along the way, not only at the end", progress.size > 2)
        assertArrayEquals(data, Files.readAllBytes(downloads.resolve("big file+1&2.bin")))
        assertEquals(listOf("big file+1&2.bin"), files(downloads))
    }

    @Test fun uploadWithTheSameNameGetsANumberedName() = runBlocking {
        val c = pairedClient()
        val first = c.upload("same.txt", 3, { ByteArrayInputStream(byteArrayOf(1, 2, 3)) }, {})
        val second = c.upload("same.txt", 2, { ByteArrayInputStream(byteArrayOf(4, 5)) }, {})
        assertEquals("same.txt", first.name)
        assertEquals("same (1).txt", second.name)
    }

    @Test fun uploadFailsWhenTheSourceIsShorterThanDeclared() {
        val c = pairedClient()
        failsWith<IOException>("short source") { c.upload("short.bin", 100, { ByteArrayInputStream(ByteArray(40)) }, {}) }
        await("the server to drop the partial upload") { files(downloads).isEmpty() }
    }

    @Test fun uploadFailsWhenTheSourceIsLongerThanDeclared() {
        val c = pairedClient()
        // Sizes around the buffer boundaries: the PC must never see a complete body of the announced length.
        for (size in listOf(0L, 1L, 40L, 8_192L, 65_536L, 70_000L)) {
            failsWith<IOException>("long source, size $size") { c.upload("long.bin", size, { ByteArrayInputStream(ByteArray(size.toInt() + 100)) }, {}) }
        }
        await("the server to drop the partial uploads") { files(downloads).isEmpty() }
    }

    @Test fun uploadFailsWhenTheSourceCannotBeRead() {
        val c = pairedClient()
        failsWith<IOException>("unreadable source") {
            c.upload("bad.bin", 10, { throw IllegalStateException("file vanished") }, {})
        }
        assertEquals(emptyList<String>(), files(downloads))
    }

    @Test fun outboxAndDownload() = runBlocking {
        val c = pairedClient()
        assertEquals(emptyList<Any>(), c.outbox())
        val data = randomBytes(3 * 1024 * 1024 + 17, seed = 7)
        val original = tmp.newFolder("pc-files").toPath().resolve("report.bin")
        Files.write(original, data)
        server.sendToPhone(original)

        val list = c.outbox()
        assertEquals(1, list.size)
        assertEquals("report.bin", list[0].name)
        assertEquals(data.size.toLong(), list[0].size)

        val sink = ByteArrayOutputStream()
        val progress = CopyOnWriteArrayList<Long>()
        c.download(list[0].id, sink) { progress += it }
        assertArrayEquals(data, sink.toByteArray())
        assertEquals(data.size.toLong(), progress.last())
        assertEquals(progress.sorted(), progress.toList())

        c.deleteOutbox(list[0].id)
        assertEquals(emptyList<Any>(), c.outbox())
        assertTrue("the user's original is never touched", Files.exists(original))
        val e = failsWith<BridgeHttpException>("download after delete") { c.download(list[0].id, ByteArrayOutputStream()) {} }
        assertEquals(404, e.status)
        c.deleteOutbox(list[0].id) // already gone: not an error
    }

    @Test fun cancellingTheCoroutineAbortsADownload() = runBlocking {
        val c = pairedClient()
        val original = tmp.newFolder("pc-big").toPath().resolve("huge.bin")
        Files.write(original, randomBytes(6 * 1024 * 1024))
        server.sendToPhone(original)
        val id = c.outbox().single().id

        val writing = CountDownLatch(1)
        val release = CountDownLatch(1)
        val blocking = object : OutputStream() {
            override fun write(b: Int) = write(byteArrayOf(b.toByte()), 0, 1)
            override fun write(b: ByteArray, off: Int, len: Int) {
                writing.countDown()
                release.await(10, TimeUnit.SECONDS)
            }
        }
        val job = launch(Dispatchers.Default, start = CoroutineStart.DEFAULT) { c.download(id, blocking) {} }
        assertTrue(writing.await(10, TimeUnit.SECONDS))
        withTimeout(5_000) { job.cancelAndJoin() } // returns although the sink is still blocked
        assertTrue(job.isCancelled)
        release.countDown()
        // The client is still usable afterwards.
        assertEquals(1, c.outbox().size)
    }

    // ---- channel -----------------------------------------------------------------------------------------------

    @Test fun inputEventsReachThePlatform() {
        val c = pairedClient()
        val channel = c.openChannel().also { channels += it }
        channel.send(InputEvent.MouseMove(5, -7)) // sent right away, before the socket is open
        channel.send(InputEvent.Click(MouseButton.Right))
        channel.send(InputEvent.TypeText("привет 😀"))
        await("the events at the platform") { platform.calls.size >= 3 }
        assertEquals(listOf("mouseMove(5,-7)", "click(Right)", "typeText(привет 😀)"), platform.calls.toList())
    }

    @Test fun eventsDeliverOutboxChanged() = runBlocking {
        val c = pairedClient()
        val channel = c.openChannel().also { channels += it }
        val original = tmp.newFolder("pc-evt").toPath().resolve("e.txt")
        Files.write(original, byteArrayOf(1))
        val received = async(Dispatchers.Default) { withTimeout(15_000) { channel.events.first() } }
        // The server subscribes a moment after the upgrade; offering the file again is harmless, so retry until seen.
        while (!received.isCompleted) {
            server.sendToPhone(original)
            delay(50)
        }
        assertEquals(ServerEvent.OutboxChanged, received.await())
    }

    @Test fun eventsDeliverUnpairedAndTheClientIsThenNotPaired() = runBlocking {
        val c = pairedClient()
        val channel = c.openChannel().also { channels += it }
        val received = async(Dispatchers.Default) { withTimeout(15_000) { channel.events.first { it == ServerEvent.Unpaired } } }
        // A frame that reached the platform proves the upgrade is done and the server listens for events.
        channel.send(InputEvent.Scroll(1))
        await("the channel to be up") { platform.calls.isNotEmpty() }
        server.unpairAll()
        assertEquals(ServerEvent.Unpaired, received.await())
        failsWith<NotPairedException>("request after unpairAll") { c.notes() }
        Unit
    }

    @Test fun eventsEndWhenTheChannelIsClosed() = runBlocking {
        val c = pairedClient()
        val channel = c.openChannel()
        val done = async(Dispatchers.Default) { withTimeout(15_000) { channel.events.toList() } }
        delay(100)
        channel.close()
        assertEquals(emptyList<ServerEvent>(), done.await())
        channel.close() // closing twice is fine
    }

    @Test fun openingAChannelWithoutCredentialsFailsClearly() {
        failsWith<NotPairedException>("no token") { client(token = null).openChannel() }
        val c = pairedClient()
        server.unpairAll()
        val channel = c.openChannel().also { channels += it }
        failsWith<NotPairedException>("channel of an unpaired device") { channel.events.toList() }
    }

    // ---- pinning -----------------------------------------------------------------------------------------------

    @Test fun aClientWithTheWrongFingerprintFailsOnEveryRequest() {
        val good = pairedClient()
        val token = lastToken
        val wrong = client(token = token, pin = "00".repeat(32))
        val sink = ByteArrayOutputStream()
        val calls: Map<String, suspend (BridgeApi) -> Unit> = mapOf(
            "hello" to { it.hello() },
            "pair" to { it.pair(server.newPairingCode().code, "Evil") },
            "notes" to { it.notes() },
            "putNote" to { it.putNote(Note("x", "y", 1)) },
            "deleteNote" to { it.deleteNote("x") },
            "clipboard" to { it.clipboard() },
            "setClipboard" to { it.setClipboard("stolen") },
            "upload" to { it.upload("x.bin", 3, { ByteArrayInputStream(byteArrayOf(1, 2, 3)) }, {}) },
            "outbox" to { it.outbox() },
            "download" to { it.download("x", sink) {} },
            "deleteOutbox" to { it.deleteOutbox("x") },
        )
        for ((name, call) in calls) {
            failsWith<SSLException>(name) { call(wrong) }
        }
        failsWith<IOException>("channel") { wrong.openChannel().also { channels += it }.events.toList() }
        assertEquals("nothing reached the server", emptyList<String>(), files(downloads))
        assertEquals("", platform.clipboard)
        assertEquals(emptyList<Note>(), runBlocking { good.notes() })
    }

    @Test fun aDifferentCertificateThanThePinnedOneIsRejected() {
        val other = newServer(tmp.newFolder("data2").toPath(), tmp.newFolder("downloads2").toPath(), FakePlatform())
        val otherPort = other.start(0)
        assertNotEquals(server.tlsFingerprintHex, other.tlsFingerprintHex)
        // Pinned to the first PC, pointed at the second one: hello() and pair() included.
        val c = client(token = "some-token", pin = server.tlsFingerprintHex, port = otherPort)
        failsWith<SSLException>("hello") { c.hello() }
        failsWith<SSLException>("pair") { c.pair(other.newPairingCode().code, "Phone") }
        failsWith<SSLException>("notes") { c.notes() }
        // The second PC is fine for a client that pinned it.
        assertEquals(other.pcId, runBlocking { client(pin = other.tlsFingerprintHex, port = otherPort).hello().pcId })
    }

    @Test fun aPinnedClientNeverFallsBackToTrustingAnyCertificateAfterAFailure() = runBlocking {
        val c = client(pin = "ab".repeat(32))
        repeat(3) { failsWith<SSLException>("attempt $it") { c.hello() } }
    }

    // ---- credentials -------------------------------------------------------------------------------------------

    @Test fun afterUnpairAllTheNextRequestIsNotPaired() {
        val c = pairedClient()
        assertEquals(emptyList<Note>(), runBlocking { c.notes() })
        server.unpairAll()
        val e = failsWith<NotPairedException>("after unpairAll") { c.notes() }
        assertTrue(e.message!!.isNotBlank())
        failsWith<NotPairedException>("upload after unpairAll") { c.upload("a.bin", 1, { ByteArrayInputStream(byteArrayOf(1)) }, {}) }
        failsWith<NotPairedException>("download after unpairAll") { c.download("x", ByteArrayOutputStream()) {} }
        assertEquals(emptyList<String>(), files(downloads))
    }

    @Test fun aClientCanPairAgainAfterBeingUnpaired() = runBlocking {
        val c = pairedClient()
        server.unpairAll()
        failsWith<NotPairedException>("unpaired") { c.notes() }
        c.pair(server.newPairingCode().code, "Pixel")
        assertEquals(emptyList<Note>(), c.notes())
    }

    @Test fun unpairForgetsTheTokenLocallyWithoutAnyRequest() {
        val c = pairedClient()
        runBlocking { c.unpair() }
        failsWith<NotPairedException>("after unpair") { c.notes() }
    }

    @Test fun aMissingOrBlankTokenIsNotPairedWithoutContactingThePc() {
        for (token in listOf(null, "", "   ")) {
            val c = client(token = token, port = 1) // nothing listens there: a network error would be a different exception
            failsWith<NotPairedException>("token '$token' notes") { c.notes() }
            failsWith<NotPairedException>("token '$token' clipboard") { c.clipboard() }
            failsWith<NotPairedException>("token '$token' upload") { c.upload("a", 1, { ByteArrayInputStream(byteArrayOf(1)) }, {}) }
            failsWith<NotPairedException>("token '$token' outbox") { c.outbox() }
            failsWith<NotPairedException>("token '$token' channel") { c.openChannel() }
        }
    }

    @Test fun aWrongTokenGivesNotPaired() {
        val c = client(token = "not-the-token")
        failsWith<NotPairedException>("wrong token") { c.notes() }
    }

    @Test fun aClockFarFromThePcClockGivesNotPaired() {
        pairedClient()
        val token = lastToken
        val skewed = client(token = token, now = { System.currentTimeMillis() + 10 * 60_000 })
        failsWith<NotPairedException>("skewed clock") { skewed.notes() }
    }

    @Test fun twoPhonesPairedSeparatelyWorkIndependently() = runBlocking {
        val a = pairedClient()
        val b = BridgeClient("127.0.0.1", port, "phone-2", null, null).also { clients += it }
        b.pair(server.newPairingCode().code, "Second")
        a.putNote(Note("n", "from a", 1))
        assertEquals("from a", b.notes().single().text)
        server.unpairAll()
        failsWith<NotPairedException>("a") { a.notes() }
        failsWith<NotPairedException>("b") { b.notes() }
        Unit
    }

    @Test fun anUnreachablePcGivesAnIoError() {
        val closedPort = java.net.ServerSocket(0).use { it.localPort }
        val c = client(token = "t", port = closedPort)
        failsWith<IOException>("connection refused") { c.notes() }
        failsWith<IOException>("hello") { c.hello() }
    }

}
