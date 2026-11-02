package io.github.alex333333.bridge.share

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast

/**
 * The target of the system share sheet. It only collects what was shared, hands it to [TransferService] (which does
 * the sending in the background) and closes at once.
 */
class ShareActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handle(intent)
        finish()
    }

    private fun handle(intent: Intent?) {
        if (intent == null) return
        val uris = sharedUris(intent)
        val text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
        when {
            uris.isNotEmpty() -> TransferService.start(this, uris)
            !text.isNullOrBlank() -> TransferService.startText(this, text)
            else -> Toast.makeText(this, "Нечего отправлять", Toast.LENGTH_SHORT).show()
        }
    }

    @Suppress("DEPRECATION")
    private fun sharedUris(intent: Intent): List<Uri> {
        val result = ArrayList<Uri>()
        if (intent.action == Intent.ACTION_SEND_MULTIPLE) {
            intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)?.let { result.addAll(it) }
        } else {
            intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)?.let { result.add(it) }
        }
        if (result.isEmpty()) {
            // Some apps only fill the ClipData.
            val clip = intent.clipData
            if (clip != null) {
                for (i in 0 until clip.itemCount) clip.getItemAt(i).uri?.let { result.add(it) }
            }
        }
        return result
    }
}
