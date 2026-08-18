package app.rigweave.mobile

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpotFiltersTest {
    private val spot = AndroidDXSpot(
        id = "1", callsign = "OM0RX", spotter = "W3LPL", frequencyHz = 14_074_000,
        receivedEpoch = 1, band = "20m", mode = "USB", country = "Slovakia", continent = "EU",
        cqZone = 15, ituZone = 28, latitude = 0.0, longitude = 0.0, comment = "",
        score = 0, confidence = 0, samples = 1, watchlisted = false,
        workedCountry = false, workedCall = false, workedBand = false, workedMode = false,
        workedBandMode = false, recentDupe = false, distanceKm = 0, bearingDegrees = 0,
        pathState = "", reason = "",
    )

    @Test fun emptySelectionsMeanAll() {
        assertTrue(spotMatchesFilters(spot, SpotLogStatus("NB", "C"), SpotFilters()))
    }

    @Test fun categoriesCombineAndSelectionsWithinCategoryCombineOr() {
        val filters = SpotFilters(
            bands = setOf("20m", "40m"), modes = setOf("SSB", "CW"),
            callStatuses = setOf("NB"), dxccStatuses = setOf("C"),
        )
        assertTrue(spotMatchesFilters(spot, SpotLogStatus("NB", "C"), filters))
        assertFalse(spotMatchesFilters(spot, SpotLogStatus("NC", "C"), filters))
        assertFalse(spotMatchesFilters(spot.copy(band = "15m"), SpotLogStatus("NB", "C"), filters))
    }

    @Test fun searchMatchesCallOrResolvedEntity() {
        assertTrue(spotMatchesSearch(spot, "Slovak Republic", "504", "om0"))
        assertTrue(spotMatchesSearch(spot, "Slovak Republic", "504", "slovak rep"))
        assertTrue(spotMatchesSearch(spot, "Slovak Republic", "504", "504"))
        assertFalse(spotMatchesSearch(spot, "Slovak Republic", "504", "Japan"))
    }
}
