package io.github.alex333333.bridge.share

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.IBinder
import android.provider.OpenableColumns
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import io.github.alex333333.bridge.data.BridgeSession
import io.github.alex333333.bridge.protocol.BridgeApi
import io.github.alex333333.bridge.protocol.NotPairedException
import io.github.alex333333.bridge.protocol.Note
import io.github.alex333333.bridge.protocol.client.errorText
import io.github.alex333333.bridge.protocol.displayName
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Sends shared or picked files (and shared text, as a note) to the PC. It runs as a foreground service of type
 * dataSync with an ongoing progress notification, posts a result notification and stops itself when all is done.
 */
class TransferService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val running = AtomicInteger(0)
    private val resultIds = AtomicInteger(RESULT_ID_BASE)

    @Volatile
    private var lastStartId = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        lastStartId = startId
        createChannel()
        ServiceCompat.startForeground(
            this,
            PROGRESS_ID,
            progressNotification("Отправка на ПК", 0, true),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )
        val uris = mutableListOf<Uri>()
        val clip = intent.clipData
        if (clip != null) {
            for (i in 0 until clip.itemCount) clip.getItemAt(i).uri?.let { uris.add(it) }
        }
        val text = intent.getStringExtra(EXTRA_TEXT)
        running.incrementAndGet()
        scope.launch {
            try {
                val outcome = if (text != null) sendText(text) else sendFiles(uris)
                postResult(outcome)
            } finally {
                if (running.decrementAndGet() == 0) stopSelfResult(lastStartId)
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    // ---- sending -----------------------------------------------------------------------------------------------

    private class Outcome(val title: String, val failed: Boolean)

    private suspend fun sendText(text: String): Outcome = try {
        BridgeSession.get(this).call { it.putNote(Note(UUID.randomUUID().toString(), text, System.currentTimeMillis())) }
        Outcome("Заметка отправлена на ПК", false)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Outcome(errorText(e), true)
    }

    private suspend fun sendFiles(uris: List<Uri>): Outcome {
        val session = BridgeSession.get(this)
        var sent = 0
        var problem: String? = null
        for ((index, uri) in uris.withIndex()) {
            try {
                session.call { api -> sendOne(api, uri, index, uris.size) }
                sent++
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                problem = errorText(e)
                if (e is NotPairedException) break
            }
        }
        val error = problem
        return when {
            error == null -> Outcome("Отправлено на ПК: $sent", false)
            sent == 0 -> Outcome(error, true)
            else -> Outcome("Отправлено на ПК: $sent. Ошибка: $error", true)
        }
    }

    private suspend fun sendOne(api: BridgeApi, uri: Uri, index: Int, total: Int) {
        var name: String? = null
        var size = -1L
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val nameColumn = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeColumn = c.getColumnIndex(OpenableColumns.SIZE)
                if (nameColumn >= 0 && !c.isNull(nameColumn)) name = c.getString(nameColumn)
                if (sizeColumn >= 0 && !c.isNull(sizeColumn)) size = c.getLong(sizeColumn)
            }
        }
        val fileName = displayName(name, uri.lastPathSegment)
        val label = if (total > 1) "$fileName (${index + 1} из $total)" else fileName

        var cache: File? = null
        try {
            var open: () -> InputStream = {
                contentResolver.openInputStream(uri) ?: throw IOException("Не удалось открыть файл")
            }
            if (size < 0) {
                // The size is unknown, but the upload needs it up front: copy to a cache file to learn it.
                val copy = File.createTempFile("upload", ".tmp", cacheDir)
                cache = copy
                open().use { input -> copy.outputStream().use { output -> input.copyTo(output) } }
                size = copy.length()
                open = { copy.inputStream() }
            }
            val bytes = size
            var lastPercent = -1
            api.upload(fileName, bytes, open) { sentBytes ->
                val percent = if (bytes > 0) (sentBytes * 100 / bytes).toInt() else 100
                if (percent != lastPercent) {
                    lastPercent = percent
                    notificationManager().notify(PROGRESS_ID, progressNotification(label, percent, false))
                }
            }
        } finally {
            cache?.delete()
        }
    }

    // ---- notifications -----------------------------------------------------------------------------------------

    private fun notificationManager(): NotificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private fun createChannel() {
        val channel = NotificationChannel(CHANNEL_ID, "Передача файлов", NotificationManager.IMPORTANCE_LOW)
        notificationManager().createNotificationChannel(channel)
    }

    private fun progressNotification(title: String, percent: Int, indeterminate: Boolean): Notification =
        Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle(title)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, percent, indeterminate)
            .build()

    private fun postResult(outcome: Outcome) {
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(if (outcome.failed) android.R.drawable.stat_notify_error else android.R.drawable.stat_sys_upload_done)
            .setContentTitle(outcome.title)
            .setAutoCancel(true)
            .build()
        notificationManager().notify(resultIds.incrementAndGet(), notification)
    }

    companion object {
        private const val CHANNEL_ID = "transfer"
        private const val PROGRESS_ID = 1
        private const val RESULT_ID_BASE = 100
        private const val EXTRA_TEXT = "text"

        /**
         * Sends [uris] to the PC. The URIs travel as ClipData with a read grant, so the service may read what the
         * share sheet or the file picker gave to the caller.
         */
        fun start(context: Context, uris: List<Uri>) {
            if (uris.isEmpty()) return
            val clip = ClipData.newRawUri("files", uris.first())
            for (uri in uris.drop(1)) clip.addItem(ClipData.Item(uri))
            val intent = Intent(context, TransferService::class.java)
            intent.clipData = clip
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            ContextCompat.startForegroundService(context, intent)
        }

        /** Sends [text] to the PC as a new note. */
        fun startText(context: Context, text: String) {
            val intent = Intent(context, TransferService::class.java).putExtra(EXTRA_TEXT, text)
            ContextCompat.startForegroundService(context, intent)
        }
    }
}
