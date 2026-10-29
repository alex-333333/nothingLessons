package io.github.alex333333.lessons.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.alex333333.lessons.core.Bell
import io.github.alex333333.lessons.core.BellError
import io.github.alex333333.lessons.core.Config
import io.github.alex333333.lessons.core.DayOverride
import io.github.alex333333.lessons.core.override
import io.github.alex333333.lessons.core.regularDay
import io.github.alex333333.lessons.core.suggestNextBell
import io.github.alex333333.lessons.core.validateBells
import io.github.alex333333.lessons.core.withOverride
import io.github.alex333333.lessons.core.withoutOverride
import java.time.Duration
import java.time.LocalDate

private data class SlotTime(val index: Int, val start: Boolean)

/**
 * Edits the lessons of one [date] as a draft; nothing is saved until «Готово». Back discards
 * the draft. Replaces the whole settings screen while open.
 */
@Composable
internal fun ChangeDayEditor(config: Config, date: LocalDate, onUpdate: ConfigUpdate, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val regular = remember(config, date) { config.regularDay(date) }
    val initial = remember(date) { (config.override(date)?.takeIf { !it.cancelled } ?: regular).aligned() }
    var draft by remember(date) { mutableStateOf(initial) }
    var editing by remember { mutableStateOf<SlotTime?>(null) }
    val errors = remember(draft.bells) { validateBells(draft.bells) }

    BackHandler(onBack = onClose)

    Column(modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            EditorHeader(date, onClose)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                draft.bells.forEachIndexed { index, bell ->
                    LessonCard(
                        index = index,
                        bell = bell,
                        subject = draft.subjects[index],
                        error = errors[index],
                        was = wasLabel(index, draft.subjects[index], regular),
                        last = index == draft.bells.lastIndex,
                        onEditTime = { start -> editing = SlotTime(index, start) },
                        onSubject = { text -> draft = draft.copy(subjects = draft.subjects.toMutableList().also { it[index] = text }) },
                        onDelete = { draft = draft.withoutSlot(index, bell) },
                    )
                }
            }
            PillButton(
                text = "+ ДОБАВИТЬ УРОК",
                dashed = true,
                onClick = { draft = draft.copy(bells = draft.bells + suggestNextBell(draft.bells), subjects = draft.subjects + "") },
            )
        }
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            RoundButton("Сбросить к обычному", primary = false, onClick = { draft = regular.aligned() }, modifier = Modifier.weight(1.5f))
            RoundButton(
                text = "Готово",
                primary = true,
                modifier = Modifier.weight(1f),
                onClick = {
                    val result = draft
                    // Untouched drafts save nothing, so opening a cancelled day doesn't un-cancel it.
                    if (result != initial) {
                        onUpdate { if (result == it.regularDay(date)) it.withoutOverride(date) else it.withOverride(result) }
                    }
                    onClose()
                },
            )
        }
    }

    val edit = editing
    val bell = edit?.let { draft.bells.getOrNull(it.index) }
    if (edit != null && bell != null) {
        key(edit) {
            TimeDialog(
                title = if (edit.start) "Начало ${edit.index + 1} урока" else "Конец ${edit.index + 1} урока",
                initial = if (edit.start) bell.start else bell.end,
                onDismiss = { editing = null },
                onConfirm = { time ->
                    editing = null
                    draft = draft.copy(bells = draft.bells.withTime(edit.index, edit.start, time))
                },
            )
        }
    }
}

@Composable
private fun EditorHeader(date: LocalDate, onBack: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CircleChevronButton(forward = false, description = "Назад к заменам", onClick = onBack)
            Row(
                modifier = Modifier
                    .padding(start = 4.dp)
                    .semantics(mergeDescendants = true) { heading() },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(Palette.Accent),
                )
                Text("ЗАМЕНА · ${date.shortDate}", style = monoStyle(14.sp, bold = true, color = Palette.Text), maxLines = 1)
            }
        }
        Text(
            text = "Только на этот день. Обычное расписание не изменится.",
            modifier = Modifier.padding(horizontal = 4.dp),
            style = bodyStyle(14.sp, color = Palette.Secondary).copy(lineHeight = 20.sp),
        )
    }
}

@Composable
private fun LessonCard(
    index: Int,
    bell: Bell,
    subject: String,
    error: BellError?,
    was: String?,
    last: Boolean,
    onEditTime: (start: Boolean) -> Unit,
    onSubject: (String) -> Unit,
    onDelete: () -> Unit,
) {
    val number = index + 1
    val timeStyle = bodyStyle(20.sp, medium = true, color = if (error != null) Palette.Error else Palette.Text)
        .copy(fontFeatureSettings = "tnum")
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Palette.Card)
            .padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 8.dp),
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
                TimeButton(bell.start, timeStyle, "Изменить начало $number урока") { onEditTime(true) }
                Text("—", style = timeStyle.copy(color = Palette.Muted))
                TimeButton(bell.end, timeStyle, "Изменить конец $number урока") { onEditTime(false) }
            }
            val minutes = Duration.between(bell.start, bell.end).toMinutes()
            Text(
                text = if (minutes > 0) "$minutes мин" else "—",
                modifier = Modifier.padding(horizontal = 8.dp),
                style = monoStyle(11.sp, spacing = 0f),
                maxLines = 1,
            )
            DeleteButton("Убрать $number урок на этот день", onDelete)
        }
        error?.let {
            Text(
                text = it.message(number),
                modifier = Modifier.padding(start = 34.dp, bottom = 6.dp),
                style = bodyStyle(12.sp, color = Palette.Error),
            )
        }
        Row(
            modifier = Modifier.padding(end = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier
                    .width(22.dp)
                    .heightIn(min = 44.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (was != null) {
                    Box(
                        Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(Palette.Accent),
                    )
                }
            }
            Column(Modifier.weight(1f)) {
                HorizontalDivider(thickness = 1.dp, color = Palette.Divider)
                LessonSubjectField(subject, onSubject, "$number урок, предмет", last)
                was?.let { Text(it, modifier = Modifier.padding(bottom = 6.dp), style = monoStyle(10.sp)) }
            }
        }
    }
}

@Composable
private fun LessonSubjectField(value: String, onValueChange: (String) -> Unit, description: String, last: Boolean) {
    val style = bodyStyle(17.sp)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
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
                if (value.isEmpty()) Text("предмет", style = style.copy(color = Palette.Placeholder))
                inner()
            }
        },
    )
}

/** «БЫЛО · ЛИТЕРАТУРА» when the subject differs from the regular day, «НОВЫЙ УРОК» past its bells. */
private fun wasLabel(index: Int, subject: String, regular: DayOverride): String? {
    if (index >= regular.bells.size) return "НОВЫЙ УРОК"
    val before = regular.subjects.getOrNull(index).orEmpty().trim()
    return when {
        subject.trim() == before -> null
        before.isEmpty() -> "НОВЫЙ УРОК"
        else -> "БЫЛО · ${before.uppercase()}"
    }
}

/** Subjects padded or cut to the bells, so rows can be edited by index. */
private fun DayOverride.aligned(): DayOverride =
    copy(subjects = List(bells.size) { subjects.getOrNull(it).orEmpty() })

/** Removes a lesson's bell and subject; ignores a stale tap after the row has moved. */
private fun DayOverride.withoutSlot(index: Int, bell: Bell): DayOverride {
    if (bells.getOrNull(index) != bell) return this
    return copy(bells = bells.filterIndexed { i, _ -> i != index }, subjects = subjects.filterIndexed { i, _ -> i != index })
}
