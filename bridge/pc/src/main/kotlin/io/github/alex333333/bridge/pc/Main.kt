package io.github.alex333333.bridge.pc

import io.github.alex333333.bridge.protocol.Api
import java.io.IOException
import java.net.BindException
import java.net.InetAddress
import java.net.ServerSocket
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import java.util.concurrent.CountDownLatch
import kotlin.system.exitProcess

data class Args(val selftest: Boolean = false, val port: Int = Api.PORT, val out: String? = null)

/** `--selftest`, `--port N`, `--out FILE`. Anything else, or a port outside 0..65535, is an [IllegalArgumentException]. */
fun parseArgs(args: Array<String>): Args {
    var result = Args()
    var i = 0
    while (i < args.size) {
        when (val flag = args[i]) {
            "--selftest" -> result = result.copy(selftest = true)
            "--port" -> {
                val value = args.getOrNull(++i) ?: throw IllegalArgumentException("--port needs a number")
                val port = value.toIntOrNull()?.takeIf { it in 0..65535 }
                    ?: throw IllegalArgumentException("Invalid port: '$value'")
                result = result.copy(port = port)
            }
            "--out" -> result = result.copy(out = args.getOrNull(++i) ?: throw IllegalArgumentException("--out needs a file name"))
            else -> throw IllegalArgumentException("Unknown argument: $flag")
        }
        i++
    }
    return result
}

fun main(args: Array<String>) {
    val parsed = try {
        parseArgs(args)
    } catch (e: IllegalArgumentException) {
        System.err.println("${e.message}\nUsage: Bridge [--selftest] [--port N] [--out FILE]")
        exitProcess(1)
    }
    exitProcess(if (parsed.selftest) selfTestToOutput(parsed.out) else runApp(parsed.port))
}

/** One line per step to [out] (appended and written at once, so a hang still leaves a trace) or to stdout. */
private fun selfTestToOutput(out: String?): Int {
    val file = out?.let { Paths.get(it).toAbsolutePath() }
    file?.let { f ->
        try {
            f.parent?.let(Files::createDirectories)
            Files.write(f, ByteArray(0))
        } catch (e: IOException) {
            System.err.println("Cannot write $f: $e")
            return 1
        }
    }
    return runSelfTest { line ->
        if (file == null) println(line) else try {
            Files.write(file, (line + System.lineSeparator()).toByteArray(Charsets.UTF_8), StandardOpenOption.APPEND)
        } catch (_: IOException) {
            System.err.println(line)
        }
    }
}

private const val EXIT_PORT_BUSY = 2

private fun appDataDir(): Path {
    val appData = System.getenv("APPDATA")?.takeIf { it.isNotBlank() }
    return if (appData != null) Paths.get(appData, "Bridge") else Paths.get(System.getProperty("user.home"), ".bridge")
}

private fun pcName(): String =
    try {
        InetAddress.getLocalHost().hostName
    } catch (_: Exception) {
        System.getenv("COMPUTERNAME") ?: System.getenv("HOSTNAME") ?: "PC"
    }

private fun portIsFree(port: Int): Boolean =
    try {
        ServerSocket(port).use { true }
    } catch (_: IOException) {
        false
    }

private fun isBindFailure(e: Throwable): Boolean = generateSequence(e) { it.cause }.any { it is BindException }

private fun runApp(port: Int): Int {
    val ui = Ui.create()
    var server: BridgeServer? = null
    var discovery: Discovery? = null
    var uiShown = false
    try {
        // A second copy of the program finds the port taken; that is the whole single-instance check.
        if (port != 0 && !portIsFree(port)) {
            ui.error("Мост уже запущен или порт $port занят другой программой.")
            return EXIT_PORT_BUSY
        }

        val dataDir = appDataDir()
        val downloads = Paths.get(System.getProperty("user.home"), "Downloads", "Мост")
        Files.createDirectories(dataDir)
        Files.createDirectories(downloads)

        val platform = WindowsPlatform { ui.notify(it) }
        val bridge = BridgeServer(dataDir, downloads, platform, pcName(), MacList::current)
        server = bridge
        val boundPort = try {
            bridge.start(port)
        } catch (e: Exception) {
            if (isBindFailure(e)) {
                ui.error("Мост уже запущен или порт $port занят другой программой.")
                return EXIT_PORT_BUSY
            }
            throw e
        }

        val announcer = Discovery(pcName(), bridge.pcId, boundPort)
        discovery = announcer
        try {
            announcer.start()
        } catch (e: Exception) {
            // The phone can still be paired by typing the address; do not stop the program for this.
            System.err.println("mDNS announcement failed: $e")
        }

        val done = CountDownLatch(1)
        uiShown = true
        ui.show(
            UiActions(
                newCode = bridge::newPairingCode,
                sendToPhone = bridge::sendToPhone,
                downloadsDir = downloads,
                unpairAll = bridge::unpairAll,
                listNotes = bridge::listNotes,
                saveNote = { id, text -> bridge.saveNote(id, text) },
                deleteNote = bridge::deleteNote,
                onNotesChanged = bridge::onNotesChanged,
                exit = { done.countDown() },
            ),
        )
        done.await()
        return 0
    } catch (t: Throwable) {
        // No silent death and no zombie: tell the user, stop whatever already runs, exit with an error.
        val message = "Не удалось запустить Мост: ${t.message ?: t}"
        runCatching { ui.error(message) }.onFailure { System.err.println(message) }
        return 1
    } finally {
        if (uiShown) runCatching { ui.dispose() }
        discovery?.let { runCatching { it.close() } }
        server?.let { runCatching { it.stop() } }
    }
}
