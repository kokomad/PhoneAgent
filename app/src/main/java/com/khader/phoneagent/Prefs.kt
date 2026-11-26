package com.khader.phoneagent

import android.content.Context

class Prefs(c: Context) {
    private val sp = c.getSharedPreferences("agent", Context.MODE_PRIVATE)

    var key: String
        get() = sp.getString("key", "") ?: ""
        set(v) { sp.edit().putString("key", v.trim()).apply() }

    var auto: Boolean
        get() = sp.getBoolean("auto", true)
        set(v) { sp.edit().putBoolean("auto", v).apply() }

    var model: String
        get() = sp.getString("model", "gemini-2.5-flash") ?: "gemini-2.5-flash"
        set(v) { sp.edit().putString("model", v).apply() }

    fun effectiveKey(): String = if (key.isNotEmpty()) key else BuildConfig.DEFAULT_GEMINI_KEY
}
