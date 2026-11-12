package io.github.thiemotorres.huru

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler
import java.io.BufferedInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.zip.GZIPInputStream
import javax.xml.parsers.SAXParserFactory

/** A parsed playlist: channels in playlist order and the EPG address from its header (url-tvg / x-tvg-url). */
data class Playlist(val channels: List<Channel>, val epgUrl: String?)

private val ATTRIBUTE = Regex("""([\w-]+)="([^"]*)"""")

/**
 * M3U/M3U8 playlist: `#EXTINF:-1 tvg-id=".." tvg-logo=".." group-title="..",Name` followed by the stream URL.
 * Channel ids come from the URL so favourites survive a reordered playlist (unique: lists are keyed by id);
 * categories are the group titles.
 */
fun parseM3u(text: String): Playlist {
    var epgUrl: String? = null
    val channels = mutableListOf<Channel>()
    val ids = HashSet<Int>()
    var info: String? = null
    text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.forEach { line ->
        when {
            line.startsWith("#EXTM3U") -> attributes(line).let { epgUrl = it["url-tvg"] ?: it["x-tvg-url"] }
            line.startsWith("#EXTINF") -> info = line
            line.startsWith("#") -> Unit // #EXTVLCOPT, #EXTGRP, …
            else -> {
                val attrs = info?.let(::attributes).orEmpty()
                // The name follows the last comma outside the attribute quotes.
                val name = info?.replace(ATTRIBUTE, "")?.substringAfter(',', "")?.trim().orEmpty()
                    .ifEmpty { attrs["tvg-name"] ?: line.substringAfterLast('/') }
                var id = line.hashCode()
                while (!ids.add(id)) id++ // duplicate entry or hash collision
                channels += Channel(
                    id = id,
                    num = attrs["tvg-chno"]?.toIntOrNull() ?: (channels.size + 1),
                    name = name,
                    logo = attrs["tvg-logo"]?.ifBlank { null },
                    categoryId = attrs["group-title"]?.ifBlank { null } ?: "",
                    url = line,
                    epgId = attrs["tvg-id"]?.ifBlank { null } ?: attrs["tvg-name"]?.ifBlank { null },
                )
                info = null
            }
        }
    }
    return Playlist(channels, epgUrl?.split(',')?.firstOrNull()?.trim()?.ifBlank { null })
}

private fun attributes(line: String) = ATTRIBUTE.findAll(line).associate { it.groupValues[1] to it.groupValues[2] }

/**
 * XMLTV programmes of the channels in [ids] that overlap [from, to), by channel id and sorted.
 * Streamed with SAX: EPG files of 100 MB and more never sit in memory as a whole.
 */
fun parseXmltv(input: InputStream, ids: Set<String>, from: Long, to: Long): Map<String, List<Programme>> {
    val result = HashMap<String, MutableList<Programme>>()
    val handler = object : DefaultHandler() {
        var channel: String? = null
        var start = 0L
        var end = 0L
        var title: StringBuilder? = null
        var inTitle = false // only the first <title> counts (others are translations)

        override fun startElement(uri: String?, local: String?, qName: String, attrs: Attributes) {
            when (qName) {
                "programme" -> {
                    channel = attrs.getValue("channel")?.takeIf { it in ids }
                    start = xmltvTime(attrs.getValue("start"))
                    end = xmltvTime(attrs.getValue("stop"))
                    title = null
                }
                "title" -> if (channel != null && title == null) { title = StringBuilder(); inTitle = true }
            }
        }

        override fun characters(ch: CharArray, offset: Int, length: Int) { if (inTitle) title?.append(ch, offset, length) }

        override fun endElement(uri: String?, local: String?, qName: String) {
            when (qName) {
                "title" -> inTitle = false
                "programme" -> {
                    val id = channel
                    if (id != null && end > from && start < to && start > 0) {
                        result.getOrPut(id) { mutableListOf() } += Programme(title?.toString()?.trim().orEmpty(), start, end)
                    }
                    channel = null; title = null
                }
            }
        }
    }
    SAXParserFactory.newInstance().newSAXParser().parse(input, handler)
    return result.mapValues { (_, list) -> list.sortedBy { it.start } }
}

/** "20261112183000 +0100" → epoch ms; without an offset the time is taken as UTC. 0 if unreadable. */
fun xmltvTime(value: String?): Long {
    val v = value?.trim() ?: return 0
    return runCatching {
        if (' ' in v) SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US).parse(v)!!.time
        else SimpleDateFormat("yyyyMMddHHmmss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.parse(v.take(14))!!.time
    }.getOrDefault(0)
}

/** gzip is recognised by its magic bytes, not the file name (many EPG URLs hide it). */
private fun maybeGzip(input: InputStream): InputStream {
    val buffered = BufferedInputStream(input)
    buffered.mark(2)
    val gzip = buffered.read() == 0x1f && buffered.read() == 0x8b
    buffered.reset()
    return if (gzip) GZIPInputStream(buffered) else buffered
}

private fun open(url: String): HttpURLConnection =
    (URL(url.trim()).openConnection() as HttpURLConnection).apply {
        connectTimeout = 10_000
        readTimeout = 60_000
        if (responseCode != 200) { disconnect(); throw IOException("Server responded with HTTP $responseCode") }
    }

/** M3U source: live channels only; the guide comes from XMLTV (the playlist's own or the one entered). */
class M3u(override val account: Account) : Provider {
    @Volatile private var channels: List<Channel> = emptyList()
    @Volatile private var headerEpg: String? = null
    private var guide: Map<String, List<Programme>>? = null
    private val guideLock = Mutex()

    private suspend fun download(): String = withContext(Dispatchers.IO) {
        val conn = open(account.server)
        try { maybeGzip(conn.inputStream).bufferedReader().use { it.readText() } } finally { conn.disconnect() }
    }

    override suspend fun login(): Long? {
        if (withContext(Dispatchers.Default) { parseM3u(download()) }.channels.isEmpty()) throw IOException("No channels in the playlist")
        return null
    }

    override suspend fun loadLive(cacheDir: File, show: suspend (List<Category>, List<Channel>) -> Unit) =
        cachedLists(cacheDir, account, listOf("playlist"), { download() }) { (text) ->
            val playlist = withContext(Dispatchers.Default) { parseM3u(text) }
            channels = playlist.channels
            headerEpg = playlist.epgUrl
            // Groups in playlist order; channels without a group collect under the source's name.
            val other = account.name.ifBlank { hostOf(account.server) }
            show(playlist.channels.map { it.categoryId }.distinct().map { Category(it, it.ifEmpty { other }) }, playlist.channels)
        }

    // ponytail: the XMLTV file is downloaded once per app start and kept for 24 h ahead in memory;
    // cache the trimmed guide on disk if big EPG files make the first rows slow.
    override suspend fun shortEpg(channel: Channel, limit: Int): List<Programme> {
        val id = channel.epgId ?: return emptyList()
        val all = guideLock.withLock { guide ?: loadGuide().also { guide = it } }
        return upcoming(all[id].orEmpty(), System.currentTimeMillis(), limit)
    }

    private suspend fun loadGuide(): Map<String, List<Programme>> {
        val url = account.epg.ifBlank { null } ?: headerEpg ?: return emptyMap()
        val ids = channels.mapNotNull { it.epgId }.toSet()
        val now = System.currentTimeMillis()
        return withContext(Dispatchers.IO) {
            runCatching {
                val conn = open(url)
                try { maybeGzip(conn.inputStream).use { parseXmltv(it, ids, now - 3_600_000, now + 86_400_000) } } finally { conn.disconnect() }
            }.getOrDefault(emptyMap()) // no guide is no reason to fail the channel list
        }
    }

    override fun streamUrl(channel: Channel) = channel.url ?: ""
}
