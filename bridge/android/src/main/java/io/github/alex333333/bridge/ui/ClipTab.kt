package io.github.alex333333.bridge.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.alex333333.bridge.data.BridgeSession
import io.github.alex333333.bridge.protocol.Note
import io.github.alex333333.bridge.protocol.PcRecord
import io.github.alex333333.bridge.protocol.client.errorText
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** The clipboards of the phone and the PC, and the shared notes. */
@Composable
fun ClipTab(session: BridgeSession, pc: PcRecord?, active: Boolean, tick: Int, onGoToPc: () -> Unit) {
    if (pc == null) NeedPairing(onGoToPc) else ClipContent(session, pc, active, tick)
}

@Composable
private fun ClipContent(session: BridgeSession, pc: PcRecord, active: Boolean, tick: Int) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var notes by remember { mutableStateOf(emptyList<Note>()) }
    var draft by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<Note?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var failed by remember { mutableStateOf(false) }

    suspend fun load() {
        try {
            notes = session.call { it.notes() }.sortedByDescending { it.updatedAt }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            message = errorText(e)
            failed = true
        }
    }

    LaunchedEffect(pc, active, tick) {
        if (active) load()
    }

    /** Runs [block] in the background; shows [done] when it works and the error text when it does not. */
    fun perform(done: String, block: suspend () -> Unit) {
        scope.launch {
            try {
                block()
                message = done
                failed = false
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                message = errorText(e)
                failed = true
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionLabel("БУФЕР ОБМЕНА")
        PillButton(
            text = "ОТПРАВИТЬ БУФЕР НА ПК",
            onClick = {
                // The phone's clipboard is only read here, in the foreground, as a reaction to a tap.
                val text = phoneClipboardText(context)
                if (text.isNullOrEmpty()) {
                    message = "Буфер телефона пуст"
                    failed = true
                } else {
                    perform("Буфер отправлен на ПК") { session.call { it.setClipboard(text) } }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            primary = true,
        )
        PillButton(
            text = "ВЗЯТЬ БУФЕР С ПК",
            onClick = {
                perform("Буфер ПК скопирован в телефон") {
                    val text = session.call { it.clipboard() }
                    val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    manager.setPrimaryClip(ClipData.newPlainText("Мост", text))
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        message?.let { Message(it, isError = failed) }

        SectionLabel("ЗАМЕТКИ")
        Field(
            value = draft,
            onValueChange = { draft = it },
            placeholder = if (editing == null) "Новая заметка" else "Правка заметки",
            singleLine = false,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PillButton(
                text = if (editing == null) "ДОБАВИТЬ" else "СОХРАНИТЬ",
                onClick = {
                    val text = draft.trim()
                    if (text.isNotEmpty()) {
                        val note = Note(editing?.id ?: UUID.randomUUID().toString(), text, System.currentTimeMillis())
                        perform("Заметка сохранена") {
                            session.call { it.putNote(note) }
                            draft = ""
                            editing = null
                            load()
                        }
                    }
                },
                modifier = Modifier.weight(1f),
                enabled = draft.isNotBlank(),
            )
            if (editing != null) {
                PillButton(
                    text = "ОТМЕНА",
                    onClick = {
                        draft = ""
                        editing = null
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        if (notes.isEmpty()) Message("Заметок пока нет.")
        for (note in notes) {
            Panel {
                Text(note.text, style = bodyStyle(16.sp), maxLines = 6, overflow = TextOverflow.Ellipsis)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PillButton(
                        text = "ПРАВИТЬ",
                        onClick = {
                            editing = note
                            draft = note.text
                        },
                        modifier = Modifier.weight(1f),
                    )
                    PillButton(
                        text = "УДАЛИТЬ",
                        onClick = {
                            perform("Заметка удалена") {
                                session.call { it.deleteNote(note.id) }
                                if (editing?.id == note.id) {
                                    editing = null
                                    draft = ""
                                }
                                load()
                            }
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/** The text on the phone's clipboard, or null if it is empty. */
private fun phoneClipboardText(context: Context): String? {
    val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = manager.primaryClip ?: return null
    if (clip.itemCount == 0) return null
    return clip.getItemAt(0).coerceToText(context)?.toString()
}
