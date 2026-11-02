package io.github.alex333333.bridge.pc

import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/** Writes [bytes] to a temporary file next to [target] and renames it over [target], so a crash never leaves half a file. */
internal fun writeAtomically(target: Path, bytes: ByteArray) {
    val dir = target.toAbsolutePath().parent
    Files.createDirectories(dir)
    val tmp = Files.createTempFile(dir, target.fileName.toString(), ".tmp")
    try {
        Files.write(tmp, bytes)
        try {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING)
        }
    } finally {
        Files.deleteIfExists(tmp)
    }
}
