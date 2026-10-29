package io.github.alex333333.lessons.ui.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.alex333333.lessons.core.Config
import io.github.alex333333.lessons.core.Countdown
import io.github.alex333333.lessons.core.CountdownUnit
import io.github.alex333333.lessons.core.DayOffReason
import io.github.alex333333.lessons.core.DotMatrix
import io.github.alex333333.lessons.core.ScheduleCalculator
import io.github.alex333333.lessons.core.Upcoming
import io.github.alex333333.lessons.core.WidgetState
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay

private val timeFormat = DateTimeFormatter.ofPattern("HH:mm")

internal fun LocalTime.hhmm(): String = format(timeFormat)

private val shortDays = listOf("ПН", "ВТ", "СР", "ЧТ", "ПТ", "СБ", "ВС")

internal val DayOfWeek.shortLabel: String get() = shortDays[value - 1]

/**
 * [text] drawn as 5×7 dots. Glyphs are laid out one by one so the gap column between them
 * stays empty instead of showing unlit dots.
 */
@Composable
internal fun DotMatrixText(
    text: String,
    modifier: Modifier = Modifier,
    dot: Dp = 5.dp,
    gap: Dp = 2.dp,
    on: Color = Palette.Text,
    off: Color = Palette.DotOff,
) {
    val glyphs = remember(text) { text.map { DotMatrix.layout(it.toString()) } }
    val columns = glyphs.sumOf { it.width } + (glyphs.size - 1).coerceAtLeast(0)
    if (columns <= 0) return
    Canvas(modifier.size(width = dot * columns + gap * (columns - 1), height = dot * DotMatrix.ROWS + gap * (DotMatrix.ROWS - 1))) {
        val radius = dot.toPx() / 2
        val pitch = (dot + gap).toPx()
        var x0 = 0
        for (glyph in glyphs) {
            for (x in 0 until glyph.width) {
                for (y in 0 until glyph.height) {
                    drawCircle(
                        color = if (glyph[x, y]) on else off,
                        radius = radius,
                        center = Offset((x0 + x) * pitch + radius, y * pitch + radius),
                    )
                }
            }
            x0 += glyph.width + 1
        }
    }
}

@Composable
internal fun Header(subtitle: String) {
    Column(Modifier.padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        DotMatrixText(
            text = "УРОКИ",
            modifier = Modifier
                .padding(bottom = 6.dp)
                .semantics {
                    heading()
                    contentDescription = "Уроки"
                },
        )
        Text(subtitle, style = monoStyle(11.sp))
    }
}

@Composable
internal fun PillTabs(selected: SettingsTab, onSelect: (SettingsTab) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        for (tab in SettingsTab.entries) {
            val isSelected = tab == selected
            Box(
                modifier = Modifier
                    .weight(tab.width)
                    .height(44.dp)
                    .pill(filled = isSelected)
                    .selectable(selected = isSelected, role = Role.Tab, onClick = { onSelect(tab) }),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = tab.label,
                    style = monoStyle(12.sp, bold = true, color = if (isSelected) Palette.Background else Palette.Text),
                    maxLines = 1,
                )
            }
        }
    }
}

/** A fully rounded chip: white fill when [filled], otherwise a thin grey outline. */
internal fun Modifier.pill(filled: Boolean): Modifier =
    clip(CircleShape).then(
        if (filled) Modifier.background(Palette.Text) else Modifier.border(1.dp, Palette.Border, CircleShape),
    )

/** Outlined (or dashed) full-width pill button with a mono caps label. */
@Composable
internal fun PillButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, dashed: Boolean = false) {
    val outline = if (dashed) {
        Modifier.drawBehind {
            val stroke = 1.dp.toPx()
            drawRoundRect(
                color = Palette.Muted,
                topLeft = Offset(stroke / 2, stroke / 2),
                size = Size(size.width - stroke, size.height - stroke),
                cornerRadius = CornerRadius((size.height - stroke) / 2),
                style = Stroke(width = stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))),
            )
        }
    } else {
        Modifier.border(1.dp, Palette.Border, CircleShape)
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(CircleShape)
            .then(outline)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = monoStyle(12.sp, bold = true, color = Palette.Text))
    }
}

@Composable
internal fun SectionLabel(text: String) {
    Text(text, modifier = Modifier.padding(horizontal = 4.dp), style = monoStyle(11.sp))
}

/** A thin "×", drawn rather than taken from an icon set. */
@Composable
internal fun CrossIcon(modifier: Modifier = Modifier, color: Color = Palette.Secondary) {
    Canvas(modifier.size(16.dp)) {
        val unit = size.width / 16
        val stroke = 1.6.dp.toPx()
        drawLine(color, Offset(3 * unit, 3 * unit), Offset(13 * unit, 13 * unit), stroke, StrokeCap.Round)
        drawLine(color, Offset(13 * unit, 3 * unit), Offset(3 * unit, 13 * unit), stroke, StrokeCap.Round)
    }
}

