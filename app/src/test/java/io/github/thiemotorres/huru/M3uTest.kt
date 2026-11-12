package io.github.thiemotorres.huru

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class M3uTest {
    private val playlist = """
        #EXTM3U url-tvg="http://epg.example/guide.xml.gz,http://epg.example/other.xml"
        #EXTINF:-1 tvg-id="first.example" tvg-chno="7" tvg-logo="http://img.example/1.png" group-title="News",First News HD
        http://tv.example/live/1.ts
        #EXTINF:-1 tvg-name="Second" group-title="Movies, Classics",Second, the channel
        #EXTVLCOPT:http-user-agent=Test
        http://tv.example/live/2.m3u8

        #EXTINF:-1,No Group
        http://tv.example/live/3.ts
        #EXTINF:-1,Same stream again
        http://tv.example/live/1.ts
    """.trimIndent()

    @Test fun playlist() {
        val p = parseM3u(playlist)
        assertEquals("http://epg.example/guide.xml.gz", p.epgUrl)
        val (a, b, c, d) = p.channels
        assertEquals(listOf("First News HD", "Second, the channel", "No Group", "Same stream again"), p.channels.map { it.name })
        assertEquals(7, a.num)
        assertEquals("News", a.categoryId)
        assertEquals("first.example", a.epgId)
        assertEquals("http://img.example/1.png", a.logo)
        assertEquals("http://tv.example/live/1.ts", a.url)
        assertEquals("Movies, Classics", b.categoryId)
        assertEquals("Second", b.epgId) // tvg-name when there is no tvg-id
        assertEquals(3, c.num) // position when there is no tvg-chno
        assertEquals("", c.categoryId)
        assertNull(c.epgId)
        assertEquals(4, p.channels.map { it.id }.toSet().size) // the same URL twice still gets two ids
        assertEquals(d.url, a.url)
    }

    @Test fun xmltv() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <tv>
              <channel id="first.example"><display-name>First</display-name></channel>
              <programme start="20261112180000 +0000" stop="20261112190000 +0000" channel="first.example">
                <title lang="en">Evening News</title><title lang="de">Abendnachrichten</title><desc>…</desc>
              </programme>
              <programme start="20261112170000 +0000" stop="20261112180000 +0000" channel="first.example"><title>Earlier</title></programme>
              <programme start="20261112100000 +0000" stop="20261112110000 +0000" channel="first.example"><title>Ended</title></programme>
              <programme start="20261112180000 +0000" stop="20261112190000 +0000" channel="unknown"><title>Other</title></programme>
            </tv>
        """.trimIndent()
        val from = xmltvTime("20261112163000 +0000")
        val guide = parseXmltv(xml.byteInputStream(), setOf("first.example"), from, from + 86_400_000)
        assertEquals(setOf("first.example"), guide.keys)
        assertEquals(listOf("Earlier", "Evening News"), guide["first.example"]!!.map { it.title }) // sorted, first title only
    }

    @Test fun times() {
        assertEquals(xmltvTime("20261112190000 +0100"), xmltvTime("20261112180000 +0000"))
        assertEquals(xmltvTime("20261112180000"), xmltvTime("20261112180000 +0000")) // no offset: UTC
        assertEquals(0L, xmltvTime("garbage"))
        assertTrue(xmltvTime("20261112180000 +0000") > 0)
    }
}
