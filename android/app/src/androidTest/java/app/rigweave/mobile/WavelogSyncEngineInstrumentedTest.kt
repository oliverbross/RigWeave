package app.rigweave.mobile

import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class WavelogSyncEngineInstrumentedTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var database: QsoDatabase
    private lateinit var store: WavelogSyncStore
    private lateinit var mutations: QsoMutationCoordinator

    @Before fun openDatabase() {
        context.deleteDatabase(databaseName)
        database = QsoDatabase(context, databaseName)
        store = WavelogSyncStore(database)
        mutations = QsoMutationCoordinator(database, store)
    }

    @After fun closeDatabase() {
        database.close()
        context.deleteDatabase(databaseName)
    }

    @Test fun coordinatorCoalescesEveryLocalMutationAndHonoursStationAndReadOnlyBoundaries() {
        val binding = writableBinding()
        store.saveBinding(binding)
        val local = qso("local", "VK8AAA")
        assertTrue(mutations.save(local))
        assertEquals(WavelogOperation.CREATE, store.pending(binding.id).single().operation)

        mutations.update(local.copy(notes = "edited before first upload"))
        assertEquals(WavelogOperation.CREATE, store.pending(binding.id).single().operation)
        assertTrue(store.pending(binding.id).single().canonicalPayload.contains("edited before first upload"))

        val otherStation = qso("other", "VK8BBB").copy(stationProfileId = "OTHER")
        assertTrue(mutations.save(otherStation))
        assertNull(store.pendingFor(binding.id, otherStation.id))

        mutations.delete(local.id)
        assertNull(database.qso(local.id))
        assertTrue(store.pending(binding.id).isEmpty())

        store.saveBinding(binding.copy(state = WavelogBindingState.READ_ONLY))
        val readOnly = qso("readonly", "VK8RO")
        assertTrue(mutations.save(readOnly))
        assertEquals(1, store.blockedCreates(binding.id).size)
        assertTrue(store.blockedCreates(binding.id).single().lastError.contains("Read-only"))
    }

    @Test fun jsonCreateLinksBeforeAcceptanceThenPatchAndTombstoneDeleteUseExactRemoteIdentity() {
        val binding = writableBinding()
        store.saveBinding(binding)
        val requests = mutableListOf<WavelogV2Request>()
        var remote = remoteRow("71", "VK8NEW", notes = "")
        val client = client { request ->
            requests += request
            when (request.method) {
                "POST" -> WavelogV2Response(201, envelope(remote))
                "PATCH" -> {
                    remote = remoteRow("71", "VK8NEW", notes = "corrected")
                    WavelogV2Response(200, envelope(remote))
                }
                "GET" -> WavelogV2Response(200, envelope(remote))
                "DELETE" -> WavelogV2Response(204, "")
                else -> error("Unexpected ${request.method}")
            }
        }
        val engine = WavelogSyncEngine(database, store, client, mutations)
        val local = qso("create-update-delete", "VK8NEW")
        assertTrue(mutations.save(local))
        assertEquals(1, engine.drainOutbox(binding))
        assertEquals("71", store.link(binding.id, local.id)?.remoteQsoId)
        assertTrue(store.pending(binding.id).isEmpty())

        mutations.update(local.copy(notes = "corrected"))
        assertEquals(WavelogOperation.UPDATE, store.pending(binding.id).single().operation)
        assertEquals(1, engine.drainOutbox(binding))
        assertTrue(requests.single { it.method == "PATCH" }.body.orEmpty().contains("\"notes\":\"corrected\""))

        mutations.delete(local.id)
        assertNull(database.qso(local.id))
        assertNull(store.link(binding.id, local.id))
        assertTrue(store.tombstone(binding.id, local.id) != null)
        assertEquals(1, engine.drainOutbox(binding))
        assertTrue(requests.any { it.method == "DELETE" && it.url.endsWith("/qso/71") })
        assertTrue(store.tombstone(binding.id, local.id)?.first?.acknowledgedAt != null)
    }

    @Test fun fastEntryBatchAndAdifImportUseTheSameMappedOutbox() {
        val binding = writableBinding()
        store.saveBinding(binding)
        val batch = mutations.saveBatch(listOf(qso("batch-1", "VK8B1"), qso("batch-2", "VK8B2")), QsoOrigin.IMPORT)
        assertEquals(2 to 0, batch)
        val imported = mutations.importADIF(
            "<CALL:5>VK8AD<QSO_DATE:8>20231115<TIME_ON:6>010203<FREQ:6>14.060<MODE:2>CW<BAND:3>20m<EOR>")
        assertEquals(1 to 0, imported)
        assertEquals(3, store.pending(binding.id).size)
        assertTrue(store.pending(binding.id).all { it.operation == WavelogOperation.CREATE })
    }

    @Test fun ambiguousCreateIsNeverRetriedAndNaturalScanRecoversTheRemoteId() {
        val binding = writableBinding()
        store.saveBinding(binding)
        val local = qso("ambiguous", "VK8AMB")
        mutations.save(local)
        var posts = 0
        val client = client { request ->
            when {
                request.method == "POST" -> { posts++; throw IOException("response lost") }
                request.method == "GET" && request.url.contains("/qso?") ->
                    WavelogV2Response(200, pageEnvelope(listOf(remoteRow("88", "VK8AMB")), false, 1))
                else -> error("Unexpected request ${request.method} ${request.url}")
            }
        }
        val engine = WavelogSyncEngine(database, store, client, mutations)
        assertEquals(0, engine.drainOutbox(binding))
        assertEquals(1, posts)
        assertEquals(1, store.blockedCreates(binding.id).size)

        val summary = engine.quickSync(binding)
        assertEquals(1, summary.linked)
        assertEquals("88", store.link(binding.id, local.id)?.remoteQsoId)
        assertTrue(store.blockedCreates(binding.id).isEmpty())
        assertEquals(1, posts)
    }

    @Test fun cancelledFullScanResumesCheckpointAndOnlyCompletedScanInfersHistoricDeletion() {
        val binding = writableBinding()
        store.saveBinding(binding)
        val historic = qso("historic", "VK8OLD")
        database.save(historic, QsoOrigin.REMOTE_SYNC)
        val baseline = canonical(historic)
        store.saveLink(WavelogRemoteLink(binding.id, historic.id, "5", baseline.hash, baseline.encoded))
        var cancelled = false
        val firstClient = client { request ->
            require(request.url.contains("page=1"))
            WavelogV2Response(200, pageEnvelope(listOf(remoteRow("99", "VK8PAGE")), true, 2))
        }
        val first = WavelogSyncEngine(database, store, firstClient, mutations).fullReconcile(binding,
            isCancelled = { cancelled },
            onProgress = { _, _ -> cancelled = true })
        assertTrue(first.cancelled)
        assertFalse(first.completed)
        assertTrue(database.qso(historic.id) != null)
        assertEquals(2, store.checkpoint(binding.id, "FULL")?.page)

        database.close()
        database = QsoDatabase(context, databaseName)
        store = WavelogSyncStore(database)
        mutations = QsoMutationCoordinator(database, store)
        val secondClient = client { request ->
            require(request.url.contains("page=2"))
            WavelogV2Response(200, pageEnvelope(emptyList(), false, 2))
        }
        val second = WavelogSyncEngine(database, store, secondClient, mutations).fullReconcile(binding)
        assertEquals(2, second.resumedFromPage)
        assertTrue(second.completed)
        assertEquals(1, second.deleted)
        assertNull(database.qso(historic.id))
    }

    @Test fun quickPullsRecentEditAndFullFindsEditBeyondTheQuickOverlap() {
        val binding = writableBinding()
        store.saveBinding(binding)
        val recent = qso("recent-edit", "VK8REC").copy(notes = "base")
        val historic = qso("historic-edit", "VK8HIS").copy(createdAt = 1_699_999_000, notes = "base")
        listOf(recent to "101", historic to "7").forEach { (qso, remoteId) ->
            database.save(qso, QsoOrigin.REMOTE_SYNC)
            val baseline = canonical(qso)
            store.saveLink(WavelogRemoteLink(binding.id, qso.id, remoteId, baseline.hash, baseline.encoded))
        }
        val quick = WavelogSyncEngine(database, store, client { request ->
            require(request.url.contains("page=1"))
            WavelogV2Response(200, pageEnvelope(listOf(remoteRow("101", "VK8REC", "recent remote")), false, 1))
        }, mutations).quickSync(binding)
        assertEquals(1, quick.pulled)
        assertEquals("recent remote", database.qso(recent.id)?.notes)
        assertEquals("base", database.qso(historic.id)?.notes)

        val full = WavelogSyncEngine(database, store, client { request ->
            if (request.url.contains("page=1")) WavelogV2Response(200,
                pageEnvelope(listOf(remoteRow("101", "VK8REC", "recent remote")), true, 2))
            else WavelogV2Response(200,
                pageEnvelope(listOf(remoteRow("7", "VK8HIS", "historic remote")), false, 2))
        }, mutations).fullReconcile(binding)
        assertTrue(full.completed)
        assertEquals("historic remote", database.qso(historic.id)?.notes)
    }

    @Test fun threeWayConflictPersistsFieldValuesAndKeepRemoteResolvesWithoutEcho() {
        val binding = writableBinding()
        store.saveBinding(binding)
        val base = qso("conflict", "VK8CON").copy(notes = "base")
        database.save(base, QsoOrigin.REMOTE_SYNC)
        val baseCanonical = canonical(base)
        store.saveLink(WavelogRemoteLink(binding.id, base.id, "44", baseCanonical.hash, baseCanonical.encoded))
        mutations.update(base.copy(notes = "local"))
        val remote = remoteRow("44", "VK8CON", notes = "remote")
        val client = client { request ->
            when {
                request.method == "PATCH" -> throw IOException("leave local update pending")
                request.method == "GET" && request.url.contains("/qso?") ->
                    WavelogV2Response(200, pageEnvelope(listOf(remote), false, 1))
                else -> error("Unexpected request")
            }
        }
        val engine = WavelogSyncEngine(database, store, client, mutations)
        val summary = engine.fullReconcile(binding)
        assertEquals(1, summary.conflicts)
        val conflict = store.conflicts(binding.id).single()
        assertEquals(setOf("NOTES"), conflict.conflictingFields)
        assertEquals("local", CanonicalQso.decode(conflict.localCanonical).fields["NOTES"])
        assertEquals("remote", CanonicalQso.decode(conflict.remoteCanonical).fields["NOTES"])

        engine.resolveConflict(binding, conflict, WavelogConflictState.KEEP_REMOTE)
        assertEquals("remote", database.qso(base.id)?.notes)
        assertEquals(WavelogConflictState.KEEP_REMOTE, store.conflicts(binding.id).single().state)
        assertEquals(0, outstandingWrites(binding.id, base.id))
    }

    @Test fun keepLocalAndMergedResolutionsRequeueExactlyOneOperatorChoice() {
        val binding = writableBinding()
        store.saveBinding(binding)
        val base = qso("resolution-matrix", "VK8RES").copy(notes = "base")
        database.save(base, QsoOrigin.REMOTE_SYNC)
        val baseline = canonical(base)
        val local = canonical(base.copy(notes = "local"))
        val remote = canonical(base.copy(notes = "remote"))
        store.saveLink(WavelogRemoteLink(binding.id, base.id, "62", baseline.hash, baseline.encoded))
        val engine = WavelogSyncEngine(database, store, client { error("Network is not used by resolution") }, mutations)

        val keepLocal = conflict(binding, base.id, "62", baseline, local, remote)
        store.saveConflict(keepLocal)
        engine.resolveConflict(binding, keepLocal, WavelogConflictState.KEEP_LOCAL)
        assertEquals(1, outstandingWrites(binding.id, base.id))
        store.pendingFor(binding.id, base.id)?.let {
            store.updateOutbox(it.copy(state = WavelogOutboxState.ACCEPTED))
        }

        val mergeConflict = conflict(binding, base.id, "62", baseline, local, remote)
        store.saveConflict(mergeConflict)
        val merged = canonical(base.copy(notes = "operator merge"))
        engine.resolveConflict(binding, mergeConflict, WavelogConflictState.MERGED, merged)
        assertEquals("operator merge", database.qso(base.id)?.notes)
        assertEquals(1, outstandingWrites(binding.id, base.id))
        assertEquals(WavelogConflictState.MERGED, store.conflicts(binding.id).last().state)
    }

    @Test fun remoteChangeAfterLocalDeleteBlocksDeleteAndKeepRemoteRestoresTheQso() {
        val binding = writableBinding()
        store.saveBinding(binding)
        val base = qso("delete-conflict", "VK8DEL").copy(notes = "base")
        database.save(base, QsoOrigin.REMOTE_SYNC)
        val baseline = canonical(base)
        store.saveLink(WavelogRemoteLink(binding.id, base.id, "73", baseline.hash, baseline.encoded))
        mutations.delete(base.id)
        var deletes = 0
        val changedRemote = remoteRow("73", "VK8DEL", notes = "changed remotely")
        val engine = WavelogSyncEngine(database, store, client { request ->
            when (request.method) {
                "GET" -> WavelogV2Response(200, envelope(changedRemote))
                "DELETE" -> { deletes++; WavelogV2Response(204, "") }
                else -> error("Unexpected request")
            }
        }, mutations)
        assertEquals(0, engine.drainOutbox(binding))
        assertEquals(0, deletes)
        val conflict = store.conflicts(binding.id).single()
        assertEquals(setOf("LOCAL_DELETE"), conflict.conflictingFields)

        engine.resolveConflict(binding, conflict, WavelogConflictState.KEEP_REMOTE)
        assertEquals("changed remotely", database.qso(base.id)?.notes)
        assertEquals("73", store.link(binding.id, base.id)?.remoteQsoId)
        assertTrue(store.pending(binding.id).isEmpty())
    }

    @Test fun versionEightMigrationAddsDurableTombstoneBaselineWithoutLosingQso() {
        val saved = qso("migration", "VK8V8")
        assertTrue(database.save(saved))
        database.close()
        SQLiteDatabase.openDatabase(context.getDatabasePath(databaseName).path, null, SQLiteDatabase.OPEN_READWRITE).use { raw ->
            raw.execSQL("ALTER TABLE wavelog_tombstone DROP COLUMN baseline_canonical")
            raw.version = 8
        }
        database = QsoDatabase(context, databaseName)
        store = WavelogSyncStore(database)
        mutations = QsoMutationCoordinator(database, store)
        assertEquals("VK8V8", database.qso(saved.id)?.callsign)
        val columns = mutableSetOf<String>()
        database.readableDatabase.rawQuery("PRAGMA table_info(wavelog_tombstone)", null).use { cursor ->
            while (cursor.moveToNext()) columns += cursor.getString(1)
        }
        assertTrue("baseline_canonical" in columns)
    }

    private fun writableBinding() = WavelogBinding(
        id = "binding", baseUrl = "https://example.invalid/index.php", credentialAlias = "keystore",
        apiGeneration = WavelogApiGeneration.V2,
        capabilities = WavelogCapabilities(
            setOf("qso:read", "qso:write", "qso:delete", "station:read"),
            canReadQsos = true, canWriteQsos = true, canDeleteQsos = true, canReadStations = true,
        ),
        remoteStationId = "11", localStationProfileId = DEFAULT_LOCAL_STATION,
    )

    private fun qso(id: String, call: String) = Qso(
        id = id, callsign = call, frequencyHz = 14_060_000, mode = "CW",
        rstSent = "599", rstReceived = "599", createdAt = 1_700_000_300,
        band = "20m", stationCallsign = "OM0RX", stationProfileId = "",
    )

    private fun canonical(qso: Qso) = WavelogCanonicalizer.fromAdif(database.toADIF(qso))

    private fun conflict(binding: WavelogBinding, localId: String, remoteId: String, baseline: CanonicalQso,
        local: CanonicalQso, remote: CanonicalQso) = WavelogConflict(
        java.util.UUID.randomUUID().toString(), binding.id, localId, remoteId,
        baseline.encoded, local.encoded, remote.encoded, setOf("NOTES"),
        WavelogConflictState.OPEN, System.currentTimeMillis() / 1_000,
    )

    private fun outstandingWrites(bindingId: String, localId: String): Int = database.readableDatabase.rawQuery(
        "SELECT COUNT(*) FROM wavelog_outbox WHERE binding_id=? AND local_qso_id=? " +
            "AND operation IN ('CREATE','UPDATE') AND state<>'ACCEPTED'",
        arrayOf(bindingId, localId),
    ).use { cursor -> cursor.moveToFirst(); cursor.getInt(0) }

    private fun remoteRow(id: String, call: String, notes: String = "") = JSONObject()
        .put("id", id).put("call", call).put("qso_date", "2023-11-14 22:18:20")
        .put("band", "20m").put("mode", "CW").put("freq", 14_060_000)
        .put("rst_sent", "599").put("rst_rcvd", "599").put("station_callsign", "OM0RX")
        .put("notes", notes)

    private fun envelope(row: JSONObject) = JSONObject().put("data", row).put("meta", JSONObject()).toString()

    private fun pageEnvelope(rows: List<JSONObject>, hasMore: Boolean, totalPages: Int) = JSONObject()
        .put("data", org.json.JSONArray(rows))
        .put("meta", JSONObject().put("page", if (hasMore) 1 else totalPages).put("total_pages", totalPages)
            .put("total", rows.size).put("has_more", hasMore)).toString()

    private fun client(transport: (WavelogV2Request) -> WavelogV2Response) =
        WavelogApiV2Client("https://example.invalid", "wl2_test", WavelogV2Transport(transport))

    companion object { private const val databaseName = "rigweave-wavelog-sync-instrumented.sqlite" }
}
