// SPDX-License-Identifier: GPL-3.0-only
package app.rigweave.mobile

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RfEvidenceBasemapTest {
    @Test fun datelineCrossingRfPathsAreSplitBeforeMapLibreRendering() {
        val segments = splitRfMapPath(listOf(
            GeoArcPoint(10.0, 170.0),
            GeoArcPoint(11.0, 179.0),
            GeoArcPoint(12.0, -179.0),
            GeoArcPoint(13.0, -170.0),
        ))
        assertEquals(2, segments.size)
        assertTrue(segments.flatten().all { it.longitude in -180.0..180.0 })
    }

    @Test fun intelligenceMapsReuseTheHomeMapLibreBasemapAndExplainSpectrumEmptyState() {
        val mapSource = File("src/main/java/app/rigweave/mobile/RfEvidenceBasemap.kt").readText()
        val spectrumSource = File("src/main/java/app/rigweave/mobile/AndroidSdrWorkbenchScreensV4.kt").readText()
        val intelligenceSource = File("src/main/java/app/rigweave/mobile/AndroidSdrScreens.kt").readText()
        assertTrue("OPEN_FREE_MAP_DARK_STYLE" in mapSource)
        assertTrue("MapLibre global RF evidence view" in mapSource)
        assertTrue("NO SPECTRUM AGGREGATES YET · GEOGRAPHIC RF CONTEXT" in spectrumSource)
        assertTrue("RF GLOBE · MAPLIBRE WORLD VIEW" in intelligenceSource)
    }
}
