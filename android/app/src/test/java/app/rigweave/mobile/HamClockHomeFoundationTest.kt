package app.rigweave.mobile

import app.rigweave.mobile.hamclock.HamClockDxTarget
import app.rigweave.mobile.hamclock.HamClockDxTargetSource
import app.rigweave.mobile.hamclock.HamClockMapLayerAvailability
import app.rigweave.mobile.hamclock.HamClockMapLayerId
import app.rigweave.mobile.hamclock.defaultHamClockMapLayers
import app.rigweave.mobile.hamclock.defaultHamClockPanels
import app.rigweave.mobile.hamclock.hamClockMapLayerRegistry
import app.rigweave.mobile.hamclock.hamClockModuleRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HamClockHomeFoundationTest {
    @Test
    fun moduleRegistryIsTheCompleteDefaultLayoutAuthority() {
        assertEquals(hamClockModuleRegistry.map { it.id }, defaultHamClockPanels().map { it.id })
        assertEquals(hamClockModuleRegistry.size, hamClockModuleRegistry.map { it.id }.toSet().size)
        hamClockModuleRegistry.zip(defaultHamClockPanels()).forEach { (spec, preference) ->
            assertEquals(spec.defaultVisible, preference.visible)
            assertEquals(spec.defaultColumn, preference.column)
            assertEquals(spec.defaultColumnSpan, preference.columnSpan)
            assertEquals(spec.defaultRowSpan, preference.rowSpan)
        }
    }

    @Test
    fun mapRegistryIsCompleteBoundedAndExplainsUnavailableLayers() {
        assertEquals(hamClockMapLayerRegistry.map { it.id }, defaultHamClockMapLayers().map { it.id })
        assertEquals(hamClockMapLayerRegistry.size, hamClockMapLayerRegistry.map { it.id }.toSet().size)
        assertTrue(hamClockMapLayerRegistry.all { it.maximumObjectCount >= 0 })
        assertTrue(hamClockMapLayerRegistry.filter { it.availability == HamClockMapLayerAvailability.UNAVAILABLE }
            .all { it.unavailableReason.isNotBlank() && it.maximumObjectCount == 0 })
    }

    @Test
    fun boundedSnapshotCapsEachLayerAndDropsUnknownLayerObjects() {
        val points = (0 until 240).map {
            HamClockMapPoint(it.toString(), HamClockMapLayerId.DX_SPOTS, "DX$it", "", 0.0, 0.0, "#fff")
        } + HamClockMapPoint("unknown", "future.unknown", "Unknown", "", 0.0, 0.0, "#fff")

        val bounded = boundedHamClockMapSnapshot(HamClockMapSnapshot(points = points))

        assertEquals(160, bounded.points.count { it.layerId == HamClockMapLayerId.DX_SPOTS })
        assertTrue(bounded.points.none { it.layerId == "future.unknown" })
    }

    @Test
    fun lockedManualTargetBlocksAutomaticReplacement() {
        val manual = HamClockDxTarget("VK9MAN", "QF56", -33.0, 151.0, true, HamClockDxTargetSource.MANUAL)
        val automatic = dxSpot("ZL1AUTO", -36.8, 174.7)

        val resolved = resolveHamClockTarget(manual, automatic)

        assertEquals("VK9MAN", resolved?.callsign)
        assertEquals(HamClockDxTargetSource.MANUAL, resolved?.source)
    }

    @Test
    fun unlockedTargetUsesRankedAutomaticGeometry() {
        val manual = HamClockDxTarget("VK9MAN", "QF56", -33.0, 151.0, false, HamClockDxTargetSource.MANUAL)
        val automatic = dxSpot("ZL1AUTO", -36.8, 174.7)

        val resolved = resolveHamClockTarget(manual, automatic)

        assertEquals("ZL1AUTO", resolved?.callsign)
        assertEquals(HamClockDxTargetSource.AUTOMATIC, resolved?.source)
    }

    private fun dxSpot(call: String, latitude: Double, longitude: Double) = AndroidDXSpot(
        id = call, callsign = call, spotter = "TEST", frequencyHz = 14_074_000,
        receivedEpoch = 1, band = "20m", mode = "FT8", country = "New Zealand", continent = "OC",
        cqZone = 32, ituZone = 60, latitude = latitude, longitude = longitude, comment = "",
        score = 90, confidence = 90, samples = 1, watchlisted = false,
        workedCountry = false, workedCall = false, workedBand = false, workedMode = false,
        workedBandMode = false, recentDupe = false, distanceKm = 0, bearingDegrees = 0,
        pathState = "", reason = "test",
    )
}
