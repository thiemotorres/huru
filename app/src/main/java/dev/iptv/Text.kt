package dev.iptv

import java.text.Normalizer

/** Für die Suche: klein, ohne Akzente ("Ação" → "acao"). */
fun searchKey(s: String): String =
    Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase()

private val BRACKET_TAG = Regex("""\[([^\]]*)]""")
private val EPISODE_CODE = Regex("""\bS\d{1,2}\s*E\d{1,3}\b""", RegexOption.IGNORE_CASE)
private val YEAR = Regex("""\(\d{4}\)""")

/**
 * Folgentitel ohne Seriennamen, SxxEyy und Jahreszahl; Tags in eckigen Klammern getrennt.
 * "Serie S01E01 [DUAL FHD]" → ("", "DUAL FHD"), "Serie - S01E02 - Der Plan" → ("Der Plan", null)
 */
fun cleanEpisodeTitle(title: String, series: String): Pair<String, String?> {
    val tags = BRACKET_TAG.findAll(title).map { it.groupValues[1].trim() }.filter { it.isNotEmpty() }
        .joinToString(" ").ifEmpty { null }
    var t = title.replace(BRACKET_TAG, " ").trim()
    val base = series.replace(YEAR, "").trim()
    if (base.isNotEmpty() && t.startsWith(base, ignoreCase = true)) t = t.substring(base.length)
    t = t.replace(EPISODE_CODE, " ").replace(YEAR, " ")
    t = t.replace(Regex("\\s+"), " ").trim(' ', '-', '–', '·', ':', '|', '.')
    return t to tags
}
