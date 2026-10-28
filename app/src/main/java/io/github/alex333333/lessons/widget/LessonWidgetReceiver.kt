package io.github.alex333333.lessons.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

class LessonWidgetReceiver : GlanceAppWidgetReceiver() {

    // Placing or updating a widget runs provideGlance, which schedules the next redraw.
    override val glanceAppWidget: GlanceAppWidget = LessonWidget()

    /** The last widget was removed: nothing left to redraw. */
    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        WidgetUpdateScheduler.cancel(context)
    }
}
