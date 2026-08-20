package app.rigweave.mobile.groupsio

import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class GroupsIoDatabaseInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val featureName = "groupsio-test-${System.nanoTime()}.sqlite"
    private val mainName = "groupsio-main-fixture-${System.nanoTime()}.sqlite"
    private lateinit var database: GroupsIoDatabase

    @Before fun open() { database = GroupsIoDatabase(context, featureName) }
    @After fun close() { database.close(); context.deleteDatabase(featureName); context.deleteDatabase(mainName) }

    @Test fun schemaIsPhysicallyIsolatedAndContainsOnlyFeatureTables() {
        val names = database.writableDatabase.rawQuery("SELECT name FROM sqlite_master WHERE type IN ('table','view')", null).use { cursor ->
            buildSet { while (cursor.moveToNext()) add(cursor.getString(0)) }
        }
        assertTrue(names.containsAll(setOf("groups", "topics", "messages", "sync_state", "message_search")))
        assertFalse(names.any { it == "qso" || it == "settings" || it == "radio_profile" })
        assertNotEquals(context.getDatabasePath("rigweave.sqlite"), context.getDatabasePath(featureName))
    }

    @Test fun repeatedPagesRemainIdempotentReadableOfflineAndSearchable() {
        val now = System.currentTimeMillis()
        val group = GroupsIoGroup(1001, "field-operators", "Field Operators", "", "sub_status_normal", true, true, now)
        val topic = GroupsIoTopic(2001, 1001, "Portable antennas", now, 1, false, 41, 41)
        val message = GroupsIoMessage(3001, 1001, 2001, 41, null, "Portable antennas", "Synthetic Operator", now, "Try the linked dipole.", false, false, false)
        repeat(2) {
            database.applyMemberships(listOf(group), true, now)
            database.applyTopics(1001, listOf(topic), null, false, now)
            database.applyMessages(1001, 2001, listOf(message), null, false, now)
        }
        assertEquals(1, database.groups().size)
        assertEquals(1, database.topics(1001).size)
        assertEquals(1, database.messages(2001).size)
        assertEquals(41, database.search("linked dipole").single().messageNumber)
    }

    @Test fun deleteDownloadedDataCannotTouchSuppliedMainDatabasePath() {
        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(mainName), null).use { it.execSQL("CREATE TABLE sentinel(value TEXT NOT NULL)"); it.execSQL("INSERT INTO sentinel VALUES('preserve')") }
        database.writableDatabase
        database.deleteDownloadedData()
        assertFalse(context.getDatabasePath(featureName).exists())
        assertTrue(context.getDatabasePath(mainName).exists())
        SQLiteDatabase.openDatabase(context.getDatabasePath(mainName).path, null, SQLiteDatabase.OPEN_READONLY).use { main ->
            assertEquals("preserve", main.rawQuery("SELECT value FROM sentinel", null).use { it.moveToFirst(); it.getString(0) })
        }
    }
}
