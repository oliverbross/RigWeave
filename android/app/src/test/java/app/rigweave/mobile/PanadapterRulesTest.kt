package app.rigweave.mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PanadapterRulesTest {
    @Test fun settingsRoundTripAndValidationAreVersionedAndBounded() {
        val settings = PanadapterSettings(fftSize = 8_192, overlapPercent = 75, zoomDecimation = 8,
            requestedRate = 48_000, swapIq = true, iqCorrectionEnabled = true, iqBReal = -.08f,
            autoLevel = true, levelAttack = .22f, waterfallAverageFrames = 7,
            measuredFlatnessEnabled = true, measuredFlatnessOffsetsCsv = "0,12000,48000",
            measuredFlatnessGainsCsv = "0,1,4", measuredFlatnessDeviceKey = "usb-audio",
            measuredFlatnessRate = 48_000, measuredFlatnessRadio = "KX3", measuredFlatnessEpochMs = 42,
            levelCalibrationEnabled = true, dbfsToDbmOffset = 31.5f,
            levelCalibrationFrequencyHz = 14_074_000, levelCalibrationDeviceKey = "usb-audio")
        assertEquals(settings.validated(), PanadapterSettings.decode(settings.encode()))
        val invalid = PanadapterSettings(fftSize = 32_768, overlapPercent = 10,
            requestedRate = 44_100, zoomDecimation = 3, qsyStepHz = 7).validated()
        assertEquals(4_096, invalid.fftSize)
        assertEquals(50, invalid.overlapPercent)
        assertEquals(96_000, invalid.requestedRate)
        assertEquals(1, invalid.zoomDecimation)
        assertEquals(10, invalid.qsyStepHz)
    }

    @Test fun measuredFlatnessIsValidatedSymmetricAndInterpolated() {
        val settings = PanadapterSettings(measuredFlatnessOffsetsCsv = "0,12000,48000",
            measuredFlatnessGainsCsv = "0,1,4")
        val points = parseMeasuredFlatness(settings)
        assertEquals(3, points.size)
        assertEquals(2f, measuredFlatnessCorrection(points, 24_000f), .001f)
        assertEquals(2f, measuredFlatnessCorrection(points, -24_000f), .001f)
        assertTrue(parseMeasuredFlatness(settings.copy(measuredFlatnessOffsetsCsv = "100,200")).isEmpty())
    }

    @Test fun routeProofRequiresExactPreferredStereoProductionFormat() {
        assertTrue(PanadapterRouteProof("fingerprint", true, "fingerprint", 96_000,
            96_000, 2, 2, 4096).verified)
        assertFalse(PanadapterRouteProof("fingerprint", true, "other", 96_000,
            96_000, 2, 2, 4096).verified)
        assertFalse(PanadapterRouteProof("fingerprint", true, "fingerprint", 96_000,
            96_000, 1, 2, 4096).verified)
        assertFalse(PanadapterRouteProof("fingerprint", true, "fingerprint", 96_000,
            44_100, 2, 2, 4096).verified)
    }

    @Test fun passbandUsesModeDirectionBandwidthAndShift() {
        val usb = panadapterPassband(RadioState(mode = "USB", bandwidthHz = 2_700, ifShiftHz = 1_500))
        assertEquals(150f, usb.lowOffsetHz)
        assertEquals(2_850f, usb.highOffsetHz)
        val lsb = panadapterPassband(RadioState(mode = "LSB", bandwidthHz = 2_700, ifShiftHz = 1_500))
        assertEquals(-2_850f, lsb.lowOffsetHz)
        assertEquals(-150f, lsb.highOffsetHz)
        val am = panadapterPassband(RadioState(mode = "AM", bandwidthHz = 6_000, ifShiftHz = -1))
        assertEquals(-3_000f, am.lowOffsetHz)
        assertEquals(3_000f, am.highOffsetHz)
    }

    @Test fun tuneRoundingAndWaterfallReframeThresholdAreDeterministic() {
        assertEquals(14_074_120L, roundedPanadapterFrequency(14_074_116L, 10))
        assertFalse(isMaterialCenterChange(14_074_000, 14_074_005, 12f))
        assertTrue(isMaterialCenterChange(14_074_000, 14_074_050, 12f))
    }
}
