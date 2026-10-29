package io.github.alex333333.lessons.ui.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.alex333333.lessons.core.Config
import io.github.alex333333.lessons.core.DayOverride
import io.github.alex333333.lessons.core.Holiday
import io.github.alex333333.lessons.core.holidayOn
import io.github.alex333333.lessons.core.override
import io.github.alex333333.lessons.core.regularDay
import io.github.alex333333.lessons.core.withCancelled
import io.github.alex333333.lessons.core.withHoliday
import io.github.alex333333.lessons.core.withoutHoliday
import io.github.alex333333.lessons.core.withoutOverride
import java.time.LocalDate
import java.time.temporal.ChronoUnit

private enum class ChangeDay { Tomorrow, Today, Other }

/** Date hint on an active (white) quick-cancel pill. */
private val ActiveHint = Color(0xFF4A4A4A)

@Composable
internal fun ChangesTab(config: Config, onUpdate: ConfigUpdate, onEditDay: (LocalDate) -> Unit) {
    val today = LocalDate.now()
    val tomorrow = today.plusDays(1)
    var choice by rememberSaveable { mutableStateOf(ChangeDay.Tomorrow) }
    var otherDate by rememberSaveable { mutableStateOf<LocalDate?>(null) }
    var pickingDay by rememberSaveable { mutableStateOf(false) }
    var addingHoliday by rememberSaveable { mutableStateOf(false) }
    // A picked day that has since passed falls back to tomorrow.
    val other = otherDate?.takeIf { !it.isBefore(today) }
    val day = if (choice == ChangeDay.Other && other == null) ChangeDay.Tomorrow else choice

    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CancelPill(config, today, "Сегодня не учимся", "Вернуть уроки на сегодня", onUpdate)
            CancelPill(config, tomorrow, "Завтра не учимся", "Вернуть уроки на завтра", onUpdate)
            Text(
                text = "Виджет сразу покажет следующий учебный день.",
                modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 2.dp),
                style = bodyStyle(13.sp, color = Palette.Secondary).copy(lineHeight = 19.sp),
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionLabel("ЗАМЕНА НА ДЕНЬ")
            DayChoiceChips(day, other) { picked ->
                if (picked == ChangeDay.Other) pickingDay = true else choice = picked
            }
            val date = when (day) {
                ChangeDay.Tomorrow -> tomorrow
                ChangeDay.Today -> today
                ChangeDay.Other -> other ?: tomorrow
            }
            val cancelled = day != ChangeDay.Other && config.override(date)?.cancelled == true
            if (cancelled) {
                val which = if (day == ChangeDay.Today) "Сегодня" else "Завтра"
                BlockedNote("$which уроков нет. Верните их кнопкой выше, чтобы сделать замену.")
            } else {
                val target = when (day) {
                    ChangeDay.Tomorrow -> "завтра"
                    ChangeDay.Today -> "сегодня"
                    ChangeDay.Other -> date.spoken
                }
                ActionCard("Изменить расписание на $target", config.dayMeta(date)) { onEditDay(date) }
            }
        }

        val upcoming = config.overrides.filter { !it.date.isBefore(today) }
        if (upcoming.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionLabel("БЛИЖАЙШИЕ ИЗМЕНЕНИЯ")
                ListCard {
                    upcoming.forEachIndexed { index, change ->
                        ChangeRow(
                            change = change,
                            today = today,
                            onOpen = if (change.cancelled) null else ({ onEditDay(change.date) }),
                            onDelete = { onUpdate { it.withoutOverride(change.date) } },
                        )
                        if (index != upcoming.lastIndex) HorizontalDivider(thickness = 1.dp, color = Palette.Divider)
                    }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionLabel("КАНИКУЛЫ")
            val holidays = config.holidays.filter { !it.last.isBefore(today) }
            if (holidays.isNotEmpty()) {
                ListCard {
                    holidays.forEachIndexed { index, holiday ->
                        HolidayRow(holiday) { onUpdate { it.withoutHoliday(holiday) } }
                        if (index != holidays.lastIndex) HorizontalDivider(thickness = 1.dp, color = Palette.Divider)
                    }
                }
            }
            PillButton(text = "+ ДОБАВИТЬ КАНИКУЛЫ", dashed = true, onClick = { addingHoliday = true })
        }
    }

    if (pickingDay) {
        DayPickerDialog(
            initial = other,
            today = today,
            onDismiss = { pickingDay = false },
            onPick = {
                pickingDay = false
                otherDate = it
                choice = ChangeDay.Other
            },
        )
    }
    if (addingHoliday) {
        HolidayDialog(
            today = today,
            onDismiss = { addingHoliday = false },
            onSave = { holiday ->
                addingHoliday = false
                onUpdate { it.withHoliday(holiday) }
            },
        )
    }
}

/** «Сегодня не учимся» ↔ white «Вернуть уроки на сегодня»: a toggle for one day's cancellation. */
@Composable
private fun CancelPill(config: Config, date: LocalDate, offLabel: String, onLabel: String, onUpdate: ConfigUpdate) {
    val cancelled = config.override(date)?.cancelled == true
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .pill(filled = cancelled)
            .toggleable(value = cancelled, role = Role.Button, onValueChange = { cancel -> onUpdate { it.withCancelled(date, cancel) } })
            .padding(start = 22.dp, end = 20.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (cancelled) UndoIcon(Palette.Background)
            Text(
                text = if (cancelled) onLabel else offLabel,
                style = bodyStyle(17.sp, medium = true, color = if (cancelled) Palette.Background else Palette.Text),
            )
        }
        Text(date.shortDate, style = monoStyle(11.sp, color = if (cancelled) ActiveHint else Palette.Secondary), maxLines = 1)
    }
}

