package com.khader.phoneagent

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.SystemClock
import java.util.Locale

class Snap(
    val ramTotal: Long, val ramFree: Long,
    val stTotal: Long, val stFree: Long,
    val batt: Int, val battTemp: Float, val charging: Boolean,
    val cores: Int, val model: String, val android: String,
    val uptimeMin: Long, val lowMem: Boolean
) {
    val ramUsed: Float get() = if (ramTotal > 0) 1f - ramFree.toFloat() / ramTotal else 0f
    val stUsed: Float get() = if (stTotal > 0) 1f - stFree.toFloat() / stTotal else 0f

    fun score(): Int {
        var s = 100f - ramUsed * 40f - stUsed * 30f
        if (battTemp > 40f) s -= 15f
        if (lowMem) s -= 10f
        if (uptimeMin > 60 * 24 * 5) s -= 5f
        return s.toInt().coerceIn(0, 100)
    }

    fun toPrompt(): String =
        "الجهاز: $model | أندرويد $android | أنوية المعالج: $cores\n" +
        "الرام: المستخدم ${(ramUsed * 100).toInt()}% (متاح ${SystemInfo.fmt(ramFree)} من ${SystemInfo.fmt(ramTotal)})" +
        (if (lowMem) " — النظام يبلّغ عن ذاكرة منخفضة" else "") + "\n" +
        "التخزين: المستخدم ${(stUsed * 100).toInt()}% (متاح ${SystemInfo.fmt(stFree)} من ${SystemInfo.fmt(stTotal)})\n" +
        "البطارية: $batt% ${if (charging) "(تشحن)" else ""} | حرارة ${"%.1f".format(Locale.US, battTemp)}°C\n" +
        "مدة التشغيل منذ آخر إعادة تشغيل: ${uptimeMin / 60} ساعة\n" +
        "درجة الصحة العامة: ${score()}/100"
}

object SystemInfo {
    fun fmt(b: Long): String {
        if (b < 1024) return "$b B"
        val u = arrayOf("KB", "MB", "GB", "TB")
        var v = b.toDouble()
        var i = -1
        while (v >= 1024 && i < u.size - 1) {
            v /= 1024
            i++
        }
        return String.format(Locale.US, "%.1f %s", v, u[i])
    }

    fun snap(c: Context): Snap {
        val am = c.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        val sf = StatFs(Environment.getDataDirectory().path)
        val bi: Intent? = c.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val lvl = bi?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = bi?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val temp = (bi?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10f
        val st = bi?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val pct = if (lvl >= 0 && scale > 0) lvl * 100 / scale else 0
        val charging = st == BatteryManager.BATTERY_STATUS_CHARGING || st == BatteryManager.BATTERY_STATUS_FULL
        return Snap(
            mi.totalMem, mi.availMem,
            sf.blockCountLong * sf.blockSizeLong, sf.availableBlocksLong * sf.blockSizeLong,
            pct, temp, charging,
            Runtime.getRuntime().availableProcessors(),
            "${Build.MANUFACTURER} ${Build.MODEL}", Build.VERSION.RELEASE,
            SystemClock.elapsedRealtime() / 60000L, mi.lowMemory
        )
    }
}
