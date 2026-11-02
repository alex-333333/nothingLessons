package io.github.alex333333.bridge.net

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import io.github.alex333333.bridge.data.BridgeSession
import io.github.alex333333.bridge.protocol.FileInfo
import io.github.alex333333.bridge.protocol.SafeName
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Saves a file from the PC's outbox on the phone. */
object Downloader {
    private const val FOLDER = "Мост"

    /** Downloads [file] into Downloads/Мост and returns a text that says where it went. */
    suspend fun save(context: Context, session: BridgeSession, file: FileInfo): String = withContext(Dispatchers.IO) {
        val name = SafeName.of(file.name)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveWithMediaStore(context, session, file, name)
        } else {
            saveToAppFolder(context, session, file, name)
        }
    }

    /** Android 10+: MediaStore puts the file into the public Downloads folder without any storage permission. */
    private suspend fun saveWithMediaStore(context: Context, session: BridgeSession, file: FileInfo, name: String): String {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeOf(name))
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/" + FOLDER)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw IOException("Не удалось создать файл в загрузках")
        try {
            val out = resolver.openOutputStream(uri) ?: throw IOException("Не удалось открыть файл для записи")
            out.use { stream -> session.call { api -> api.download(file.id, stream) { } } }
            val done = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
            resolver.update(uri, done, null, null)
        } catch (e: Exception) {
            resolver.delete(uri, null, null) // no half-written file stays in Downloads
            throw e
        }
        return "Загрузки/$FOLDER/$name"
    }

    /**
     * Android 8 to 9: writing to the public Downloads folder needs the storage permission, which the app avoids by
     * writing into its own external files folder (Android/data/<app>/files/Download/Мост).
     */
    private suspend fun saveToAppFolder(context: Context, session: BridgeSession, file: FileInfo, name: String): String {
        val base = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
        val dir = File(base, FOLDER)
        dir.mkdirs()
        val target = File(dir, SafeName.unique(name) { File(dir, it).exists() })
        try {
            FileOutputStream(target).use { stream -> session.call { api -> api.download(file.id, stream) { } } }
        } catch (e: Exception) {
            target.delete()
            throw e
        }
        return target.absolutePath
    }

    private fun mimeOf(name: String): String {
        val extension = name.substringAfterLast('.', "").lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "application/octet-stream"
    }
}
