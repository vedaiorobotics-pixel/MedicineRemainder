package com.tabletreminder.app

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.view.View
import android.widget.RemoteViews

class TabletWidget : AppWidgetProvider() {

    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        update(ctx, mgr, ids)
    }

    companion object {
        fun updateAll(ctx: Context) {
            val mgr = AppWidgetManager.getInstance(ctx) ?: return
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, TabletWidget::class.java))
            if (ids.isNotEmpty()) update(ctx, mgr, ids)
        }

        fun update(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
            val rv = RemoteViews(ctx.packageName, R.layout.widget)
            val s = Store.session(ctx)
            if (s == null) {
                val n = Store.medicines(ctx).sumOf { it.count }
                rv.setInt(R.id.widget_root, "setBackgroundResource", R.drawable.widget_bg_light)
                rv.setTextViewText(R.id.widget_title, "Tablet after food")
                rv.setTextColor(R.id.widget_title, Col.INK)
                rv.setTextViewText(R.id.widget_sub, if (n == 0) "Add medicines in the app" else "${Reminder.tablets(n)} after your meal")
                rv.setTextColor(R.id.widget_sub, Col.MUTED)
                rv.setViewVisibility(R.id.btn_start, View.VISIBLE)
                rv.setViewVisibility(R.id.btn_took, View.GONE)
                rv.setOnClickPendingIntent(R.id.btn_start, Notifs.actionIntent(ctx, ActionReceiver.START, 20))
            } else {
                rv.setInt(R.id.widget_root, "setBackgroundResource", R.drawable.widget_bg_dark)
                rv.setTextViewText(R.id.widget_title, "${Reminder.tablets(s.totalLeft)} left")
                rv.setTextColor(R.id.widget_title, Col.WHITE)
                rv.setTextViewText(R.id.widget_sub, "Next reminder at ${Reminder.time(ctx, s.nextAt)}")
                rv.setTextColor(R.id.widget_sub, Col.NIGHT_TEXT)
                rv.setViewVisibility(R.id.btn_start, View.GONE)
                rv.setViewVisibility(R.id.btn_took, View.VISIBLE)
                rv.setOnClickPendingIntent(R.id.btn_took, Notifs.actionIntent(ctx, ActionReceiver.TOOK_ALL, 21))
            }
            rv.setOnClickPendingIntent(R.id.widget_root, Notifs.openAppIntent(ctx))
            mgr.updateAppWidget(ids, rv)
        }
    }
}
