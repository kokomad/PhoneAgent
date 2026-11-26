package com.khader.phoneagent

import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.io.File

class CleanPage(act: MainActivity) : Page(act) {
    private lateinit var permCard: LinearLayout
    private lateinit var status: TextView
    private lateinit var results: LinearLayout
    private var res: ScanResult? = null
    private var busy = false

    override val view: View by lazy { build() }

    private fun build(): View {
        val sv = ScrollView(act)
        val col = LinearLayout(act)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(dp(16), dp(8), dp(16), dp(16))
        sv.addView(col)

        permCard = act.card()
        permCard.addView(act.tv("🔐 صلاحية الوصول للملفات", 15f, C.WARN, true))
        permCard.addView(act.tv("لفحص التخزين وتنظيفه يحتاج التطبيق إذن «الوصول إلى كل الملفات».", 12f, C.MUTED))
        permCard.addView(act.btn("منح الصلاحية") { act.ensureAccess() })
        col.addView(permCard)

        val c = act.card()
        c.addView(act.tv("🔍 فحص ذكي للتخزين", 15f, C.TEXT, true))
        c.addView(act.tv("يبحث عن ملفات مؤقتة وسجلات، مجلدات فارغة، ملفات مكررة، وملفات كبيرة.", 12f, C.MUTED))
        status = act.tv("", 12f, C.ACCENT)
        c.addView(act.btn("ابدأ الفحص") { scan() })
        c.addView(status)
        c.addView(act.btn("🗂️ ترتيب مجلد التنزيلات", C.BLUE) { organize() })
        col.addView(c)

        results = LinearLayout(act)
        results.orientation = LinearLayout.VERTICAL
        col.addView(results)
        return sv
    }

    override fun onShow() {
        view
        permCard.visibility = if (Cleaner.hasAccess(act)) View.GONE else View.VISIBLE
    }

    fun scan() {
        if (!Cleaner.hasAccess(act)) {
            act.ensureAccess()
            return
        }
        if (busy) return
        busy = true
        status.text = "جارٍ الفحص…"
        Thread {
            try {
                val r = Cleaner.scan { p -> act.runOnUiThread { status.text = p } }
                res = r
                act.runOnUiThread {
                    status.text = "اكتمل الفحص: ${r.scanned} ملف"
                    render()
                }
            } catch (e: Exception) {
                act.runOnUiThread { status.text = "تعذّر الفحص: ${e.message}" }
            }
            busy = false
        }.start()
    }

    private fun render() {
        val r = res ?: return
        results.removeAllViews()

        val j = act.card()
        j.addView(act.tv("🗑️ ملفات مؤقتة ومخلّفات: ${r.junk.size}", 14f, C.TEXT, true))
        j.addView(act.tv("المساحة: ${SystemInfo.fmt(r.junkSize)}", 12f, C.MUTED))
        if (r.junk.isNotEmpty()) j.addView(act.btn("حذف المخلّفات") {
            act.confirm("حذف المخلّفات؟", "سيتم حذف ${r.junk.size} ملفاً (${SystemInfo.fmt(r.junkSize)}). لا يمكن التراجع.") {
                Thread {
                    val d = Cleaner.delete(act, r.junk)
                    act.runOnUiThread {
                        act.toast("حُذف ${d.first} ملفاً وتحرر ${SystemInfo.fmt(d.second)}")
                        scan()
                    }
                }.start()
            }
        })
        results.addView(j)

        val e = act.card()
        e.addView(act.tv("📁 مجلدات فارغة: ${r.empty.size}", 14f, C.TEXT, true))
        if (r.empty.isNotEmpty()) e.addView(act.btn("حذف المجلدات الفارغة", C.BLUE) {
            act.confirm("حذف المجلدات الفارغة؟", "سيتم حذف ${r.empty.size} مجلداً فارغاً.") {
                Thread {
                    val n = Cleaner.deleteEmpty(r.empty)
                    act.runOnUiThread {
                        act.toast("حُذف $n مجلداً")
                        scan()
                    }
                }.start()
            }
        })
        results.addView(e)

        val d = act.card()
        d.addView(act.tv("👯 مجموعات ملفات مكررة: ${r.dups.size}", 14f, C.TEXT, true))
        d.addView(act.tv("المساحة المهدورة: ${SystemInfo.fmt(r.dupSize)}  (يُبقى الأقدم من كل مجموعة)", 12f, C.MUTED))
        if (r.dups.isNotEmpty()) d.addView(act.btn("حذف النسخ المكررة", C.WARN) {
            val victims: List<File> = r.dups.flatMap { it.drop(1) }
            act.confirm("حذف المكررات؟", "سيتم حذف ${victims.size} نسخة (${SystemInfo.fmt(r.dupSize)}) وإبقاء نسخة واحدة من كل ملف.") {
                Thread {
                    val x = Cleaner.delete(act, victims)
                    act.runOnUiThread {
                        act.toast("حُذف ${x.first} ملفاً وتحرر ${SystemInfo.fmt(x.second)}")
                        scan()
                    }
                }.start()
            }
        })
        results.addView(d)

        if (r.large.isNotEmpty()) {
            val l = act.card()
            l.addView(act.tv("📦 أكبر الملفات (اضغط لحذف ملف)", 14f, C.TEXT, true))
            for (f in r.large) {
                val t = act.tv("${SystemInfo.fmt(f.length())}  •  ${f.name}", 12f, C.MUTED)
                t.setPadding(0, dp(8), 0, dp(8))
                t.setOnClickListener {
                    act.confirm("حذف الملف؟", "${f.absolutePath}\n${SystemInfo.fmt(f.length())}") {
                        Thread {
                            Cleaner.delete(act, listOf(f))
                            act.runOnUiThread { scan() }
                        }.start()
                    }
                }
                l.addView(t)
            }
            results.addView(l)
        }
    }

    fun organize() {
        if (!Cleaner.hasAccess(act)) {
            act.ensureAccess()
            return
        }
        Thread {
            val plan = Cleaner.organize(true)
            act.runOnUiThread {
                if (plan.isEmpty()) {
                    act.msg("🗂️ ترتيب التنزيلات", "لا توجد ملفات تحتاج ترتيباً في مجلد التنزيلات.")
                } else {
                    val s = plan.entries.joinToString("\n") { "• ${it.key}: ${it.value} ملف" }
                    act.confirm("ترتيب التنزيلات؟", "سيتم نقل الملفات إلى مجلدات حسب النوع:\n$s") {
                        Thread {
                            Cleaner.organize(false)
                            act.runOnUiThread { act.toast("تم ترتيب مجلد التنزيلات ✅") }
                        }.start()
                    }
                }
            }
        }.start()
    }
}
