package io.github.alex333333.bridge.pc

import io.github.alex333333.bridge.protocol.BridgeJson
import io.github.alex333333.bridge.protocol.FileInfo
import io.github.alex333333.bridge.protocol.SafeName
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import java.io.OutputStream
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.UUID

/** An upload the server refuses (empty, or the wrong length); the message is safe to show to the client. */
internal class UploadRejected(message: String) : Exception(message)

/**
 * Files in both directions: uploads from the phone into [downloadsDir], and the outbox of files the PC offers to the
 * phone (kept in `outbox.json`; it only remembers paths, it never copies or deletes the user's files).
 */
internal class FileStore(private val downloadsDir: Path, dataDir: Path) {
    @Serializable private data class OutboxEntry(val id: String, val name: String, val path: String)

    private val outboxFile = dataDir.resolve("outbox.json")
    private val outboxSerializer = ListSerializer(OutboxEntry.serializer())
    private val commitLock = Any()
    private var outbox: List<OutboxEntry> = try {
        if (Files.exists(outboxFile)) BridgeJson.decodeFromString(outboxSerializer, Files.readString(outboxFile)) else emptyList()
    } catch (_: Exception) {
        emptyList()
    }

    // ---- uploads -----------------------------------------------------------------------------------------------

    /**
     * Streams [source] into `<safe name>.part` inside the downloads folder and renames it to a free name only after
     * the whole body arrived and its length is right. On every failure, including a client that disconnects, the
     * `.part` file is deleted. The name is never trusted: it always goes through [SafeName.of] and [SafeName.unique].
     */
    suspend fun receive(rawName: String, declaredLength: Long?, source: ByteReadChannel): FileInfo = withContext(Dispatchers.IO) {
        Files.createDirectories(downloadsDir)
        val safe = SafeName.of(rawName)
        val (part, stream) = createPart(safe)
        var committed = false
        try {
            var total = 0L
            stream.use { out ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val n = source.readAvailable(buffer, 0, buffer.size)
                    if (n < 0) break
                    out.write(buffer, 0, n)
                    total += n
                }
            }
            if (total == 0L) throw UploadRejected("Empty file")
            if (declaredLength != null && declaredLength != total) throw UploadRejected("Incomplete upload")
            val finalName = commit(part, safe)
            committed = true
            FileInfo(finalName, finalName, total)
        } finally {
            if (!committed) Files.deleteIfExists(part)
        }
    }

    private fun createPart(safe: String): Pair<Path, OutputStream> {
        var i = 0
        while (true) {
            val candidate = downloadsDir.resolve(if (i == 0) "$safe.part" else "$safe.$i.part")
            check(candidate.normalize().parent == downloadsDir.normalize()) { "Name escapes the downloads folder" }
            try {
                return candidate to Files.newOutputStream(candidate, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)
            } catch (_: FileAlreadyExistsException) {
                i++
            }
        }
    }

    private fun commit(part: Path, safe: String): String {
        synchronized(commitLock) {
            while (true) {
                val name = SafeName.unique(safe) { Files.exists(downloadsDir.resolve(it)) }
                val target = downloadsDir.resolve(name)
                check(target.normalize().parent == downloadsDir.normalize()) { "Name escapes the downloads folder" }
                try {
                    Files.move(part, target) // no REPLACE_EXISTING: never overwrite a file
                    return name
                } catch (_: FileAlreadyExistsException) {
                    // Someone created the name between the check and the move; pick the next free one.
                }
            }
        }
    }

    // ---- outbox ------------------------------------------------------------------------------------------------

    /** Offers [file] to the phone. Offering the same path again replaces the earlier entry. */
    @Synchronized fun addToOutbox(file: Path): FileInfo {
        val absolute = file.toAbsolutePath().normalize()
        require(Files.isRegularFile(absolute)) { "Not a file: $file" }
        val entry = OutboxEntry(UUID.randomUUID().toString(), absolute.fileName.toString(), absolute.toString())
        outbox = outbox.filter { it.path != entry.path } + entry
        saveOutbox()
        return FileInfo(entry.id, entry.name, Files.size(absolute))
    }

    /** The outbox without entries whose file has disappeared. */
    @Synchronized fun listOutbox(): List<FileInfo> {
        val alive = outbox.filter { Files.isRegularFile(Path.of(it.path)) }
        if (alive.size != outbox.size) {
            outbox = alive
            saveOutbox()
        }
        return alive.map { FileInfo(it.id, it.name, Files.size(Path.of(it.path))) }
    }

    /** The file behind [id]. The id is only compared with stored ids, never used as (part of) a path. */
    @Synchronized fun outboxFile(id: String): Path? =
        outbox.firstOrNull { it.id == id }?.let { Path.of(it.path) }?.takeIf { Files.isRegularFile(it) }

    @Synchronized fun removeFromOutbox(id: String): Boolean {
        if (outbox.none { it.id == id }) return false
        outbox = outbox.filter { it.id != id }
        saveOutbox()
        return true
    }

    private fun saveOutbox() = writeAtomically(outboxFile, BridgeJson.encodeToString(outboxSerializer, outbox).toByteArray(Charsets.UTF_8))
}
