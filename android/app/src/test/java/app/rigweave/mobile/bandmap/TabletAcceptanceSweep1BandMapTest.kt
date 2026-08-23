package app.rigweave.mobile.bandmap

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TabletAcceptanceSweep1BandMapTest {
    private fun spot(index: Int, frequencyHz: Long = 14_074_000L) = BandMapSpot(
        id = "spot-$index",
        callsign = "T$index",
        displayCallsign = "T$index",
        frequencyHz = frequencyHz,
        band = "20m",
        modeFamily = BandMapModeFamily.DIGI,
        submode = "FT8",
        observations = emptyList(),
        oldestObservationEpoch = 1_000,
        newestObservationEpoch = 1_000,
    )

    @Test fun visibleBandSetIsExactlyTheAcceptanceRangeFrom160mThrough23cm() {
        assertEquals(
            listOf("160m", "80m", "60m", "40m", "30m", "20m", "17m", "15m", "12m", "10m", "6m", "4m", "2m", "70cm", "23cm"),
            bandMapVisibleBands,
        )
    }

    @Test fun densePlacementIsBoundedAndPreservesExactChooserMemberIds() {
        val segment = BandMapSegment("20m", lowerHz = 14_000_000, upperHz = 14_350_000)
        val input = (0 until 40).map(::spot)
        val ordered = input.sortedWith(compareBy<BandMapSpot>(BandMapSpot::frequencyHz).thenBy(BandMapSpot::callsign))
        val placed = BandMapLayoutEngine.place(input, segment, pixels = 800)

        assertEquals(6, placed.size)
        assertEquals(ordered.take(5).map { it.id }, placed.take(5).map { it.id })
        assertEquals(ordered.drop(5).take(20).map { it.id }, placed.last().memberIds)
        assertEquals(placed.last().memberIds.distinct(), placed.last().memberIds)
    }

    @Test fun layoutCoordinateSupportsBothDirectionsAndFrequencyAxisTicksStayBounded() {
        val segment = BandMapSegment("20m", lowerHz = 14_000_000, upperHz = 14_350_000)
        assertEquals(0f, BandMapLayoutEngine.coordinate(segment.lowerHz, segment), 0f)
        assertEquals(1f, BandMapLayoutEngine.coordinate(segment.lowerHz, segment, BandMapDirection.HIGH_TO_LOW), 0f)
        val ticks = BandMapLayoutEngine.ticks(segment, 1_200)
        assertTrue(ticks.size in 2..64)
        assertTrue(ticks.all { it.frequencyHz in segment.lowerHz..segment.upperHz && it.position in 0f..1f })
    }

    @Test fun iaruDisplayPlanIsGuidanceNotRegulatoryAuthority() {
        val plan = BandMapDisplayPlans.forBand("20m", BandMapIaruRegion.REGION_1)
        assertFalse(plan.regulatoryAuthority)
        assertEquals(listOf("CW", "DATA", "SSB / PHONE"), plan.segments.map { it.label })
        assertTrue(plan.segments.zipWithNext().all { (left, right) -> left.upperHz == right.lowerHz })
    }
}
