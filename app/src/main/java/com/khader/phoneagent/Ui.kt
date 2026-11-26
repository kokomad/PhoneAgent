package com.khader.phoneagent

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

object C {
    val BG = Color.parseColor("#0B1220")
    val CARD = Color.parseColor("#131C2E")
    val STROKE = Color.parseColor("#22304A")
    val TEXT = Color.parseColor("#E8EEF9")
    val MUTED = Color.parseColor("#8A98B3")
    val ACCENT = Color.parseColor("#2DE2B0")
    val BLUE = Color.parseColor("#4DA3FF")
    val WARN = Color.parseColor("#FFB020")
    val BAD = Color.parseColor("#FF5C6C")
}

fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

fun Context.tv(text: String, size: Float = 14f, color: Int = C.TEXT, bold: Boolean = false): TextView {
    val t = TextView(this)
    t.text = text
    t.textSize = size
    t.setTextColor(color)
    if (bold) t.setTypeface(Typeface.DEFAULT_BOLD)
    return t
}

fun Context.bg(color: Int, radius: Int = 16, stroke: Int = 0): GradientDrawable {
    val g = GradientDrawable()
    g.setColor(color)
    g.cornerRadius = dp(radius).toFloat()
    if (stroke != 0) g.setStroke(dp(1), stroke)
    return g
}

fun Context.card(): LinearLayout {
    val l = LinearLayout(this)
    l.orientation = LinearLayout.VERTICAL
    l.background = bg(C.CARD, 18, C.STROKE)
    l.setPadding(dp(14), dp(14), dp(14), dp(14))
    val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
    lp.setMargins(0, 0, 0, dp(12))
    l.layoutParams = lp
    return l
}

fun Context.btn(text: String, color: Int = C.ACCENT, onClick: () -> Unit): Button {
    val b = Button(this)
    b.text = text
    b.isAllCaps = false
    b.textSize = 14f
    b.setTextColor(Color.parseColor("#04201A"))
    b.setTypeface(Typeface.DEFAULT_BOLD)
    b.background = bg(color, 14)
    b.setOnClickListener { onClick() }
    val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(48))
    lp.setMargins(0, dp(4), 0, dp(4))
    b.layoutParams = lp
    return b
}

class Gauge(c: Context) : View(c) {
    var value: Float = 0f
        set(v) {
            field = v.coerceIn(0f, 1f)
            invalidate()
        }
    var color: Int = C.ACCENT
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val r = RectF()

    override fun onDraw(cv: Canvas) {
        val stroke = width * 0.12f
        val d = minOf(width, height).toFloat() - stroke
        val l = (width - d) / 2f
        val t = (height - d) / 2f
        r.set(l, t, l + d, t + d)
        p.style = Paint.Style.STROKE
        p.strokeWidth = stroke
        p.strokeCap = Paint.Cap.ROUND
        p.color = C.STROKE
        cv.drawArc(r, 135f, 270f, false, p)
        p.color = color
        if (value > 0.005f) cv.drawArc(r, 135f, 270f * value, false, p)
        p.style = Paint.Style.FILL
        p.color = C.TEXT
        p.textAlign = Paint.Align.CENTER
        p.textSize = d * 0.28f
        p.typeface = Typeface.DEFAULT_BOLD
        cv.drawText("${(value * 100).toInt()}%", width / 2f, height / 2f + p.textSize * 0.35f, p)
    }
}
