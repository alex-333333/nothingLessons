package io.github.alex333333.bridge.ui

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.alex333333.bridge.data.BridgeSession
import io.github.alex333333.bridge.protocol.BridgeChannel
import io.github.alex333333.bridge.protocol.InputEvent
import io.github.alex333333.bridge.protocol.Key
import io.github.alex333333.bridge.protocol.MediaAction
import io.github.alex333333.bridge.protocol.MouseButton
import io.github.alex333333.bridge.protocol.MouseCoalescer
import io.github.alex333333.bridge.protocol.PcRecord
import io.github.alex333333.bridge.protocol.PowerAction
import io.github.alex333333.bridge.protocol.client.errorText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

/** Mouse, keyboard, media and power buttons for the PC. The connection exists only while this tab is on screen. */
@Composable
fun RemoteTab(session: BridgeSession, pc: PcRecord?, active: Boolean, onGoToPc: () -> Unit) {
    if (pc == null) NeedPairing(onGoToPc) else RemoteContent(session, pc, active)
}

@Composable
private fun RemoteContent(session: BridgeSession, pc: PcRecord, active: Boolean) {
    val palette = LocalPalette.current
    var channel by remember { mutableStateOf<BridgeChannel?>(null) }
    var problem by remember { mutableStateOf<String?>(null) }
    var text by remember { mutableStateOf("") }
    var confirmShutdown by remember { mutableStateOf(false) }

    // Opened when the tab is shown (and the app is on screen), closed when it leaves; reconnects if it drops.
    LaunchedEffect(pc, active) {
        if (!active) return@LaunchedEffect
        while (isActive) {
            var opened: BridgeChannel? = null
            try {
                val fresh = withContext(Dispatchers.IO) { session.client().openChannel() }
                opened = fresh
                channel = fresh
                problem = null
                fresh.events.collect { }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                problem = errorText(e)
            } finally {
                channel = null
                opened?.close()
            }
            delay(2_000)
        }
    }

    fun send(event: InputEvent) {
        channel?.send(event)
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusDot(on = channel != null)
            Text(if (channel != null) "ПОДКЛЮЧЕНО" else "ПОДКЛЮЧЕНИЕ…", style = monoStyle(11.sp))
        }
        problem?.let { Message(it, isError = true) }

        Touchpad(onEvent = { event -> send(event) }, modifier = Modifier.fillMaxWidth().height(240.dp))

        SectionLabel("ТЕКСТ")
        Field(text, { text = it }, "Текст для ввода на ПК")
        ButtonRow {
            PillButton(
                text = "ОТПРАВИТЬ",
                onClick = {
                    if (text.isNotEmpty()) {
                        send(InputEvent.TypeText(text))
                        text = ""
                    }
                },
                modifier = Modifier.weight(1f),
                primary = true,
            )
            PillButton("ENTER", { send(InputEvent.PressKey(Key.Enter)) }, Modifier.weight(1f))
            PillButton("СТЕРЕТЬ", { send(InputEvent.PressKey(Key.Backspace)) }, Modifier.weight(1f))
        }

        SectionLabel("ЗВУК И ПЛЕЕР")
        ButtonRow {
            PillButton("ТИШЕ", { send(InputEvent.Media(MediaAction.VolumeDown)) }, Modifier.weight(1f))
            PillButton("ЗВУК ВЫКЛ", { send(InputEvent.Media(MediaAction.Mute)) }, Modifier.weight(1f))
            PillButton("ГРОМЧЕ", { send(InputEvent.Media(MediaAction.VolumeUp)) }, Modifier.weight(1f))
        }
        ButtonRow {
            PillButton("НАЗАД", { send(InputEvent.Media(MediaAction.Previous)) }, Modifier.weight(1f))
            PillButton("ПАУЗА", { send(InputEvent.Media(MediaAction.PlayPause)) }, Modifier.weight(1f))
            PillButton("ДАЛЬШЕ", { send(InputEvent.Media(MediaAction.Next)) }, Modifier.weight(1f))
        }

        SectionLabel("ПИТАНИЕ")
        ButtonRow {
            PillButton("БЛОКИРОВКА", { send(InputEvent.Power(PowerAction.Lock)) }, Modifier.weight(1f))
            PillButton("ВЫКЛЮЧИТЬ", { confirmShutdown = true }, Modifier.weight(1f))
        }
    }

    if (confirmShutdown) {
        AlertDialog(
            onDismissRequest = { confirmShutdown = false },
            title = { Text("Выключить компьютер?", style = bodyStyle(18.sp, medium = true)) },
            text = { Text("ПК выключится сразу, несохранённая работа пропадёт.", style = bodyStyle(14.sp, color = palette.secondary)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        send(InputEvent.Power(PowerAction.Shutdown, confirmed = true))
                        confirmShutdown = false
                    },
                ) {
                    Text("ВЫКЛЮЧИТЬ", style = monoStyle(12.sp, bold = true, color = palette.accent))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmShutdown = false }) {
                    Text("ОТМЕНА", style = monoStyle(12.sp, bold = true, color = palette.text))
                }
            },
            containerColor = palette.card,
        )
    }
}

