package app.rigweave.mobile

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import org.json.JSONArray
import java.util.UUID

internal fun createWavelogSyncTables(db: SQLiteDatabase) {
    db.execSQL("""CREATE TABLE IF NOT EXISTS wavelog_binding(
        id TEXT PRIMARY KEY, provider TEXT NOT NULL DEFAULT 'WAVELOG', base_url TEXT NOT NULL,
        credential_alias TEXT NOT NULL, api_generation TEXT NOT NULL, scopes_json TEXT NOT NULL DEFAULT '[]',
        token_owner TEXT NOT NULL DEFAULT '', remote_station_id TEXT NOT NULL DEFAULT '',
        remote_station_uuid TEXT NOT NULL DEFAULT '', remote_station_name TEXT NOT NULL DEFAULT '',
        local_station_profile_id TEXT NOT NULL DEFAULT '', state TEXT NOT NULL,
        downstream_policy TEXT NOT NULL DEFAULT 'WAVELOG_AUTHORITY', last_quick_sync INTEGER,
        last_full_reconcile INTEGER, high_water TEXT NOT NULL DEFAULT '', last_error_class TEXT NOT NULL DEFAULT 'NONE',
        last_error_summary TEXT NOT NULL DEFAULT '', tested_release TEXT NOT NULL DEFAULT '')""".trimIndent())
    db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS one_writable_wavelog_binding ON wavelog_binding(provider) WHERE state='ENABLED'")
    db.execSQL("""CREATE TABLE IF NOT EXISTS wavelog_remote_link(
        binding_id TEXT NOT NULL, local_qso_id TEXT NOT NULL, remote_qso_id TEXT NOT NULL,
        baseline_hash TEXT NOT NULL, baseline_canonical TEXT NOT NULL, remote_updated_at TEXT NOT NULL DEFAULT '',
        PRIMARY KEY(binding_id,local_qso_id), UNIQUE(binding_id,remote_qso_id),
        FOREIGN KEY(binding_id) REFERENCES wavelog_binding(id) ON DELETE CASCADE,
        FOREIGN KEY(local_qso_id) REFERENCES qso(id) ON DELETE CASCADE)""".trimIndent())
    db.execSQL("""CREATE TABLE IF NOT EXISTS wavelog_outbox(
        id TEXT PRIMARY KEY, binding_id TEXT NOT NULL, local_qso_id TEXT NOT NULL, operation TEXT NOT NULL,
        idempotency_key TEXT NOT NULL UNIQUE, payload_hash TEXT NOT NULL, canonical_payload TEXT NOT NULL,
        state TEXT NOT NULL, attempt_count INTEGER NOT NULL DEFAULT 0, next_attempt_at INTEGER,
        last_error TEXT NOT NULL DEFAULT '', created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL,
        FOREIGN KEY(binding_id) REFERENCES wavelog_binding(id) ON DELETE CASCADE)""".trimIndent())
    db.execSQL("CREATE INDEX IF NOT EXISTS wavelog_outbox_queue ON wavelog_outbox(binding_id,state,next_attempt_at,created_at)")
    db.execSQL("""CREATE TABLE IF NOT EXISTS wavelog_checkpoint(
        binding_id TEXT NOT NULL, kind TEXT NOT NULL, page INTEGER NOT NULL DEFAULT 1,
        high_water TEXT NOT NULL DEFAULT '', overlap_hash TEXT NOT NULL DEFAULT '', completed INTEGER NOT NULL DEFAULT 0,
        updated_at INTEGER NOT NULL, PRIMARY KEY(binding_id,kind),
        FOREIGN KEY(binding_id) REFERENCES wavelog_binding(id) ON DELETE CASCADE)""".trimIndent())
    db.execSQL("""CREATE TABLE IF NOT EXISTS wavelog_conflict(
        id TEXT PRIMARY KEY, binding_id TEXT NOT NULL, local_qso_id TEXT NOT NULL, remote_qso_id TEXT NOT NULL,
        baseline_canonical TEXT NOT NULL, local_canonical TEXT NOT NULL, remote_canonical TEXT NOT NULL,
        conflicting_fields_json TEXT NOT NULL, state TEXT NOT NULL, created_at INTEGER NOT NULL, resolved_at INTEGER,
        FOREIGN KEY(binding_id) REFERENCES wavelog_binding(id) ON DELETE CASCADE)""".trimIndent())
    db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS wavelog_open_conflict ON wavelog_conflict(binding_id,local_qso_id) WHERE state='OPEN'")
    db.execSQL("""CREATE TABLE IF NOT EXISTS wavelog_tombstone(
        binding_id TEXT NOT NULL, local_qso_id TEXT NOT NULL, remote_qso_id TEXT NOT NULL DEFAULT '',
        canonical_hash TEXT NOT NULL DEFAULT '', deleted_at INTEGER NOT NULL, acknowledged_at INTEGER,
        PRIMARY KEY(binding_id,local_qso_id), FOREIGN KEY(binding_id) REFERENCES wavelog_binding(id) ON DELETE CASCADE)""".trimIndent())
}

