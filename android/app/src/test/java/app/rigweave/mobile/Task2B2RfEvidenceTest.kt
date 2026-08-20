package app.rigweave.mobile

import app.rigweave.mobile.hamclock.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.time.Instant

class Task2B2RfEvidenceTest {
    @Test fun rbnParserPreservesSkimmerDxFrequencyModeSnrSpeedAndFlags() {
        val row = requireNotNull(parseRbnClusterLine("DX de K1ABC-#: 14025.3 VK9DX CW 23 dB 31 WPM CQ", 1_000))
        assertEquals("K1ABC-#", row.skimmerCall); assertEquals("VK9DX", row.dxCall)
        assertEquals(14_025_300, row.frequencyHz); assertEquals("20m", row.band); assertEquals("CW", row.mode)
        assertEquals(23, row.snr); assertEquals(31, row.wpm); assertTrue(row.cq); assertFalse(row.test)
    }

    @Test fun genericClusterLineIsNotMislabelledAsRbn() {
        assertEquals(null, parseRbnClusterLine("DX de W1AW: 14074.0 VK3ABC FT8 -12 dB", 1_000))
    }

    @Test fun rbnPolicyFiltersDeduplicatesAndCaps() {
        val base = requireNotNull(parseRbnClusterLine("DX de K1ABC-#: 14025.3 VK9DX CW 23 dB 31 WPM CQ", 1_000))
        val duplicate = base.copy(id = "new", observedEpoch = 1_001)
        val other = base.copy(id = "other", dxCall = "W1AW", observedEpoch = 1_002)
        val preference = HamClockRbnPreference(windowMinutes = 2, maximumRows = 1, bands = setOf("20M"),
            modes = setOf("CW"), minimumSnr = 20, dxCall = "VK")
        assertEquals(listOf("new"), boundedRbnObservations(listOf(base, duplicate, other), preference, emptySet(), 1_010).map { it.id })
    }

    @Test fun personalWsprReusesPskTransportWithModeAndNoLocator() {
        val directory = Files.createTempDirectory("wspr-shared").toFile()
        try {
            val urls = mutableListOf<String>()
            val client = HamClockHttpClient { request ->
                urls += request.url
                val hearing = request.url.contains("receiverCallsign")
                val sender = if (hearing) "VK3ABC" else "OM0RX"
                val receiver = if (hearing) "OM0RX" else "VK3ABC"
                HamClockHttpResponse("""rwPsk({"receptionReport":[{"senderCallsign":"$sender","senderLocator":"QF22","receiverCallsign":"$receiver","receiverLocator":"JN88TQ","frequency":14095600,"flowStartSeconds":990,"mode":"WSPR","sNR":"-18"}]});""",
                    contentType = "application/javascript", effectiveUrl = request.url)
            }
            val psk = PskReporterRepository(directory, client, HamClockInFlightCoalescer())
            val snapshot = HamClockWsprRepository(psk).refreshPersonal("OM0RX", null,
                HamClockWsprPreference(windowMinutes = 2), nowEpoch = 1_000)
            assertEquals(2, urls.size); assertTrue(urls.all { it.contains("mode=WSPR") && it.contains("nolocator=1") })
            assertEquals(setOf(SignalDirection.BEING_HEARD, SignalDirection.HEARING), snapshot.reports.map { it.direction }.toSet())
        } finally { directory.deleteRecursively() }
    }

    @Test fun regionalWsprIsPolicyUnavailableAndMakesNoRequest() {
        val directory = Files.createTempDirectory("wspr-policy").toFile()
        try {
            var requests = 0
            val psk = PskReporterRepository(directory, HamClockHttpClient { requests++; error("network forbidden") },
                HamClockInFlightCoalescer())
            val snapshot = HamClockWsprRepository(psk).refreshPersonal("", null,
                HamClockWsprPreference(personalEnabled = false, regionalEnabled = true))
            assertEquals(HamClockWsprRegionalState.UNAVAILABLE_POLICY, snapshot.regionalState); assertEquals(0, requests)
        } finally { directory.deleteRecursively() }
    }

    @Test fun ibpManifestHasEighteenUniqueOfficialScheduleSitesAndStableHash() {
        assertEquals(18, hamClockIbpManifest.size)
        assertEquals(18, hamClockIbpManifest.map { it.callsign }.toSet().size)
        assertEquals(64, HAMCLOCK_IBP_MANIFEST_HASH.length)
        assertEquals(HAMCLOCK_IBP_MANIFEST_HASH, HAMCLOCK_IBP_MANIFEST_HASH)
    }

