package app.rigweave.mobile

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import java.time.Instant
import java.time.ZoneOffset
import java.util.Locale

enum class ProjectionState { OPTIMISING, READY, REPAIR_REQUIRED }

data class ProjectionHealth(
    val state: ProjectionState,
    val canonicalRows: Int,
    val projectionRows: Int,
    val referenceRows: Int,
    val processedRows: Int,
    val lastError: String = "",
) {
    val progress: Float get() = if (canonicalRows == 0) 1f else (processedRows.toFloat() / canonicalRows).coerceIn(0f, 1f)
}

internal object QsoProjectionStore {
    const val VERSION = 1
    private const val META_VERSION = "version"
    private const val META_STATE = "state"
    private const val META_CURSOR_TIME = "cursor_time"
    private const val META_CURSOR_ID = "cursor_id"
    private const val META_PROCESSED = "processed"
    private const val META_COMPLETED = "completed_at"
    private const val META_REPAIRED = "repaired_at"
    private const val META_ERROR = "last_error"

    fun createSchema(db: SQLiteDatabase, initialState: ProjectionState) {
        db.execSQL("""CREATE TABLE IF NOT EXISTS qso_projection(
            qso_id TEXT PRIMARY KEY NOT NULL,
            created_at INTEGER NOT NULL,
            utc_day TEXT NOT NULL DEFAULT '', utc_month TEXT NOT NULL DEFAULT '', utc_year INTEGER NOT NULL DEFAULT 0,
            callsign_norm TEXT NOT NULL DEFAULT '', frequency_hz INTEGER NOT NULL DEFAULT 0, frequency_rx_hz INTEGER NOT NULL DEFAULT 0,
            band_norm TEXT NOT NULL DEFAULT '', band_rx_norm TEXT NOT NULL DEFAULT '', mode_norm TEXT NOT NULL DEFAULT '',
            submode_norm TEXT NOT NULL DEFAULT '', mode_family TEXT NOT NULL DEFAULT '', station_profile_id TEXT NOT NULL DEFAULT '',
            station_callsign_norm TEXT NOT NULL DEFAULT '', operator_norm TEXT NOT NULL DEFAULT '', name_norm TEXT NOT NULL DEFAULT '',
            qth_norm TEXT NOT NULL DEFAULT '', email_norm TEXT NOT NULL DEFAULT '', country_norm TEXT NOT NULL DEFAULT '', grid_norm TEXT NOT NULL DEFAULT '',
            dxcc TEXT NOT NULL DEFAULT '', continent TEXT NOT NULL DEFAULT '', cq_zone TEXT NOT NULL DEFAULT '', itu_zone TEXT NOT NULL DEFAULT '',
            state_norm TEXT NOT NULL DEFAULT '', county_norm TEXT NOT NULL DEFAULT '', dok_norm TEXT NOT NULL DEFAULT '', iota_norm TEXT NOT NULL DEFAULT '',
            sota_ref_norm TEXT NOT NULL DEFAULT '', wwff_ref_norm TEXT NOT NULL DEFAULT '', pota_ref_norm TEXT NOT NULL DEFAULT '',
            my_iota_norm TEXT NOT NULL DEFAULT '', my_sota_ref_norm TEXT NOT NULL DEFAULT '', my_wwff_ref_norm TEXT NOT NULL DEFAULT '',
            my_pota_ref_norm TEXT NOT NULL DEFAULT '', contest_id_norm TEXT NOT NULL DEFAULT '', propagation_mode TEXT NOT NULL DEFAULT '',
            satellite_name TEXT NOT NULL DEFAULT '', satellite_mode TEXT NOT NULL DEFAULT '', orbit TEXT NOT NULL DEFAULT '',
            radio_model_norm TEXT NOT NULL DEFAULT '', antenna_path_norm TEXT NOT NULL DEFAULT '', tx_power_w INTEGER NOT NULL DEFAULT 0,
            distance_km REAL NOT NULL DEFAULT 0, duration_seconds INTEGER NOT NULL DEFAULT 0,
            paper_received INTEGER NOT NULL DEFAULT 0, lotw_received INTEGER NOT NULL DEFAULT 0, eqsl_received INTEGER NOT NULL DEFAULT 0,
            qrz_received INTEGER NOT NULL DEFAULT 0, clublog_received INTEGER NOT NULL DEFAULT 0, dcl_received INTEGER NOT NULL DEFAULT 0,
            qsl_sent INTEGER NOT NULL DEFAULT 0, qsl_method_norm TEXT NOT NULL DEFAULT '', qsl_received_method_norm TEXT NOT NULL DEFAULT '',
            qsl_via_norm TEXT NOT NULL DEFAULT '', qsl_message_norm TEXT NOT NULL DEFAULT '', lotw_sent INTEGER NOT NULL DEFAULT 0, eqsl_sent INTEGER NOT NULL DEFAULT 0,
            qrz_sent INTEGER NOT NULL DEFAULT 0, clublog_sent INTEGER NOT NULL DEFAULT 0, dcl_sent INTEGER NOT NULL DEFAULT 0,
            sync_state TEXT NOT NULL DEFAULT 'LOCAL', remote_id TEXT NOT NULL DEFAULT '', activation_session_id TEXT NOT NULL DEFAULT '',
            activation_program TEXT NOT NULL DEFAULT '', has_qsl_images INTEGER NOT NULL DEFAULT 0, is_valid INTEGER NOT NULL DEFAULT 1,
            searchable_text TEXT NOT NULL DEFAULT '',
            FOREIGN KEY(qso_id) REFERENCES qso(id) ON DELETE CASCADE
        )""".trimIndent())
        db.execSQL("""CREATE TABLE IF NOT EXISTS qso_reference(
            qso_id TEXT NOT NULL, direction TEXT NOT NULL, program TEXT NOT NULL, reference_norm TEXT NOT NULL,
            PRIMARY KEY(qso_id,direction,program,reference_norm),
            FOREIGN KEY(qso_id) REFERENCES qso(id) ON DELETE CASCADE
        )""".trimIndent())
        db.execSQL("CREATE TABLE IF NOT EXISTS qso_projection_meta(key TEXT PRIMARY KEY,value TEXT NOT NULL)")
        createIndexes(db)
        putMeta(db, META_VERSION, VERSION.toString(), onlyIfMissing = true)
        putMeta(db, META_STATE, initialState.name, onlyIfMissing = true)
        putMeta(db, META_PROCESSED, "0", onlyIfMissing = true)
    }

