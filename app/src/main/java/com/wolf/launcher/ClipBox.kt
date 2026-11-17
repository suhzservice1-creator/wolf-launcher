package com.wolf.launcher

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

// The only file that knows about your ClipBox server.
// Until BASE is filled in, the home screen shows sample titles.
object ClipBox {
    const val BASE = ""          // e.g. "http://10.0.0.20:8080"
    const val PATH = "/continue" // TODO: the real endpoint for in-progress videos
    const val TOKEN = ""         // TODO: only if your server needs a login token

    fun continueWatching(): JSONArray {
        if (BASE.isEmpty()) return JSONArray()
        val c = URL(BASE + PATH).openConnection() as HttpURLConnection
        c.connectTimeout = 4000
        c.readTimeout = 4000
        if (TOKEN.isNotEmpty()) c.setRequestProperty("Authorization", "Bearer " + TOKEN)
        val src = JSONArray(c.inputStream.bufferedReader().use { it.readText() })
        val out = JSONArray()
        for (i in 0 until src.length()) out.put(map(src.getJSONObject(i)))
        return out
    }

    // TODO: match these field names to what your server sends.
    private fun map(o: JSONObject) = JSONObject()
        .put("title", o.optString("title"))
        .put("meta", o.optString("type") + " · " + o.optString("year"))
        .put("l1", o.optString("genre"))
        .put("l2", o.optString("timeLeft"))
        .put("p", o.optInt("progress"))
        .put("img", if (o.has("poster")) BASE + o.getString("poster") else "")
}
