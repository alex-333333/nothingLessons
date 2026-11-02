package io.github.alex333333.bridge.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.alex333333.bridge.data.BridgeSession
import io.github.alex333333.bridge.net.Downloader
import io.github.alex333333.bridge.protocol.FileInfo
import io.github.alex333333.bridge.protocol.PcRecord
import io.github.alex333333.bridge.protocol.client.errorText
import io.github.alex333333.bridge.protocol.formatSize
import io.github.alex333333.bridge.share.TransferService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Files the PC put into its outbox, and sending files from the phone. */
@Composable
fun FilesTab(session: BridgeSession, pc: PcRecord?, active: Boolean, tick: Int, onGoToPc: () -> Unit) {
    if (pc == null) NeedPairing(onGoToPc) else FilesContent(session, pc, active, tick)
}

@Composable
private fun FilesContent(session: BridgeSession, pc: PcRecord, active: Boolean, tick: Int) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var files by remember { mutableStateOf(emptyList<FileInfo>()) }
    var message by remember { mutableStateOf<String?>(null) }
    var failed by remember { mutableStateOf(false) }
    var busyId by remember { mutableStateOf<String?>(null) }

    suspend fun load() {
        try {
            files = session.call { it.outbox() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            message = errorText(e)
            failed = true
        }
    }

    // Reload when the tab opens, when the app comes back to the screen and when the PC reports a change.
    LaunchedEffect(pc, active, tick) {
        if (active) load()
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            TransferService.start(context, uris)
            message = "Отправка началась, ход виден в уведомлении"
            failed = false
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PillButton(
            text = "ОТПРАВИТЬ ФАЙЛ",
            onClick = { picker.launch(arrayOf("*/*")) },
            modifier = Modifier.fillMaxWidth(),
            primary = true,
        )
        message?.let { Message(it, isError = failed) }

        SectionLabel("ФАЙЛЫ С ПК")
        if (files.isEmpty()) Message("На ПК нет файлов для телефона. Положите файл в окне программы «Мост» на ПК.")
        for (file in files) {
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(file.name, style = bodyStyle(16.sp), maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(formatSize(file.size), style = monoStyle(11.sp))
                    }
                    PillButton(
                        text = if (busyId == file.id) "…" else "СКАЧАТЬ",
                        onClick = {
                            scope.launch {
                                busyId = file.id
                                try {
                                    val where = Downloader.save(context, session, file)
                                    session.call { it.deleteOutbox(file.id) }
                                    message = "Сохранено: $where"
                                    failed = false
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    message = errorText(e)
                                    failed = true
                                }
                                busyId = null
                                load()
                            }
                        },
                        modifier = Modifier.width(120.dp),
                        enabled = busyId == null,
                    )
                }
            }
        }
    }
}
