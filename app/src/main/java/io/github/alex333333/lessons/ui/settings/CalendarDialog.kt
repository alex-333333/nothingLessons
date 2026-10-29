package io.github.alex333333.lessons.ui.settings

import android.view.Gravity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import io.github.alex333333.lessons.core.Holiday
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/** Picks the first and last day of a holiday: first tap starts a range, second ends it. */
@Composable
internal fun HolidayDialog(today: LocalDate, onDismiss: () -> Unit, onSave: (Holiday) -> Unit) {
    var start by rememberSaveable { mutableStateOf<LocalDate?>(null) }
    var end by rememberSaveable { mutableStateOf<LocalDate?>(null) }
    val first = start
    val last = end
    CalendarDialog(
        title = "Каникулы",
        summary = when {
            first == null -> "ВЫБЕРИТЕ ПЕРВЫЙ ДЕНЬ"
            last == null -> "${first.dayMonth} · ВЫБЕРИТЕ ПОСЛЕДНИЙ ДЕНЬ"
            else -> "${rangeLabel(first, last)} · ${daysLabel(ChronoUnit.DAYS.between(first, last).toInt() + 1)}"
        },
        hint = "Нажмите первый и последний день. В эти дни виджет покажет, сколько осталось до учёбы.",
        confirmLabel = "Сохранить",
        canConfirm = first != null,
        initialMonth = YearMonth.from(today),
        first = first,
        last = last,
        minDate = null,
        onPick = { day ->
            when {
                first == null || last != null -> {
                    start = day
                    end = null
                }
                day.isBefore(first) -> start = day
                else -> end = day
            }
        },
        onDismiss = onDismiss,
        onConfirm = { if (first != null) onSave(Holiday(first, last ?: first)) },
    )
}

/** Picks one day from [today] on. */
@Composable
internal fun DayPickerDialog(initial: LocalDate?, today: LocalDate, onDismiss: () -> Unit, onPick: (LocalDate) -> Unit) {
    var selected by rememberSaveable { mutableStateOf(initial?.takeIf { !it.isBefore(today) }) }
    val day = selected
    CalendarDialog(
        title = "Другой день",
        summary = day?.shortDate ?: "ВЫБЕРИТЕ ДЕНЬ",
        hint = null,
        confirmLabel = "Выбрать",
        canConfirm = day != null,
        initialMonth = YearMonth.from(day ?: today),
        first = day,
        last = day,
        minDate = today,
        onPick = { selected = it },
        onDismiss = onDismiss,
        onConfirm = { if (day != null) onPick(day) },
    )
}

