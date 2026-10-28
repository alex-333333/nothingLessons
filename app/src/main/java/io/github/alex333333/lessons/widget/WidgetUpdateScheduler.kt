package io.github.alex333333.lessons.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import io.github.alex333333.lessons.core.Config
import io.github.alex333333.lessons.core.ScheduleCalculator
import java.time.LocalDateTime
import java.time.ZoneId

object WidgetUpdateScheduler {

    private const val WINDOW_MILLIS = 15_000L

    /**
     * Schedules one redraw at [ScheduleCalculator.nextRefreshAt]. The alarm is non-wakeup RTC on
     * purpose: it costs nothing while the screen is off and is delivered once the screen turns on.
     * It is exact when allowed, because since Android 12 an inexact window is stretched to ~45 s
     * for a one-minute alarm and the countdown would visibly lag.
     */
    fun schedule(context: Context, config: Config, now: LocalDateTime = LocalDateTime.now()) {
        val next = ScheduleCalculator.nextRefreshAt(config, now) ?: return cancel(context)
        val triggerMillis = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val alarms = alarmManager(context)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()) {
            alarms.setExact(AlarmManager.RTC, triggerMillis, pendingIntent(context))
        } else {
            alarms.setWindow(AlarmManager.RTC, triggerMillis, WINDOW_MILLIS, pendingIntent(context))
        }
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
