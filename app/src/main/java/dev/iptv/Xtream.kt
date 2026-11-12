package dev.iptv

import android.net.Uri
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
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

/** Film oder Serie in einer Liste. ext nur bei Filmen (Dateiendung des Streams). */
data class VodItem(val id: Int, val name: String, val poster: String?, val categoryId: String, val ext: String, val plot: String?)
data class Episode(val id: String, val season: Int, val num: Int, val title: String, val ext: String, val plot: String?)

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
                logo = it.str("stream_icon"),
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

    fun parseMovies(json: String): List<VodItem> =
        JSONArray(json).objects().map {
            VodItem(
                id = it.optInt("stream_id"),
                name = it.optString("name"),
                poster = it.str("stream_icon"),
                categoryId = it.optString("category_id"),
                ext = it.str("container_extension") ?: "mp4",
                plot = null,
            )
        }

    fun parseSeries(json: String): List<VodItem> =
        JSONArray(json).objects().map {
            VodItem(
                id = it.optInt("series_id"),
                name = it.optString("name"),
                poster = it.str("cover"),
                categoryId = it.optString("category_id"),
                ext = "",
                plot = it.str("plot"),
            )
        }

    suspend fun episodes(series: VodItem): List<Episode> =
        get("&action=get_series_info&series_id=${series.id}").let { withContext(Dispatchers.Default) { parseEpisodes(it) } }

    fun movieUrl(movie: VodItem) =
        "$base/movie/${Uri.encode(account.user)}/${Uri.encode(account.pass)}/${movie.id}.${movie.ext}"

    fun episodeUrl(episode: Episode) =
        "$base/series/${Uri.encode(account.user)}/${Uri.encode(account.pass)}/${episode.id}.${episode.ext}"

    fun streamUrl(channel: Channel) =
        "$base/live/${Uri.encode(account.user)}/${Uri.encode(account.pass)}/${channel.id}.ts"
}

/** "episodes" ist je nach Panel ein Objekt {"1": [...], "2": [...]} oder ein Array von Arrays. */
fun parseEpisodes(json: String): List<Episode> {
    fun episode(o: JSONObject, season: Int) = Episode(
        id = o.optString("id"),
        season = o.optInt("season", season),
        num = o.optInt("episode_num"),
        title = o.optString("title"),
        ext = o.str("container_extension") ?: "mp4",
        plot = o.optJSONObject("info")?.str("plot"),
    )
    val episodes = when (val e = JSONObject(json).opt("episodes")) {
        is JSONObject -> e.keys().asSequence().flatMap { key ->
            e.optJSONArray(key)?.objects().orEmpty().map { episode(it, key.toIntOrNull() ?: 0) }
        }.toList()
        is JSONArray -> (0 until e.length()).flatMap { i ->
            e.optJSONArray(i)?.objects().orEmpty().map { episode(it, i + 1) }
        }
        else -> emptyList()
    }
    return episodes.sortedWith(compareBy({ it.season }, { it.num }))
}

/** Erst den Cache-Stand (falls vorhanden), dann den frischen Server-Stand an [show] liefern; Server-Fehler werfen. */
suspend fun Xtream.loadLists(cacheDir: File, actions: List<String>, show: suspend (List<String>) -> Unit) {
    val dir = File(cacheDir, "lists-${account.hashCode()}").apply { mkdirs() }
    val files = actions.map { File(dir, "$it.json") }
    withContext(Dispatchers.IO) { runCatching { files.map { it.readText() } }.getOrNull() }
        ?.let { runCatching { show(it) } }
    val fresh = actions.map { raw(it) }
    withContext(Dispatchers.IO) { files.zip(fresh).forEach { (f, s) -> f.writeText(s) } }
    show(fresh)
}

/** Optionaler Text: JSON-null und leere Werte werden zu null (optString liefert sonst "null"). */
private fun JSONObject.str(key: String): String? = if (isNull(key)) null else optString(key).ifBlank { null }

private fun JSONArray.objects() = (0 until length()).mapNotNull { optJSONObject(it) }
