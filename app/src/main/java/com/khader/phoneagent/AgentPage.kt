package com.khader.phoneagent

import android.graphics.Color
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class AgentPage(act: MainActivity) : Page(act) {
    private val hist = ArrayList<Pair<String, String>>()
    private lateinit var list: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var input: EditText
    private lateinit var status: TextView
    private var busy = false

    private val system =
        "أنت «فون إيجنت»، المدير العام الذكي للجوال، وخبير في أداء أندرويد والذاكرة والرام والتخزين والبطارية والحرارة. " +
            "تصلك مع كل رسالة لقطة حيّة لحالة الجهاز. أجب بالعربية باختصار ووضوح وبخطوات عملية مرتبة، " +
            "ولا تخترع أرقاماً غير موجودة في اللقطة. لا تدّعِ أنك نفّذت شيئاً؛ التطبيق ينفّذ الإجراءات عندما يضغط المستخدم على زر. " +
            "إذا كان هناك إجراء مفيد داخل التطبيق فاختم ردك بسطر واحد فقط بالشكل ACTIONS: ثم أسماء مفصولة بفواصل " +
            "من هذه القائمة فقط: boost, scan, clean_cache, organize, storage_settings, battery. " +
            "لا تستخدم تنسيق ماركداون."

    private val labels = mapOf(
        "boost" to "⚡ تسريع الجوال",
        "scan" to "🔍 فحص التخزين",
        "clean_cache" to "🧹 مسح الكاش",
        "organize" to "🗂️ ترتيب التنزيلات",
        "storage_settings" to "📦 إعدادات التخزين",
        "battery" to "🔋 توفير الطاقة"
    )

    override val view: View by lazy { build() }

    private fun build(): View {
        val root = LinearLayout(act)
        root.orientation = LinearLayout.VERTICAL

        scroll = ScrollView(act)
        list = LinearLayout(act)
        list.orientation = LinearLayout.VERTICAL
        list.setPadding(dp(12), dp(8), dp(12), dp(8))
        scroll.addView(list)
        root.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        status = act.tv("", 11f, C.MUTED)
        status.setPadding(dp(14), 0, dp(14), dp(2))
        root.addView(status)

        val hs = HorizontalScrollView(act)
        hs.isHorizontalScrollBarEnabled = false
        val chips = LinearLayout(act)
        chips.orientation = LinearLayout.HORIZONTAL
        chips.setPadding(dp(10), dp(2), dp(10), dp(4))
        val qs = listOf("حلّل أداء جوالي", "لماذا الجوال بطيء؟", "ماذا أحذف لأوفر مساحة؟", "كيف أحسّن البطارية؟")
        for (q in qs) {
            val t = act.tv(q, 12f, C.ACCENT)
            t.background = act.bg(C.CARD, 20, C.STROKE)
            t.setPadding(dp(12), dp(8), dp(12), dp(8))
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.setMargins(dp(4), 0, dp(4), 0)
            t.setOnClickListener { ask(q) }
            chips.addView(t, lp)
        }
        hs.addView(chips)
        root.addView(hs)

        val row = LinearLayout(act)
        row.orientation = LinearLayout.HORIZONTAL
        row.setPadding(dp(10), dp(4), dp(10), dp(10))
        input = EditText(act)
        input.hint = "اسأل فون إيجنت…"
        input.setHintTextColor(C.MUTED)
        input.setTextColor(C.TEXT)
        input.textSize = 14f
        input.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        input.maxLines = 4
        input.background = act.bg(C.CARD, 22, C.STROKE)
        input.setPadding(dp(16), dp(10), dp(16), dp(10))
        row.addView(input, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        val send = Button(act)
        send.text = "إرسال"
        send.isAllCaps = false
        send.setTextColor(Color.parseColor("#04201A"))
        send.background = act.bg(C.ACCENT, 22)
        send.setOnClickListener {
            val t = input.text.toString().trim()
            if (t.isNotEmpty()) {
                input.setText("")
                ask(t)
            }
        }
        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(46))
        lp.setMargins(dp(8), 0, 0, 0)
        row.addView(send, lp)
        root.addView(row)

        bubble("أنا «فون إيجنت» 🤖 مدير جوالك الذكي. أراقب حالة الجهاز وأقترح عليك الحلول، ويمكنني تشغيل التسريع والتنظيف بضغطة زر. اسألني أو اختر اقتراحاً من الأسفل.", false)
        return root
    }

    private fun clean(s: String): String =
        s.replace("**", "").replace("__", "").replace(Regex("(?m)^\\s*#+\\s*"), "")
            .replace(Regex("(?m)^\\s*[*-]\\s+"), "• ")

    private fun bubble(text: String, user: Boolean) {
        val t = act.tv(text, 14f, if (user) Color.parseColor("#04201A") else C.TEXT)
        t.background = act.bg(if (user) C.ACCENT else C.CARD, 16, if (user) 0 else C.STROKE)
        t.setPadding(dp(12), dp(10), dp(12), dp(10))
        t.maxWidth = (act.resources.displayMetrics.widthPixels * 0.85).toInt()
        t.setLineSpacing(0f, 1.2f)
        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        lp.setMargins(0, dp(4), 0, dp(4))
        lp.gravity = if (user) Gravity.START else Gravity.END
        list.addView(t, lp)
        scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun actionButton(label: String, onClick: () -> Unit) {
        val b = act.btn(label, C.BLUE) { onClick() }
        list.addView(b)
        scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun friendly(e: Throwable): String = when {
        e is Gemini.Err && (e.code == 400 || e.code == 401 || e.code == 403) ->
            "المفتاح غير صالح أو غير مفعّل (${e.message}). تحقق منه في الإعدادات."
        e is Gemini.Err && e.code == 429 -> "تم تجاوز الحصة المجانية للمفتاح حالياً. حاول بعد قليل."
        e is Gemini.Err -> e.message ?: "خطأ من الخدمة"
        else -> "تعذّر الاتصال. تحقق من الإنترنت ثم حاول مجدداً."
    }

    fun ask(text: String) {
        view
        if (busy) return
        bubble(text, true)
        val key = act.prefs.effectiveKey()
        if (key.isEmpty()) {
            bubble("أضف مفتاح Gemini أولاً من تبويب الإعدادات (المفتاح مجاني من Google AI Studio).", false)
            actionButton("⚙️ فتح الإعدادات") { act.show(3) }
            return
        }
        busy = true
        status.text = "الوكيل يفكر…"
        hist.add(Pair("user", text))
        val h = hist.takeLast(10).toMutableList()
        while (h.isNotEmpty() && h[0].first == "model") h.removeAt(0)
        val snap = SystemInfo.snap(act).toPrompt()
        val msgs = h.mapIndexed { i, p ->
            if (i == h.lastIndex) Pair(p.first, "[حالة الجهاز الآن]\n$snap\n\n[طلب المستخدم]\n${p.second}") else p
        }
        Thread {
            try {
                var used = ""
                val raw = Gemini.ask(key, act.prefs.auto, act.prefs.model, system, msgs) { used = it }
                val rx = Regex("(?m)^\\s*ACTIONS:\\s*(.+)$")
                val actions = rx.find(raw)?.groupValues?.get(1)?.split(",")
                    ?.map { it.trim().lowercase() }?.filter { labels.containsKey(it) } ?: emptyList()
                val reply = clean(raw.replace(rx, "")).trim().ifEmpty { "تم." }
                hist.add(Pair("model", reply))
                act.runOnUiThread {
                    bubble(reply, false)
                    for (a in actions) actionButton(labels[a] ?: a) { act.runAction(a) }
                    status.text = "النموذج: $used"
                    busy = false
                }
            } catch (e: Throwable) {
                if (hist.isNotEmpty() && hist.last().first == "user") hist.removeAt(hist.size - 1)
                act.runOnUiThread {
                    bubble("⚠️ " + friendly(e), false)
                    status.text = ""
                    busy = false
                }
            }
        }.start()
    }
}
