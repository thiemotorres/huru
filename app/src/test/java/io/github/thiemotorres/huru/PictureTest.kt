package io.github.thiemotorres.huru

import org.junit.Assert.assertEquals
import org.junit.Test

class PictureTest {
    @Test fun scale() {
        val tv = 16f / 9
        assertEquals(0.75f to 1f, pictureScale("fit", 4f / 3, tv))         // 4:3 → pillarbox
        assertEquals(1f to 0.75f, pictureScale("fit", 64f / 27, tv))       // 21:9 → letterbox
        assertEquals(1f to 4f / 3, pictureScale("fill", 4f / 3, tv))       // 4:3 → crop top and bottom
        assertEquals(1f to 1f, pictureScale("stretch", 4f / 3, tv))
        assertEquals(1f to 1f, pictureScale("fit", 0f, tv))               // size not known yet
        assertEquals(1f to 1f, pictureScale("fit", 4f / 3, 0f / 0f))     // view not measured yet
    }
}
