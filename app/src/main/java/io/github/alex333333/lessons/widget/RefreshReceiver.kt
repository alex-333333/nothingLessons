package io.github.alex333333.lessons.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Fired by the alarm from [WidgetUpdateScheduler]. Not exported. */
open class RefreshReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                WidgetRefresher.refresh(appContext)
            } finally {
                pending.finish()
            }
        }
    }
}

/**
 * Clock, time zone, date changes, boot and app updates. Exported so the system can deliver them;
 * all it can do is redraw the widget. Alarms are cleared on reboot, so the refresh reschedules them.
 */
class SystemEventsReceiver : RefreshReceiver()
