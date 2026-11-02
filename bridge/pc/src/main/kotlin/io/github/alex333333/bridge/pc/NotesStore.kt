package io.github.alex333333.bridge.pc

import io.github.alex333333.bridge.protocol.BridgeJson
import io.github.alex333333.bridge.protocol.Note
import kotlinx.serialization.builtins.ListSerializer
import java.nio.file.Files
import java.nio.file.Path

/** The shared notes, kept in `notes.json`. */
internal class NotesStore(dataDir: Path) {
    private val file = dataDir.resolve("notes.json")
    private val serializer = ListSerializer(Note.serializer())
    private var notes: List<Note> = try {
        if (Files.exists(file)) BridgeJson.decodeFromString(serializer, Files.readString(file)) else emptyList()
    } catch (_: Exception) {
        emptyList()
    }

    @Synchronized fun list(): List<Note> = notes

    @Synchronized fun put(note: Note) {
        val index = notes.indexOfFirst { it.id == note.id }
        notes = if (index < 0) notes + note else notes.toMutableList().also { it[index] = note }
        save()
    }

    /** True if a note was removed. */
    @Synchronized fun delete(id: String): Boolean {
        if (notes.none { it.id == id }) return false
        notes = notes.filter { it.id != id }
        save()
        return true
    }

    private fun save() = writeAtomically(file, BridgeJson.encodeToString(serializer, notes).toByteArray(Charsets.UTF_8))
}
