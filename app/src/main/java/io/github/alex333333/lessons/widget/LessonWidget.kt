package io.github.alex333333.lessons.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Box
import androidx.glance.layout.fillMaxSize
import io.github.alex333333.lessons.MainActivity
import io.github.alex333333.lessons.core.ScheduleCalculator
import io.github.alex333333.lessons.data.ConfigRepository
import java.time.Duration
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

class LessonWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Responsive(setOf(SMALL, WIDE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repository = ConfigRepository.get(context)
        val initial = repository.config.first()
        // provideContent never returns, so the next redraw is scheduled before it.
        WidgetUpdateScheduler.schedule(context, initial)

        provideContent {
            // A Glance session can outlive one update (e.g. right after the config is edited),
            // so keep both inputs live instead of freezing the state computed above.
            val config by repository.config.collectAsState(initial)
            val now by currentMinute()
            Box(GlanceModifier.fillMaxSize().clickable(actionStartActivity<MainActivity>())) {
                LessonWidgetContent(ScheduleCalculator.stateAt(config, now))
            }
        }
    }

    companion object {
        /** 2×2 cells. */
        val SMALL = DpSize(110.dp, 110.dp)

        /** 4×2 cells. */
        val WIDE = DpSize(250.dp, 110.dp)
    }
}

/** The current time, updated at each minute boundary while the composition is alive. */
@Composable
private fun currentMinute(): State<LocalDateTime> = produceState(LocalDateTime.now()) {
    while (true) {
        val now = LocalDateTime.now()
        delay(Duration.between(now, now.truncatedTo(ChronoUnit.MINUTES).plusMinutes(1)).toMillis() + 50)
        value = LocalDateTime.now()
    }
}
