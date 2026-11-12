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
