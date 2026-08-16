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
}
