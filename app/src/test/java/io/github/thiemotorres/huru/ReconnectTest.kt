package io.github.thiemotorres.huru

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import org.junit.Assert.assertEquals
import org.junit.Test

class ReconnectTest {
    @Test fun threeRetriesUntilReset() {
        val scope = CoroutineScope(Job())
        val r = Reconnect(scope)
        assertEquals(listOf(true, true, true, false), List(4) { r.retry {} })
        r.reset()
        assertEquals(true, r.retry {})
        scope.cancel()
    }
}
