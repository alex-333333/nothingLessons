package io.github.alex333333.bridge.ui

import android.widget.Toast
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.alex333333.bridge.data.BridgeSession
import io.github.alex333333.bridge.net.DiscoveredPc
import io.github.alex333333.bridge.net.Discovery
import io.github.alex333333.bridge.net.WakeOnLanSender
import io.github.alex333333.bridge.protocol.Fingerprint
import io.github.alex333333.bridge.protocol.PcRecord
import io.github.alex333333.bridge.protocol.client.errorText
import io.github.alex333333.bridge.protocol.parseHostPort
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Pairing with a PC, and once paired: its status, the pairing check code, switching it on and unpairing. */
@Composable
fun PcTab(session: BridgeSession, pc: PcRecord?, active: Boolean) {
    if (pc == null) PairForm(session, active) else PairedCard(session, pc, active)
}

@Composable
private fun PairForm(session: BridgeSession, active: Boolean) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var found by remember { mutableStateOf(emptyList<DiscoveredPc>()) }
    var chosen by remember { mutableStateOf<DiscoveredPc?>(null) }
    var manual by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(active) {
        if (active) {
            try {
                Discovery(context).discover().collect { found = it }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // No discovery (for example no Wi-Fi): the manual field still works.
            }
        }
    }

    val target: Pair<String, Int>? = if (manual.isNotBlank()) {
        parseHostPort(manual)
    } else {
        chosen?.let { it.host to it.port }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionLabel("НАЙДЕНО В СЕТИ")
        if (found.isEmpty()) {
            Message("Ищем компьютеры с программой «Мост». Если ничего не нашлось, введите IP ниже.")
        }
        for (pcFound in found) {
            PillButton(
                text = "${pcFound.name} · ${pcFound.host}",
                onClick = {
                    chosen = pcFound
                    manual = ""
                },
                modifier = Modifier.fillMaxWidth(),
                selected = chosen == pcFound && manual.isBlank(),
            )
        }

        SectionLabel("ИЛИ ВВЕДИТЕ IP")
        Field(manual, { manual = it }, "192.168.1.5", keyboardType = KeyboardType.Uri)

        SectionLabel("КОД С ЭКРАНА ПК")
        Field(code, { text -> code = text.filter { it.isDigit() }.take(6) }, "000000", keyboardType = KeyboardType.Number)

        PillButton(
            text = if (busy) "ПРИВЯЗКА…" else "ПРИВЯЗАТЬ",
            onClick = {
                val (host, port) = target ?: return@PillButton
                scope.launch {
                    busy = true
                    error = null
                    try {
                        session.pair(host, port, code)
                        code = ""
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        error = errorText(e)
                    }
                    busy = false
                }
            },
            modifier = Modifier.fillMaxWidth(),
            primary = true,
            enabled = !busy && code.length == 6 && target != null,
        )
        if (manual.isNotBlank() && target == null) Message("Введите IP как 192.168.1.5 или 192.168.1.5:48750", isError = true)
        error?.let { Message(it, isError = true) }
    }
}

@Composable
private fun PairedCard(session: BridgeSession, pc: PcRecord, active: Boolean) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var online by remember { mutableStateOf<Boolean?>(null) }

    LaunchedEffect(pc, active) {
        if (active) {
            while (true) {
                online = session.isOnline()
                delay(5_000)
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Panel {
            Text(pc.name, style = bodyStyle(22.sp, medium = true))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusDot(on = online == true)
                Text(
                    text = when (online) {
                        true -> "В СЕТИ"
                        false -> "НЕ В СЕТИ"
                        null -> "ПРОВЕРКА…"
                    },
                    style = monoStyle(11.sp),
                )
            }
            Text("${pc.host}:${pc.port}", style = monoStyle(11.sp))
        }
        Panel {
            Text("КОД СЕРТИФИКАТА", style = monoStyle(11.sp))
            Text(Fingerprint.short(pc.fingerprintHex), style = monoStyle(28.sp, bold = true, color = LocalPalette.current.text))
            Message("Сверьте с кодом на экране ПК")
        }
        PillButton(
            text = "ВКЛЮЧИТЬ",
            onClick = {
                scope.launch {
                    val sent = WakeOnLanSender.send(context, pc.macs)
                    val message = when {
                        pc.macs.isEmpty() -> "ПК не сообщил свой MAC-адрес"
                        sent > 0 -> "Сигнал включения отправлен"
                        else -> "Не удалось отправить сигнал"
                    }
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier.fillMaxWidth(),
            primary = true,
        )
        PillButton(
            text = "ОТВЯЗАТЬ",
            onClick = { scope.launch { session.unpair() } },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
