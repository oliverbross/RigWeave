package app.rigweave.mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WavelogApiV2Test {
    @Test fun normalizesHttpsBaseWithoutDuplicatingApiPath() {
        assertEquals("https://example.test/index.php/api/v2", normalizeWavelogV2Root("example.test"))
        assertEquals("https://example.test/index.php/api/v2", normalizeWavelogV2Root("https://example.test/index.php"))
        assertEquals("https://example.test/api/v2", normalizeWavelogV2Root("https://example.test/api/v2/"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsInsecureV2Endpoint() { normalizeWavelogV2Root("http://example.test") }

    @Test fun negotiatesScopesAndStationsFromRealV2Envelope() {
        val requests = mutableListOf<WavelogV2Request>()
        val client = WavelogApiV2Client("https://example.test", "wl2_secret", WavelogV2Transport { request ->
            requests += request
            when {
                request.url.endsWith("/token") -> WavelogV2Response(200,
                    """{"data":{"id":7,"name":"RigWeave","owner":"OM0RX","user_id":4,"scopes":["qso:read","station:read"],"expires_at":"2027-01-01T00:00:00Z"},"meta":{}}""")
                request.url.endsWith("/station") -> WavelogV2Response(200,
                    """{"data":[{"id":11,"uuid":"station-uuid","name":"Home","callsign":"OM0RX","gridsquare":"JN88TQ","active":true}],"meta":{}}""")
                else -> error("Unexpected request")
            }
        })
        val token = client.tokenMetadata()
        val stations = client.stations()
        assertTrue(token.capabilities.canReadQsos)
        assertFalse(token.capabilities.canWriteQsos)
        assertEquals("OM0RX", token.owner)
        assertEquals("station-uuid", stations.single().uuid)
        assertTrue(requests.all { it.bearerToken == "wl2_secret" })
    }

    @Test fun classifiesMissingScopeAndRateLimitWithoutLeakingToken() {
        val client = WavelogApiV2Client("https://example.test", "wl2_secret", WavelogV2Transport {
            WavelogV2Response(429, """{"error":{"code":"rate_limited","message":"Slow down"}}""", "120")
        })
        val error = runCatching { client.tokenMetadata() }.exceptionOrNull() as WavelogApiException
        assertEquals(WavelogErrorClass.RATE_LIMIT, error.errorClass)
        assertEquals(120L, error.retryAfterSeconds)
        assertFalse(error.message.orEmpty().contains("wl2_secret"))
    }

    @Test fun sendsAdifCreateWithStableIdempotencyHeader() {
        var captured: WavelogV2Request? = null
        val client = WavelogApiV2Client("https://example.test", "wl2_secret", WavelogV2Transport { request ->
            captured = request
            WavelogV2Response(201, """{"data":{"imported":1},"meta":{}}""")
        })
        client.createAdif("11", "<CALL:5>OM0RX<EOR>", "stable-key")
        assertEquals("POST", captured?.method)
        assertEquals("stable-key", captured?.idempotencyKey)
        assertTrue(captured?.body.orEmpty().contains("\"import_type\":\"adif\""))
    }
}
