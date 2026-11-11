package com.tabletreminder.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon

object Notifs {
    const val STATUS_ID = 1
    const val RING_ID = 2
    const val INFO_ID = 3

    private const val CH_RING = "ring"
    private const val CH_STATUS = "status"
    private const val CH_INFO = "info"

    private fun nm(ctx: Context) = ctx.getSystemService(NotificationManager::class.java)

    private fun ensureChannels(ctx: Context) {
        val nm = nm(ctx)
        if (nm.getNotificationChannel(CH_RING) == null) {
            val ring = NotificationChannel(CH_RING, "Tablet alarm", NotificationManager.IMPORTANCE_HIGH)
            ring.description = "The alarm that rings when it is time for your tablets"
            ring.setSound(null, null) // the app plays its own sound at media volume
            ring.enableVibration(false) // the app vibrates by itself
            ring.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            nm.createNotificationChannel(ring)
        }
        if (nm.getNotificationChannel(CH_STATUS) == null) {
            val status = NotificationChannel(CH_STATUS, "Reminders running", NotificationManager.IMPORTANCE_LOW)
            status.description = "Shows a countdown while reminders are on"
            nm.createNotificationChannel(status)
        }
        if (nm.getNotificationChannel(CH_INFO) == null) {
            val info = NotificationChannel(CH_INFO, "Missed tablets", NotificationManager.IMPORTANCE_DEFAULT)
            info.description = "Tells you when reminders stopped before you took your tablets"
            nm.createNotificationChannel(info)
        }
    }

    fun actionIntent(ctx: Context, action: String, req: Int): PendingIntent =
        PendingIntent.getBroadcast(
            ctx, req, Intent(ctx, ActionReceiver::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    fun openAppIntent(ctx: Context): PendingIntent =
        PendingIntent.getActivity(
            ctx, 2, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun alarmScreenIntent(ctx: Context): PendingIntent =
        PendingIntent.getActivity(
            ctx, 3,
            Intent(ctx, AlarmActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun action(ctx: Context, title: String, pi: PendingIntent): Notification.Action =
        Notification.Action.Builder(Icon.createWithResource(ctx, R.drawable.ic_notif), title, pi).build()

    private fun leftText(s: Session): String =
        s.items.filter { it.left > 0 }.joinToString(", ") { "${it.name} ${it.left}" }

    fun ringNotification(ctx: Context): Notification {
        ensureChannels(ctx)
        val s = Store.session(ctx)
        val text = if (s != null) "Still to take: ${leftText(s)}" else "Time for your tablets"
        val b = Notification.Builder(ctx, CH_RING)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle("Time for your tablets")
            .setContentText(text)
            .setCategory(Notification.CATEGORY_ALARM)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setContentIntent(alarmScreenIntent(ctx))
            .setFullScreenIntent(alarmScreenIntent(ctx), true)
            .addAction(action(ctx, "Took all", actionIntent(ctx, ActionReceiver.TOOK_ALL, 10)))
        if (s != null && s.items.any { it.left > 1 } && s.items.count { it.left > 0 } > 0) {
            b.addAction(action(ctx, "Took 1 of each", actionIntent(ctx, ActionReceiver.TOOK_EACH, 11)))
        }
        b.addAction(action(ctx, "Stop ringing", actionIntent(ctx, ActionReceiver.STOP_RING, 12)))
        return b.build()
    }

    /** Used only if the sound service could not start. */
    fun showRingFallback(ctx: Context) {
        try {
            nm(ctx).notify(RING_ID, ringNotification(ctx))
        } catch (e: SecurityException) {
        }
    }

    fun cancelRing(ctx: Context) {
        nm(ctx).cancel(RING_ID)
    }

    fun updateStatus(ctx: Context) {
        val s = Store.session(ctx)
        if (s == null) {
            nm(ctx).cancel(STATUS_ID)
            return
        }
        ensureChannels(ctx)
        val st = Store.settings(ctx)
        val n = Notification.Builder(ctx, CH_STATUS)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle("Reminders on · ${Reminder.tablets(s.totalLeft)} left")
            .setContentText("Next reminder at ${Reminder.time(ctx, s.nextAt)} · ${s.fired} of ${st.maxReminders} so far")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(true)
            .setWhen(s.nextAt)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setContentIntent(openAppIntent(ctx))
            .addAction(action(ctx, "Took all", actionIntent(ctx, ActionReceiver.TOOK_ALL, 13)))
            .build()
        try {
            nm(ctx).notify(STATUS_ID, n)
        } catch (e: SecurityException) {
        }
    }

    fun showMissed(ctx: Context, s: Session) {
        ensureChannels(ctx)
        val n = Notification.Builder(ctx, CH_INFO)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle("Reminders stopped")
            .setContentText("Not marked as taken: ${leftText(s)}")
            .setAutoCancel(true)
            .setContentIntent(openAppIntent(ctx))
            .build()
        try {
            nm(ctx).notify(INFO_ID, n)
        } catch (e: SecurityException) {
        }
    }
}
