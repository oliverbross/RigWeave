package app.rigweave.mobile

import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class NexusDigiSessionStoreInstrumentedTest {
    private lateinit var context: Context
    private lateinit var store: DigiSessionStore

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase("rigweave-digi.sqlite")
        File(context.filesDir, "digi/sstv").deleteRecursively()
        store = DigiSessionStore(context)
    }

    @After fun tearDown() {
        store.close()
        context.deleteDatabase("rigweave-digi.sqlite")
        File(context.filesDir, "digi/sstv").deleteRecursively()
    }

    @Test fun decodeHistoryIsSeparateAndHardCapped() {
        val now = System.currentTimeMillis() / 1_000
        val rows = (0 until 1_005).map { index ->
            DigiDecodeEvent("id-$index", "session", now + index, "FT8", now, -10f, .1f, 1_000f, "CQ K1ABC FN31")
        }
        store.appendDecodes(rows, hardCap = 1_000)
        assertEquals(1_000, store.recentDecodes(3_000).size)
    }

    @Test fun expiredDecodeRowsAreRemoved() {
        val now = System.currentTimeMillis() / 1_000
        store.appendDecodes(listOf(
            DigiDecodeEvent("old", "session", now - 3 * 86_400, "FT8", now, -10f, 0f, 1_000f, "old"),
            DigiDecodeEvent("new", "session", now, "FT8", now, -10f, 0f, 1_000f, "new"),
        ), retentionDays = 1)
        assertEquals(listOf("new"), store.recentDecodes().map { it.id })
    }

    @Test fun galleryUsesAtomicPrivatePngAndMetadata() {
        val bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
        val item = store.saveSstvPng(bitmap, "Martin 1", 100, 14_230_000, "Home", "F1ABC", "source.jpg", 25)
        assertTrue(File(item.path).isFile)
        assertTrue(item.path.startsWith(context.filesDir.absolutePath))
        assertFalse(File(item.path + ".tmp").exists())
        assertEquals("F1ABC", store.gallery().single().fskId)
    }

    @Test fun pinnedGalleryItemSurvivesExplicitQuotaSweep() {
        val bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
        val item = store.saveSstvPng(bitmap, "Scottie 1", 100, 14_230_000, "Home", "", "", 25)
        store.updateGallery(item.id, caption = "keeper", pinned = true)
        store.enforceGalleryQuota(0)
        assertEquals("keeper", store.gallery().single().caption)
    }
}