@Composable
private fun ButtonRow(content: @Composable RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), content = content)
}

private const val MOUSE_SPEED = 1.6f
private const val TAP_MILLIS = 300L

/**
 * A touchpad. One finger moves the mouse (merged into at most one move per 16 ms), a short tap is a left click, a
 * short tap with two fingers a right click, and two fingers dragging up or down scroll.
 */
@Composable
private fun Touchpad(onEvent: (InputEvent) -> Unit, modifier: Modifier = Modifier) {
    val palette = LocalPalette.current
    val latest = rememberUpdatedState(onEvent)
    val send: (InputEvent) -> Unit = { event -> latest.value.invoke(event) }
    val coalescer = remember { MouseCoalescer(now = { SystemClock.uptimeMillis() }) }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(palette.card)
            .border(1.dp, palette.border, RoundedCornerShape(24.dp))
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    val slop = viewConfiguration.touchSlop
                    val scrollStep = 28.dp.toPx()
                    var pointers = 1
                    var multi = false
                    var travel = 0f
                    var pendingX = 0f
                    var pendingY = 0f
                    var pendingScroll = 0f
                    var lastTime = down.uptimeMillis
                    do {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.filter { it.pressed }
                        if (pressed.size > pointers) pointers = pressed.size
                        if (pressed.size >= 2) multi = true
                        if (pressed.isNotEmpty()) {
                            val dx = pressed.map { it.position.x - it.previousPosition.x }.average().toFloat()
                            val dy = pressed.map { it.position.y - it.previousPosition.y }.average().toFloat()
                            travel += Offset(dx, dy).getDistance()
                            if (multi) {
                                if (pressed.size >= 2) {
                                    // Fingers moving up scroll down, as on a laptop touchpad.
                                    pendingScroll -= dy / scrollStep
                                    val steps = pendingScroll.toInt()
                                    if (steps != 0) {
                                        pendingScroll -= steps
                                        send(InputEvent.Scroll(steps))
                                    }
                                }
                            } else {
                                pendingX += dx * MOUSE_SPEED
                                pendingY += dy * MOUSE_SPEED
                                // Nothing moves until the finger has left the tap area, so a tap does not jiggle the pointer.
                                if (travel > slop) {
                                    val ix = pendingX.toInt()
                                    val iy = pendingY.toInt()
                                    pendingX -= ix
                                    pendingY -= iy
                                    if (ix != 0 || iy != 0) coalescer.add(ix, iy)?.let { send(it) }
                                }
                            }
                        }
                        lastTime = event.changes.maxOf { it.uptimeMillis }
                        event.changes.forEach { it.consume() }
                    } while (event.changes.any { it.pressed })
                    coalescer.flush()?.let { send(it) }
                    if (travel < slop && lastTime - down.uptimeMillis < TAP_MILLIS) {
                        when (pointers) {
                            1 -> send(InputEvent.Click(MouseButton.Left))
                            2 -> send(InputEvent.Click(MouseButton.Right))
                            else -> Unit
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text("ТАЧПАД", style = monoStyle(11.sp, color = palette.muted))
    }
}