/** Current time, ticking at the start of every minute. */
@Composable
private fun rememberNow(): LocalDateTime {
    val now by produceState(LocalDateTime.now()) {
        while (true) {
            val t = LocalDateTime.now()
            delay(60_000L - (t.second * 1_000L + t.nano / 1_000_000L))
            value = LocalDateTime.now()
        }
    }
    return now
}

/** A compact replica of what the widget shows right now. */
@Composable
internal fun PreviewCard(config: Config) {
    val now = rememberNow()
    val state = remember(config, now) { ScheduleCalculator.stateAt(config, now) }
    val subjectFont = rememberSubjectFont(config.font, config.weight)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 88.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Palette.Card)
            .semantics(mergeDescendants = true) {}
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (state is WidgetState.NotConfigured) {
            Text("Добавьте звонки и предметы", style = bodyStyle(17.sp, medium = true, color = Palette.Secondary))
        } else {
            PreviewContent(state, subjectFont)
        }
    }
}

@Composable
private fun RowScope.PreviewContent(state: WidgetState, subjectFont: FontFamily) {
    val view = PreviewText.of(state)
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(if (state is WidgetState.Lesson) Palette.Accent else Palette.Muted),
            )
            Text(view.label, style = monoStyle(10.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        view.nextLabel?.let { Text(it, style = monoStyle(10.sp), maxLines = 1) }
        view.nextSubject?.let {
            Text(it, style = subjectStyle(subjectFont, 22.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
    view.countdown?.let { countdown ->
        val unit = if (countdown.unit == CountdownUnit.Hours) "ЧАС" else "МИН"
        val caption = "$unit ${view.countdownTarget}"
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            DotMatrixText(
                text = countdown.text,
                off = Color(0xFF1E1E1E),
                modifier = Modifier.semantics { contentDescription = countdown.text },
            )
            Text(caption, style = monoStyle(9.sp, spacing = 0.06f), maxLines = 1)
        }
    }
}

/** The preview card's texts for one [WidgetState]. */
private class PreviewText(
    val label: String,
    val nextLabel: String?,
    val nextSubject: String?,
    val countdown: Countdown?,
    val countdownTarget: String,
) {
    companion object {
        fun of(state: WidgetState): PreviewText = when (state) {
            is WidgetState.Lesson -> PreviewText(
                label = "СЕЙЧАС · ${state.current.subject}",
                nextLabel = state.next?.let { upcomingLabel(it) },
                nextSubject = state.next?.slot?.subject,
                countdown = Countdown.of(state.minutesLeft),
                countdownTarget = "ДО ЗВОНКА",
            )
            is WidgetState.Break -> PreviewText(
                label = "ПЕРЕМЕНА",
                nextLabel = "ДАЛЕЕ · ${state.next.start.hhmm()}",
                nextSubject = state.next.subject,
                countdown = Countdown.of(state.minutesUntil),
                countdownTarget = "ДО УРОКА",
            )
            is WidgetState.BeforeSchool -> PreviewText(
                label = "ДО НАЧАЛА",
                nextLabel = "ДАЛЕЕ · ${state.next.start.hhmm()}",
                nextSubject = state.next.subject,
                countdown = Countdown.of(state.minutesUntil),
                countdownTarget = "ДО УРОКА",
            )
            is WidgetState.DayOff -> PreviewText(
                label = when (state.reason) {
                    DayOffReason.Cancelled -> "УРОКИ ОТМЕНЕНЫ"
                    DayOffReason.Finished -> "НА СЕГОДНЯ ВСЁ"
                    DayOffReason.NoLessons -> "ВЫХОДНОЙ"
                },
                nextLabel = upcomingLabel(state.next),
                nextSubject = state.next.slot.subject,
                countdown = null,
                countdownTarget = "",
            )
            is WidgetState.Holidays -> PreviewText(
                label = "КАНИКУЛЫ",
                nextLabel = state.next?.let { upcomingLabel(it) },
                nextSubject = state.next?.slot?.subject,
                countdown = null,
                countdownTarget = "",
            )
            WidgetState.NotConfigured -> PreviewText("УРОКИ", null, null, null, "")
        }
    }
}

private fun upcomingLabel(next: Upcoming): String {
    val time = next.slot.start.hhmm()
    return when (next.daysAhead) {
        0 -> "ДАЛЕЕ · $time"
        1 -> "ЗАВТРА · $time"
        else -> "${next.day.shortLabel} · $time"
    }
}
