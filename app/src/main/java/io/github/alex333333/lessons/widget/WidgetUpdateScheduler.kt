package io.github.alex333333.lessons.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import io.github.alex333333.lessons.core.Config
import io.github.alex333333.lessons.core.ScheduleCalculator
import java.time.LocalDateTime
import java.time.ZoneId

object WidgetUpdateScheduler {

    private const val WINDOW_MILLIS = 15_000L

    /**
     * Schedules one redraw at [ScheduleCalculator.nextRefreshAt]. The alarm is non-wakeup RTC on
     * purpose: it costs nothing while the screen is off and is delivered once the screen turns on.
     */
    fun schedule(context: Context, config: Config, now: LocalDateTime = LocalDateTime.now()) {
        val next = ScheduleCalculator.nextRefreshAt(config, now) ?: return cancel(context)
        val triggerMillis = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        alarmManager(context).setWindow(AlarmManager.RTC, triggerMillis, WINDOW_MILLIS, pendingIntent(context))
    }

    fun cancel(context: Context) {
        alarmManager(context).cancel(pendingIntent(context))
    }

    private fun alarmManager(context: Context): AlarmManager =
        context.getSystemService(AlarmManager::class.java)

    private fun pendingIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, RefreshReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
}
