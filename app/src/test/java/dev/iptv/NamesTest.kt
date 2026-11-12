package dev.iptv

import org.junit.Assert.assertEquals
import org.junit.Test

class NamesTest {
    private fun ch(name: String) = Channel(1, 1, name, null, "1")

    @Test fun quality() {
        assertEquals("SPORT TV 1" to "FHD", ch("SPORT TV 1 FHD").let { it.label to it.quality })
        assertEquals("RTP 1" to "LOW", ch("RTP 1 LOW").let { it.label to it.quality })
        assertEquals("Canal" to "HD", ch("Canal (hd)").let { it.label to it.quality })
        assertEquals("SHD Kanal" to null, ch("SHD Kanal").let { it.label to it.quality })
        assertEquals("HD" to null, ch("HD").let { it.label to it.quality }) // nur ein Wort: nicht leeren
    }

    @Test fun countryPrefix() {
        assertEquals("PT" to "Desporto", Category("1", "PT | Desporto").let { it.prefix to it.label })
        assertEquals("BR" to "Canais Abertos", Category("1", "BR: Canais Abertos").let { it.prefix to it.label })
        assertEquals(null to "Portugal (SD)", Category("1", "Portugal (SD)").let { it.prefix to it.label })
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
        assertEquals("Der Plan" to null, cleanEpisodeTitle("Serie - S01E02 - Der Plan", "Serie (2024)"))
        assertEquals("Pilot" to "4K", cleanEpisodeTitle("Pilot [4K]", "Andere Serie"))
        assertEquals("Ganz normal" to null, cleanEpisodeTitle("Ganz normal", "X"))
    }

    @Test fun search() {
        assertEquals("acao e coracao", searchKey("Ação e Coração"))
        assertEquals(true, searchKey("Pokémon: O Filme").contains(searchKey("POKEMON")))
    }
}