    @Test fun ibpScheduleIsFiveBandsTenSecondSlotsAndOneHundredEightySecondCycle() {
        val first = hamClockIbpSchedule(180)
        val next = hamClockIbpSchedule(190)
        val wrapped = hamClockIbpSchedule(360)
        assertEquals(listOf("20m", "17m", "15m", "12m", "10m"), first.transmissions.map { it.band })
        assertEquals(10, first.transmissions.first().slotEndEpoch - first.transmissions.first().slotStartEpoch)
        assertNotEquals(first.transmissions.first().beacon, next.transmissions.first().beacon)
        assertEquals(first.transmissions.map { it.beacon.callsign }, wrapped.transmissions.map { it.beacon.callsign })
    }

    @Test fun allUnavailableBandHealthSaysNoLiveEvidenceNeverClosed() {
        val preference = HamClockBandHealthPreference(visibleBands = setOf("20m"), enabledSources = setOf("RBN", "WSPR"))
        val row = computeHamClockBandHealth(emptyList(), mapOf("RBN" to HamClockEvidenceAvailability.UNAVAILABLE,
            "WSPR" to HamClockEvidenceAvailability.DISABLED), preference, 1_000).single()
        assertEquals("NO LIVE EVIDENCE", row.state); assertFalse(row.state.contains("CLOSED"))
    }

    @Test fun availableButEmptyBandHealthSaysNoRecentEvidence() {
        val preference = HamClockBandHealthPreference(visibleBands = setOf("20m"), enabledSources = setOf("RBN"))
        val row = computeHamClockBandHealth(emptyList(), mapOf("RBN" to HamClockEvidenceAvailability.CURRENT),
            preference, 1_000).single()
        assertEquals("NO RECENT EVIDENCE", row.state)
    }

    @Test fun bandHealthCapsRepeatedContributorAndExplainsConfidenceTrend() {
        val rows = (1..8).map { HamClockBandEvidence("RBN", "20m", "CW", "VK9DX", "K1ABC-#", -10, 990L + it) } +
            listOf(HamClockBandEvidence("WSPR", "20m", "WSPR", "VK3ABC", "OM0RX", -18, 999))
        val preference = HamClockBandHealthPreference(visibleBands = setOf("20m"), enabledSources = setOf("RBN", "WSPR"))
        val result = computeHamClockBandHealth(rows, mapOf("RBN" to HamClockEvidenceAvailability.CURRENT,
            "WSPR" to HamClockEvidenceAvailability.CURRENT), preference, 1_000).single()
        assertEquals(4, result.observations); assertEquals(2, result.diversity)
        assertEquals("MEDIUM", result.confidence); assertTrue(result.reasons.joinToString().contains("capped"))
    }

    @Test fun task2B2SettingsRoundTripThroughProfilesAndImportCodec() {
        val settings = HamClockUserSettings(rbn = HamClockRbnPreference(false, maximumRows = 77, minimumSnr = -12),
            wspr = HamClockWsprPreference(false, windowMinutes = 120, regionalEnabled = true, showRegionalGrid = true),
            ibp = HamClockIbpPreference(false, false),
            bandHealth = HamClockBandHealthPreference(60, "CW", setOf("RBN"), setOf("20m")))
        val document = HamClockSettingsDocument(settings = settings, profiles = listOf(
            HamClockNamedProfile("rf", "RF", settings, 1, 1)))
        val decoded = HamClockSettingsCodec.decode(HamClockSettingsCodec.encode(document))
        assertEquals(settings.rbn, decoded.settings.rbn); assertEquals(settings.wspr, decoded.settings.wspr)
        assertEquals(settings.ibp, decoded.profiles.single().settings.ibp)
        assertEquals(setOf("20M"), decoded.profiles.single().settings.bandHealth.visibleBands)
    }

    @Test fun pskHearingPathRunsRemoteToDeAndNewLayerCapsAreExplicit() {
        val heardByDe = SignalReport("VK3ABC", "QF22", -37.5, 145.0, 14_095_600, "20m", "WSPR", -18,
            null, 1_000, SignalDirection.HEARING, "OM0RX", "VK3ABC", "QF22", "OM0RX", "JN88TQ")
        val endpoints = requireNotNull(hamClockSignalPathEndpoints(heardByDe, requireNotNull(maidenheadCenter("JN88TQ"))))
        assertEquals(-37.5, endpoints.first.latitude, 0.001)
        assertEquals(120, requireNotNull(hamClockMapLayerSpec(HamClockMapLayerId.RBN)).maximumObjectCount)
        assertEquals(100, requireNotNull(hamClockMapLayerSpec(HamClockMapLayerId.WSPR_EXPANDED)).maximumObjectCount)
        assertEquals(23, requireNotNull(hamClockMapLayerSpec(HamClockMapLayerId.IBP)).maximumObjectCount)
    }
}
