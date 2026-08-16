package app.rigweave.mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CwMacroRulesTest {
    @Test fun recognisesCwAndReverseCwModesOnly() {
        listOf("CW", "cw", "CW-R", "cw-r", "CWR").forEach { assertTrue(isCwMacroMode(it)) }
        listOf("USB", "LSB", "DATA", "CW-A").forEach { assertFalse(isCwMacroMode(it)) }
    }

    @Test fun sanitizesLabelsAndKx3Text() {
        assertEquals("CQ TEST/1", sanitizeCwMacroLabel("cq test/1;"))
        assertEquals("CQ OM0RX? 5NN", sanitizeCwMacroText("cq om0rx? 5nn;\n"))
        assertEquals(CW_MACRO_TEXT_MAX, sanitizeCwMacroText("A".repeat(40)).length)
    }

    @Test fun buildsOnlySafeNonBlankKx3Commands() {
        assertEquals("KYCQ CQ OM0RX;", cwMacroCommand("cq cq om0rx;TX;"))
        assertNull(cwMacroCommand(" ;\n"))
    }

    @Test fun providesSixSlotsAndPreservesEstablishedDefaults() {
        assertEquals(6, CW_MACRO_COUNT)
        assertEquals(listOf("CQ", "EXCH", "TU", "", "", ""), (0 until CW_MACRO_COUNT).map(::defaultCwMacroLabel))
        assertEquals(1, CQ_REPEAT_MIN_SECONDS)
        assertEquals(5, CQ_REPEAT_MAX_SECONDS)
    }
}
