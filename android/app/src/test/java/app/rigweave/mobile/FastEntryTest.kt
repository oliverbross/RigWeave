package app.rigweave.mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class FastEntryTest {
    private val defaults = FastEntryDefaults(LocalDate.parse("2026-08-19"), "OM0RX", "OM0RX", "7", "JN88TQ")
    @Test fun inheritsContextTimeShorthandAndDefaultReports() {
        val result = FastEntryParser.parse("20m ssb\n2134 4W7EST\n6 LA8AJA 47 46", defaults)
        assertTrue(result.errors.toString(), result.errors.isEmpty()); assertEquals(2, result.rows.size); assertEquals("59", result.rows[0].qso.rstSent)
        assertEquals("LA8AJA", result.rows[1].qso.callsign); assertEquals("47", result.rows[1].qso.rstSent)
        assertEquals(36, java.time.Instant.ofEpochSecond(result.rows[1].qso.createdAt).atZone(ZoneOffset.UTC).minute)
    }
    @Test fun appliesDayTimezoneReferencesAndArbitraryAdif() {
        val result = FastEntryParser.parse("TIMEZONE +2\ndate 2026-08-19\n14.060 cw\n1400 OM0RX/P OM-0001 <my_pota_ref:OM-0002> <APP_VENDOR:abc> [73]\nday ++\n1 VK8ABC #PH57 <sat_name:QO-100>", defaults)
        assertTrue(result.errors.toString(), result.errors.isEmpty()); assertEquals("OM-0001", result.rows[0].qso.potaRef)
        assertEquals("abc", result.rows[0].qso.extraAdifFields["APP_VENDOR"]); assertEquals("QO-100", result.rows[1].qso.extraAdifFields["SAT_NAME"])
        assertEquals(12, java.time.Instant.ofEpochSecond(result.rows[0].qso.createdAt).atZone(ZoneOffset.UTC).hour)
        assertEquals(21, java.time.Instant.ofEpochSecond(result.rows[1].qso.createdAt).atZone(ZoneOffset.UTC).dayOfMonth)
    }
    @Test fun reportsLineSpecificMissingContextWithoutCreatingHiddenRows() {
        val result = FastEntryParser.parse("2134 OM0RX", defaults); assertEquals(0, result.rows.size)
        assertEquals(setOf("Band or frequency is required", "Mode is required"), result.errors.map { it.message }.toSet())
    }
}
