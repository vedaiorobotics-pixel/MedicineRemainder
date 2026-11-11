package com.tabletreminder.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast

/** Fires when a reminder is due. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        Reminder.onAlarm(ctx)
    }
}

/** Buttons on notifications and on the widget. */
class ActionReceiver : BroadcastReceiver() {
    companion object {
        const val START = "com.tabletreminder.app.START"
        const val TOOK_ALL = "com.tabletreminder.app.TOOK_ALL"
        const val TOOK_EACH = "com.tabletreminder.app.TOOK_EACH"
        const val STOP_RING = "com.tabletreminder.app.STOP_RING"
    }

    override fun onReceive(ctx: Context, intent: Intent) {
        when (intent.action) {
            START -> {
                val err = Reminder.start(ctx)
                val msg = when {
                    err != null -> err
                    Reminder.mediaVolumePercent(ctx) == 0 -> "Reminders started. Your media volume is off, so turn it up to hear them."
                    else -> "Reminders started."
                }
                Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()
            }
            TOOK_ALL -> Reminder.tookAll(ctx)
            TOOK_EACH -> Reminder.tookOneOfEach(ctx)
            STOP_RING -> AlarmService.stop(ctx)
        }
    }
}

/** Puts reminders back after the phone restarts or the app is updated. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        Reminder.restore(ctx)
    }
}
