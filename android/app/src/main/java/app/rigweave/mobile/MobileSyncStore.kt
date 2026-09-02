package app.rigweave.mobile

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import java.util.UUID

enum class MobileSyncOperation { CREATE, UPDATE, TOMBSTONE, RESTORE, RESOLVE_CONFLICT, CHECKPOINT }
data class MobileSyncSpaceSummary(val id:String,val stationId:String,val logbookId:String,val mode:String,val authority:String,val keyVersion:Int,val state:String)
data class MobileSyncDeviceSummary(val id:String,val name:String,val platform:String,val state:String,val keyVersion:Int,val lastSeenAt:Long?)
data class MobileSyncDashboard(val spaces:List<MobileSyncSpaceSummary>,val devices:List<MobileSyncDeviceSummary>,val pending:Int,val conflicts:Int,val domains:Int)

/**
 * Local M9 journal. QSO bodies remain in the canonical qso table; the outbox contains only
 * immutable identity/revision metadata and a reference resolved immediately before transport.
 */
class MobileSyncStore(private val database: QsoDatabase) {
    fun enqueueQsoMutation(qso: Qso, operation: MobileSyncOperation) {
        if (!database.writableDatabase.inTransaction()) error("MOBILE_SYNC_OUTBOX_REQUIRES_QSO_TRANSACTION")
        val station = qso.stationProfileId.ifBlank { DEFAULT_LOCAL_STATION }
        database.writableDatabase.rawQuery(
            """SELECT s.id FROM sync_spaces s
               JOIN mobile_station_links l ON l.sync_space_id=s.id
               WHERE s.state='ACTIVE' AND l.local_station_profile_id=? AND l.state='ACTIVE'""".trimIndent(),
            arrayOf(station),
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val spaceId = cursor.getString(0)
                val revision = nextEntityRevision(database.writableDatabase, spaceId, qso.id)
                val sequence = nextDeviceSequence(database.writableDatabase, spaceId)
                val now = System.currentTimeMillis() / 1_000
                val values = ContentValues().apply {
                    put("id", UUID.randomUUID().toString())
                    put("sync_space_id", spaceId)
                    put("domain", "QSO")
                    put("entity_id", qso.id)
                    put("entity_revision", revision)
                    put("device_sequence", sequence)
                    put("operation", operation.name)
                    put("payload_reference", "qso:${qso.id}:$revision")
                    put("state", "PENDING")
                    put("attempt_count", 0)
                    put("created_at", now)
                    put("updated_at", now)
                }
                check(database.writableDatabase.insertOrThrow("sync_outbox", null, values) > 0)
            }
        }
    }

    fun pendingCount(): Int = database.readableDatabase.rawQuery(
        "SELECT COUNT(*) FROM sync_outbox WHERE state IN ('PENDING','RETRY','BLOCKED')", null,
    ).use { cursor -> cursor.moveToFirst(); cursor.getInt(0) }

    fun dashboard(): MobileSyncDashboard {
        val db=database.readableDatabase
        val spaces=db.rawQuery("SELECT id,station_id,logbook_id,mode,authority,key_version,state FROM sync_spaces ORDER BY updated_at DESC",null).use { cursor ->
            buildList { while(cursor.moveToNext()) add(MobileSyncSpaceSummary(cursor.getString(0),cursor.getString(1),cursor.getString(2),cursor.getString(3),cursor.getString(4),cursor.getInt(5),cursor.getString(6))) }
        }
        val devices=db.rawQuery("SELECT id,display_name,platform,state,key_version,last_seen_at FROM sync_devices ORDER BY display_name",null).use { cursor ->
            buildList { while(cursor.moveToNext()) add(MobileSyncDeviceSummary(cursor.getString(0),cursor.getString(1),cursor.getString(2),cursor.getString(3),cursor.getInt(4),if(cursor.isNull(5))null else cursor.getLong(5))) }
        }
        val conflicts=db.rawQuery("SELECT COUNT(*) FROM sync_conflicts WHERE state='OPEN'",null).use { it.moveToFirst();it.getInt(0) }
        val domains=db.rawQuery("SELECT COUNT(*) FROM sync_domain_registry WHERE enabled=1",null).use { it.moveToFirst();it.getInt(0) }
        return MobileSyncDashboard(spaces,devices,pendingCount(),conflicts,domains)
    }

    private fun nextEntityRevision(db: SQLiteDatabase, spaceId: String, entityId: String): Long =
        db.rawQuery(
            "SELECT COALESCE(MAX(entity_revision),0)+1 FROM sync_outbox WHERE sync_space_id=? AND entity_id=?",
            arrayOf(spaceId, entityId),
        ).use { cursor -> cursor.moveToFirst(); cursor.getLong(0) }

    private fun nextDeviceSequence(db: SQLiteDatabase, spaceId: String): Long =
        db.rawQuery(
            "SELECT COALESCE(MAX(device_sequence),0)+1 FROM sync_outbox WHERE sync_space_id=?",
            arrayOf(spaceId),
        ).use { cursor -> cursor.moveToFirst(); cursor.getLong(0) }

    companion object {
        fun createSchema(db: SQLiteDatabase) {
            db.execSQL("""CREATE TABLE IF NOT EXISTS sync_spaces(
                id TEXT PRIMARY KEY, station_id TEXT NOT NULL, logbook_id TEXT NOT NULL,
                mode TEXT NOT NULL CHECK(mode IN ('DIRECT_STATION_SYNC','ENCRYPTED_CLOUD_SYNC')),
                authority TEXT NOT NULL CHECK(authority IN ('LOCAL_DEVICE_ONLY','STATION_CANONICAL','WAVELOG_CANONICAL')),
                key_version INTEGER NOT NULL DEFAULT 1 CHECK(key_version>0), state TEXT NOT NULL DEFAULT 'ACTIVE',
                encrypted_cloud_opt_in INTEGER NOT NULL DEFAULT 0 CHECK(encrypted_cloud_opt_in IN (0,1)),
                created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL)""".trimIndent())
            db.execSQL("""CREATE TABLE IF NOT EXISTS sync_devices(
                id TEXT PRIMARY KEY, sync_space_id TEXT NOT NULL, display_name TEXT NOT NULL, platform TEXT NOT NULL,
                public_identity_pem TEXT NOT NULL, identity_fingerprint_sha256 TEXT NOT NULL,
                key_version INTEGER NOT NULL, state TEXT NOT NULL, last_seen_at INTEGER, revoked_at INTEGER,
                FOREIGN KEY(sync_space_id) REFERENCES sync_spaces(id) ON DELETE CASCADE)""".trimIndent())
            db.execSQL("""CREATE TABLE IF NOT EXISTS sync_device_keys_metadata(
                sync_space_id TEXT NOT NULL, device_id TEXT NOT NULL, key_version INTEGER NOT NULL,
                public_key BLOB NOT NULL, algorithm TEXT NOT NULL, created_at INTEGER NOT NULL, revoked_at INTEGER,
                PRIMARY KEY(sync_space_id,device_id,key_version),
                FOREIGN KEY(sync_space_id) REFERENCES sync_spaces(id) ON DELETE CASCADE)""".trimIndent())
            db.execSQL("""CREATE TABLE IF NOT EXISTS sync_key_versions(
                sync_space_id TEXT NOT NULL, key_version INTEGER NOT NULL, reason TEXT NOT NULL,
                created_at INTEGER NOT NULL, retired_at INTEGER, PRIMARY KEY(sync_space_id,key_version),
                FOREIGN KEY(sync_space_id) REFERENCES sync_spaces(id) ON DELETE CASCADE)""".trimIndent())
            db.execSQL("""CREATE TABLE IF NOT EXISTS sync_outbox(
                id TEXT PRIMARY KEY, sync_space_id TEXT NOT NULL, domain TEXT NOT NULL, entity_id TEXT NOT NULL,
                entity_revision INTEGER NOT NULL, operation TEXT NOT NULL, payload_reference TEXT NOT NULL,
                device_sequence INTEGER NOT NULL CHECK(device_sequence>0),
                state TEXT NOT NULL, attempt_count INTEGER NOT NULL DEFAULT 0, next_attempt_at INTEGER,
                safe_error TEXT, created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL,
                UNIQUE(sync_space_id,domain,entity_id,entity_revision,operation), UNIQUE(sync_space_id,device_sequence),
                FOREIGN KEY(sync_space_id) REFERENCES sync_spaces(id) ON DELETE CASCADE)""".trimIndent())
            db.execSQL("""CREATE TABLE IF NOT EXISTS sync_inbox(
                event_id TEXT PRIMARY KEY, sync_space_id TEXT NOT NULL, origin_device_id TEXT NOT NULL,
                device_sequence INTEGER NOT NULL, domain TEXT NOT NULL, entity_id TEXT NOT NULL,
                operation TEXT NOT NULL, key_version INTEGER NOT NULL, content_hash_sha256 TEXT NOT NULL,
                received_at INTEGER NOT NULL, applied_at INTEGER, UNIQUE(sync_space_id,origin_device_id,device_sequence),
                FOREIGN KEY(sync_space_id) REFERENCES sync_spaces(id) ON DELETE CASCADE)""".trimIndent())
            db.execSQL("""CREATE TABLE IF NOT EXISTS sync_event_results(
                event_id TEXT PRIMARY KEY, result_code TEXT NOT NULL, canonical_revision INTEGER,
                conflict_id TEXT, safe_message TEXT NOT NULL, recorded_at INTEGER NOT NULL,
                FOREIGN KEY(event_id) REFERENCES sync_inbox(event_id) ON DELETE CASCADE)""".trimIndent())
            db.execSQL("""CREATE TABLE IF NOT EXISTS sync_cursors(
                sync_space_id TEXT NOT NULL, peer_id TEXT NOT NULL, accepted_order INTEGER NOT NULL DEFAULT 0,
                updated_at INTEGER NOT NULL, PRIMARY KEY(sync_space_id,peer_id),
                FOREIGN KEY(sync_space_id) REFERENCES sync_spaces(id) ON DELETE CASCADE)""".trimIndent())
            db.execSQL("""CREATE TABLE IF NOT EXISTS sync_checkpoints(
                id TEXT PRIMARY KEY, sync_space_id TEXT NOT NULL, accepted_order INTEGER NOT NULL,
                key_version INTEGER NOT NULL, encrypted_blob BLOB, state TEXT NOT NULL, created_at INTEGER NOT NULL,
                FOREIGN KEY(sync_space_id) REFERENCES sync_spaces(id) ON DELETE CASCADE)""".trimIndent())
            db.execSQL("""CREATE TABLE IF NOT EXISTS sync_conflicts(
                id TEXT PRIMARY KEY, sync_space_id TEXT NOT NULL, domain TEXT NOT NULL, entity_id TEXT NOT NULL,
                local_revision INTEGER NOT NULL, remote_event_id TEXT NOT NULL, differences_json TEXT NOT NULL,
                state TEXT NOT NULL, detected_at INTEGER NOT NULL, resolved_at INTEGER,
                FOREIGN KEY(sync_space_id) REFERENCES sync_spaces(id) ON DELETE CASCADE)""".trimIndent())
            db.execSQL("""CREATE TABLE IF NOT EXISTS sync_peer_state(
                sync_space_id TEXT NOT NULL, peer_id TEXT NOT NULL, last_sequence INTEGER NOT NULL DEFAULT 0,
                last_seen_at INTEGER, state TEXT NOT NULL, PRIMARY KEY(sync_space_id,peer_id),
                FOREIGN KEY(sync_space_id) REFERENCES sync_spaces(id) ON DELETE CASCADE)""".trimIndent())
            db.execSQL("""CREATE TABLE IF NOT EXISTS sync_audit(
                id TEXT PRIMARY KEY, sync_space_id TEXT, event TEXT NOT NULL, outcome TEXT NOT NULL,
                safe_detail TEXT NOT NULL, occurred_at INTEGER NOT NULL)""".trimIndent())
            db.execSQL("""CREATE TABLE IF NOT EXISTS sync_domain_registry(
                domain TEXT PRIMARY KEY, schema_version INTEGER NOT NULL, authority TEXT NOT NULL,
                merge_policy TEXT NOT NULL, privacy TEXT NOT NULL, maximum_bytes INTEGER NOT NULL,
                enabled INTEGER NOT NULL CHECK(enabled IN (0,1)), required INTEGER NOT NULL CHECK(required IN (0,1)))""".trimIndent())
            db.execSQL("""CREATE TABLE IF NOT EXISTS mobile_station_links(
                sync_space_id TEXT PRIMARY KEY, local_station_profile_id TEXT NOT NULL, state TEXT NOT NULL,
                created_at INTEGER NOT NULL, FOREIGN KEY(sync_space_id) REFERENCES sync_spaces(id) ON DELETE CASCADE)""".trimIndent())
            db.execSQL("""CREATE TABLE IF NOT EXISTS mobile_logbook_links(
                sync_space_id TEXT PRIMARY KEY, local_logbook_id TEXT NOT NULL, wavelog_station_id TEXT,
                state TEXT NOT NULL, created_at INTEGER NOT NULL,
                FOREIGN KEY(sync_space_id) REFERENCES sync_spaces(id) ON DELETE CASCADE)""".trimIndent())
            db.execSQL("CREATE INDEX IF NOT EXISTS sync_outbox_state_idx ON sync_outbox(sync_space_id,state,next_attempt_at,created_at)")
            db.execSQL("CREATE INDEX IF NOT EXISTS sync_inbox_entity_idx ON sync_inbox(sync_space_id,domain,entity_id,received_at)")
            db.execSQL("CREATE INDEX IF NOT EXISTS sync_conflict_state_idx ON sync_conflicts(sync_space_id,state,detected_at)")
            seedRequiredDomains(db)
        }

        private fun seedRequiredDomains(db: SQLiteDatabase) {
            val rows = listOf(
                arrayOf("QSO", "REVISION_REVIEW"), arrayOf("QSO_TOMBSTONE_RESTORE", "TOMBSTONE_DOMINATES"),
                arrayOf("CONFIRMATION", "FIELD_AWARE"), arrayOf("CONFLICT_RESOLUTION", "EXPLICIT_OPERATOR"),
                arrayOf("STATION_LOGBOOK_MAPPING", "STATION_CANONICAL"), arrayOf("GOAL", "FIELD_AWARE"),
                arrayOf("WATCHLIST", "FIELD_AWARE"),
            )
            rows.forEach { row -> db.execSQL(
                "INSERT OR IGNORE INTO sync_domain_registry(domain,schema_version,authority,merge_policy,privacy,maximum_bytes,enabled,required) VALUES(?,1,'STATION_CANONICAL',?,'NO_CREDENTIALS',262144,1,1)",
                row,
            ) }
        }
    }
}