/** A dark sheet at the bottom of the screen with a month calendar, as in the mockup. */
@Composable
private fun CalendarDialog(
    title: String,
    summary: String,
    hint: String?,
    confirmLabel: String,
    canConfirm: Boolean,
    initialMonth: YearMonth,
    first: LocalDate?,
    last: LocalDate?,
    minDate: LocalDate?,
    onPick: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    var month by rememberSaveable { mutableStateOf(initialMonth) }
    val minMonth = minDate?.let { YearMonth.from(it) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect { window?.setGravity(Gravity.BOTTOM) }
        val shape = RoundedCornerShape(28.dp)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
                .clip(shape)
                .background(Palette.Card)
                .border(1.dp, Palette.SheetBorder, shape)
                .verticalScroll(rememberScrollState())
                .padding(start = 18.dp, end = 18.dp, top = 22.dp, bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(Modifier.padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = title,
                    modifier = Modifier.semantics { heading() },
                    style = bodyStyle(26.sp, medium = true).copy(lineHeight = 29.sp),
                )
                Text(summary, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }, style = monoStyle(11.sp))
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircleChevronButton(
                        forward = false,
                        description = "Предыдущий месяц",
                        enabled = minMonth == null || month > minMonth,
                        onClick = { month = month.minusMonths(1) },
                    )
                    Text(
                        text = month.title.uppercase(),
                        modifier = Modifier.weight(1f),
                        style = monoStyle(13.sp, bold = true, color = Palette.Text, spacing = 0.1f),
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                    )
                    CircleChevronButton(forward = true, description = "Следующий месяц", onClick = { month = month.plusMonths(1) })
                }
                MonthGrid(month, first, last, minDate, onPick)
            }
            hint?.let {
                Text(
                    text = it,
                    modifier = Modifier.padding(horizontal = 4.dp),
                    style = bodyStyle(13.sp, color = Palette.Secondary).copy(lineHeight = 19.sp),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RoundButton("Отмена", primary = false, onClick = onDismiss, modifier = Modifier.weight(1f))
                RoundButton(confirmLabel, primary = true, enabled = canConfirm, onClick = onConfirm, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun MonthGrid(month: YearMonth, first: LocalDate?, last: LocalDate?, minDate: LocalDate?, onPick: (LocalDate) -> Unit) {
    val offset = month.atDay(1).dayOfWeek.value - 1
    val length = month.lengthOfMonth()
    Column {
        Row(
            Modifier
                .padding(top = 6.dp)
                .clearAndSetSemantics {},
        ) {
            for (day in DayOfWeek.entries) {
                Box(
                    Modifier
                        .weight(1f)
                        .height(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(day.shortLabel, style = monoStyle(10.sp, color = if (day.isWeekend) Palette.Soft else Palette.Secondary, spacing = 0.06f))
                }
            }
        }
        for (week in 0 until (offset + length + 6) / 7) {
            Row {
                for (column in 0..6) {
                    val dayOfMonth = week * 7 + column - offset + 1
                    if (dayOfMonth in 1..length) {
                        val date = month.atDay(dayOfMonth)
                        DayCell(date, first, last, enabled = minDate == null || !date.isBefore(minDate), onPick)
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

private val DayOfWeek.isWeekend: Boolean get() = this == DayOfWeek.SATURDAY || this == DayOfWeek.SUNDAY

/** One day: a white circle on the range ends and a grey band between them, rounded at row edges. */
@Composable
private fun RowScope.DayCell(date: LocalDate, first: LocalDate?, last: LocalDate?, enabled: Boolean, onPick: (LocalDate) -> Unit) {
    val endpoint = date == first || date == last
    val range = first != null && last != null && first != last
    val inside = first != null && last != null && date.isAfter(first) && date.isBefore(last)
    val bandLeft = range && (inside || date == last)
    val bandRight = range && (inside || date == first)
    val roundLeft = date.dayOfWeek == DayOfWeek.MONDAY
    val roundRight = date.dayOfWeek == DayOfWeek.SUNDAY
    val color = when {
        endpoint -> Palette.Background
        !enabled -> Palette.Muted
        date.dayOfWeek.isWeekend -> Palette.Soft
        else -> Palette.Text
    }
    Box(
        modifier = Modifier
            .weight(1f)
            .height(48.dp)
            .drawBehind {
                val height = 44.dp.toPx()
                val top = (size.height - height) / 2
                if (bandLeft || bandRight) {
                    val radius = CornerRadius(height / 2)
                    val left = if (roundLeft) radius else CornerRadius.Zero
                    val right = if (roundRight) radius else CornerRadius.Zero
                    val rect = Rect(
                        left = if (bandLeft) 0f else size.width / 2,
                        top = top,
                        right = if (bandRight) size.width else size.width / 2,
                        bottom = top + height,
                    )
                    val band = RoundRect(
                        rect = rect,
                        topLeft = if (bandLeft) left else CornerRadius.Zero,
                        topRight = if (bandRight) right else CornerRadius.Zero,
                        bottomRight = if (bandRight) right else CornerRadius.Zero,
                        bottomLeft = if (bandLeft) left else CornerRadius.Zero,
                    )
                    drawPath(Path().apply { addRoundRect(band) }, Palette.Band)
                }
                if (endpoint) {
                    drawCircle(Palette.Text, radius = minOf(size.width, height) / 2, center = Offset(size.width / 2, size.height / 2))
                }
            }
            .selectable(selected = endpoint || inside, enabled = enabled, role = Role.Button, onClick = { onPick(date) })
            .semantics { contentDescription = date.spoken },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = date.dayOfMonth.toString(),
            style = monoStyle(14.sp, bold = endpoint, color = color, spacing = 0f).copy(fontFeatureSettings = "tnum"),
        )
    }
}