class WavelogSyncStore(private val database: QsoDatabase) {
    fun saveBinding(binding: WavelogBinding) {
        val values = ContentValues().apply {
            put("id", binding.id); put("provider", "WAVELOG"); put("base_url", binding.baseUrl)
            put("credential_alias", binding.credentialAlias); put("api_generation", binding.apiGeneration.name)
            put("scopes_json", JSONArray(binding.capabilities.scopes.toList()).toString()); put("token_owner", binding.tokenOwner)
            put("remote_station_id", binding.remoteStationId); put("remote_station_uuid", binding.remoteStationUuid)
            put("remote_station_name", binding.remoteStationName); put("local_station_profile_id", binding.localStationProfileId)
            put("state", binding.state.name); put("downstream_policy", binding.downstreamPolicy)
            binding.lastQuickSync?.let { put("last_quick_sync", it) }; binding.lastFullReconcile?.let { put("last_full_reconcile", it) }
            put("high_water", binding.highWater); put("last_error_class", binding.lastErrorClass.name)
            put("last_error_summary", binding.lastErrorSummary.take(500)); put("tested_release", binding.testedRelease)
        }
        database.writableDatabase.insertWithOnConflict("wavelog_binding", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun activeBinding(): WavelogBinding? = database.readableDatabase.rawQuery(
        "SELECT id,base_url,credential_alias,api_generation,scopes_json,token_owner,remote_station_id,remote_station_uuid,remote_station_name,local_station_profile_id,state,downstream_policy,last_quick_sync,last_full_reconcile,high_water,last_error_class,last_error_summary,tested_release FROM wavelog_binding WHERE state IN ('ENABLED','READ_ONLY') ORDER BY CASE state WHEN 'ENABLED' THEN 0 ELSE 1 END LIMIT 1", null
    ).use { cursor ->
        if (!cursor.moveToFirst()) return@use null
        val scopes = runCatching { JSONArray(cursor.getString(4)).jsonStringList().toSet() }.getOrDefault(emptySet())
        WavelogBinding(
            id = cursor.getString(0), baseUrl = cursor.getString(1), credentialAlias = cursor.getString(2),
            apiGeneration = WavelogApiGeneration.valueOf(cursor.getString(3)), capabilities = capabilities(scopes),
            tokenOwner = cursor.getString(5), remoteStationId = cursor.getString(6), remoteStationUuid = cursor.getString(7),
            remoteStationName = cursor.getString(8), localStationProfileId = cursor.getString(9),
            state = WavelogBindingState.valueOf(cursor.getString(10)), downstreamPolicy = cursor.getString(11),
            lastQuickSync = cursor.getLong(12).takeUnless { cursor.isNull(12) },
            lastFullReconcile = cursor.getLong(13).takeUnless { cursor.isNull(13) }, highWater = cursor.getString(14),
            lastErrorClass = WavelogErrorClass.valueOf(cursor.getString(15)), lastErrorSummary = cursor.getString(16),
            testedRelease = cursor.getString(17),
        )
    }

    fun saveWithOutbox(qso: Qso, binding: WavelogBinding, origin: QsoOrigin = QsoOrigin.OPERATOR): Boolean =
        database.transaction {
            val inserted = database.save(qso, origin)
            if (inserted && origin != QsoOrigin.REMOTE_SYNC && binding.state == WavelogBindingState.ENABLED) {
                enqueue(binding.id, qso.id, WavelogOperation.CREATE, WavelogCanonicalizer.fromAdif(database.toADIF(qso)))
            }
            inserted
        }

    fun enqueue(bindingId: String, localQsoId: String, operation: WavelogOperation, canonical: CanonicalQso): String {
        val now = System.currentTimeMillis() / 1_000
        val existing = if (operation == WavelogOperation.UPDATE) pendingFor(bindingId, localQsoId) else null
        val id = existing?.id ?: UUID.randomUUID().toString()
        val key = existing?.idempotencyKey ?: "$bindingId:$localQsoId:${operation.name}:${UUID.randomUUID()}"
        val values = ContentValues().apply {
            put("id", id); put("binding_id", bindingId); put("local_qso_id", localQsoId); put("operation", operation.name)
            put("idempotency_key", key); put("payload_hash", canonical.hash); put("canonical_payload", canonical.encoded)
            put("state", WavelogOutboxState.PENDING.name); put("attempt_count", existing?.attemptCount ?: 0)
            put("last_error", ""); put("created_at", existing?.createdAt ?: now); put("updated_at", now)
        }
        database.writableDatabase.insertWithOnConflict("wavelog_outbox", null, values, SQLiteDatabase.CONFLICT_REPLACE)
        return id
    }

    fun pending(bindingId: String, now: Long = System.currentTimeMillis() / 1_000): List<WavelogOutboxEntry> =
        outbox("binding_id=? AND state IN ('PENDING','RETRY_WAIT') AND (next_attempt_at IS NULL OR next_attempt_at<=?) ORDER BY created_at LIMIT 100", arrayOf(bindingId, now.toString()))

    fun pendingFor(bindingId: String, localQsoId: String): WavelogOutboxEntry? =
        outbox("binding_id=? AND local_qso_id=? AND state IN ('PENDING','RETRY_WAIT') ORDER BY created_at DESC LIMIT 1", arrayOf(bindingId, localQsoId)).firstOrNull()

    fun updateOutbox(entry: WavelogOutboxEntry) {
        database.writableDatabase.execSQL("UPDATE wavelog_outbox SET state=?,attempt_count=?,next_attempt_at=?,last_error=?,updated_at=? WHERE id=?",
            arrayOf(entry.state.name, entry.attemptCount, entry.nextAttemptAt, entry.lastError.take(500), entry.updatedAt, entry.id))
    }

    fun saveLink(link: WavelogRemoteLink) {
        database.writableDatabase.execSQL("""INSERT OR REPLACE INTO wavelog_remote_link
            (binding_id,local_qso_id,remote_qso_id,baseline_hash,baseline_canonical,remote_updated_at) VALUES(?,?,?,?,?,?)""".trimIndent(),
            arrayOf(link.bindingId, link.localQsoId, link.remoteQsoId, link.baselineHash, link.baselineCanonical, link.remoteUpdatedAt))
    }

    fun link(bindingId: String, localQsoId: String): WavelogRemoteLink? = database.readableDatabase.rawQuery(
        "SELECT remote_qso_id,baseline_hash,baseline_canonical,remote_updated_at FROM wavelog_remote_link WHERE binding_id=? AND local_qso_id=?",
        arrayOf(bindingId, localQsoId)).use { cursor -> if (!cursor.moveToFirst()) null else WavelogRemoteLink(
            bindingId, localQsoId, cursor.getString(0), cursor.getString(1), cursor.getString(2), cursor.getString(3)) }

    fun linkByRemote(bindingId: String, remoteQsoId: String): WavelogRemoteLink? = database.readableDatabase.rawQuery(
        "SELECT local_qso_id,baseline_hash,baseline_canonical,remote_updated_at FROM wavelog_remote_link WHERE binding_id=? AND remote_qso_id=?",
        arrayOf(bindingId, remoteQsoId)).use { cursor -> if (!cursor.moveToFirst()) null else WavelogRemoteLink(
            bindingId, cursor.getString(0), remoteQsoId, cursor.getString(1), cursor.getString(2), cursor.getString(3)) }

    fun saveCheckpoint(checkpoint: WavelogSyncCheckpoint) {
        database.writableDatabase.execSQL("INSERT OR REPLACE INTO wavelog_checkpoint(binding_id,kind,page,high_water,overlap_hash,completed,updated_at) VALUES(?,?,?,?,?,?,?)",
            arrayOf(checkpoint.bindingId, checkpoint.kind, checkpoint.page, checkpoint.highWater, checkpoint.overlapHash, if (checkpoint.completed) 1 else 0, checkpoint.updatedAt))
    }

    fun saveConflict(conflict: WavelogConflict) {
        database.writableDatabase.execSQL("INSERT OR REPLACE INTO wavelog_conflict(id,binding_id,local_qso_id,remote_qso_id,baseline_canonical,local_canonical,remote_canonical,conflicting_fields_json,state,created_at,resolved_at) VALUES(?,?,?,?,?,?,?,?,?,?,?)",
            arrayOf(conflict.id, conflict.bindingId, conflict.localQsoId, conflict.remoteQsoId, conflict.baselineCanonical,
                conflict.localCanonical, conflict.remoteCanonical, JSONArray(conflict.conflictingFields.toList()).toString(),
                conflict.state.name, conflict.createdAt, conflict.resolvedAt))
    }

    fun openConflicts(bindingId: String): Int = database.readableDatabase.rawQuery(
        "SELECT COUNT(*) FROM wavelog_conflict WHERE binding_id=? AND state='OPEN'", arrayOf(bindingId)
    ).use { if (it.moveToFirst()) it.getInt(0) else 0 }

    private fun outbox(where: String, args: Array<String>): List<WavelogOutboxEntry> = buildList {
        database.readableDatabase.rawQuery("SELECT id,binding_id,local_qso_id,operation,idempotency_key,payload_hash,canonical_payload,state,attempt_count,next_attempt_at,last_error,created_at,updated_at FROM wavelog_outbox WHERE $where", args).use { cursor ->
            while (cursor.moveToNext()) add(WavelogOutboxEntry(
                cursor.getString(0), cursor.getString(1), cursor.getString(2), WavelogOperation.valueOf(cursor.getString(3)),
                cursor.getString(4), cursor.getString(5), cursor.getString(6), WavelogOutboxState.valueOf(cursor.getString(7)),
                cursor.getInt(8), cursor.getLong(9).takeUnless { cursor.isNull(9) }, cursor.getString(10), cursor.getLong(11), cursor.getLong(12)))
        }
    }

    private fun capabilities(scopes: Set<String>) = WavelogCapabilities(
        scopes = scopes, canReadQsos = "qso:read" in scopes, canWriteQsos = "qso:write" in scopes,
        canDeleteQsos = "qso:delete" in scopes, canReadStations = "station:read" in scopes,
        canReadStatistics = "statistic:read" in scopes,
    )
}
