package app.rigweave.mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RadioFeedbackTest {
    private val live = RadioState(connected = true, revision = 1)

    @Test fun ignoresVfoAndMeterTraffic() {
        assertNull(detectRadioFeedback(live, live.copy(frequencyHz = 14_074_120, frequencyBHz = 7_030_000,
            meter = 8, swrTenths = 13, rfOutputTenths = 42, revision = 2)))
    }

    @Test fun reportsKeyerSpeedImmediatelyInOperatorUnits() {
        assertEquals(RadioFeedback("KEYER SPEED", "24 WPM"),
            detectRadioFeedback(live.copy(keyerSpeed = 20), live.copy(keyerSpeed = 24, revision = 2)))
    }

    @Test fun reportsControlsAndToggleState() {
        assertEquals(RadioFeedback("AF GAIN", "96 / 255"),
            detectRadioFeedback(live.copy(afGain = 80), live.copy(afGain = 96, revision = 2)))
        assertEquals(RadioFeedback("RIT", "ON"),
            detectRadioFeedback(live.copy(rit = false), live.copy(rit = true, revision = 2)))
    }

    @Test fun doesNotAnnounceInitialConnectionSnapshot() {
        assertNull(detectRadioFeedback(RadioState(), live.copy(keyerSpeed = 24, revision = 2)))
    }
}
