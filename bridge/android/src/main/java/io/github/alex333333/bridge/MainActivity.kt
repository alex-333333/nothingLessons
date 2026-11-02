package io.github.alex333333.bridge

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import io.github.alex333333.bridge.data.BridgeSession
import io.github.alex333333.bridge.ui.BridgeApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class MainActivity : ComponentActivity() {
    /** True between onStart and onStop: the connection to the PC is only kept while the app is on screen. */
    private val foreground = MutableStateFlow(false)

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        askForNotificationsOnce()
        val session = BridgeSession.get(this)
        val visible: StateFlow<Boolean> = foreground
        setContent { BridgeApp(session, visible) }
    }

    override fun onStart() {
        super.onStart()
        foreground.value = true
    }

    override fun onStop() {
        foreground.value = false
        super.onStop()
    }

    /** Android 13+ asks for the right to show the progress and result notifications; asked at the first launch only. */
    private fun askForNotificationsOnce() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val prefs = getSharedPreferences("bridge_prefs", MODE_PRIVATE)
        if (prefs.getBoolean(KEY_ASKED, false)) return
        prefs.edit().putBoolean(KEY_ASKED, true).apply()
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private companion object {
        const val KEY_ASKED = "asked_notifications"
    }
}
