package io.github.thiemotorres.huru

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExpiryTest {
    @Test fun expiry() {
        assertEquals(1_800_000_000_000L, expiryOf(JSONObject("""{"exp_date":"1800000000"}""")))
        assertEquals(1_800_000_000_000L, expiryOf(JSONObject("""{"exp_date":1800000000}""")))
        listOf("""{"exp_date":null}""", """{"exp_date":""}""", """{"exp_date":"0"}""", "{}").forEach {
            assertNull(it, expiryOf(JSONObject(it)))
        }
    }
}
