package io.github.thiemotorres.huru

import org.junit.Assert.assertEquals
import org.junit.Test

class NamesTest {
    private fun ch(name: String) = Channel(1, 1, name, null, "1")

    @Test fun quality() {
        assertEquals("SPORT TV 1" to "FHD", ch("SPORT TV 1 FHD").let { it.label to it.quality })
        assertEquals("RTP 1" to "LOW", ch("RTP 1 LOW").let { it.label to it.quality })
        assertEquals("Canal" to "HD", ch("Canal (hd)").let { it.label to it.quality })
        assertEquals("SHD Kanal" to null, ch("SHD Kanal").let { it.label to it.quality })
        assertEquals("HD" to null, ch("HD").let { it.label to it.quality }) // a single word must not be stripped to nothing
    }

    @Test fun countryPrefix() {
        assertEquals("PT" to "Desporto", Category("1", "PT | Desporto").let { it.prefix to it.label })
        assertEquals("BR" to "Canais Abertos", Category("1", "BR: Canais Abertos").let { it.prefix to it.label })
        assertEquals(null to "Portugal (SD)", Category("1", "Portugal (SD)").let { it.prefix to it.label })
        assertEquals("DE" to "MAGENTA TV", Category("1", "DE MAGENTA TV").let { it.prefix to it.label })
        assertEquals("UK" to "News", Category("1", "UK News").let { it.prefix to it.label })
        assertEquals(null to "TV TOTAL", Category("1", "TV TOTAL").let { it.prefix to it.label })
        assertEquals(null to "THE BEST", Category("1", "THE BEST").let { it.prefix to it.label })
        assertEquals("\uD83C\uDDE9\uD83C\uDDEA", flagOf("DE"))
        assertEquals("\uD83C\uDDEC\uD83C\uDDE7", flagOf("UK"))
        assertEquals(null, flagOf("EXYU"))
    }
}

class EpisodesTest {
    @Test fun objectShape() {
        val eps = parseEpisodes("""{"episodes":{"2":[{"id":"20","episode_num":1,"title":"b","container_extension":"mkv"}],
            "1":[{"id":"12","episode_num":2,"title":"a2"},{"id":"11","episode_num":1,"title":"a1","info":{"plot":"p"}}]}}""")
        assertEquals(listOf("11", "12", "20"), eps.map { it.id })
        assertEquals(listOf(1, 1, 2), eps.map { it.season })
        assertEquals("mkv", eps.last().ext)
        assertEquals("mp4", eps.first().ext)
        assertEquals("p", eps.first().plot)
    }

    @Test fun arrayShape() {
        val eps = parseEpisodes("""{"episodes":[[{"id":"1","episode_num":1}],[{"id":"2","episode_num":1}]]}""")
        assertEquals(listOf(1 to "1", 2 to "2"), eps.map { it.season to it.id })
    }

    @Test fun missing() = assertEquals(emptyList<Episode>(), parseEpisodes("""{"info":{}}"""))
}

class NullFieldsTest {
    @Test fun jsonNullIsNotText() {
        val ep = parseEpisodes("""{"episodes":{"1":[{"id":"1","episode_num":1,"container_extension":null,"info":{"plot":null}}]}}""").single()
        assertEquals(null, ep.plot)
        assertEquals("mp4", ep.ext)
    }
}

class TextTest {
    @Test fun episodeTitles() {
        val s = "A Forma Errada de Usar Magia de Cura"
        assertEquals("" to "DUAL FHD", cleanEpisodeTitle("$s S01E01 [DUAL FHD]", s))
        assertEquals("The Plan" to null, cleanEpisodeTitle("Show - S01E02 - The Plan", "Show (2024)"))
        assertEquals("Pilot" to "4K", cleanEpisodeTitle("Pilot [4K]", "Another Show"))
        assertEquals("Just a title" to null, cleanEpisodeTitle("Just a title", "X"))
    }

    @Test fun search() {
        assertEquals("acao e coracao", searchKey("Ação e Coração"))
        assertEquals(true, searchKey("Pokémon: O Filme").contains(searchKey("POKEMON")))
    }
}

class SourcesTest {
    @Test fun roundTrip() {
        val sources = listOf(DEMO_SOURCE.copy(id = "a1"), Account("http://tv.example.com:8080", "u", "p\"w", id = "", name = "Home"))
        assertEquals(sources, decodeSources(encodeSources(sources)))
    }

    @Test fun host() {
        assertEquals("tv.example.com", hostOf("http://tv.example.com:8080/"))
        assertEquals("huru-demo.feto.dev", hostOf("https://huru-demo.feto.dev"))
        assertEquals("10.0.0.5", hostOf("10.0.0.5:25461"))
    }

    @Test fun upcomingSkipsEndedProgrammes() {
        // Panel answer that starts yesterday evening: only the running and next programme count.
        val list = listOf(Programme("old", 0, 100), Programme("next", 300, 400), Programme("now", 100, 300), Programme("later", 400, 500))
        assertEquals(listOf("now", "next"), upcoming(list, now = 150, limit = 2).map { it.title })
        assertEquals(emptyList<String>(), upcoming(list, now = 600, limit = 2).map { it.title })
    }
}
