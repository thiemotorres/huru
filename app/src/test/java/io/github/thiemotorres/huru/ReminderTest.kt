package io.github.thiemotorres.huru

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReminderTest {
    @Test fun roundTrip() {
        val r = Reminder(42, 1_000_000_000_000L, "Film | Part 2")
        assertEquals(r, decodeReminder(r.encode()))
        assertNull(decodeReminder("x|1|t"))
    }
}
