package com.khader.phoneagent

import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class DashboardPage(act: MainActivity) : Page(act) {
    private val h = Handler(Looper.getMainLooper())
    private var running = false
    private lateinit var gRam: Gauge
    private lateinit var gSt: Gauge
    private lateinit var gBat: Gauge
    private lateinit var score: TextView
    private lateinit var alert: TextView
    private lateinit var info: TextView

    private val tick = object : Runnable {
        override fun run() {
            if (!running) return
            refresh()
            h.postDelayed(this, 2000)
        }
    }

    override val view: View by lazy { build() }

    private fun build(): View {
        val sv = ScrollView(act)
        val col = LinearLayout(act)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(dp(16), dp(8), dp(16), dp(16))
        sv.addView(col)

        val sc = act.card()
        score = act.tv("", 30f, C.ACCENT, true)
        score.gravity = Gravity.CENTER
        alert = act.tv("", 13f, C.WARN)
        alert.gravity = Gravity.CENTER
        sc.addView(act.tv("صحة الجوال", 13f, C.MUTED).also { it.gravity = Gravity.CENTER })
        sc.addView(score)
        sc.addView(alert)
        col.addView(sc)

        val gc = act.card()
        val row = LinearLayout(act)
        row.orientation = LinearLayout.HORIZONTAL
        gRam = Gauge(act)
        gSt = Gauge(act)
        gBat = Gauge(act)
        gSt.color = C.BLUE
        gBat.color = C.WARN
        val names = listOf("الرام", "التخزين", "البطارية")
        val gs = listOf(gRam, gSt, gBat)
        for (i in gs.indices) {
            val c = LinearLayout(act)
            c.orientation = LinearLayout.VERTICAL
            c.gravity = Gravity.CENTER_HORIZONTAL
            c.addView(gs[i], LinearLayout.LayoutParams(dp(96), dp(96)))
            c.addView(act.tv(names[i], 12f, C.MUTED).also { it.gravity = Gravity.CENTER })
            row.addView(c, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
        gc.addView(row)
        col.addView(gc)

        val ic = act.card()
        info = act.tv("", 13f, C.TEXT)
        info.setLineSpacing(0f, 1.25f)
        ic.addView(info)
        col.addView(ic)

        val ac = act.card()
        ac.addView(act.btn("⚡ تسريع الجوال الآن") { boost() })
        ac.addView(act.btn("🧹 مسح الكاش", C.BLUE) { clearCache() })
        ac.addView(act.btn("🤖 تحليل ذكي بالذكاء الاصطناعي", C.WARN) {
            act.agentAsk("حلّل حالة جوالي الآن وأعطني خطة تحسين مرتبة حسب الأهمية.")
        })
        col.addView(ac)

        val f = act.tv("المطوّر: ${Dev.NAME}\n${Dev.EMAIL}", 11f, C.MUTED)
        f.gravity = Gravity.CENTER
        col.addView(f)
        return sv
    }

    private fun refresh() {
        val s = SystemInfo.snap(act)
        gRam.value = s.ramUsed
        gSt.value = s.stUsed
        gBat.value = s.batt / 100f
        gRam.color = if (s.ramUsed > 0.85f) C.BAD else if (s.ramUsed > 0.7f) C.WARN else C.ACCENT
        gSt.color = if (s.stUsed > 0.9f) C.BAD else C.BLUE
        val sc = s.score()
        score.text = "$sc / 100"
        score.setTextColor(if (sc >= 75) C.ACCENT else if (sc >= 50) C.WARN else C.BAD)
        alert.text = when {
            s.ramUsed > 0.85f -> "⚠️ الذاكرة شبه ممتلئة — جرّب التسريع"
            s.stUsed > 0.9f -> "⚠️ التخزين شبه ممتلئ — افتح تبويب التنظيف"
            s.battTemp > 40f -> "🌡️ حرارة البطارية مرتفعة"
            else -> "✅ الحالة جيدة"
        }
        info.text = "📱 ${s.model}\n🤖 أندرويد ${s.android}  •  ${s.cores} أنوية\n" +
            "🧠 الرام المتاحة: ${SystemInfo.fmt(s.ramFree)} من ${SystemInfo.fmt(s.ramTotal)}\n" +
            "💾 التخزين المتاح: ${SystemInfo.fmt(s.stFree)} من ${SystemInfo.fmt(s.stTotal)}\n" +
            "🔋 البطارية ${s.batt}%${if (s.charging) " (تشحن)" else ""}  •  ${"%.1f".format(s.battTemp)}°C\n" +
            "⏱️ منذ آخر تشغيل: ${s.uptimeMin / 60} ساعة"
    }

    fun boost() {
        act.toast("جارٍ التسريع…")
        Thread {
            val r = Cleaner.boost(act)
            act.runOnUiThread {
                act.msg(
                    "⚡ تم التسريع",
                    "أُوقفت العمليات الخلفية لـ ${r.first} تطبيقاً.\n" +
                        "الرام المتاحة: ${SystemInfo.fmt(r.second)} ← ${SystemInfo.fmt(r.third)}\n\n" +
                        "ملاحظة: أندرويد قد يعيد تشغيل بعض التطبيقات تلقائياً."
                )
                refresh()
            }
        }.start()
    }

    fun clearCache() {
        Thread {
            val n = Cleaner.clearOwnCache(act)
            act.runOnUiThread {
                act.msg(
                    "🧹 الكاش",
                    "تم مسح كاش التطبيق (${SystemInfo.fmt(n)}).\n\n" +
                        "أندرويد لا يسمح لأي تطبيق بمسح كاش التطبيقات الأخرى مباشرة. لمسحه: الإعدادات ← التخزين ← الملفات المؤقتة، أو استخدم تبويب التنظيف لحذف ملفات المخلفات من التخزين."
                )
            }
        }.start()
    }

    override fun onShow() {
        running = true
        h.removeCallbacks(tick)
        h.post(tick)
    }

    override fun onHide() {
        running = false
        h.removeCallbacks(tick)
    }
}
