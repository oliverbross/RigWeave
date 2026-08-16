package app.rigweave.mobile

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertFalse
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

    companion object { private const val databaseName = "rigweave-instrumented.sqlite" }
}