@Composable
private fun DayChoiceChips(selected: ChangeDay, other: LocalDate?, onSelect: (ChangeDay) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        for (day in ChangeDay.entries) {
            val isSelected = day == selected
            val label = when (day) {
                ChangeDay.Tomorrow -> "ЗАВТРА"
                ChangeDay.Today -> "СЕГОДНЯ"
                ChangeDay.Other -> if (isSelected && other != null) other.shortDate else "ДРУГОЙ ДЕНЬ…"
            }
            Box(
                modifier = Modifier
                    .weight(if (day == ChangeDay.Other) 1.3f else 1f)
                    .height(48.dp)
                    .pill(filled = isSelected)
                    .selectable(selected = isSelected, role = Role.RadioButton, onClick = { onSelect(day) }),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = monoStyle(12.sp, bold = true, color = if (isSelected) Palette.Background else Palette.Text, spacing = 0.06f),
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Clip,
                )
            }
        }
    }
}

@Composable
private fun ActionCard(title: String, meta: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 68.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Palette.Card)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(start = 18.dp, end = 14.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = bodyStyle(17.sp, medium = true))
            Text(meta, style = monoStyle(11.sp))
        }
        ChevronIcon(forward = true, color = Palette.Secondary)
    }
}

@Composable
private fun BlockedNote(text: String) {
    Text(
        text = text,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 68.dp)
            .border(1.dp, Palette.Border, RoundedCornerShape(20.dp))
            .padding(horizontal = 18.dp, vertical = 14.dp),
        style = bodyStyle(14.sp, color = Palette.Soft).copy(lineHeight = 20.sp),
    )
}

@Composable
private fun ListCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Palette.Card)
            .padding(vertical = 6.dp),
        content = content,
    )
}

@Composable
private fun ChangeRow(change: DayOverride, today: LocalDate, onOpen: (() -> Unit)?, onDelete: () -> Unit) {
    val slots = change.slots()
    val description = when {
        change.cancelled || slots.isEmpty() -> "Уроков нет"
        else -> "${lessonsLabel(slots.size)}, первый в ${slots.first().start.hm()}"
    }
    val kind = if (change.cancelled) "отмену" else "замену"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .padding(start = 16.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 52.dp)
                .clip(RoundedCornerShape(12.dp))
                .then(
                    if (onOpen != null) {
                        Modifier.clickable(onClickLabel = "Изменить замену", role = Role.Button, onClick = onOpen)
                    } else {
                        Modifier.semantics(mergeDescendants = true) {}
                    },
                ),
            verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(change.date.relativeDate(today), style = monoStyle(12.sp, bold = true, color = Palette.Text, spacing = 0.06f))
                Tag(if (change.cancelled) "ОТМЕНА" else "ЗАМЕНА", if (change.cancelled) Palette.Muted else Palette.Accent)
            }
            Text(description, style = bodyStyle(15.sp, color = Palette.Soft))
        }
        DeleteButton("Удалить $kind на ${change.date.spoken}", onDelete)
    }
}

@Composable
private fun Tag(text: String, border: Color) {
    Text(
        text = text,
        modifier = Modifier
            .border(1.dp, border, RoundedCornerShape(8.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
        style = monoStyle(9.sp, bold = true, color = Palette.Text),
    )
}

@Composable
private fun HolidayRow(holiday: Holiday, onDelete: () -> Unit) {
    val days = ChronoUnit.DAYS.between(holiday.first, holiday.last).toInt() + 1
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .padding(start = 16.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .semantics(mergeDescendants = true) {},
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = rangeLabel(holiday.first, holiday.last),
                style = bodyStyle(20.sp, medium = true).copy(fontFeatureSettings = "tnum"),
            )
            Text(daysLabel(days), style = monoStyle(11.sp))
        }
        DeleteButton("Удалить каникулы ${spokenRange(holiday.first, holiday.last)}", onDelete)
    }
}

/** «ПТ, 30 ОКТ · ЗАМЕНА · 5 УРОКОВ» for the action card. */
private fun Config.dayMeta(date: LocalDate): String {
    val change = override(date)
    val kind = when {
        holidayOn(date) != null -> return "${date.shortDate} · КАНИКУЛЫ"
        change?.cancelled == true -> return "${date.shortDate} · ОТМЕНА"
        change != null -> "ЗАМЕНА"
        else -> "КАК ОБЫЧНО"
    }
    val lessons = (change ?: regularDay(date)).slots().size
    val count = if (lessons == 0) "НЕТ УРОКОВ" else lessonsLabel(lessons).uppercase()
    return "${date.shortDate} · $kind · $count"
}

/** A curved "undo" arrow. */
@Composable
private fun UndoIcon(color: Color) {
    Canvas(Modifier.size(16.dp)) {
        val unit = size.width / 16
        val path = Path().apply {
            moveTo(4 * unit, 6 * unit)
            lineTo(11 * unit, 6 * unit)
            arcTo(Rect(7.5f * unit, 6 * unit, 14.5f * unit, 13 * unit), -90f, 180f, false)
            lineTo(7 * unit, 13 * unit)
            moveTo(6.5f * unit, 3.5f * unit)
            lineTo(4 * unit, 6 * unit)
            lineTo(6.5f * unit, 8.5f * unit)
        }
        drawPath(path, color, style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}
