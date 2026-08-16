package app.rigweave.mobile

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class QsoDatabaseInstrumentedTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var database: QsoDatabase

    @Before fun openIsolatedDatabase() {
        context.deleteDatabase(databaseName)
        database = QsoDatabase(context, databaseName)
    }

    @After fun closeIsolatedDatabase() {
        database.close()
        context.deleteDatabase(databaseName)
    }

    @Test fun savesCompleteQsoAndRoundTripsAdifWithoutDuplicates() {
        val qso = Qso(
            id = "instrumented-qso", callsign = "VK0TEST", frequencyHz = 14_200_000, mode = "USB",
            rstSent = "59", rstReceived = "57", createdAt = 1_787_000_000, name = "Field Test",
            qth = "Darwin", notes = "Isolated instrumentation record", country = "Australia", band = "20m",
            grid = "PH57", iota = "OC-001", sotaRef = "VK8/TEST", wwffRef = "VKFF-0001", potaRef = "VK-0001",
            comment = "RigWeave test", frequencyRxHz = 14_205_000, bandRx = "20m", txPowerW = 10,
            operatorCallsign = "VK0TEST", stationCallsign = "VK0TEST", stationProfileId = "7",
            stationLocation = "Portable", myGrid = "PH57", myCountry = "Australia", myDxcc = "150",
            myCqZone = "29", myItuZone = "55", radioModel = "Elecraft KX3", dxcc = "150",
            continent = "OC", region = "Oceania", cqZone = "29", ituZone = "55", state = "NT",
            email = "test@example.invalid", propagationMode = "F2", antennaPath = "S", qslSent = "Q",
            qslMethod = "E", qslVia = "TEST", qslMessage = "73", submode = "USB", county = "DARWIN",
            dok = "A01", contestId = "FIELD-DAY", distanceKm = 550.5, durationSeconds = 420,
            qslReceived = "Y", qslReceivedMethod = "B", lotwSent = "Y", lotwReceived = "Y",
            clublogSent = "Y", clublogReceived = "N", eqslSent = "Y", eqslReceived = "N",
            dclSent = "N", dclReceived = "Y", qrzSent = "Y", qrzReceived = "N", qslImages = "front.jpg",
            syncState = "pending")

        assertTrue(database.save(qso))
        assertFalse(database.save(qso.copy(id = "duplicate")))
        val adif = database.exportADIF()
        listOf("<CALL:7>VK0TEST", "<BAND:3>20m", "<TX_PWR:2>10", "<DXCC:3>150", "<CONT:2>OC",
            "<PROP_MODE:2>F2", "<ANT_PATH:1>S", "<QSL_SENT:1>Q", "<QSLMSG:2>73", "<CNTY:6>DARWIN",
            "<CONTEST_ID:9>FIELD-DAY", "<QSL_RCVD:1>Y", "<LOTW_QSL_SENT:1>Y",
            "<QRZCOM_QSO_UPLOAD_STATUS:1>Y").forEach { assertTrue(it, adif.contains(it)) }
        val imported = database.importADIF(adif)
        assertTrue(imported.first == 0 && imported.second == 1)
    }

    @Test fun derivesAmateurBandsFromLiveFrequency() {
        assertTrue(bandForFrequency(1_830_000) == "160m")
        assertTrue(bandForFrequency(14_200_000) == "20m")
        assertTrue(bandForFrequency(50_100_000) == "6m")
        assertTrue(bandForFrequency(100_000_000).isBlank())
    }

    @Test fun pagesAndFiltersInsideSqlBeforeReturningRows() {
        val target = Qso(
            id = "target", callsign = "OM0RX", frequencyHz = 14_060_000, mode = "CW", rstSent = "599",
            rstReceived = "579", createdAt = 200, country = "Slovakia", band = "20m", grid = "JN98",
            iota = "EU-001", sotaRef = "OM/ZA-001", wwffRef = "OMFF-0001", potaRef = "OM-0001",
            comment = "Summit contact", notes = "Strong signal", operatorCallsign = "OM0RX", dxcc = "504",
            continent = "EU", state = "ZA", propagationMode = "F2", county = "ZILINA", dok = "A01",
            contestId = "CQ-WW-CW", distanceKm = 1_250.0, durationSeconds = 3_600, qslSent = "Y",
            qslReceived = "Y", qslMethod = "D", qslReceivedMethod = "B", qslVia = "OM0RX",
            lotwSent = "Y", lotwReceived = "Y", clublogSent = "Y", clublogReceived = "Y",
            eqslSent = "Y", eqslReceived = "Y", dclSent = "Y", dclReceived = "Y", qrzSent = "Y",
            qrzReceived = "Y", qslImages = "front.jpg", stationProfileId = "7", syncState = "synced")
        assertTrue(database.save(target))
        repeat(75) { index -> assertTrue(database.save(Qso(
            id = "other-$index", callsign = "VK${index}TEST", frequencyHz = 7_100_000, mode = "SSB",
            rstSent = "59", rstReceived = "59", createdAt = 1_000L + index, country = "Australia",
            band = "40m", stationProfileId = "7", syncState = "synced"))) }
        assertTrue(database.save(target.copy(id = "other-station", callsign = "OM0RX/P", createdAt = 500,
            stationProfileId = "8")))

        val first = database.page(0, 25, stationId = "7")
        val second = database.page(1, 25, stationId = "7")
        assertEquals(76, first.total); assertEquals(4, first.pageCount); assertEquals(25, first.rows.size)
        assertEquals(25, second.rows.size); assertTrue(first.rows.map { it.id }.intersect(second.rows.map { it.id }.toSet()).isEmpty())

        listOf(
            LogbookFilter(callsign = "0rx"), LogbookFilter(dxcc = "504"), LogbookFilter(state = "za"),
            LogbookFilter(grid = "jn98"), LogbookFilter(mode = "CW"), LogbookFilter(band = "20m"),
            LogbookFilter(propagation = "F2"), LogbookFilter(county = "zil"), LogbookFilter(dok = "A01"),
            LogbookFilter(sota = "ZA-001"), LogbookFilter(pota = "OM-0001"), LogbookFilter(iota = "EU-001"),
            LogbookFilter(wwff = "OMFF"), LogbookFilter(operator = "OM0RX"), LogbookFilter(contest = "CQ-WW"),
            LogbookFilter(continent = "EU"), LogbookFilter(comment = "strong signal"),
            LogbookFilter(distance = ">1000"), LogbookFilter(duration = ">=60"), LogbookFilter(qslSent = "Y"),
            LogbookFilter(qslReceived = "Y"), LogbookFilter(qslSentMethod = "D"),
            LogbookFilter(qslReceivedMethod = "B"), LogbookFilter(lotwSent = "Y"),
            LogbookFilter(lotwReceived = "Y"), LogbookFilter(clublogSent = "Y"),
            LogbookFilter(clublogReceived = "Y"), LogbookFilter(eqslSent = "Y"),
            LogbookFilter(eqslReceived = "Y"), LogbookFilter(dclSent = "Y"),
            LogbookFilter(dclReceived = "Y"), LogbookFilter(qrzSent = "Y"),
            LogbookFilter(qrzReceived = "Y"), LogbookFilter(qslVia = "0rx"), LogbookFilter(qslImages = "Y"),
        ).forEach { filter -> assertEquals(filter.toString(), listOf("target"), database.page(0, 50, filter, "7").rows.map { it.id }) }
    }

    companion object { private const val databaseName = "rigweave-instrumented.sqlite" }
}
