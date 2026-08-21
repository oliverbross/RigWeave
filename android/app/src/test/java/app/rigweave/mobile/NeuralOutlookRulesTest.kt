package app.rigweave.mobile

import app.rigweave.mobile.hamclock.HamClockMapLayerId
import app.rigweave.mobile.hamclock.HamClockModuleRenderer
import app.rigweave.mobile.hamclock.hamClockMapLayerRegistry
import app.rigweave.mobile.hamclock.hamClockModuleRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class NeuralOutlookRulesTest {
    @Test fun canonicalBandsRemainCompleteAndOrdered() {
        assertEquals(listOf("160m", "80m", "60m", "40m", "30m", "20m", "17m", "15m", "12m", "10m",
            "6m", "4m", "2m", "70cm", "23cm", "3cm"), NEURAL_OUTLOOK_BANDS)
    }

    @Test fun utcMatchingUsesTargetQuarterHourAndWrapsMidnight() {
        val target = Instant.parse("2026-08-21T00:00:00Z").epochSecond
        assertEquals(1, utcQuarterHourDistance(target, target - 15 * 60))
        assertEquals(2, utcQuarterHourDistance(target, target + 30 * 60))
        assertEquals(24, utcQuarterHourDistance(target, target + 6 * 60 * 60))
    }

    @Test fun insufficientBaselineNeverBecomesAQuietClaim() {
        assertEquals(OutlookLabel.INSUFFICIENT_EVIDENCE, RigWeaveEmpiricalOutlookV1.label(99, false, false))
        assertEquals(OutlookLabel.DEGRADED, RigWeaveEmpiricalOutlookV1.label(80, true, true))
        assertEquals(OutlookLabel.STRONG, RigWeaveEmpiricalOutlookV1.label(80, true, false))
    }

    @Test fun qsoHistoryCannotChangeLiveContextSupport() {
        val base = NeuralOutlookInput("station", "OM0RX", "JN88TQ", 1, emptyList(), emptyMap(), sfi = 130.0)
        assertEquals(RigWeaveEmpiricalOutlookV1.contextAdjustment(base, "20m"),
            RigWeaveEmpiricalOutlookV1.contextAdjustment(base.copy(qsoSummary = NeuralLogSummary(qsos = 1_000_000)), "20m"))
    }

    @Test fun calibrationGateKeepsPercentagesNullUntilBothThresholdsPass() {
        assertNull(calibratedOutlookRate(39, 20, 10))
        assertNull(calibratedOutlookRate(80, 14, 10))
        assertEquals(65, calibratedOutlookRate(80, 15, 10))
    }

    @Test fun providerOutageIsUnverifiableAndValidAbsenceIsMiss() {
        assertEquals(OutlookVerification.UNVERIFIABLE, outlookVerification(0, 0, false))
        assertEquals(OutlookVerification.MISS, outlookVerification(0, 0, true))
        assertEquals(OutlookVerification.HIT, outlookVerification(2, 1, true))
        assertEquals(OutlookVerification.HIT, outlookVerification(1, 2, true))
    }

    @Test fun terrestrialAndMicrowaveBandsRequireRealEvidence() {
        assertFalse(outlookBandSupported("2m", 20, 1, 3))
        assertTrue(outlookBandSupported("2m", 8, 2, 1))
        assertFalse(outlookBandSupported("3cm", 40, 3, 4))
        assertFalse(outlookBandSupported("3cm", 40, 5, 1))
        assertTrue(outlookBandSupported("3cm", 16, 4, 2))
    }

    @Test fun homeAndMapRegistriesShareOneBoundedNonCatOutlookContract() {
        assertEquals(HamClockModuleRenderer.NEURAL_OUTLOOK,
            hamClockModuleRegistry.single { it.id == app.rigweave.mobile.hamclock.HamClockPanelId.NEURAL_OUTLOOK }.renderer)
        val layer = hamClockMapLayerRegistry.single { it.id == HamClockMapLayerId.NEURAL_OUTLOOK }
        assertEquals(72, layer.maximumObjectCount)
        assertFalse(layer.defaultVisible)
        assertFalse(OutlookForecast::class.java.declaredFields.any { it.name.contains("frequency", true) || it.name.contains("cat", true) })
    }
}
