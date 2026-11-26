package com.khader.phoneagent

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

object Dev {
    const val NAME = "Khader Abo Salha"
    const val EMAIL = "mankhader@gmail.com"
}

abstract class Page(val act: MainActivity) {
    abstract val view: View
    open fun onShow() {}
    open fun onHide() {}
    protected fun dp(v: Int): Int = act.dp(v)
}

class MainActivity : Activity() {
    lateinit var prefs: Prefs
    private lateinit var content: FrameLayout
    private val pages = ArrayList<Page>()
    private val navs = ArrayList<TextView>()
    private var cur = -1

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        prefs = Prefs(this)
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(C.BG)

        val head = LinearLayout(this)
        head.orientation = LinearLayout.VERTICAL
        head.setPadding(dp(18), dp(14), dp(18), dp(8))
        head.addView(tv("Phone Agent", 22f, C.ACCENT, true))
        head.addView(tv("مدير الجوال الذكي  •  by ${Dev.NAME}", 12f, C.MUTED))
        root.addView(head)

        content = FrameLayout(this)
        root.addView(content, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        val nav = LinearLayout(this)
        nav.orientation = LinearLayout.HORIZONTAL
        nav.setBackgroundColor(C.CARD)
        val labels = listOf("🏠\nالرئيسية", "🧹\nالتنظيف", "🤖\nالوكيل", "⚙️\nالإعدادات")
        for (i in labels.indices) {
            val t = tv(labels[i], 11f, C.MUTED)
            t.gravity = Gravity.CENTER
            t.setPadding(0, dp(8), 0, dp(8))
            t.setOnClickListener { show(i) }
            nav.addView(t, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            navs.add(t)
        }
        root.addView(nav)
        setContentView(root)

        pages.add(DashboardPage(this))
        pages.add(CleanPage(this))
        pages.add(AgentPage(this))
        pages.add(SettingsPage(this))
        show(0)
    }

    override fun onResume() {
        super.onResume()
        if (cur >= 0) pages[cur].onShow()
    }

    override fun onPause() {
        super.onPause()
        if (cur >= 0) pages[cur].onHide()
    }

    fun show(i: Int) {
        if (cur >= 0) pages[cur].onHide()
        cur = i
        content.removeAllViews()
        content.addView(pages[i].view)
        pages[i].onShow()
        for (k in navs.indices) navs[k].setTextColor(if (k == i) C.ACCENT else C.MUTED)
    }

    fun agentAsk(text: String) {
        show(2)
        (pages[2] as AgentPage).ask(text)
    }

    fun runAction(a: String) {
        when (a) {
            "boost" -> (pages[0] as DashboardPage).boost()
            "clean_cache" -> (pages[0] as DashboardPage).clearCache()
            "scan" -> { show(1); (pages[1] as CleanPage).scan() }
            "organize" -> { show(1); (pages[1] as CleanPage).organize() }
            "storage_settings" -> openSettings(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)
            "battery" -> openSettings(Settings.ACTION_BATTERY_SAVER_SETTINGS)
        }
    }

    fun openSettings(action: String) {
        try {
            startActivity(Intent(action))
        } catch (e: Exception) {
            try { startActivity(Intent(Settings.ACTION_SETTINGS)) } catch (e2: Exception) { }
        }
    }

    fun openUrl(url: String) {
        try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } catch (e: Exception) { }
    }

    fun ensureAccess() {
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:$packageName")))
            } catch (e: Exception) {
                openSettings(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
            }
        } else {
            requestPermissions(
                arrayOf(
                    android.Manifest.permission.READ_EXTERNAL_STORAGE,
                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                ), 1
            )
        }
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<out String>, res: IntArray) {
        super.onRequestPermissionsResult(code, perms, res)
        if (cur >= 0) pages[cur].onShow()
    }

    fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    fun msg(title: String, text: String) {
        AlertDialog.Builder(this).setTitle(title).setMessage(text).setPositiveButton("حسناً", null).show()
    }

    fun confirm(title: String, text: String, ok: () -> Unit) {
        AlertDialog.Builder(this).setTitle(title).setMessage(text)
            .setPositiveButton("نعم، نفّذ") { _, _ -> ok() }
            .setNegativeButton("إلغاء", null).show()
    }
}
