package com.khader.phoneagent

import android.app.ActivityManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest

class ScanResult(
    val junk: List<File>, val junkSize: Long,
    val empty: List<File>,
    val dups: List<List<File>>, val dupSize: Long,
    val large: List<File>, val scanned: Int
)

object Cleaner {
    private val junkExt = setOf("tmp", "temp", "log", "bak", "old", "chk", "thumbdata", "crdownload", "part", "dmp")
    private val junkDirs = setOf(".thumbnails", "thumbnails", ".cache", "cache", "temp", "tmp", ".temp", "lost.dir")

    private val types = linkedMapOf(
        "صور" to setOf("jpg", "jpeg", "png", "gif", "webp", "heic", "bmp"),
        "فيديو" to setOf("mp4", "mkv", "3gp", "avi", "mov", "webm"),
        "صوتيات" to setOf("mp3", "m4a", "wav", "ogg", "aac", "flac", "opus"),
        "مستندات" to setOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "csv", "epub", "md"),
        "أرشيف" to setOf("zip", "rar", "7z", "tar", "gz"),
        "تطبيقات" to setOf("apk", "xapk", "apks")
    )

    fun hasAccess(c: Context): Boolean =
        if (Build.VERSION.SDK_INT >= 30) Environment.isExternalStorageManager()
        else c.checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

    private class St(val progress: (String) -> Unit) {
        val junk = ArrayList<File>()
        var junkSize = 0L
        val empty = ArrayList<File>()
        val bySize = HashMap<Long, MutableList<File>>()
        val large = ArrayList<File>()
        var n = 0
    }

    /** Returns true when [dir] holds no files (only empty folders). */
    private fun walk(dir: File, depth: Int, st: St, inJunk: Boolean): Boolean {
        val list = dir.listFiles() ?: return false
        if (list.isEmpty()) {
            if (depth > 0) st.empty.add(dir)
            return true
        }
        var allEmpty = true
        for (f in list) {
            if (f.isDirectory) {
                if (depth == 0 && f.name == "Android") {
                    allEmpty = false
                    continue
                }
                if (depth < 14) {
                    val j = inJunk || f.name.lowercase() in junkDirs
                    if (!walk(f, depth + 1, st, j)) allEmpty = false
                } else {
                    allEmpty = false
                }
            } else {
                allEmpty = false
                val len = f.length()
                st.n++
                if (st.n % 400 == 0) st.progress("تم فحص ${st.n} ملف…")
                val ext = f.extension.lowercase()
                val isJunk = inJunk || f.name.endsWith("~") ||
                    (f.name != ".nomedia" && (len == 0L || ext in junkExt))
                if (isJunk) {
                    st.junk.add(f)
                    st.junkSize += len
                } else {
                    if (len >= 10240L) st.bySize.getOrPut(len) { ArrayList() }.add(f)
                    if (len >= 100L * 1024 * 1024) st.large.add(f)
                }
            }
        }
        if (allEmpty && depth > 0) {
            st.empty.removeAll(list.toSet())
            st.empty.add(dir)
        }
        return allEmpty
    }

    private fun hash(f: File): String {
        val md = MessageDigest.getInstance("MD5")
        val buf = ByteArray(65536)
        RandomAccessFile(f, "r").use { raf ->
            val len = raf.length()
            if (len <= 32L * 1024 * 1024) {
                var r = raf.read(buf)
                while (r > 0) {
                    md.update(buf, 0, r)
                    r = raf.read(buf)
                }
            } else {
                for (pos in longArrayOf(0L, len / 2, len - 262144L)) {
                    raf.seek(pos)
                    var left = 262144
                    while (left > 0) {
                        val r = raf.read(buf, 0, minOf(buf.size, left))
                        if (r <= 0) break
                        md.update(buf, 0, r)
                        left -= r
                    }
                }
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    fun scan(progress: (String) -> Unit): ScanResult {
        val st = St(progress)
        walk(Environment.getExternalStorageDirectory(), 0, st, false)
        progress("فحص الملفات المكررة…")
        val dups = ArrayList<List<File>>()
        var dupSize = 0L
        for ((size, files) in st.bySize) {
            if (files.size < 2) continue
            val groups = HashMap<String, MutableList<File>>()
            for (f in files) {
                val h = try { hash(f) } catch (e: Exception) { continue }
                groups.getOrPut(h) { ArrayList() }.add(f)
            }
            for (g in groups.values) {
                if (g.size > 1) {
                    g.sortBy { it.lastModified() }
                    dups.add(g)
                    dupSize += size * (g.size - 1)
                }
            }
        }
        val large = st.large.sortedByDescending { it.length() }.take(8)
        return ScanResult(st.junk, st.junkSize, st.empty, dups, dupSize, large, st.n)
    }

    fun delete(c: Context, files: List<File>): Pair<Int, Long> {
        var n = 0
        var sz = 0L
        val paths = ArrayList<String>()
        for (f in files) {
            val l = f.length()
            if (f.exists() && f.delete()) {
                n++
                sz += l
                paths.add(f.absolutePath)
            }
        }
        if (paths.isNotEmpty()) {
            try { MediaScannerConnection.scanFile(c, paths.toTypedArray(), null, null) } catch (e: Exception) { }
        }
        return Pair(n, sz)
    }

    fun deleteEmpty(dirs: List<File>): Int {
        var n = 0
        for (d in dirs.sortedByDescending { it.path.length }) {
            if (d.exists() && d.deleteRecursively()) n++
        }
        return n
    }

    fun organize(dry: Boolean): LinkedHashMap<String, Int> {
        val out = LinkedHashMap<String, Int>()
        val dl = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val files = dl.listFiles() ?: return out
        for (f in files) {
            if (!f.isFile) continue
            val ext = f.extension.lowercase()
            val t = types.entries.firstOrNull { ext in it.value }?.key ?: continue
            out[t] = (out[t] ?: 0) + 1
            if (!dry) {
                val dir = File(dl, t)
                dir.mkdirs()
                var dest = File(dir, f.name)
                var i = 1
                while (dest.exists()) {
                    dest = File(dir, "${f.nameWithoutExtension} ($i).${f.extension}")
                    i++
                }
                f.renameTo(dest)
            }
        }
        return out
    }

    private fun size(f: File): Long =
        if (f.isFile) f.length() else (f.listFiles()?.fold(0L) { a, x -> a + size(x) } ?: 0L)

    fun clearOwnCache(c: Context): Long {
        var s = 0L
        for (d in listOfNotNull(c.cacheDir, c.externalCacheDir)) {
            s += size(d)
            d.listFiles()?.forEach { it.deleteRecursively() }
        }
        return s
    }

    private fun avail(c: Context): Long {
        val am = c.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        return mi.availMem
    }

    /** Stops background processes of installed user apps. Returns (count, availBefore, availAfter). */
    fun boost(c: Context): Triple<Int, Long, Long> {
        val before = avail(c)
        val am = c.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        var n = 0
        for (ai in c.packageManager.getInstalledApplications(0)) {
            if (ai.packageName == c.packageName) continue
            if ((ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0) continue
            try {
                am.killBackgroundProcesses(ai.packageName)
                n++
            } catch (e: Exception) { }
        }
        Thread.sleep(1200)
        return Triple(n, before, avail(c))
    }
}
