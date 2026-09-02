package app.rigweave.mobile

import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MobileSyncStoreInstrumentedTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var database: QsoDatabase

    @Before fun openDatabase() {
        context.deleteDatabase(name)
        database = QsoDatabase(context, name)
        database.writableDatabase.execSQL(
            "INSERT INTO sync_spaces(id,station_id,logbook_id,mode,authority,key_version,state,created_at,updated_at) VALUES('space-1','station-1','log-1','DIRECT_STATION_SYNC','STATION_CANONICAL',1,'ACTIVE',1,1)",
        )
        database.writableDatabase.execSQL(
            "INSERT INTO mobile_station_links(sync_space_id,local_station_profile_id,state,created_at) VALUES('space-1',?,'ACTIVE',1)",
            arrayOf(DEFAULT_LOCAL_STATION),
        )
    }

    @After fun closeDatabase() { database.close(); context.deleteDatabase(name) }

    @Test fun canonicalQsoAndMetadataOnlyOutboxCommitAtomically() {
        val qso = sample("m9-atomic")
        assertTrue(QsoMutationCoordinator(database).save(qso))
        database.readableDatabase.rawQuery(
            "SELECT domain,entity_id,entity_revision,operation,payload_reference FROM sync_outbox", null,
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("QSO", cursor.getString(0)); assertEquals(qso.id, cursor.getString(1))
            assertEquals(1, cursor.getInt(2)); assertEquals("CREATE", cursor.getString(3))
            assertEquals("qso:${qso.id}:1", cursor.getString(4))
        }
        val columns = database.readableDatabase.rawQuery("PRAGMA table_info(sync_outbox)", null).use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.getString(1)) }
        }
        assertFalse(columns.any { it.contains("body", true) || it.contains("payload_json", true) })
    }

    @Test fun outboxFailureRollsBackCanonicalQso() {
        database.writableDatabase.execSQL("CREATE TRIGGER reject_m9_outbox BEFORE INSERT ON sync_outbox BEGIN SELECT RAISE(ABORT,'test rollback'); END")
        assertThrows { QsoMutationCoordinator(database).save(sample("m9-rollback")) }
        assertEquals(null, database.qso("m9-rollback"))
    }

    @Test fun versionSeventeenMigratesAdditivelyToEighteen() {
        database.close(); context.deleteDatabase(name)
        val path = context.getDatabasePath(name)
        path.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(path, null).use { legacy ->
            legacy.execSQL("CREATE TABLE settings(key TEXT PRIMARY KEY,value TEXT NOT NULL)")
            legacy.execSQL("CREATE TABLE radio_profile(id TEXT PRIMARY KEY,model TEXT NOT NULL)")
            legacy.execSQL("CREATE TABLE qso(id TEXT PRIMARY KEY,callsign TEXT NOT NULL,frequency_hz INTEGER NOT NULL,mode TEXT NOT NULL,rst_sent TEXT NOT NULL,rst_received TEXT NOT NULL,created_at INTEGER NOT NULL,name TEXT NOT NULL DEFAULT '',qth TEXT NOT NULL DEFAULT '',notes TEXT NOT NULL DEFAULT '',country TEXT NOT NULL DEFAULT '',details_json TEXT NOT NULL DEFAULT '{}')")
            legacy.execSQL("INSERT INTO qso(id,callsign,frequency_hz,mode,rst_sent,rst_received,created_at) VALUES('kept','OM0RX',14060000,'CW','599','599',1)")
            legacy.version = 17
        }
        database = QsoDatabase(context, name)
        assertEquals(18, database.readableDatabase.version)
        assertEquals("OM0RX", database.qso("kept")?.callsign)
        assertEquals(7, database.readableDatabase.rawQuery("SELECT COUNT(*) FROM sync_domain_registry WHERE required=1", null).use { it.moveToFirst(); it.getInt(0) })
    }

    private fun sample(id: String) = Qso(id=id,callsign="OM0RX",frequencyHz=14_060_000,mode="CW",rstSent="599",rstReceived="599",createdAt=1_700_000_000)
    private fun assertThrows(block: () -> Unit) { var thrown=false; try { block() } catch (_: Exception) { thrown=true }; assertTrue(thrown) }
    companion object { private const val name = "rigweave-m9-sync-instrumented.sqlite" }
}
