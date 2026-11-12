package dev.iptv

import android.net.Uri
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

data class Account(val server: String, val user: String, val pass: String)
data class Category(val id: String, val name: String) {
    private val match = COUNTRY_PREFIX.matchEntire(name)
    /** Länderkürzel aus "PT | Desporto" → "PT". */
    val prefix: String? = match?.groupValues?.get(1)
    val label: String = match?.groupValues?.get(2) ?: name
}

data class Channel(val id: Int, val num: Int, val name: String, val logo: String?, val categoryId: String) {
    /** Qualitätsangabe am Namensende: "SPORT TV 1 FHD" → "FHD". */
    val quality: String? = QUALITY_SUFFIX.find(name)?.groupValues?.get(1)?.uppercase()
    val label: String = name.replace(QUALITY_SUFFIX, "").trim().ifEmpty { name }
}

private val COUNTRY_PREFIX = Regex("""^\s*([A-Z]{2,3})\s*[|:]\s*(.+?)\s*$""")
private val QUALITY_SUFFIX = Regex("""\s+[\[(]?(UHD|4K|FHD|HD|SD|LOW|HEVC)[\])]?\s*$""", RegexOption.IGNORE_CASE)
data class Programme(val title: String, val start: Long, val end: Long)

/** Xtream-Codes player_api. Doku gibt es keine offizielle; Felder kommen je nach Panel als String oder Zahl. */
class Xtream(val account: Account) {
    private val base = account.server.trim().trimEnd('/').let { if ("://" in it) it else "http://$it" }
    private val auth = "username=${Uri.encode(account.user)}&password=${Uri.encode(account.pass)}"

    private suspend fun get(params: String): String = withContext(Dispatchers.IO) {
        val conn = URL("$base/player_api.php?$auth$params").openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 30_000
        try {
            if (conn.responseCode != 200) throw IOException("Server antwortet mit HTTP ${conn.responseCode}")
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    suspend fun login() {
        val info = JSONObject(get("")).optJSONObject("user_info")
        if (info?.optInt("auth") != 1) throw IOException("Login fehlgeschlagen")
    }

    /** Rohes JSON einer Listen-Action (z. B. get_live_streams), damit es sich unverändert cachen lässt. */
    suspend fun raw(action: String): String = get("&action=$action")

    fun parseCategories(json: String): List<Category> =
        JSONArray(json).objects().map {
            Category(it.optString("category_id"), it.optString("category_name"))
        }

    fun parseChannels(json: String): List<Channel> =
        JSONArray(json).objects().map {
            Channel(
                id = it.optInt("stream_id"),
                num = it.optInt("num"),
                name = it.optString("name"),
                logo = it.optString("stream_icon").ifBlank { null },
                categoryId = it.optString("category_id"),
            )
        }

    suspend fun shortEpg(channel: Channel, limit: Int = 2): List<Programme> =
        JSONObject(get("&action=get_short_epg&stream_id=${channel.id}&limit=$limit"))
            .optJSONArray("epg_listings")?.objects().orEmpty().map {
                Programme(
                    title = String(Base64.decode(it.optString("title"), Base64.DEFAULT)),
                    start = it.optLong("start_timestamp") * 1000,
                    end = it.optLong("stop_timestamp") * 1000,
                )
            }

    fun streamUrl(channel: Channel) =
        "$base/live/${Uri.encode(account.user)}/${Uri.encode(account.pass)}/${channel.id}.ts"
}

private fun JSONArray.objects() = (0 until length()).mapNotNull { optJSONObject(it) }