    private fun createIndexes(db: SQLiteDatabase) {
        listOf(
            "CREATE INDEX IF NOT EXISTS qso_projection_time_idx ON qso_projection(created_at DESC,qso_id)",
            "CREATE INDEX IF NOT EXISTS qso_projection_call_idx ON qso_projection(callsign_norm,created_at DESC)",
            "CREATE INDEX IF NOT EXISTS qso_projection_frequency_idx ON qso_projection(frequency_hz,created_at DESC)",
            "CREATE INDEX IF NOT EXISTS qso_projection_band_time_idx ON qso_projection(band_norm,created_at DESC)",
            "CREATE INDEX IF NOT EXISTS qso_projection_mode_time_idx ON qso_projection(mode_family,submode_norm,created_at DESC)",
            "CREATE INDEX IF NOT EXISTS qso_projection_station_time_idx ON qso_projection(station_profile_id,created_at DESC)",
            "CREATE INDEX IF NOT EXISTS qso_projection_station_call_idx ON qso_projection(station_callsign_norm,created_at DESC)",
            "CREATE INDEX IF NOT EXISTS qso_projection_operator_idx ON qso_projection(operator_norm,created_at DESC)",
            "CREATE INDEX IF NOT EXISTS qso_projection_dxcc_bm_idx ON qso_projection(dxcc,band_norm,mode_family,created_at DESC)",
            "CREATE INDEX IF NOT EXISTS qso_projection_country_idx ON qso_projection(country_norm,created_at DESC)",
            "CREATE INDEX IF NOT EXISTS qso_projection_geo_idx ON qso_projection(continent,cq_zone,itu_zone,state_norm)",
            "CREATE INDEX IF NOT EXISTS qso_projection_grid_idx ON qso_projection(grid_norm,created_at DESC)",
            "CREATE INDEX IF NOT EXISTS qso_projection_contest_idx ON qso_projection(contest_id_norm,created_at DESC)",
            "CREATE INDEX IF NOT EXISTS qso_projection_satellite_idx ON qso_projection(satellite_name,satellite_mode,created_at DESC)",
            "CREATE INDEX IF NOT EXISTS qso_projection_activation_idx ON qso_projection(activation_program,activation_session_id,created_at DESC)",
            "CREATE INDEX IF NOT EXISTS qso_projection_sync_idx ON qso_projection(sync_state,remote_id,created_at DESC)",
            "CREATE INDEX IF NOT EXISTS qso_projection_confirmation_idx ON qso_projection(paper_received,lotw_received,created_at DESC)",
            "CREATE INDEX IF NOT EXISTS qso_reference_program_idx ON qso_reference(program,direction,reference_norm,qso_id)",
            "CREATE INDEX IF NOT EXISTS qso_reference_qso_idx ON qso_reference(qso_id,program,direction)",
        ).forEach(db::execSQL)
    }

