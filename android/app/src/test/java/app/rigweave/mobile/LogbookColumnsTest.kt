package app.rigweave.mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LogbookColumnsTest {
    @Test fun missingOrEmptyPreferenceShowsEveryColumn() {
        assertEquals(LogbookColumn.entries, decodeLogbookColumns(null))
        assertEquals(LogbookColumn.entries, decodeLogbookColumns(""))
    }

    @Test fun savedColumnsRestoreInCanonicalTableOrder() {
        val decoded = decodeLogbookColumns("LOTW,DATE_TIME,CALLSIGN,UNKNOWN")
        assertEquals(listOf(LogbookColumn.DATE_TIME, LogbookColumn.CALLSIGN, LogbookColumn.LOTW), decoded)
        assertEquals("DATE_TIME,CALLSIGN,LOTW", encodeLogbookColumns(decoded))
    }

    @Test fun corruptPreferenceFallsBackToVisibleTable() {
        assertTrue(decodeLogbookColumns("UNKNOWN_ONLY").isNotEmpty())
    }

    @Test fun logbookDensityKeepsTouchTargetAndIncreasesRowTypeThirtyPercent() {
        assertEquals(48, LOGBOOK_HEADER_HEIGHT_DP)
        assertEquals(48, LOGBOOK_ROW_HEIGHT_DP)
        assertEquals(18f * 1.3f, LOGBOOK_ROW_FONT_SP, 0.001f)
    }

    @Test fun compactStatusColumnsTrackTheirShortHeaders() {
        assertEquals(64, LogbookColumn.RST_SENT.width)
        assertEquals(64, LogbookColumn.RST_RECEIVED.width)
        assertEquals(56, LogbookColumn.QSL.width)
        assertEquals(64, LogbookColumn.EQSL.width)
        assertEquals(64, LogbookColumn.LOTW.width)
        assertEquals(92, LogbookColumn.CLUBLOG.width)
        assertEquals(56, LogbookColumn.QRZ.width)
    }
}
