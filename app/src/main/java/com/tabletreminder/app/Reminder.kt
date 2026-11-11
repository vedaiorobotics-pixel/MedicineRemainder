package com.tabletreminder.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import java.util.Date

/** All the rules for starting, ringing and finishing reminders. */
object Reminder {
    private const val REQ_ALARM = 100

    fun tablets(n: Int) = if (n == 1) "1 tablet" else "$n tablets"

    fun time(ctx: Context, t: Long): String =
        android.text.format.DateFormat.getTimeFormat(ctx).format(Date(t))

    fun mediaVolumePercent(ctx: Context): Int {
        val am = ctx.getSystemService(AudioManager::class.java)
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        return am.getStreamVolume(AudioManager.STREAM_MUSIC) * 100 / max
    }

    /** Returns an error message, or null when reminders started. */
    fun start(ctx: Context): String? {
        val meds = Store.medicines(ctx).filter { it.count > 0 }
        if (meds.isEmpty()) return "Add your medicines first."
        if (Store.session(ctx) != null) finish(ctx, Store.CANCELLED)
        val st = Store.settings(ctx)
        val now = System.currentTimeMillis()
        val first = if (st.firstWait <= 0) now + 5_000L else now + st.firstWait * 60_000L
        val s = Session(now, 0, first, meds.map { SessionItem(it.id, it.name, it.count) }.toMutableList())
        Store.saveSession(ctx, s)
        schedule(ctx, first)
        refresh(ctx)
        return null
    }

    /** Called by AlarmReceiver when a reminder is due. */
    fun onAlarm(ctx: Context) {
        val s = Store.session(ctx) ?: return
        val st = Store.settings(ctx)
        if (s.fired >= st.maxReminders) {
            finish(ctx, Store.MISSED)
            Notifs.showMissed(ctx, s)
            return
        }
        s.fired += 1
        s.nextAt = System.currentTimeMillis() + st.interval * 60_000L
        Store.saveSession(ctx, s)
        schedule(ctx, s.nextAt)
        refresh(ctx)
        AlarmService.ring(ctx)
    }

    fun tookOne(ctx: Context, medId: Long) = take(ctx) { s ->
        s.items.firstOrNull { it.medId == medId && it.left > 0 }?.taken?.add(System.currentTimeMillis())
    }

    fun tookOneOfEach(ctx: Context) = take(ctx) { s ->
        val now = System.currentTimeMillis()
        s.items.filter { it.left > 0 }.forEach { it.taken.add(now) }
    }

    fun tookAll(ctx: Context) = take(ctx) { s ->
        val now = System.currentTimeMillis()
        s.items.forEach { item -> repeat(item.left) { item.taken.add(now) } }
    }

    private fun take(ctx: Context, change: (Session) -> Unit) {
        val s = Store.session(ctx) ?: return
        change(s)
        AlarmService.stop(ctx)
        if (s.totalLeft == 0) {
            Store.saveSession(ctx, s)
            finish(ctx, Store.TAKEN)
        } else {
            Store.saveSession(ctx, s)
            refresh(ctx)
        }
    }

    fun cancel(ctx: Context) = finish(ctx, Store.CANCELLED)

    fun finish(ctx: Context, status: String) {
        val s = Store.session(ctx) ?: return
        cancelAlarm(ctx)
        AlarmService.stop(ctx)
        Store.addHistory(ctx, HistoryEntry(s.ateAt, System.currentTimeMillis(), status, s.items))
        Store.saveSession(ctx, null)
        refresh(ctx)
    }

    /** After a restart or app update, put the alarm back. */
    fun restore(ctx: Context) {
        val s = Store.session(ctx) ?: return
        val now = System.currentTimeMillis()
        if (s.nextAt < now + 5_000L) {
            s.nextAt = now + 10_000L
            Store.saveSession(ctx, s)
        }
        schedule(ctx, s.nextAt)
        refresh(ctx)
    }

    fun refresh(ctx: Context) {
        Notifs.updateStatus(ctx)
        TabletWidget.updateAll(ctx)
    }

    private fun alarmIntent(ctx: Context): PendingIntent =
        PendingIntent.getBroadcast(
            ctx, REQ_ALARM, Intent(ctx, AlarmReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    fun canUseExactAlarms(ctx: Context): Boolean {
        val am = ctx.getSystemService(AlarmManager::class.java)
        return Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
    }

    private fun schedule(ctx: Context, at: Long) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val pi = alarmIntent(ctx)
        if (canUseExactAlarms(ctx)) {
            val show = PendingIntent.getActivity(
                ctx, 1, Intent(ctx, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            am.setAlarmClock(AlarmManager.AlarmClockInfo(at, show), pi)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    private fun cancelAlarm(ctx: Context) {
        ctx.getSystemService(AlarmManager::class.java).cancel(alarmIntent(ctx))
    }
}
