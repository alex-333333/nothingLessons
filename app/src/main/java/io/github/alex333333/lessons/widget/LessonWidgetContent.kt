package io.github.alex333333.lessons.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import io.github.alex333333.lessons.core.WidgetState

/** Placeholder: a plain text summary. The real dot-matrix layout replaces this file. */
@Composable
fun LessonWidgetContent(state: WidgetState) {
    Box(
        modifier = GlanceModifier.fillMaxSize().background(Color.Black).padding(12.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = summary(state),
            style = TextStyle(color = ColorProvider(Color.White), fontSize = 14.sp),
        )
    }
}

private fun summary(state: WidgetState): String = when (state) {
    WidgetState.NotConfigured -> "Нажмите, чтобы добавить расписание"
    is WidgetState.Lesson -> buildString {
        append("${state.current.number}. ${state.current.subject}\nещё ${state.minutesLeft} мин")
        state.next?.let { append("\nдалее: ${it.slot.subject}") }
    }
    is WidgetState.Break -> "Перемена\n${state.next.subject} через ${state.minutesUntil} мин"
    is WidgetState.BeforeSchool -> "${state.next.subject} через ${state.minutesUntil} мин"
    is WidgetState.DayOff -> "Уроки закончились\nдалее: ${state.next.slot.subject}"
}
