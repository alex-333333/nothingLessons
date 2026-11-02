package io.github.alex333333.bridge.pc

import io.github.alex333333.bridge.protocol.Key
import io.github.alex333333.bridge.protocol.MediaAction
import io.github.alex333333.bridge.protocol.MouseButton
import io.github.alex333333.bridge.protocol.PowerAction
import io.github.alex333333.bridge.protocol.client.BridgeClient
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.nio.file.Files
import java.nio.file.Path

/** Does nothing at all: the self-test must never touch AWT, the tray, Robot or the real clipboard. */
private object NoOpPlatform : PlatformHooks {
    override fun mouseMove(dx: Int, dy: Int) {}
    override fun click(button: MouseButton) {}
    override fun scroll(dy: Int) {}
    override fun typeText(text: String) {}
    override fun pressKey(key: Key) {}
    override fun media(action: MediaAction) {}
    override fun power(action: PowerAction) {}
    override fun read(): String = ""
    override fun write(text: String) {}
    override fun notify(text: String) {}
}

/**
 * Starts the real server on a random port with temporary folders, then pairs and makes one signed request with the
 * real client over HTTPS. Reports one line per step to [log]. Returns the process exit code: 0 on success, 1 on any failure.
 * This is what CI runs against the packaged program to prove that no JRE module is missing.
 */
internal fun runSelfTest(log: (String) -> Unit): Int {
    var data: Path? = null
    var downloads: Path? = null
    var server: BridgeServer? = null
    var client: BridgeClient? = null
    try {
        data = Files.createTempDirectory("bridge-selftest-data")
        downloads = Files.createTempDirectory("bridge-selftest-downloads")
        server = BridgeServer(data, downloads, NoOpPlatform, "selftest", emptyList())
        val port = server.start(0)
        log("OK server started on port $port")

        val c = BridgeClient("127.0.0.1", port, "selftest-device", null, null)
        client = c
        runBlocking {
            withTimeout(STEP_TIMEOUT_MS) {
                val hello = c.hello()
                check(hello.app == "bridge") { "unexpected hello answer: app=${hello.app}" }
                log("OK hello: ${hello.name}, pc ${hello.pcId}")

                val paired = c.pair(server.newPairingCode().code, "selftest")
                check(paired.fingerprintHex.equals(server.tlsFingerprintHex, ignoreCase = true)) { "certificate fingerprint mismatch" }
                log("OK pair: certificate ${paired.fingerprintHex.take(8)}")

                val notes = c.notes()
                log("OK signed request: ${notes.size} notes")
            }
        }
        log("SELFTEST PASSED")
        return 0
    } catch (e: Throwable) {
        log("FAILED: $e")
        return 1
    } finally {
        runCatching { client?.close() }
        runCatching { server?.stop() }
        runCatching { downloads?.let(::deleteTree) }
        runCatching { data?.let(::deleteTree) }
    }
}

private const val STEP_TIMEOUT_MS = 30_000L

private fun deleteTree(dir: Path) {
    Files.walk(dir).use { s -> s.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) } }
}
