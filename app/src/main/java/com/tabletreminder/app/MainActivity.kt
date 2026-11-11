package com.tabletreminder.app

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.GradientDrawable
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import java.util.Calendar

class MainActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var content: FrameLayout
    private lateinit var nav: LinearLayout
    private var tab = 0
    private var lastSig = ""
    private var countdown: TextView? = null
    private var countdownLabel: TextView? = null
    private var testPlayer: MediaPlayer? = null

    private val tick = object : Runnable {
        override fun run() {
            onTick()
            handler.postDelayed(this, 1000)
        }
    }

    companion object {
        private const val REQ_SOUND = 42
        private const val REQ_NOTIF = 43
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Col.BG
        window.navigationBarColor = Col.WHITE
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR

        val root = vbox()
        root.setBackgroundColor(Col.BG)
        content = FrameLayout(this)
        root.add(content, MATCH, 0, weight = 1f)
        nav = buildNav()
        root.add(nav)
        setContentView(root)

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQ_NOTIF)
        }
    }

    override fun onResume() {
        super.onResume()
        render()
        handler.removeCallbacks(tick)
        handler.post(tick)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(tick)
        stopTest()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        render()
    }

    // ------------------------------------------------------------ navigation

    private val tabs = listOf(
        "Home" to R.drawable.ic_home,
        "Medicines" to R.drawable.ic_pill,
        "History" to R.drawable.ic_clock,
        "Settings" to R.drawable.ic_sliders
    )

    private fun buildNav(): LinearLayout {
        val bar = hbox()
        bar.setBackgroundColor(Col.WHITE)
        bar.setPadding(dp(6), dp(8), dp(6), dp(10))
        tabs.forEachIndexed { i, (name, icon) ->
            val item = vbox()
            item.gravity = Gravity.CENTER_HORIZONTAL
            item.setPadding(0, dp(4), 0, dp(4))
            val iconWrap = FrameLayout(this)
            iconWrap.setPadding(dp(16), dp(3), dp(16), dp(3))
            val img = ImageView(this)
            img.setImageResource(icon)
            iconWrap.addView(img, FrameLayout.LayoutParams(dp(22), dp(22)))
            item.add(iconWrap, WRAP, WRAP, gravity = Gravity.CENTER_HORIZONTAL)
            val label = tv(name, 13f, Col.MUTED)
            label.gravity = Gravity.CENTER
            item.add(label, WRAP, WRAP, top = dp(3), gravity = Gravity.CENTER_HORIZONTAL)
            item.isClickable = true
            item.contentDescription = name
            item.setOnClickListener { tab = i; render() }
            bar.add(item, 0, WRAP, weight = 1f)
        }
        return bar
    }

    private fun updateNav() {
        for (i in 0 until nav.childCount) {
            val item = nav.getChildAt(i) as LinearLayout
            val iconWrap = item.getChildAt(0)
            val label = item.getChildAt(1) as TextView
            val on = i == tab
            iconWrap.background = if (on) round(Col.PILL, 99) else null
            label.setTextColor(if (on) Col.INK else Col.MUTED)
            label.setTypeface(null, if (on) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
        }
    }

    private fun signature(): String {
        val s = Store.session(this) ?: return "none"
        return "${s.ateAt}-${s.fired}-" + s.items.joinToString(",") { it.taken.size.toString() }
    }

    private fun onTick() {
        if (tab != 0) return
        val sig = signature()
        if (sig != lastSig) {
            render()
            return
        }
        val s = Store.session(this) ?: return
        val left = ((s.nextAt - System.currentTimeMillis()) / 1000).coerceAtLeast(0)
        countdown?.text = String.format("%d:%02d", left / 60, left % 60)
    }

    private fun render() {
        lastSig = signature()
        countdown = null
        countdownLabel = null
        content.removeAllViews()
        val scroll = ScrollView(this)
        scroll.isFillViewport = true
        val page = vbox()
        page.setPadding(dp(22), dp(24), dp(22), dp(20))
        scroll.addView(page, FrameLayout.LayoutParams(MATCH, WRAP))
        content.addView(scroll, FrameLayout.LayoutParams(MATCH, MATCH))
        when (tab) {
            0 -> if (Store.session(this) == null) homeIdle(page) else homeRunning(page)
            1 -> medicinesPage(page)
            2 -> historyPage(page)
            else -> settingsPage(page)
        }
        updateNav()
    }

    // ------------------------------------------------------------ problems / permissions

    private class Problem(val title: String, val text: String, val important: Boolean, val fix: () -> Unit)

    private fun problems(): List<Problem> {
        val out = mutableListOf<Problem>()
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            out.add(Problem("Allow notifications", "Needed to show the alarm.", true) {
                if (shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
                    requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQ_NOTIF)
                } else openNotificationSettings()
            })
        } else if (!nm.areNotificationsEnabled()) {
            out.add(Problem("Turn on notifications", "Needed to show the alarm.", true) { openNotificationSettings() })
        }
        if (!Reminder.canUseExactAlarms(this) && Build.VERSION.SDK_INT >= 31) {
            out.add(Problem("Allow alarms", "Lets reminders ring exactly on time.", true) {
                openSettings(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName")))
            })
        }
        if (Build.VERSION.SDK_INT >= 34 && !nm.canUseFullScreenIntent()) {
            out.add(Problem("Allow full-screen alarm", "Shows the alarm over the lock screen.", true) {
                openSettings(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:$packageName")))
            })
        }
        val pm = getSystemService(PowerManager::class.java)
        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
            out.add(Problem("Don’t limit battery", "Recommended, so the phone doesn’t delay reminders.", false) {
                @Suppress("BatteryLife")
                openSettings(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
            })
        }
        return out
    }

    private fun openNotificationSettings() {
        openSettings(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
    }

    private fun openSettings(i: Intent) {
        try {
            startActivity(i)
        } catch (e: Exception) {
            try {
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
            } catch (e2: Exception) {
                Toast.makeText(this, "Open your phone’s Settings › Apps › Tablet Reminder", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun problemCard(p: Problem): View {
        val c = card()
        c.background = round(if (p.important) Col.WARN_BG else Col.WHITE, 14)
        val row = hbox()
        val texts = vbox()
        texts.add(tv(p.title, 16f, Col.PILL_INK.takeIf { p.important } ?: Col.INK, bold = true))
        texts.add(tv(p.text, 14f, if (p.important) Col.PILL_INK else Col.MUTED), top = dp(2))
        row.add(texts, 0, WRAP, weight = 1f)
        row.add(btn("Fix", Col.INK, Col.WHITE, sizeSp = 15f, radiusDp = 12) { p.fix() }, WRAP, WRAP)
        c.add(row)
        return c
    }

    // ------------------------------------------------------------ home (idle)

    private fun homeIdle(page: LinearLayout) {
        page.add(tv("Tablet after food", 28f, bold = true))
        page.add(tv("Tap the button when you finish eating.", 16f, Col.MUTED), top = dp(4))

        problems().filter { it.important }.forEach { page.add(problemCard(it), top = dp(14)) }

        val meds = Store.medicines(this)
        val c = card()
        val head = hbox()
        head.add(tv("Your tablets", 17f, bold = true), 0, WRAP, weight = 1f)
        val edit = tv("Edit", 15f, bold = true)
        edit.setPadding(dp(8), dp(10), 0, dp(10))
        edit.setOnClickListener { tab = 1; render() }
        head.add(edit, WRAP, WRAP)
        c.add(head)
        if (meds.isEmpty()) {
            c.add(tv("No medicines yet. Tap Edit to add them.", 16f, Col.MUTED), top = dp(6))
        } else {
            meds.forEach { m ->
                val row = hbox()
                row.add(dot(Col.PILL), dp(26), dp(14))
                row.add(tv(m.name, 17f), 0, WRAP, weight = 1f).also { (it.layoutParams as LinearLayout.LayoutParams).leftMargin = dp(10) }
                row.add(tv(Reminder.tablets(m.count), 17f, bold = true), WRAP, WRAP)
                c.add(row, top = dp(10))
            }
        }
        page.add(c, top = dp(18))

        // The big capsule button
        val capsule = hbox()
        capsule.background = round(Col.PILL, 99)
        capsule.elevation = dp(6).toFloat()
        val r = dp(60).toFloat()
        val left = tv("I ate food", 24f, Col.PILL_INK, bold = true)
        left.gravity = Gravity.CENTER
        left.background = GradientDrawable().apply {
            setColor(Col.PILL); cornerRadii = floatArrayOf(r, r, 0f, 0f, 0f, 0f, r, r)
        }
        val right = tv("Start", 22f, Col.INK, bold = true)
        right.gravity = Gravity.CENTER
        right.background = GradientDrawable().apply {
            setColor(Col.WHITE); cornerRadii = floatArrayOf(0f, 0f, r, r, r, r, 0f, 0f)
        }
        capsule.add(left, 0, MATCH, weight = 1f)
        capsule.add(right, dp(110), MATCH)
        capsule.isClickable = true
        capsule.contentDescription = "I ate food. Start reminders."
        capsule.setOnClickListener { onAteFood() }
        page.add(capsule, MATCH, dp(120), top = dp(36))

        val st = Store.settings(this)
        val info = tv(
            "First reminder ${if (st.firstWait == 0) "right away" else "${st.firstWait} min after food"},\nthen every ${st.interval} min, up to ${st.maxReminders} times.",
            15f, Col.MUTED
        )
        info.gravity = Gravity.CENTER
        page.add(info, top = dp(18))

        val last = Store.history(this).firstOrNull()
        if (last != null) {
            val row = hbox()
            row.background = round(Col.SOFT, 14)
            row.setPadding(dp(16), dp(12), dp(16), dp(12))
            row.add(tv("Last time: ${dayLabel(last.ateAt).lowercase()}, ${Reminder.time(this, last.ateAt)}", 15f), 0, WRAP, weight = 1f)
            val (label, color) = statusLabel(last.status)
            row.add(tv(label, 15f, color, bold = true), WRAP, WRAP)
            page.add(row, top = dp(28))
        }
    }

    private fun onAteFood() {
        val err = Reminder.start(this)
        if (err != null) {
            Toast.makeText(this, err, Toast.LENGTH_LONG).show()
            if (Store.medicines(this).isEmpty()) { tab = 1; render() }
            return
        }
        render()
        if (Reminder.mediaVolumePercent(this) == 0) {
            AlertDialog.Builder(this)
                .setTitle("Media volume is off")
                .setMessage("The alarm plays at your media volume. Turn it up with the volume buttons so you can hear your reminders.")
                .setPositiveButton("OK", null)
                .show()
        }
    }

    // ------------------------------------------------------------ home (running)

    private fun homeRunning(page: LinearLayout) {
        val s = Store.session(this) ?: return
        val st = Store.settings(this)

        val head = hbox()
        head.add(tv("Reminders on", 22f, bold = true), 0, WRAP, weight = 1f)
        head.add(badge("Reminder ${s.fired} of ${st.maxReminders}", Col.WHITE, Col.INK), WRAP, WRAP)
        page.add(head)

        val timer = card(dark = true)
        timer.background = round(Col.INK, 24)
        timer.gravity = Gravity.CENTER_HORIZONTAL
        timer.setPadding(dp(20), dp(26), dp(20), dp(26))
        val left = ((s.nextAt - System.currentTimeMillis()) / 1000).coerceAtLeast(0)
        val count = tv(String.format("%d:%02d", left / 60, left % 60), 64f, Col.WHITE, bold = true)
        count.gravity = Gravity.CENTER
        timer.add(count, WRAP, WRAP, gravity = Gravity.CENTER_HORIZONTAL)
        val label = tv(if (s.fired == 0) "until the first reminder" else "until the next reminder", 17f, Col.WHITE)
        timer.add(label, WRAP, WRAP, gravity = Gravity.CENTER_HORIZONTAL)
        val ate = tv("You ate at ${Reminder.time(this, s.ateAt)}. Then every ${st.interval} min.", 15f, Col.NIGHT_TEXT)
        ate.gravity = Gravity.CENTER
        timer.add(ate, WRAP, WRAP, top = dp(6), gravity = Gravity.CENTER_HORIZONTAL)
        page.add(timer, top = dp(16))
        countdown = count
        countdownLabel = label

        val list = card()
        list.setPadding(dp(18), dp(4), dp(18), dp(4))
        s.items.forEachIndexed { i, item ->
            if (i > 0) list.add(divider(), MATCH, dp(1))
            val row = hbox()
            row.setPadding(0, dp(12), 0, dp(12))
            val info = vbox()
            info.add(tv(item.name, 17f, bold = true))
            val dots = hbox()
            for (d in 0 until item.total) {
                dots.add(dot(if (d < item.left) Col.PILL else Col.LINE), dp(26), dp(12))
                    .also { (it.layoutParams as LinearLayout.LayoutParams).rightMargin = dp(6) }
            }
            val leftText = when {
                item.left == 0 -> "All taken"
                item.total == 1 -> "1 left"
                else -> "${item.left} of ${item.total} left"
            }
            dots.add(tv(leftText, 14f, Col.MUTED), WRAP, WRAP)
            info.add(dots, top = dp(6))
            row.add(info, 0, WRAP, weight = 1f)
            if (item.left > 0) {
                row.add(btn("Took 1", Col.WHITE, Col.DONE, stroke = Col.DONE, sizeSp = 15f, radiusDp = 12) {
                    Reminder.tookOne(this, item.medId); render()
                }, WRAP, WRAP)
            } else {
                row.add(tv("✓", 22f, Col.DONE, bold = true), WRAP, WRAP)
            }
            list.add(row)
        }
        page.add(list, top = dp(16))

        page.add(btn("I took them all", Col.DONE, Col.WHITE, sizeSp = 19f, radiusDp = 16) {
            Reminder.tookAll(this); render()
        }, MATCH, dp(60), top = dp(20))
        page.add(btn("Cancel reminders", Col.BG, Col.INK, stroke = Col.LINE, sizeSp = 16f) {
            AlertDialog.Builder(this)
                .setTitle("Cancel reminders?")
                .setMessage("This meal will be saved in History as cancelled.")
                .setPositiveButton("Cancel reminders") { _, _ -> Reminder.cancel(this); render() }
                .setNegativeButton("Keep them", null)
                .show()
        }, MATCH, WRAP, top = dp(10))
    }

    // ------------------------------------------------------------ medicines

    private fun medicinesPage(page: LinearLayout) {
        page.add(tv("Medicines", 28f, bold = true))
        page.add(tv("What to take after each meal.", 16f, Col.MUTED), top = dp(4))
        if (Store.session(this) != null) {
            val note = tv("Changes apply from your next meal.", 15f, Col.PILL_INK)
            note.background = round(Col.WARN_BG, 12)
            note.setPadding(dp(14), dp(10), dp(14), dp(10))
            page.add(note, top = dp(12))
        }

        val meds = Store.medicines(this)
        meds.forEach { m ->
            val c = card()
            val head = hbox()
            val name = tv(m.name, 18f, bold = true)
            name.setOnClickListener { renameDialog(m) }
            head.add(name, 0, WRAP, weight = 1f)
            val del = btn("Delete", Col.WHITE, Col.MUTED, sizeSp = 14f) {
                AlertDialog.Builder(this)
                    .setTitle("Delete ${m.name}?")
                    .setPositiveButton("Delete") { _, _ ->
                        Store.saveMedicines(this, Store.medicines(this).filter { it.id != m.id })
                        TabletWidget.updateAll(this)
                        render()
                    }
                    .setNegativeButton("Keep", null)
                    .show()
            }
            head.add(del, WRAP, WRAP)
            c.add(head)
            val row = hbox()
            row.add(tv("Tablets each time", 15f, Col.MUTED), 0, WRAP, weight = 1f)
            row.add(stepper(m.count, 1, 10, { "$it" }) { v ->
                val list = Store.medicines(this)
                list.firstOrNull { it.id == m.id }?.count = v
                Store.saveMedicines(this, list)
                TabletWidget.updateAll(this)
            }, WRAP, WRAP)
            c.add(row, top = dp(6))
            val hint = tv("Tap the name to rename it.", 13f, Col.MUTED)
            c.add(hint, top = dp(4))
            page.add(c, top = dp(14))
        }

        val add = card()
        add.background = round(Col.WHITE, 18, Col.LINE)
        add.add(tv("New medicine name", 15f, Col.MUTED))
        val input = EditText(this)
        input.hint = "e.g. Iron"
        input.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
        input.textSize = 17f
        input.setTextColor(Col.INK)
        input.background = round(Col.WHITE, 12, Col.LINE)
        input.setPadding(dp(14), dp(12), dp(14), dp(12))
        add.add(input, top = dp(8))
        add.add(btn("Add medicine", Col.PILL, Col.PILL_INK) {
            val name = input.text.toString().trim()
            if (name.isEmpty()) {
                Toast.makeText(this, "Type a name first.", Toast.LENGTH_SHORT).show()
            } else {
                val list = Store.medicines(this)
                list.add(Medicine(System.currentTimeMillis(), name, 1))
                Store.saveMedicines(this, list)
                TabletWidget.updateAll(this)
                render()
            }
        }, top = dp(10))
        page.add(add, top = dp(14))
    }

    private fun renameDialog(m: Medicine) {
        val input = EditText(this)
        input.setText(m.name)
        input.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
        val wrap = FrameLayout(this)
        wrap.setPadding(dp(20), dp(8), dp(20), 0)
        wrap.addView(input)
        AlertDialog.Builder(this)
            .setTitle("Rename medicine")
            .setView(wrap)
            .setPositiveButton("Save") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    val list = Store.medicines(this)
                    list.firstOrNull { it.id == m.id }?.name = name
                    Store.saveMedicines(this, list)
                    render()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ------------------------------------------------------------ history

    private fun dayLabel(t: Long): String {
        val c = Calendar.getInstance().apply { timeInMillis = t }
        val today = Calendar.getInstance()
        val yest = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
        fun same(a: Calendar, b: Calendar) = a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
        return when {
            same(c, today) -> "Today"
            same(c, yest) -> "Yesterday"
            else -> android.text.format.DateFormat.getMediumDateFormat(this).format(java.util.Date(t))
        }
    }

    private fun statusLabel(status: String): Pair<String, Int> = when (status) {
        Store.TAKEN -> "All taken" to Col.DONE
        Store.MISSED -> "Missed" to Col.PILL_INK
        else -> "Cancelled" to Col.GREY_BADGE
    }

    private fun historyPage(page: LinearLayout) {
        page.add(tv("History", 28f, bold = true))
        val list = Store.history(this)
        if (list.isEmpty()) {
            page.add(tv("Nothing yet. Each meal shows up here after you tap “I ate food”.", 16f, Col.MUTED), top = dp(10))
            return
        }
        var lastDay = ""
        list.forEach { h ->
            val day = dayLabel(h.ateAt)
            if (day != lastDay) {
                page.add(tv(day, 16f, Col.MUTED, bold = true), top = dp(18))
                lastDay = day
            }
            val c = card()
            val head = hbox()
            head.add(tv("Ate at ${Reminder.time(this, h.ateAt)}", 17f, bold = true), 0, WRAP, weight = 1f)
            val badge = when (h.status) {
                Store.TAKEN -> badge("All taken", Col.DONE, Col.WHITE)
                Store.MISSED -> badge("Missed", Col.PILL, Col.PILL_INK)
                else -> badge("Cancelled", Col.SOFT, Col.INK)
            }
            head.add(badge, WRAP, WRAP)
            c.add(head)
            h.items.forEach { item ->
                val times = item.taken.distinct().joinToString(", ") { Reminder.time(this, it) }
                val line = if (item.taken.isEmpty()) "${item.name}: 0 of ${item.total} taken"
                else "${item.name}: ${item.taken.size} of ${item.total} · $times"
                c.add(tv(line, 15f, Col.MUTED), top = dp(6))
            }
            page.add(c, top = dp(10))
        }
        page.add(btn("Clear history", Col.BG, Col.INK, stroke = Col.LINE, sizeSp = 15f) {
            AlertDialog.Builder(this)
                .setTitle("Clear all history?")
                .setMessage("This can’t be undone.")
                .setPositiveButton("Clear") { _, _ -> Store.clearHistory(this); render() }
                .setNegativeButton("Keep", null)
                .show()
        }, top = dp(24))
    }

    // ------------------------------------------------------------ settings

    private fun settingRow(parent: LinearLayout, label: String, control: View, first: Boolean) {
        if (!first) parent.add(divider(), MATCH, dp(1))
        val row = hbox()
        row.setPadding(0, dp(12), 0, dp(12))
        row.add(tv(label, 16f, bold = true), 0, WRAP, weight = 1f)
        row.add(control, WRAP, WRAP)
        parent.add(row)
    }

    private fun settingsPage(page: LinearLayout) {
        val st = Store.settings(this)
        page.add(tv("Settings", 28f, bold = true))

        val timing = card()
        timing.setPadding(dp(18), dp(4), dp(18), dp(4))
        settingRow(timing, "First reminder after food", stepper(st.firstWait, 0, 120, { "$it min" }) {
            val s = Store.settings(this); s.firstWait = it; Store.saveSettings(this, s)
        }, true)
        settingRow(timing, "Then remind every", stepper(st.interval, 1, 60, { "$it min" }) {
            val s = Store.settings(this); s.interval = it; Store.saveSettings(this, s)
        }, false)
        settingRow(timing, "Stop after", stepper(st.maxReminders, 1, 30, { if (it == 1) "1 time" else "$it times" }) {
            val s = Store.settings(this); s.maxReminders = it; Store.saveSettings(this, s)
        }, false)
        page.add(timing, top = dp(16))

        val sound = card()
        sound.setPadding(dp(18), dp(4), dp(18), dp(4))
        val soundRow = hbox()
        soundRow.setPadding(0, dp(14), 0, dp(14))
        val soundTexts = vbox()
        soundTexts.add(tv("Alarm sound", 16f, bold = true))
        soundTexts.add(tv(st.soundName, 15f, Col.MUTED), top = dp(2))
        soundRow.add(soundTexts, 0, WRAP, weight = 1f)
        soundRow.add(tv("Change", 15f, Col.INK, bold = true), WRAP, WRAP)
        soundRow.isClickable = true
        soundRow.setOnClickListener { pickSound() }
        sound.add(soundRow)
        sound.add(divider(), MATCH, dp(1))

        val vib = Switch(this)
        vib.isChecked = st.vibrate
        vib.setOnCheckedChangeListener { _, on -> val s = Store.settings(this); s.vibrate = on; Store.saveSettings(this, s) }
        settingRow(sound, "Vibrate", vib, true)
        sound.add(divider(), MATCH, dp(1))

        val vol = Reminder.mediaVolumePercent(this)
        val volBox = vbox()
        volBox.setPadding(0, dp(14), 0, dp(14))
        volBox.add(tv("Volume", 16f, bold = true))
        volBox.add(tv("The alarm plays at your phone’s media volume, now $vol%. Use the volume buttons to change it.", 15f, Col.MUTED), top = dp(4))
        val bar = FrameLayout(this)
        bar.background = round(Col.DIVIDER, 99)
        val fill = View(this)
        fill.background = round(Col.INK, 99)
        bar.post {
            fill.layoutParams = FrameLayout.LayoutParams((bar.width * vol / 100).coerceAtLeast(0), MATCH)
        }
        bar.addView(fill, FrameLayout.LayoutParams(0, MATCH))
        volBox.add(bar, MATCH, dp(8), top = dp(10))
        if (vol == 0) {
            volBox.add(tv("Media volume is off. You won’t hear the alarm.", 15f, Col.PILL_INK, bold = true), top = dp(8))
        }
        volBox.add(btn("Test the alarm sound", Col.BG, Col.INK, stroke = Col.LINE, sizeSp = 15f) { testSound() }, top = dp(12))
        sound.add(volBox)
        page.add(sound, top = dp(16))

        val probs = problems()
        page.add(tv("Make sure it rings", 16f, Col.MUTED, bold = true), top = dp(22))
        if (probs.isEmpty()) {
            val ok = card()
            ok.add(tv("All set. Everything the alarm needs is allowed.", 15f, Col.DONE, bold = true))
            page.add(ok, top = dp(8))
        } else {
            probs.forEach { page.add(problemCard(it), top = dp(8)) }
        }
        page.add(
            tv("Tip: add the “I ate food” widget to your home screen. Long-press the home screen, tap Widgets, and find Tablet Reminder.", 14f, Col.MUTED),
            top = dp(18)
        )
    }

    private fun pickSound() {
        val st = Store.settings(this)
        val i = Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
            .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALL)
            .putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Alarm sound")
            .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
            .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
            .putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI, Settings.System.DEFAULT_ALARM_ALERT_URI)
        st.soundUri?.let { i.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(it)) }
        try {
            startActivityForResult(i, REQ_SOUND)
        } catch (e: Exception) {
            Toast.makeText(this, "This phone has no sound picker.", Toast.LENGTH_LONG).show()
        }
    }

    @Deprecated("Uses the platform activity result API")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQ_SOUND || resultCode != RESULT_OK || data == null) return
        @Suppress("DEPRECATION")
        val uri: Uri? = data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
        val s = Store.settings(this)
        if (uri == null || uri == Settings.System.DEFAULT_ALARM_ALERT_URI) {
            s.soundUri = null
            s.soundName = "Default alarm"
        } else {
            s.soundUri = uri.toString()
            s.soundName = try {
                RingtoneManager.getRingtone(this, uri)?.getTitle(this) ?: "Custom sound"
            } catch (e: Exception) {
                "Custom sound"
            }
        }
        Store.saveSettings(this, s)
        render()
    }

    private fun testSound() {
        stopTest()
        val p = AlarmService.createPlayer(this)
        if (p == null) {
            Toast.makeText(this, "Couldn’t play this sound. Pick another one.", Toast.LENGTH_LONG).show()
            return
        }
        testPlayer = p
        p.start()
        if (Reminder.mediaVolumePercent(this) == 0) {
            Toast.makeText(this, "Media volume is off. Turn it up to hear the alarm.", Toast.LENGTH_LONG).show()
        }
        handler.postDelayed({ stopTest() }, 4000)
    }

    private fun stopTest() {
        testPlayer?.let {
            try { it.stop() } catch (e: Exception) { }
            it.release()
        }
        testPlayer = null
    }
}
