package io.github.thiemotorres.huru

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** Public demo server with legal content only (see demo-server/). */
val DEMO_SOURCE = Account("https://huru-demo.feto.dev", "demo", "demo", name = "Huru Demo")

/** Per-source data files; they are deleted together with the source. */
private val SOURCE_PREFS = listOf("favorites", "last", "categories", "continue")

// ponytail: credentials stored in plain text in app-private SharedPreferences. Fine for a personal device.
/** Saved sources and the active one. Migrates the single account of earlier versions as source "". */
class SourceStore(private val context: Context) {
    private val prefs = context.getSharedPreferences("sources", Context.MODE_PRIVATE)

    fun load(): List<Account> = prefs.getString("list", null)?.let(::decodeSources) ?: migrate()

    fun activeId(): String? = prefs.getString("active", null)

    fun save(sources: List<Account>, activeId: String?) =
        prefs.edit().putString("list", encodeSources(sources)).putString("active", activeId).apply()

    /** Removes everything stored for a source: favourites, progress, cached lists. */
    fun deleteData(source: Account) {
        SOURCE_PREFS.forEach { base ->
            if (source.id.isEmpty()) context.getSharedPreferences(base, Context.MODE_PRIVATE).edit().clear().apply()
            else context.deleteSharedPreferences("$base-${source.id}")
        }
        File(context.cacheDir, "lists-${source.hashCode()}").deleteRecursively()
    }

    private fun migrate(): List<Account> {
        val legacy = context.getSharedPreferences("account", Context.MODE_PRIVATE)
        val server = legacy.getString("server", null) ?: return emptyList()
        val source = Account(server, legacy.getString("user", "")!!, legacy.getString("pass", "")!!, id = "", name = hostOf(server))
        save(listOf(source), source.id)
        legacy.edit().clear().apply()
        return listOf(source)
    }
}

/** Preferences of one source; the migrated source "" keeps the original file names. */
fun Context.sourcePrefs(base: String, source: Account) =
    getSharedPreferences(if (source.id.isEmpty()) base else "$base-${source.id}", Context.MODE_PRIVATE)

fun newSourceId(): String = UUID.randomUUID().toString().take(8)

/** "http://tv.example.com:8080/" → "tv.example.com" – default name for a source. */
fun hostOf(server: String): String =
    server.trim().substringAfter("://").substringBefore("/").substringBefore(":").ifBlank { server.trim() }

fun encodeSources(sources: List<Account>): String = JSONArray(sources.map {
    JSONObject().put("id", it.id).put("name", it.name).put("server", it.server).put("user", it.user).put("pass", it.pass)
        .put("type", it.type).put("epg", it.epg)
}).toString()

fun decodeSources(json: String): List<Account> = JSONArray(json).objects().map {
    Account(
        it.getString("server"), it.optString("user"), it.optString("pass"), it.optString("id"), it.optString("name"),
        type = it.optString("type", XTREAM), epg = it.optString("epg"),
    )
}