    fun write(db: SQLiteDatabase, qso: Qso) {
        val instant = Instant.ofEpochSecond(qso.createdAt).atZone(ZoneOffset.UTC)
        val values = ContentValues().apply {
            put("qso_id", qso.id); put("created_at", qso.createdAt); put("utc_day", instant.toLocalDate().toString())
            put("utc_month", instant.toLocalDate().toString().take(7)); put("utc_year", instant.year)
            put("callsign_norm", norm(qso.callsign)); put("frequency_hz", qso.frequencyHz); put("frequency_rx_hz", qso.frequencyRxHz)
            put("band_norm", norm(qso.band.ifBlank { bandForFrequency(qso.frequencyHz) })); put("band_rx_norm", norm(qso.bandRx))
            put("mode_norm", norm(qso.mode)); put("submode_norm", norm(qso.submode)); put("mode_family", modeFamily(qso))
            put("station_profile_id", qso.stationProfileId); put("station_callsign_norm", norm(qso.stationCallsign)); put("operator_norm", norm(qso.operatorCallsign))
            put("name_norm", norm(qso.name)); put("qth_norm", norm(qso.qth)); put("email_norm", norm(qso.email)); put("country_norm", norm(qso.country)); put("grid_norm", norm(qso.grid))
            put("dxcc", norm(qso.dxcc)); put("continent", norm(qso.continent)); put("cq_zone", qso.cqZone.trim()); put("itu_zone", qso.ituZone.trim())
            put("state_norm", norm(qso.state)); put("county_norm", norm(qso.county)); put("dok_norm", norm(qso.dok)); put("iota_norm", norm(qso.iota))
            put("sota_ref_norm", norm(qso.sotaRef)); put("wwff_ref_norm", norm(qso.wwffRef)); put("pota_ref_norm", norm(qso.potaRef))
            put("my_iota_norm", norm(qso.myIota)); put("my_sota_ref_norm", norm(qso.mySotaRef)); put("my_wwff_ref_norm", norm(qso.myWwffRef)); put("my_pota_ref_norm", norm(qso.myPotaRef))
            put("contest_id_norm", norm(qso.contestId)); put("propagation_mode", norm(qso.propagationMode))
            put("satellite_name", norm(qso.extraAdifFields["SAT_NAME"].orEmpty())); put("satellite_mode", norm(qso.extraAdifFields["SAT_MODE"].orEmpty()))
            put("orbit", norm(qso.extraAdifFields["ORBIT"].orEmpty())); put("radio_model_norm", norm(qso.radioModel)); put("antenna_path_norm", norm(qso.antennaPath))
            put("tx_power_w", qso.txPowerW); put("distance_km", qso.distanceKm); put("duration_seconds", qso.durationSeconds)
            put("paper_received", flag(qso.qslReceived)); put("lotw_received", flag(qso.lotwReceived)); put("eqsl_received", flag(qso.eqslReceived))
            put("qrz_received", flag(qso.qrzReceived)); put("clublog_received", flag(qso.clublogReceived)); put("dcl_received", flag(qso.dclReceived))
            put("qsl_sent", flag(qso.qslSent)); put("qsl_method_norm", norm(qso.qslMethod)); put("qsl_received_method_norm", norm(qso.qslReceivedMethod))
            put("qsl_via_norm", norm(qso.qslVia)); put("qsl_message_norm", norm(qso.qslMessage)); put("lotw_sent", flag(qso.lotwSent)); put("eqsl_sent", flag(qso.eqslSent))
            put("qrz_sent", flag(qso.qrzSent)); put("clublog_sent", flag(qso.clublogSent)); put("dcl_sent", flag(qso.dclSent))
            put("sync_state", norm(qso.syncState).ifBlank { "LOCAL" }); put("remote_id", qso.remoteId); put("activation_session_id", qso.activationSessionId)
            put("activation_program", norm(qso.activationProgram)); put("has_qsl_images", if (qso.qslImages.isBlank() || qso.qslImages.equals("N", true)) 0 else 1)
            put("is_valid", if (qso.callsign.isNotBlank() && qso.frequencyHz > 0 && qso.mode.isNotBlank() && qso.createdAt > 0) 1 else 0)
            put("searchable_text", listOf(qso.callsign,qso.name,qso.qth,qso.notes,qso.comment,qso.country).joinToString(" ") { norm(it) }.take(4_096))
        }
        db.insertWithOnConflict("qso_projection", null, values, SQLiteDatabase.CONFLICT_REPLACE)
        db.delete("qso_reference", "qso_id=?", arrayOf(qso.id))
        references(qso).forEach { (direction, program, reference) ->
            db.insertWithOnConflict("qso_reference", null, ContentValues().apply {
                put("qso_id", qso.id); put("direction", direction); put("program", program); put("reference_norm", reference)
            }, SQLiteDatabase.CONFLICT_IGNORE)
        }
    }

