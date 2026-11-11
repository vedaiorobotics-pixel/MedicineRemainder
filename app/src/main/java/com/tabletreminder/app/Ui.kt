package com.tabletreminder.app

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.roundToInt

/** The app's colours (same as the design). */
object Col {
    const val BG = 0xFFE6EEF0.toInt()
    const val INK = 0xFF183243.toInt()
    const val MUTED = 0xFF4A6375.toInt()
    const val PILL = 0xFFF2B705.toInt()
    const val PILL_INK = 0xFF3A2B00.toInt()
    const val DONE = 0xFF23735A.toInt()
    const val WHITE = 0xFFFFFFFF.toInt()
    const val LINE = 0xFFC7D6DB.toInt()
    const val DIVIDER = 0xFFDCE6E9.toInt()
    const val SOFT = 0xFFD5E2E6.toInt()
    const val WARN_BG = 0xFFFFF4CC.toInt()
    const val NIGHT_TEXT = 0xFFC9D8E0.toInt()
    const val NIGHT_CARD = 0x1FFFFFFF
    const val GREY_BADGE = 0xFF6B7F8C.toInt()
}

const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

fun Context.dp(v: Number): Int = (v.toFloat() * resources.displayMetrics.density).roundToInt()

fun Context.round(color: Int, radiusDp: Number, stroke: Int? = null, strokeDp: Number = 2): GradientDrawable {
    val g = GradientDrawable()
    g.setColor(color)
    g.cornerRadius = dp(radiusDp).toFloat()
    if (stroke != null) g.setStroke(dp(strokeDp), stroke)
    return g
}

fun Context.tv(text: CharSequence, sizeSp: Float = 17f, color: Int = Col.INK, bold: Boolean = false): TextView {
    val t = TextView(this)
    t.text = text
    t.textSize = sizeSp
    t.setTextColor(color)
    if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD)
    t.setLineSpacing(0f, 1.15f)
    return t
}

fun Context.vbox(): LinearLayout {
    val l = LinearLayout(this)
    l.orientation = LinearLayout.VERTICAL
    return l
}

fun Context.hbox(): LinearLayout {
    val l = LinearLayout(this)
    l.orientation = LinearLayout.HORIZONTAL
    l.gravity = Gravity.CENTER_VERTICAL
    return l
}

fun Context.card(dark: Boolean = false): LinearLayout {
    val c = vbox()
    c.background = round(if (dark) Col.INK else Col.WHITE, 18)
    c.setPadding(dp(18), dp(14), dp(18), dp(14))
    return c
}

fun Context.btn(
    label: String,
    bg: Int,
    fg: Int,
    stroke: Int? = null,
    sizeSp: Float = 17f,
    radiusDp: Int = 14,
    onClick: () -> Unit
): Button {
    val b = Button(this)
    b.text = label
    b.isAllCaps = false
    b.textSize = sizeSp
    b.setTextColor(fg)
    b.setTypeface(Typeface.DEFAULT, Typeface.BOLD)
    b.background = round(bg, radiusDp, stroke)
    b.stateListAnimator = null
    b.minHeight = dp(52)
    b.minimumHeight = dp(52)
    b.setPadding(dp(16), dp(8), dp(16), dp(8))
    b.setOnClickListener { onClick() }
    return b
}

fun Context.circleBtn(label: String, onClick: () -> Unit): Button {
    val b = Button(this)
    b.text = label
    b.textSize = 22f
    b.setTextColor(Col.INK)
    b.setTypeface(Typeface.DEFAULT, Typeface.BOLD)
    val g = GradientDrawable()
    g.shape = GradientDrawable.OVAL
    g.setColor(Col.BG)
    g.setStroke(dp(2), Col.LINE)
    b.background = g
    b.stateListAnimator = null
    b.setPadding(0, 0, 0, 0)
    b.minWidth = 0
    b.minimumWidth = 0
    b.minHeight = 0
    b.minimumHeight = 0
    b.setOnClickListener { onClick() }
    return b
}

/** A − value + control. */
fun Context.stepper(
    value: Int,
    min: Int,
    max: Int,
    format: (Int) -> String,
    onChange: (Int) -> Unit
): LinearLayout {
    var v = value
    val row = hbox()
    val label = tv(format(v), 18f, bold = true)
    label.gravity = Gravity.CENTER
    val minus = circleBtn("−") {
        if (v > min) { v--; label.text = format(v); onChange(v) }
    }
    minus.contentDescription = "Less"
    val plus = circleBtn("+") {
        if (v < max) { v++; label.text = format(v); onChange(v) }
    }
    plus.contentDescription = "More"
    row.add(minus, dp(44), dp(44))
    row.add(label, dp(72), WRAP)
    row.add(plus, dp(44), dp(44))
    return row
}

fun LinearLayout.add(
    v: View,
    w: Int = MATCH,
    h: Int = WRAP,
    top: Int = 0,
    weight: Float = 0f,
    gravity: Int = -1
): View {
    val lp = LinearLayout.LayoutParams(w, h, weight)
    lp.topMargin = top
    if (gravity != -1) lp.gravity = gravity
    addView(v, lp)
    return v
}

fun Context.divider(color: Int = Col.DIVIDER): View {
    val d = View(this)
    d.setBackgroundColor(color)
    return d
}

/** A little coloured capsule, used as a tablet dot. */
fun Context.dot(color: Int): View {
    val d = View(this)
    d.background = round(color, 99)
    return d
}

fun Context.badge(text: String, bg: Int, fg: Int): TextView {
    val t = tv(text, 14f, fg, bold = true)
    t.background = round(bg, 99)
    t.setPadding(dp(10), dp(4), dp(10), dp(4))
    return t
}
