package io.github.alex333333.bridge.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.alex333333.bridge.data.BridgeSession
import io.github.alex333333.bridge.protocol.ServerEvent
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

private val TAB_LABELS = listOf("ПК", "ФАЙЛЫ", "БУФЕР", "ПУЛЬТ")

/**
 * The whole app: title, tabs and the selected tab. [foreground] is true while the activity is on screen; events from
 * the PC are only listened to then.
 */
@Composable
fun BridgeApp(session: BridgeSession, foreground: StateFlow<Boolean>) {
    BridgeTheme {
        val palette = LocalPalette.current
        val pc by session.pc.collectAsState()
        val active by foreground.collectAsState()
        val scope = rememberCoroutineScope()
        var tab by rememberSaveable { mutableStateOf(0) }
        // Bumped when the PC says its outbox or notes changed; the tabs reload on it.
        var outboxTick by remember { mutableIntStateOf(0) }
        var notesTick by remember { mutableIntStateOf(0) }

        LaunchedEffect(pc, active) {
            if (pc != null && active) {
                session.events().collect { event ->
                    when (event) {
                        ServerEvent.OutboxChanged -> outboxTick++
                        ServerEvent.NotesChanged -> notesTick++
                        ServerEvent.Unpaired -> scope.launch { session.unpair() }
                    }
                }
            }
        }

        Column(
            Modifier
                .fillMaxSize()
                .background(palette.background)
                .systemBarsPadding()
                .imePadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Header()
            PillTabs(TAB_LABELS, tab) { tab = it }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (tab) {
                    0 -> PcTab(session, pc, active)
                    1 -> FilesTab(session, pc, active, outboxTick) { tab = 0 }
                    2 -> ClipTab(session, pc, active, notesTick) { tab = 0 }
                    else -> RemoteTab(session, pc, active) { tab = 0 }
                }
            }
        }
    }
}
