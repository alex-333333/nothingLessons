package io.github.alex333333.lessons.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.alex333333.lessons.core.Bell
import io.github.alex333333.lessons.core.BellError
import io.github.alex333333.lessons.core.Config
import io.github.alex333333.lessons.core.suggestNextBell
import io.github.alex333333.lessons.core.validateBells
import java.time.Duration
import java.time.LocalTime

private data class TimeEdit(val index: Int, val start: Boolean)

@Composable
internal fun BellsTab(config: Config, onUpdate: ConfigUpdate) {
    val errors = remember(config.bells) { validateBells(config.bells) }
    var editing by remember { mutableStateOf<TimeEdit?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        if (config.bells.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                config.bells.forEachIndexed { index, bell ->
                    BellCard(
                        index = index,
                        bell = bell,
                        error = errors[index],
                        onEdit = { start -> editing = TimeEdit(index, start) },
                        onDelete = { onUpdate { it.withoutBell(index, bell) } },
                    )
                }
            }
        }
        PillButton(
            text = "+ ДОБАВИТЬ УРОК",
            dashed = true,
            onClick = { onUpdate { it.copy(bells = it.bells + suggestNextBell(it.bells)) } },
        )
    }

    val edit = editing
    val bell = edit?.let { config.bells.getOrNull(it.index) }
    if (edit != null && bell != null) {
        key(edit) {
            TimeDialog(
                title = if (edit.start) "Начало ${edit.index + 1} урока" else "Конец ${edit.index + 1} урока",
                initial = if (edit.start) bell.start else bell.end,
                onDismiss = { editing = null },
                onConfirm = { time ->
                    editing = null
                    onUpdate { it.withBellTime(edit.index, edit.start, time) }
                },
            )
        }
    }
}

@Composable
private fun BellCard(index: Int, bell: Bell, error: BellError?, onEdit: (start: Boolean) -> Unit, onDelete: () -> Unit) {
    val number = index + 1
    val timeStyle = bodyStyle(22.sp, medium = true, color = if (error != null) Palette.Error else Palette.Text)
        .copy(fontFeatureSettings = "tnum")
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Palette.Card)
            .padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = number.toString().padStart(2, '0'),
                modifier = Modifier.width(22.dp),
                style = monoStyle(12.sp, spacing = 0f),
            )
            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TimeButton(bell.start, timeStyle, "Изменить начало $number урока") { onEdit(true) }
                Text("—", style = timeStyle.copy(color = Palette.Muted))
                TimeButton(bell.end, timeStyle, "Изменить конец $number урока") { onEdit(false) }
            }
            val minutes = Duration.between(bell.start, bell.end).toMinutes()
            Text(
                text = if (minutes > 0) "$minutes мин" else "—",
                modifier = Modifier.padding(horizontal = 8.dp),
                style = monoStyle(11.sp, spacing = 0f),
                maxLines = 1,
            )
            DeleteButton("Удалить урок", onDelete)
        }
        error?.let {
            Text(
                text = it.message(number),
                modifier = Modifier.padding(start = 34.dp, bottom = 6.dp),
                style = bodyStyle(12.sp, color = Palette.Error),
            )
        }
    }
}

internal fun BellError.message(number: Int): String = when (this) {
    BellError.EndBeforeStart -> "Конец урока раньше начала"
    BellError.OverlapsPrevious -> "Начинается раньше, чем кончится ${number - 1} урок"
}

@Composable
internal fun TimeButton(time: LocalTime, style: TextStyle, label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClickLabel = label, role = Role.Button, onClick = onClick)
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(time.hhmm(), style = style, maxLines = 1)
    }
}

/** Material time picker in a dark dialog (material3 1.3 has no TimePickerDialog). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TimeDialog(title: String, initial: LocalTime, onDismiss: () -> Unit, onConfirm: (LocalTime) -> Unit) {
    val state = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onConfirm(LocalTime.of(state.hour, state.minute)) }) {
                Text("Готово", style = bodyStyle(15.sp, medium = true))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена", style = bodyStyle(15.sp, medium = true, color = Palette.Secondary))
            }
        },
        title = { Text(title, style = bodyStyle(20.sp, medium = true)) },
        text = { TimePicker(state = state) },
        shape = RoundedCornerShape(28.dp),
        containerColor = Palette.Card,
        titleContentColor = Palette.Text,
        textContentColor = Palette.Text,
        tonalElevation = 0.dp,
    )
}

/**
 * Removes bell [index] and the subjects in that slot, so subjects stay aligned with bells.
 * Does nothing unless the slot still holds [bell], so a double tap deletes only once.
 */
private fun Config.withoutBell(index: Int, bell: Bell): Config {
    if (bells.getOrNull(index) != bell) return this
    return copy(
        bells = bells.filterIndexed { i, _ -> i != index },
        week = week.map { day -> day.filterIndexed { i, _ -> i != index } },
    )
}

private fun Config.withBellTime(index: Int, start: Boolean, time: LocalTime): Config =
    copy(bells = bells.withTime(index, start, time))

/** Moving the start keeps the lesson's length; a lesson cannot run past midnight. */
internal fun List<Bell>.withTime(index: Int, start: Boolean, time: LocalTime): List<Bell> {
    val bell = getOrNull(index) ?: return this
    val updated = if (start) {
        val length = Duration.between(bell.start, bell.end)
        val end = when {
            length.isNegative || length.isZero -> bell.end
            else -> time.plus(length).takeIf { it.isAfter(time) } ?: LocalTime.of(23, 59)
        }
        Bell(time, end)
    } else {
        bell.copy(end = time)
    }
    return toMutableList().also { it[index] = updated }
}

@Preview
@Composable
private fun BellsTabPreview() {
    val config = Config(
        bells = listOf(
            Bell(LocalTime.of(8, 30), LocalTime.of(9, 15)),
            Bell(LocalTime.of(9, 25), LocalTime.of(10, 10)),
            Bell(LocalTime.of(10, 5), LocalTime.of(11, 5)),
        ),
    )
    SettingsTheme {
        Box(Modifier.background(Color.Black).padding(16.dp)) {
            BellsTab(config, onUpdate = {})
        }
    }
}
