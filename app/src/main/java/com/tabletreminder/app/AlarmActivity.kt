package com.tabletreminder.app

import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView

/** The full-screen page that appears when the alarm rings. */
class AlarmActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())
    private var lastSig = ""
    private val tick = object : Runnable {
        override fun run() {
            val s = Store.session(this@AlarmActivity)
            if (s == null) {
                finish()
                return
            }
            if (sig(s) != lastSig) render()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.statusBarColor = Col.INK
        window.navigationBarColor = Col.INK
        render()
    }

    override fun onResume() {
        super.onResume()
        handler.removeCallbacks(tick)
        handler.post(tick)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(tick)
    }

    private fun sig(s: Session) = "${s.fired}-" + s.items.joinToString(",") { it.taken.size.toString() }

    private fun render() {
        val s = Store.session(this)
        if (s == null) {
            finish()
            return
        }
        lastSig = sig(s)
        val st = Store.settings(this)

        val scroll = ScrollView(this)
        scroll.isFillViewport = true
        scroll.setBackgroundColor(Col.INK)
        val page = vbox()
        page.gravity = Gravity.CENTER_HORIZONTAL
        page.setPadding(dp(24), dp(40), dp(24), dp(28))
        scroll.addView(page, FrameLayout.LayoutParams(MATCH, WRAP))

        page.add(
            badge("Reminder ${s.fired} of ${st.maxReminders} · ${Reminder.time(this, System.currentTimeMillis())}", Col.NIGHT_CARD, Col.WHITE),
            WRAP, WRAP, gravity = Gravity.CENTER_HORIZONTAL
        )

        // The capsule picture
        val pill = hbox()
        pill.rotation = -18f
        val r = dp(48).toFloat()
        val a = android.view.View(this)
        a.background = android.graphics.drawable.GradientDrawable().apply {
            setColor(Col.PILL); cornerRadii = floatArrayOf(r, r, 0f, 0f, 0f, 0f, r, r)
        }
        val b = android.view.View(this)
        b.background = android.graphics.drawable.GradientDrawable().apply {
            setColor(Col.WHITE); cornerRadii = floatArrayOf(0f, 0f, r, r, r, r, 0f, 0f)
        }
        pill.add(a, 0, MATCH, weight = 1f)
        pill.add(b, 0, MATCH, weight = 1f)
        page.add(pill, dp(200), dp(92), top = dp(56), gravity = Gravity.CENTER_HORIZONTAL)

        val title = tv("Time for your tablets", 32f, Col.WHITE, bold = true)
        title.gravity = Gravity.CENTER
        page.add(title, top = dp(56))
        val mins = ((System.currentTimeMillis() - s.ateAt) / 60_000L).toInt()
        val sub = tv(if (mins <= 1) "You just ate." else "You ate $mins minutes ago.", 17f, Col.NIGHT_TEXT)
        sub.gravity = Gravity.CENTER
        page.add(sub, top = dp(6))

        val list = vbox()
        list.background = round(Col.NIGHT_CARD, 18)
        list.setPadding(dp(18), dp(4), dp(18), dp(4))
        s.items.filter { it.left > 0 }.forEachIndexed { i, item ->
            if (i > 0) list.add(divider(0x24FFFFFF), MATCH, dp(1))
            val row = hbox()
            row.setPadding(0, dp(12), 0, dp(12))
            row.add(tv(item.name, 18f, Col.WHITE), 0, WRAP, weight = 1f)
            val left = if (item.total == 1) "1 left" else "${item.left} of ${item.total} left"
            row.add(tv(left, 18f, Col.WHITE, bold = true), WRAP, WRAP)
            list.add(row)
        }
        page.add(list, top = dp(24))

        val spacer = android.view.View(this)
        page.add(spacer, MATCH, 0, weight = 1f)

        page.add(btn("I took them all", Col.PILL, Col.PILL_INK, sizeSp = 20f, radiusDp = 18) {
            Reminder.tookAll(this); finish()
        }, MATCH, dp(64), top = dp(28))
        if (s.items.any { it.left > 1 }) {
            page.add(btn("Took 1 of each", Col.INK, Col.WHITE, stroke = 0x66FFFFFF, sizeSp = 17f, radiusDp = 16) {
                Reminder.tookOneOfEach(this)
                if (Store.session(this) == null) finish() else render()
            }, MATCH, dp(56), top = dp(12))
        }
        val stop = btn("Stop ringing (remind again in ${st.interval} min)", Col.INK, Col.WHITE, sizeSp = 16f) {
            AlarmService.stop(this); finish()
        }
        stop.paintFlags = stop.paintFlags or android.graphics.Paint.UNDERLINE_TEXT_FLAG
        page.add(stop, MATCH, WRAP, top = dp(8))

        setContentView(scroll)
    }
}
