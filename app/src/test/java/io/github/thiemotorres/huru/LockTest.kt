package io.github.thiemotorres.huru

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LockTest {
    @Test fun adultNames() {
        listOf("XXX", "DE | Adult", "ADULTS 18+", "18+ Movies", "Erotik", "VOD - XXX HD").forEach { assertTrue(it, isAdult(it)) }
        listOf("News", "Kids", "Sports 1", "Documentaries", "Box Office 2018", "Classics").forEach { assertFalse(it, isAdult(it)) }
    }
}
