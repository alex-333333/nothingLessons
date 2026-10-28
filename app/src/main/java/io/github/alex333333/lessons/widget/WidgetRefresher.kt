package io.github.alex333333.lessons.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.updateAll
import io.github.alex333333.lessons.data.ConfigRepository
import kotlinx.coroutines.flow.first

object WidgetRefresher {

    /** Redraws every widget instance and reschedules the next redraw; a no-op without widgets. */
    suspend fun refresh(context: Context) {
        val ids = GlanceAppWidgetManager(context).getGlanceIds(LessonWidget::class.java)
        if (ids.isEmpty()) {
            WidgetUpdateScheduler.cancel(context)
            return
        }
        // Also scheduled here because an already running Glance session does not re-run provideGlance.
        WidgetUpdateScheduler.schedule(context, ConfigRepository.get(context).config.first())
        LessonWidget().updateAll(context)
    }
}
