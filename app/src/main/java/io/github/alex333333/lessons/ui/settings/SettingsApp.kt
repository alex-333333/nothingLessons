package io.github.alex333333.lessons.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import io.github.alex333333.lessons.core.Config
import io.github.alex333333.lessons.data.ConfigRepository

/** Applies a change to the stored config (and redraws the widget). */
internal typealias ConfigUpdate = (transform: (Config) -> Config) -> Unit

internal enum class SettingsTab(val label: String, val subtitle: String, val width: Float) {
    Bells("ЗВОНКИ", "ТАК ВИДЖЕТ ВЫГЛЯДИТ СЕЙЧАС", 1f),
    Schedule("РАСПИСАНИЕ", "ПРЕДМЕТЫ ПО ДНЯМ НЕДЕЛИ", 1.5f),
    Look("ВИД", "ШРИФТ НАЗВАНИЙ УРОКОВ", 1f),
}

@Composable
fun SettingsApp() {
    SettingsTheme {
        val context = LocalContext.current
        val repository = remember(context) { ConfigRepository.get(context) }
        val config by repository.config.collectAsState(initial = null)
        val update: ConfigUpdate = repository::launchUpdate
        var tab by rememberSaveable { mutableStateOf(SettingsTab.Bells) }
        val focusManager = LocalFocusManager.current

        Box(
            Modifier
                .fillMaxSize()
                .background(Palette.Background),
        ) {
            // Blank until the stored config arrives, so defaults never flash.
            val current = config
            if (current != null) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .safeDrawingPadding()
                        .imePadding()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    Header(tab.subtitle)
                    PreviewCard(current)
                    PillTabs(tab) {
                        focusManager.clearFocus()
                        tab = it
                    }
                    when (tab) {
                        SettingsTab.Bells -> BellsTab(current, update)
                        SettingsTab.Schedule -> ScheduleTab(current, update, onAddBells = { tab = SettingsTab.Bells })
                        SettingsTab.Look -> LookTab(current, update)
                    }
                }
            }
        }
    }
}
