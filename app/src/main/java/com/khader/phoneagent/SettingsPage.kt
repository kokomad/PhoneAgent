package com.khader.phoneagent

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.text.InputType
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView

class SettingsPage(act: MainActivity) : Page(act) {
    private lateinit var keyBox: EditText
    private lateinit var modelInfo: TextView
    private lateinit var result: TextView
    private lateinit var sw: Switch

    override val view: View by lazy { build() }

    private fun build(): View {
        val sv = ScrollView(act)
        val col = LinearLayout(act)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(dp(16), dp(8), dp(16), dp(16))
        sv.addView(col)

        val k = act.card()
        k.addView(act.tv("🔑 مفتاح Gemini (مجاني)", 15f, C.TEXT, true))
        k.addView(act.tv("احصل على مفتاح مجاني من Google AI Studio، ثم الصقه هنا. يُحفظ على جهازك فقط.", 12f, C.MUTED))
        keyBox = EditText(act)
        keyBox.hint = "AIza..."
        keyBox.setHintTextColor(C.MUTED)
        keyBox.setTextColor(C.TEXT)
        keyBox.setText(act.prefs.key)
        keyBox.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        keyBox.background = act.bg(C.BG, 12, C.STROKE)
        keyBox.setPadding(dp(12), dp(10), dp(12), dp(10))
        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        lp.setMargins(0, dp(8), 0, dp(4))
        k.addView(keyBox, lp)
        k.addView(act.btn("💾 حفظ واختبار المفتاح") { saveAndTest() })
        k.addView(act.btn("🌐 الحصول على مفتاح مجاني", C.BLUE) { act.openUrl("https://aistudio.google.com/apikey") })
        result = act.tv("", 12f, C.ACCENT)
        k.addView(result)
        col.addView(k)

        val m = act.card()
        m.addView(act.tv("🧠 النموذج", 15f, C.TEXT, true))
        sw = Switch(act)
        sw.text = "اختيار النموذج تلقائياً حسب المفتاح"
        sw.setTextColor(C.TEXT)
        sw.isChecked = act.prefs.auto
        sw.setOnCheckedChangeListener { _, on ->
            act.prefs.auto = on
            updateModelInfo()
        }
        m.addView(sw)
        modelInfo = act.tv("", 12f, C.MUTED)
        m.addView(modelInfo)
        m.addView(act.btn("اختيار نموذج يدوياً", C.BLUE) { chooseModel() })
        col.addView(m)

        val p = act.card()
        p.addView(act.tv("🔐 الصلاحيات والتحسين", 15f, C.TEXT, true))
        p.addView(act.btn("الوصول إلى كل الملفات") { act.ensureAccess() })
        p.addView(act.btn("استثناء التطبيق من توفير البطارية", C.BLUE) {
            act.openSettings(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        })
        p.addView(act.btn("إعدادات التخزين", C.BLUE) { act.openSettings(Settings.ACTION_INTERNAL_STORAGE_SETTINGS) })
        p.addView(act.btn("إدارة التطبيقات", C.BLUE) { act.openSettings(Settings.ACTION_APPLICATION_SETTINGS) })
        col.addView(p)

        val a = act.card()
        a.addView(act.tv("ℹ️ حول التطبيق", 15f, C.TEXT, true))
        a.addView(act.tv("Phone Agent • مدير الجوال الذكي\nالإصدار ${versionName()}", 13f, C.MUTED))
        a.addView(act.tv("\nالمطوّر: ${Dev.NAME}", 14f, C.ACCENT, true))
        a.addView(act.btn("✉️ ${Dev.EMAIL}", C.BLUE) {
            try {
                act.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:${Dev.EMAIL}")))
            } catch (e: Exception) {
                act.toast(Dev.EMAIL)
            }
        })
        col.addView(a)

        updateModelInfo()
        return sv
    }

    private fun versionName(): String = try {
        act.packageManager.getPackageInfo(act.packageName, 0).versionName ?: "1.0.0"
    } catch (e: Exception) {
        "1.0.0"
    }

    private fun updateModelInfo() {
        modelInfo.text = if (act.prefs.auto) {
            val l = Gemini.lastModels
            if (l.isEmpty()) "تلقائي: سيُحدَّد أفضل نموذج مجاني متاح لمفتاحك عند أول استخدام."
            else "تلقائي ← الأنسب لمفتاحك: ${l[0]}  (${l.size} نموذجاً متاحاً)"
        } else {
            "يدوي: ${act.prefs.model}"
        }
    }

    private fun saveAndTest() {
        val key = keyBox.text.toString().trim()
        act.prefs.key = key
        if (key.isEmpty()) {
            result.text = "أدخل المفتاح أولاً."
            return
        }
        result.text = "جارٍ فحص المفتاح واكتشاف النماذج…"
        Thread {
            try {
                val l = Gemini.pick(key, true)
                act.runOnUiThread {
                    result.text = "✅ المفتاح صالح\nالنموذج المختار تلقائياً: ${l[0]}\nعدد النماذج المناسبة: ${l.size}"
                    updateModelInfo()
                }
            } catch (e: Gemini.Err) {
                act.runOnUiThread { result.text = "❌ ${e.message}" }
            } catch (e: Exception) {
                act.runOnUiThread { result.text = "❌ تعذّر الاتصال. تحقق من الإنترنت." }
            }
        }.start()
    }

    private fun chooseModel() {
        val l = Gemini.lastModels
        if (l.isEmpty()) {
            act.toast("اضغط «حفظ واختبار المفتاح» أولاً لجلب النماذج")
            return
        }
        AlertDialog.Builder(act).setTitle("اختر النموذج")
            .setItems(l.toTypedArray()) { _, i ->
                act.prefs.model = l[i]
                act.prefs.auto = false
                sw.isChecked = false
                updateModelInfo()
            }.show()
    }
}
