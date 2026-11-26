package com.khader.phoneagent

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object Gemini {
    private const val BASE = "https://generativelanguage.googleapis.com/v1beta"

    class Err(val code: Int, message: String) : Exception(message)

    @Volatile private var cacheKey = ""
    @Volatile private var cacheModels: List<String> = emptyList()
    val lastModels: List<String> get() = cacheModels

    private fun call(method: String, url: String, key: String, body: String?): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.requestMethod = method
        c.connectTimeout = 15000
        c.readTimeout = 60000
        c.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        c.setRequestProperty("x-goog-api-key", key)
        if (body != null) {
            c.doOutput = true
            c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        }
        val code = c.responseCode
        val stream = if (code in 200..299) c.inputStream else c.errorStream
        val txt = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
        if (code !in 200..299) throw Err(code, parseErr(txt))
        return txt
    }

    private fun parseErr(t: String): String = try {
        JSONObject(t).getJSONObject("error").getString("message")
    } catch (e: Exception) {
        t.take(200)
    }

    private fun listModels(key: String): List<String> {
        val out = ArrayList<String>()
        var token = ""
        for (page in 0 until 5) {
            val url = "$BASE/models?pageSize=100" + (if (token.isEmpty()) "" else "&pageToken=$token")
            val j = JSONObject(call("GET", url, key, null))
            val arr = j.optJSONArray("models") ?: break
            for (i in 0 until arr.length()) {
                val m = arr.getJSONObject(i)
                val methods = m.optJSONArray("supportedGenerationMethods")
                var ok = false
                if (methods != null) {
                    for (k in 0 until methods.length()) {
                        if (methods.getString(k) == "generateContent") ok = true
                    }
                }
                if (ok) out.add(m.getString("name").removePrefix("models/"))
            }
            token = j.optString("nextPageToken", "")
            if (token.isEmpty()) break
        }
        return out
    }

    private val order = listOf(
        Regex("^gemini-2\\.5-flash$"),
        Regex("^gemini-flash-latest$"),
        Regex("^gemini-2\\.0-flash$"),
        Regex("^gemini-2\\.5-flash-lite$"),
        Regex("^gemini-flash-lite-latest$"),
        Regex("^gemini-2\\.0-flash-lite$"),
        Regex("^gemini-1\\.5-flash(-latest)?$"),
        Regex("^gemini-.*-flash$"),
        Regex("^gemini-.*-flash-lite$"),
        Regex("^gemini-.*-flash"),
        Regex("^gemini-.*-pro$"),
        Regex("^gemini-")
    )
    private val bad = listOf(
        "tts", "image", "embedding", "live", "audio", "robotics",
        "computer-use", "learnlm", "aqa", "vision", "exp", "thinking"
    )

    /** Lower is better. 9000+ means unsuitable. */
    fun rank(id: String): Int {
        val s = id.lowercase()
        if (!s.startsWith("gemini")) return 9999
        if (bad.any { s.contains(it) }) return 9000
        for (i in order.indices) {
            if (order[i].containsMatchIn(s)) return i * 10 + (if (s.contains("preview")) 5 else 0)
        }
        return 500
    }

    /** Detects the models this key can use and ranks them (free-tier friendly flash models first). */
    fun pick(key: String, force: Boolean = false): List<String> {
        if (!force && cacheKey == key && cacheModels.isNotEmpty()) return cacheModels
        val ids = listModels(key)
        val good = ids.filter { rank(it) < 9000 }.sortedBy { rank(it) }
        if (good.isEmpty()) throw Err(0, "لا توجد نماذج Gemini متاحة لهذا المفتاح")
        cacheKey = key
        cacheModels = good
        return good
    }

    private fun generate(key: String, model: String, system: String, hist: List<Pair<String, String>>): String {
        val contents = JSONArray()
        for (p in hist) {
            contents.put(
                JSONObject().put("role", p.first)
                    .put("parts", JSONArray().put(JSONObject().put("text", p.second)))
            )
        }
        val body = JSONObject()
            .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
            .put("contents", contents)
            .put("generationConfig", JSONObject().put("temperature", 0.6).put("maxOutputTokens", 2048))
        val j = JSONObject(call("POST", "$BASE/models/$model:generateContent", key, body.toString()))
        val cands = j.optJSONArray("candidates")
        if (cands == null || cands.length() == 0) throw Err(0, "لم يصل رد من النموذج (قد يكون الطلب محجوباً)")
        val parts = cands.getJSONObject(0).optJSONObject("content")?.optJSONArray("parts")
        val sb = StringBuilder()
        if (parts != null) {
            for (i in 0 until parts.length()) sb.append(parts.getJSONObject(i).optString("text", ""))
        }
        return sb.toString().trim()
    }

    /** Sends the chat. In auto mode it falls back to the next suitable model on quota/availability errors. */
    fun ask(
        key: String, auto: Boolean, manual: String, system: String,
        hist: List<Pair<String, String>>, onModel: (String) -> Unit
    ): String {
        val models = if (auto) pick(key) else listOf(manual)
        var last: Err? = null
        for (m in models.take(6)) {
            try {
                val r = generate(key, m, system, hist)
                onModel(m)
                return r
            } catch (e: Err) {
                last = e
                if (e.code != 429 && e.code != 404 && e.code != 500 && e.code != 503) throw e
            }
        }
        throw last ?: Err(0, "تعذر الوصول إلى النموذج")
    }
}
