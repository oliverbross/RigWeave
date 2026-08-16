package app.rigweave.mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NeuralDxRulesTest {
    @Test fun maidenheadUsesCellCentreAndRejectsInvalidInput() {
        val point = maidenheadCenter("JN88wt")
        assertNotNull(point)
        assertTrue(point!!.latitude in 48.0..49.0)
        assertTrue(point.longitude in 17.0..18.0)
        assertNull(maidenheadCenter("ZZ99"))
        assertNull(maidenheadCenter("JN8"))
    }

    @Test fun tropoHeuristicNeverFabricatesMissingMeasurements() {
        assertEquals(null to null, tropoIndex(null, 4.0, 80, 0.0))
        val (index, risk) = tropoIndex(20.0, 19.0, 90, 20.0)
        assertTrue(index!! >= 7)
        assertEquals("HIGH", risk)
    }

    @Test fun briefingExtractionFindsRadioCallsignsWithoutYearNoise() {
        assertEquals(listOf("OM0RX", "K1ABC/P", "VK3YW"),
            extractCallsigns("OM0RX works K1ABC/P and VK3YW during 2026"))
    }

    @Test fun briefingTextDecodesNamedDecimalAndHexEntities() {
        assertEquals("DX – AO-7 & QO-100 — active",
            decodeHtmlText("<b>DX</b> &#8211; AO-7 &amp; QO-100 &#x2014; active"))
    }

    @Test fun amsatFallbackParsesThreeLineElements() {
        val rows = parseTleOrbitRecords(
            """AO-07
                |1 07530U 74089B   26228.29930727 -.00000049  00000-0 -13279-4 0  9993
                |2 07530 101.9919 242.1742 0012360  18.4320 352.6587 12.53699110368054
            """.trimMargin()
        )
        assertEquals(1, rows.size)
        assertEquals(7530, rows.single().norad)
        assertEquals("AO-07", rows.single().name)
        assertEquals(0.001236, rows.single().eccentricity, 0.0000001)
        assertEquals(12.5369911, rows.single().meanMotion, 0.0000001)
    }
}
