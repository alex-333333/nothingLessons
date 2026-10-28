package io.github.alex333333.lessons.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.alex333333.lessons.core.Config
import java.time.DayOfWeek
import java.time.LocalDate
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop

private const val SAVE_DELAY_MS = 500L

@Composable
internal fun ScheduleTab(config: Config, onUpdate: ConfigUpdate, onAddBells: () -> Unit) {
    val days = DayOfWeek.entries.filter(config::isSchoolDay)
    var selected by rememberSaveable {
        val today = LocalDate.now().dayOfWeek
        mutableIntStateOf(if (config.isSchoolDay(today)) today.value else DayOfWeek.MONDAY.value)
    }
    // Sunday may have been switched off while selected.
    val day = DayOfWeek.of(selected).takeIf { it in days } ?: DayOfWeek.MONDAY

    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        if (config.bells.isEmpty()) {
            NoBellsHint(onAddBells)
        } else {
            DayChips(days, day) { selected = it.value }
            key(day, config.bells.size) {
                SubjectsCard(config, day, onUpdate)
            }
        }
        SundayRow(config.sundayEnabled) { enabled -> onUpdate { it.copy(sundayEnabled = enabled) } }
    }
}

@Composable
private fun DayChips(days: List<DayOfWeek>, selected: DayOfWeek, onSelect: (DayOfWeek) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        for (day in days) {
            val isSelected = day == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .pill(filled = isSelected)
                    .selectable(selected = isSelected, role = Role.Tab, onClick = { onSelect(day) }),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = day.shortLabel,
                    style = monoStyle(12.sp, bold = true, color = if (isSelected) Palette.Background else Palette.Text, spacing = 0f),
                )
            }
        }
    }
}

/** Typed subjects, saved a moment after the last keystroke rather than on every one. */
private class SubjectDrafts(private val day: DayOfWeek, initial: List<String>) {
    val values = initial.toMutableStateList()
    private var dirty = false

    fun edit(index: Int, text: String) {
        values[index] = text
        dirty = true
    }

    fun flush(onUpdate: ConfigUpdate) {
        if (!dirty) return
        dirty = false
        val subjects = values.toList()
        onUpdate { config ->
            val padding = List((config.bells.size - subjects.size).coerceAtLeast(0)) { "" }
            config.withSubjects(day, subjects + padding)
        }
    }
}

@Composable
private fun SubjectsCard(config: Config, day: DayOfWeek, onUpdate: ConfigUpdate) {
    val bells = config.bells
    val drafts = remember {
        val saved = config.subjects(day)
        SubjectDrafts(day, List(bells.size) { saved.getOrNull(it).orEmpty() })
    }
    val latestUpdate by rememberUpdatedState(onUpdate)
    LaunchedEffect(drafts) {
        snapshotFlow { drafts.values.toList() }
            .drop(1)
            .collectLatest {
                delay(SAVE_DELAY_MS)
                drafts.flush(latestUpdate)
            }
    }
    // Switching day or tab saves right away.
    DisposableEffect(drafts) {
        onDispose { drafts.flush(latestUpdate) }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Palette.Card)
            .padding(vertical = 6.dp),
    ) {
        bells.forEachIndexed { index, bell ->
            val number = index + 1
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(Modifier.width(56.dp)) {
                    Text("$number УРОК", style = monoStyle(12.sp, bold = true, color = Palette.Text, spacing = 0f), maxLines = 1)
                    Text(bell.start.hhmm(), style = monoStyle(11.sp, spacing = 0f), maxLines = 1)
                }
                SubjectField(
                    value = drafts.values[index],
                    onValueChange = { drafts.edit(index, it) },
                    description = "$number урок, предмет",
                    last = index == bells.lastIndex,
                    onFocusLost = { drafts.flush(latestUpdate) },
                    modifier = Modifier.weight(1f),
                )
            }
            if (index != bells.lastIndex) HorizontalDivider(thickness = 1.dp, color = Palette.Divider)
        }
    }
}

@Composable
private fun SubjectField(
    value: String,
    onValueChange: (String) -> Unit,
    description: String,
    last: Boolean,
    onFocusLost: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val style = bodyStyle(17.sp, medium = true)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .heightIn(min = 44.dp)
            .onFocusChanged { if (!it.isFocused) onFocusLost() }
            .semantics { contentDescription = description },
        textStyle = style,
        singleLine = true,
        cursorBrush = SolidColor(Palette.Accent),
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Sentences,
            imeAction = if (last) ImeAction.Done else ImeAction.Next,
        ),
        decorationBox = { inner ->
            Box(contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) Text("нет урока", style = style.copy(color = Palette.Placeholder))
                inner()
            }
        },
    )
}

@Composable
private fun NoBellsHint(onAddBells: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Palette.Card)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Сначала добавьте звонки", style = bodyStyle(17.sp, medium = true))
        PillButton(text = "К ЗВОНКАМ", onClick = onAddBells)
    }
}

@Composable
private fun SundayRow(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(12.dp))
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Учусь по воскресеньям", style = bodyStyle(15.sp, medium = true))
            Text("Добавит вкладку ВС", style = bodyStyle(12.sp, color = Palette.Secondary))
        }
        Switch(
            checked = checked,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Palette.Text,
                checkedTrackColor = Palette.Accent,
                checkedBorderColor = Palette.Accent,
                uncheckedThumbColor = Palette.Secondary,
                uncheckedTrackColor = Palette.Background,
                uncheckedBorderColor = Palette.Border,
            ),
        )
    }
}
