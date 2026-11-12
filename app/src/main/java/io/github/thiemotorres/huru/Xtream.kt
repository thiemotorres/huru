package io.github.thiemotorres.huru

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

/** One Xtream source. id "" is the source migrated from the single-account era (keeps its data file names). */
data class Account(val server: String, val user: String, val pass: String, val id: String = "", val name: String = "")
data class Category(val id: String, val name: String) {
    private val match = COUNTRY_PREFIX.matchEntire(name)
        ?: SPACED_PREFIX.matchEntire(name)?.takeIf { it.groupValues[1] in COUNTRY_CODES }
    /** Country prefix from "PT | Desporto", "BR: Abertos" or "DE MAGENTA TV" (known codes only) → "PT". */
    val prefix: String? = match?.groupValues?.get(1)
    val label: String = match?.groupValues?.get(2) ?: name
}

data class Channel(val id: Int, val num: Int, val name: String, val logo: String?, val categoryId: String) {
    /** Quality suffix at the end of the name: "SPORT TV 1 FHD" → "FHD". */
    val quality: String? = QUALITY_SUFFIX.find(name)?.groupValues?.get(1)?.uppercase()
    val label: String = name.replace(QUALITY_SUFFIX, "").trim().ifEmpty { name }
}

private val COUNTRY_PREFIX = Regex("""^\s*([A-Z]{2,3})\s*[|:]\s*(.+?)\s*$""")
private val SPACED_PREFIX = Regex("""^\s*([A-Z]{2})\s+(.+?)\s*$""")
// Without a separator only real country codes count ("THE BEST" stays whole); TV (Tuvalu) is too ambiguous.
private val COUNTRY_CODES = java.util.Locale.getISOCountries().toSet() + setOf("UK", "EN") - "TV"

/** Prefix → flag emoji ("DE" → 🇩🇪, UK/EN → 🇬🇧); null for codes that are no country ("EXYU"). */
fun flagOf(code: String): String? {
    val country = when (code) { "UK", "EN" -> "GB"; else -> code }
    if (country.length != 2 || country !in COUNTRY_CODES) return null
    return country.map { String(Character.toChars(0x1F1E6 + (it - 'A'))) }.joinToString("")
}
private val QUALITY_SUFFIX = Regex("""\s+[\[(]?(UHD|4K|FHD|HD|SD|LOW|HEVC)[\])]?\s*$""", RegexOption.IGNORE_CASE)
data class Programme(val title: String, val start: Long, val end: Long)

/** Movie or series in a list. ext only for movies (stream file extension). */
data class VodItem(val id: Int, val name: String, val poster: String?, val categoryId: String, val ext: String, val plot: String?)
data class Episode(val id: String, val season: Int, val num: Int, val title: String, val ext: String, val plot: String?)

/** Xtream Codes player_api. There are no official docs; fields arrive as string or number depending on the panel. */
class Xtream(val account: Account) {
    private val base = account.server.trim().trimEnd('/').let { if ("://" in it) it else "http://$it" }
    private val auth = "username=${Uri.encode(account.user)}&password=${Uri.encode(account.pass)}"

    private suspend fun get(params: String): String = withContext(Dispatchers.IO) {
        val conn = URL("$base/player_api.php?$auth$params").openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 30_000
        try {
            if (conn.responseCode != 200) throw IOException("Server responded with HTTP ${conn.responseCode}")
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    suspend fun login() {
        val info = JSONObject(get("")).optJSONObject("user_info")
        if (info?.optInt("auth") != 1) throw IOException("Login failed")
    }

    /** Raw JSON of a list action (e.g. get_live_streams) so it can be cached unchanged. */
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

    /** Live format: MPEG-TS first; switches to HLS for the rest of the session if a TS stream fails (see Player). */
    @Volatile var liveExt = "ts"

    fun streamUrl(channel: Channel) =
        "$base/live/${Uri.encode(account.user)}/${Uri.encode(account.pass)}/${channel.id}.$liveExt"
}

/** Depending on the panel, "episodes" is an object {"1": [...], "2": [...]} or an array of arrays. */
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

/** Passes the cached state (if any) to [show], then the fresh server state; server errors are thrown. */
suspend fun Xtream.loadLists(cacheDir: File, actions: List<String>, show: suspend (List<String>) -> Unit) {
    val dir = File(cacheDir, "lists-${account.hashCode()}").apply { mkdirs() }
    val files = actions.map { File(dir, "$it.json") }
    withContext(Dispatchers.IO) { runCatching { files.map { it.readText() } }.getOrNull() }
        ?.let { runCatching { show(it) } }
    val fresh = actions.map { raw(it) }
    withContext(Dispatchers.IO) { files.zip(fresh).forEach { (f, s) -> f.writeText(s) } }
    show(fresh)
}

/** Optional text: JSON null and blank values become null (optString would return "null"). */
private fun JSONObject.str(key: String): String? = if (isNull(key)) null else optString(key).ifBlank { null }

internal fun JSONArray.objects() = (0 until length()).mapNotNull { optJSONObject(it) }
