package app.rigweave.mobile

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class NeuralDxStoreInstrumentedTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val databaseName = "neural-dx-store-${System.nanoTime()}.sqlite"
    private lateinit var store: NeuralDxStore

    @Before fun createVersionTwoDatabase() {
        context.deleteDatabase(databaseName)
        context.openOrCreateDatabase(databaseName, Context.MODE_PRIVATE, null).use { db ->
            db.execSQL("""CREATE TABLE spot(id TEXT PRIMARY KEY, ts INTEGER NOT NULL, call TEXT NOT NULL, spotter TEXT NOT NULL,
                frequency_hz INTEGER NOT NULL, band TEXT NOT NULL, mode TEXT NOT NULL, country TEXT NOT NULL,
                continent TEXT NOT NULL, latitude REAL NOT NULL, longitude REAL NOT NULL, score INTEGER NOT NULL,
                watchlisted INTEGER NOT NULL, comment TEXT NOT NULL)""")
            db.execSQL("CREATE INDEX spot_ts_idx ON spot(ts DESC)")
            db.execSQL("CREATE INDEX spot_band_ts_idx ON spot(band,ts DESC)")
            db.execSQL("CREATE INDEX spot_call_ts_idx ON spot(call,ts DESC)")
            db.execSQL("CREATE TABLE prediction_result(key TEXT PRIMARY KEY)")
            val timestamp = Instant.now().epochSecond - 60
            db.execSQL("INSERT INTO spot VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)", arrayOf(
                "existing", timestamp, "JA1OLD", "W1AW", 14_074_000, "20m", "FT8", "", "", 0.0, 0.0, 45, 0, "legacy comment",
            ))
            db.version = 2
        }
        store = NeuralDxStore(context, databaseName)
        store.writableDatabase
    }

    @After fun closeAndDeleteDatabase() {
        store.close()
        context.deleteDatabase(databaseName)
    }

    @Test fun migratesVersionTwoAndUpsertsWithoutErasingEnrichmentOrCreatingFreshAlerts() {
        val db = store.writableDatabase
        assertEquals(3, db.version)
        val columns = db.rawQuery("PRAGMA table_info(spot)", null).use { cursor ->
            buildSet { while (cursor.moveToNext()) add(cursor.getString(cursor.getColumnIndexOrThrow("name"))) }
        }
        assertTrue(columns.containsAll(setOf("dxcc", "confidence", "samples", "reason", "updated_at")))
        assertFalse(tableExists("prediction_result"))
        assertEquals(readLong("existing", "ts"), readLong("existing", "updated_at"))

        val enriched = spot(
            id = "existing", call = "JA1NEW", score = 77, confidence = 66, samples = 12,
            country = "Japan", dxcc = "339", continent = "AS", latitude = 35.68, longitude = 139.76,
            comment = "new useful comment", reason = "Current evidence",
        )
        assertTrue(store.ingest(listOf(enriched)).isEmpty())
        assertEquals(1, rowCount())
        assertEquals("Japan", readText("existing", "country"))
        assertEquals("339", readText("existing", "dxcc"))
        assertEquals("AS", readText("existing", "continent"))
        assertEquals(35.68, readDouble("existing", "latitude"), 0.001)
        assertEquals(139.76, readDouble("existing", "longitude"), 0.001)
        assertEquals("new useful comment", readText("existing", "comment"))

        val rescored = enriched.copy(
            callsign = "JA1LATEST", score = 83, confidence = 72, samples = 19,
            country = "", dxcc = "", continent = "", latitude = 0.0, longitude = 0.0, comment = "", reason = "Latest evidence",
        )
        assertTrue(store.ingest(listOf(rescored)).isEmpty())
        assertEquals(1, rowCount())
        assertEquals("JA1LATEST", readText("existing", "call"))
        assertEquals(83, readLong("existing", "score").toInt())
        assertEquals(72, readLong("existing", "confidence").toInt())
        assertEquals(19, readLong("existing", "samples").toInt())
        assertEquals("Latest evidence", readText("existing", "reason"))
        assertEquals("Japan", readText("existing", "country"))
        assertEquals("339", readText("existing", "dxcc"))
        assertEquals("AS", readText("existing", "continent"))
        assertEquals(35.68, readDouble("existing", "latitude"), 0.001)
        assertEquals(139.76, readDouble("existing", "longitude"), 0.001)
        assertEquals("new useful comment", readText("existing", "comment"))

        val fresh = spot(id = "fresh", call = "VK8NEW", score = 60, confidence = 50, samples = 5)
        assertEquals(listOf("fresh"), store.ingest(listOf(fresh)).map { it.id })
        assertEquals(2, rowCount())
    }

    private fun spot(
        id: String, call: String, score: Int, confidence: Int, samples: Int,
        country: String = "Australia", dxcc: String = "150", continent: String = "OC",
        latitude: Double = -12.46, longitude: Double = 130.84, comment: String = "live comment", reason: String = "Live evidence",
    ) = AndroidDXSpot(
        id, call, "W1AW", 14_074_000, Instant.now().epochSecond, "20m", "FT8", country, continent,
        29, 55, latitude, longitude, comment, score, confidence, samples, false,
        false, false, false, false, false, false, 0, 0, "", reason, dxcc = dxcc,
    )

    private fun tableExists(name: String): Boolean = store.readableDatabase.rawQuery(
        "SELECT 1 FROM sqlite_master WHERE type='table' AND name=?", arrayOf(name),
    ).use { it.moveToFirst() }

    private fun rowCount(): Int = store.readableDatabase.rawQuery("SELECT COUNT(*) FROM spot", null).use {
        it.moveToFirst(); it.getInt(0)
    }

    private fun readText(id: String, column: String): String = store.readableDatabase.rawQuery(
        "SELECT $column FROM spot WHERE id=?", arrayOf(id),
    ).use { it.moveToFirst(); it.getString(0) }

    private fun readLong(id: String, column: String): Long = store.readableDatabase.rawQuery(
        "SELECT $column FROM spot WHERE id=?", arrayOf(id),
    ).use { it.moveToFirst(); it.getLong(0) }

    private fun readDouble(id: String, column: String): Double = store.readableDatabase.rawQuery(
        "SELECT $column FROM spot WHERE id=?", arrayOf(id),
    ).use { it.moveToFirst(); it.getDouble(0) }
}