    fun state(db: SQLiteDatabase): ProjectionState = runCatching {
        ProjectionState.valueOf(meta(db, META_STATE).ifBlank { ProjectionState.OPTIMISING.name })
    }.getOrDefault(ProjectionState.REPAIR_REQUIRED)

    fun cursor(db: SQLiteDatabase): Pair<Long, String> = meta(db, META_CURSOR_TIME).toLongOrNull().orZero() to meta(db, META_CURSOR_ID)
    fun processed(db: SQLiteDatabase): Int = meta(db, META_PROCESSED).toIntOrNull() ?: 0

    fun markProgress(db: SQLiteDatabase, createdAt: Long, id: String, processed: Int) {
        putMeta(db, META_CURSOR_TIME, createdAt.toString()); putMeta(db, META_CURSOR_ID, id)
        putMeta(db, META_PROCESSED, processed.toString()); putMeta(db, META_STATE, ProjectionState.OPTIMISING.name)
        putMeta(db, META_ERROR, "")
    }

    fun markReady(db: SQLiteDatabase, processed: Int) {
        putMeta(db, META_PROCESSED, processed.toString()); putMeta(db, META_STATE, ProjectionState.READY.name)
        putMeta(db, META_COMPLETED, (System.currentTimeMillis()/1_000).toString()); putMeta(db, META_ERROR, "")
    }

    fun markRepairRequired(db: SQLiteDatabase, error: String) {
        putMeta(db, META_STATE, ProjectionState.REPAIR_REQUIRED.name); putMeta(db, META_ERROR, error.take(240))
    }

    fun markRepaired(db: SQLiteDatabase) {
        putMeta(db, META_REPAIRED, (System.currentTimeMillis()/1_000).toString()); putMeta(db, META_ERROR, "")
    }

    fun lastError(db: SQLiteDatabase): String = meta(db, META_ERROR)

    fun reset(db: SQLiteDatabase) {
        db.delete("qso_reference", null, null); db.delete("qso_projection", null, null)
        putMeta(db, META_CURSOR_TIME, "0"); putMeta(db, META_CURSOR_ID, ""); putMeta(db, META_PROCESSED, "0")
        putMeta(db, META_STATE, ProjectionState.OPTIMISING.name); putMeta(db, META_ERROR, "")
    }

    private fun references(qso: Qso): Set<Triple<String,String,String>> = buildSet {
        fun addRefs(direction: String, program: String, values: Iterable<String>) = values.map(::norm).filter(String::isNotBlank).forEach { add(Triple(direction,program,it)) }
        addRefs("WORKED","POTA", qso.potaRefs + qso.potaRef); addRefs("MY","POTA", qso.myPotaRefs + qso.myPotaRef)
        addRefs("WORKED","SOTA", listOf(qso.sotaRef)); addRefs("MY","SOTA", listOf(qso.mySotaRef))
        addRefs("WORKED","WWFF", listOf(qso.wwffRef)); addRefs("MY","WWFF", listOf(qso.myWwffRef))
        addRefs("WORKED","IOTA", listOf(qso.iota)); addRefs("MY","IOTA", listOf(qso.myIota))
    }

    private fun meta(db: SQLiteDatabase, key: String): String = db.rawQuery("SELECT value FROM qso_projection_meta WHERE key=?", arrayOf(key)).use {
        if (it.moveToFirst()) it.getString(0) else ""
    }
    private fun putMeta(db: SQLiteDatabase, key: String, value: String, onlyIfMissing: Boolean = false) {
        val conflict = if (onlyIfMissing) SQLiteDatabase.CONFLICT_IGNORE else SQLiteDatabase.CONFLICT_REPLACE
        db.insertWithOnConflict("qso_projection_meta", null, ContentValues().apply { put("key",key); put("value",value) }, conflict)
    }
    private fun norm(value: String) = value.trim().uppercase(Locale.US)
    private fun flag(value: String) = if (norm(value) in setOf("Y","V","YES","S","SENT","UPLOADED","1","TRUE")) 1 else 0
    private fun modeFamily(qso: Qso): String = when (norm(qso.submode.ifBlank { qso.mode })) {
        "CW","CW-R","CWR" -> "CW"; "SSB","USB","LSB","FM","AM" -> "PHONE"; else -> "DIGITAL"
    }
    private fun Long?.orZero() = this ?: 0L
}
