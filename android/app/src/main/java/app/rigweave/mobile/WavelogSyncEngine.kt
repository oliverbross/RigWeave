package app.rigweave.mobile

import org.json.JSONObject
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID

data class WavelogConnectionInspection(
    val token: WavelogTokenMetadata,
    val stations: List<WavelogV2Station>,
)

data class WavelogSyncSummary(
    val imported: Int = 0,
    val linked: Int = 0,
    val pulled: Int = 0,
    val queuedForPush: Int = 0,
    val merged: Int = 0,
    val ambiguous: Int = 0,
    val conflicts: Int = 0,
    val rejected: Int = 0,
    val pages: Int = 0,
) {
    operator fun plus(other: WavelogSyncSummary) = WavelogSyncSummary(
        imported + other.imported, linked + other.linked, pulled + other.pulled,
        queuedForPush + other.queuedForPush, merged + other.merged, ambiguous + other.ambiguous,
        conflicts + other.conflicts, rejected + other.rejected, pages + other.pages,
    )
}

class WavelogSyncEngine(
    private val database: QsoDatabase,
    private val store: WavelogSyncStore,
    private val client: WavelogApiV2Client,
) {
    fun inspectConnection(): WavelogConnectionInspection {
        val token = client.tokenMetadata()
        val stations = if (token.capabilities.canReadStations) client.stations() else emptyList()
        return WavelogConnectionInspection(token, stations)
    }

    fun initialSync(binding: WavelogBinding, onProgress: (Int, WavelogSyncSummary) -> Unit = { _, _ -> }): WavelogSyncSummary {
        require(binding.capabilities.canReadQsos) { "qso:read scope is required" }
        require(binding.remoteStationId.isNotBlank()) { "Select a Wavelog station before initial sync" }
        return pagedSync(binding, "INITIAL", startPage = 1, maxPages = 10_000, onProgress = onProgress)
    }

    fun quickSync(binding: WavelogBinding, overlapPages: Int = 3,
        onProgress: (Int, WavelogSyncSummary) -> Unit = { _, _ -> }): WavelogSyncSummary {
        drainOutbox(binding)
        return pagedSync(binding, "QUICK", startPage = 1, maxPages = overlapPages.coerceIn(1, 10), onProgress = onProgress)
    }

    fun fullReconcile(binding: WavelogBinding,
        onProgress: (Int, WavelogSyncSummary) -> Unit = { _, _ -> }): WavelogSyncSummary {
        drainOutbox(binding)
        return pagedSync(binding, "FULL", startPage = 1, maxPages = 10_000, onProgress = onProgress)
    }

    fun drainOutbox(binding: WavelogBinding): Int {
        if (!binding.capabilities.canWriteQsos || binding.state != WavelogBindingState.ENABLED) return 0
        var accepted = 0
        store.pending(binding.id).forEach { entry ->
            val now = System.currentTimeMillis() / 1_000
            val qso = database.qso(entry.localQsoId)
            try {
                when (entry.operation) {
                    WavelogOperation.CREATE -> {
                        requireNotNull(qso) { "Local QSO no longer exists" }
                        client.createAdif(binding.remoteStationId, database.toADIF(qso), entry.idempotencyKey)
                    }
                    WavelogOperation.UPDATE -> {
                        val link = store.link(binding.id, entry.localQsoId) ?: error("Remote link required before update")
                        client.patchQso(link.remoteQsoId, CanonicalQso.decode(entry.canonicalPayload).fields
                            .mapKeys { it.key.lowercase() }, entry.idempotencyKey)
                    }
                    WavelogOperation.DELETE -> {
                        val link = store.link(binding.id, entry.localQsoId) ?: error("Remote link required before delete")
                        client.deleteQso(link.remoteQsoId)
                    }
                }
                store.updateOutbox(entry.copy(state = WavelogOutboxState.ACCEPTED, attemptCount = entry.attemptCount + 1,
                    nextAttemptAt = null, lastError = "", updatedAt = now))
                accepted++
            } catch (error: WavelogApiException) {
                val ambiguousWrite = error.status == 0 || error.status >= 500
                val retryable = !ambiguousWrite && error.errorClass in setOf(WavelogErrorClass.RATE_LIMIT, WavelogErrorClass.TEMPORARY)
                val delay = error.retryAfterSeconds ?: (60L shl entry.attemptCount.coerceIn(0, 6))
                store.updateOutbox(entry.copy(
                    state = if (retryable) WavelogOutboxState.RETRY_WAIT else WavelogOutboxState.BLOCKED,
                    attemptCount = entry.attemptCount + 1, nextAttemptAt = if (retryable) now + delay else null,
                    lastError = if (ambiguousWrite) "Ambiguous write result; reconcile before retry" else error.message.orEmpty(),
                    updatedAt = now,
                ))
            } catch (error: Exception) {
                store.updateOutbox(entry.copy(state = WavelogOutboxState.BLOCKED, attemptCount = entry.attemptCount + 1,
                    nextAttemptAt = null, lastError = error.message.orEmpty().take(500), updatedAt = now))
            }
        }
        return accepted
    }

    private fun pagedSync(binding: WavelogBinding, kind: String, startPage: Int, maxPages: Int,
        onProgress: (Int, WavelogSyncSummary) -> Unit): WavelogSyncSummary {
        var summary = WavelogSyncSummary()
        var pageNumber = startPage
        while (pageNumber < startPage + maxPages) {
            val page = client.qsoPage(binding.remoteStationId, pageNumber)
            val pageSummary = database.transaction {
                page.rows.fold(WavelogSyncSummary(pages = 1)) { result, row -> result + reconcileRow(binding, row) }
            }
            summary += pageSummary
            val highWater = page.rows.maxOfOrNull { it.optLong("id") }?.toString().orEmpty()
            store.saveCheckpoint(WavelogSyncCheckpoint(binding.id, kind, pageNumber + 1, highWater,
                overlapHash = page.rows.joinToString(",") { it.optString("id") }, completed = !page.hasMore,
                updatedAt = System.currentTimeMillis() / 1_000))
            onProgress(pageNumber, summary)
            if (!page.hasMore) break
            pageNumber++
        }
        return summary
    }

    private fun reconcileRow(binding: WavelogBinding, row: JSONObject): WavelogSyncSummary {
        val remoteId = row.optString("id")
        if (remoteId.isBlank()) return WavelogSyncSummary(rejected = 1)
        val remote = remoteQso(binding, row) ?: return WavelogSyncSummary(rejected = 1)
        val remoteCanonical = WavelogCanonicalizer.fromAdif(database.toADIF(remote))
        val linked = store.linkByRemote(binding.id, remoteId)
        if (linked != null) return reconcileLinked(binding, linked, remote, remoteCanonical)

        val candidates = database.naturalCandidates(remote)
        if (candidates.size == 1) {
            val local = candidates.single()
            val localCanonical = WavelogCanonicalizer.fromAdif(database.toADIF(local))
            if (localCanonical.hash == remoteCanonical.hash) {
                store.saveLink(WavelogRemoteLink(binding.id, local.id, remoteId, remoteCanonical.hash, remoteCanonical.encoded))
                return WavelogSyncSummary(linked = 1)
            }
        }
        database.insertRemoteDistinct(remote)
        store.saveLink(WavelogRemoteLink(binding.id, remote.id, remoteId, remoteCanonical.hash, remoteCanonical.encoded))
        return if (candidates.isEmpty()) WavelogSyncSummary(imported = 1) else WavelogSyncSummary(imported = 1, ambiguous = 1)
    }

    private fun reconcileLinked(binding: WavelogBinding, link: WavelogRemoteLink, remote: Qso,
        remoteCanonical: CanonicalQso): WavelogSyncSummary {
        val local = database.qso(link.localQsoId) ?: return WavelogSyncSummary(rejected = 1)
        val base = CanonicalQso.decode(link.baselineCanonical)
        val localCanonical = WavelogCanonicalizer.fromAdif(database.toADIF(local))
        val result = WavelogCanonicalizer.merge(base, localCanonical, remoteCanonical)
        return when (result.disposition) {
            "UNCHANGED", "CONVERGED" -> {
                store.saveLink(link.copy(baselineHash = remoteCanonical.hash, baselineCanonical = remoteCanonical.encoded))
                WavelogSyncSummary(linked = 1)
            }
            "PUSH_LOCAL" -> {
                store.enqueue(binding.id, local.id, WavelogOperation.UPDATE, localCanonical)
                WavelogSyncSummary(queuedForPush = 1)
            }
            "PULL_REMOTE" -> {
                database.updateLocal(remote.copy(id = local.id, syncState = "synced"))
                store.saveLink(link.copy(baselineHash = remoteCanonical.hash, baselineCanonical = remoteCanonical.encoded))
                WavelogSyncSummary(pulled = 1)
            }
            "SAFE_MERGE" -> {
                val merged = result.merged ?: return WavelogSyncSummary(rejected = 1)
                val qso = database.qsoFromFields({ name -> merged.fields[name].orEmpty() }, link.remoteQsoId,
                    binding.remoteStationId, merged.fields.filterKeys { it !in WavelogCanonicalizer.rigWeaveFields })
                    ?: return WavelogSyncSummary(rejected = 1)
                database.updateLocal(qso.copy(id = local.id, syncState = "local"))
                store.enqueue(binding.id, local.id, WavelogOperation.UPDATE, merged)
                WavelogSyncSummary(merged = 1, queuedForPush = 1)
            }
            else -> {
                store.saveConflict(WavelogConflict(UUID.randomUUID().toString(), binding.id, local.id, link.remoteQsoId,
                    base.encoded, localCanonical.encoded, remoteCanonical.encoded, result.conflictingFields,
                    WavelogConflictState.OPEN, System.currentTimeMillis() / 1_000))
                WavelogSyncSummary(conflicts = 1)
            }
        }
    }

    private fun remoteQso(binding: WavelogBinding, row: JSONObject): Qso? {
        val values = buildMap<String, String> {
            row.keys().forEach { key -> row.optString(key).takeIf { it.isNotBlank() && !it.equals("null", true) }?.let { put(key.uppercase(), it) } }
        }.toMutableMap()
        val dateTime = values["QSO_DATE"].orEmpty()
        val parsed = parseDateTime(dateTime)
        if (parsed != null) {
            values["QSO_DATE"] = parsed.first
            values["TIME_ON"] = parsed.second
        }
        values["FREQ"]?.toDoubleOrNull()?.takeIf { it > 100_000 }?.let { values["FREQ"] = (it / 1_000_000.0).toString() }
        val remoteId = values["ID"].orEmpty()
        val extras = values.filterKeys { it !in WavelogCanonicalizer.rigWeaveFields && it !in setOf("ID", "STATION_ID") }
        val qso = database.qsoFromFields({ values[it].orEmpty() }, remoteId, binding.remoteStationId, extras) ?: return null
        return qso.copy(id = "wavelog-${binding.id}-$remoteId", remoteId = remoteId, syncState = "synced")
    }

    private fun parseDateTime(value: String): Pair<String, String>? {
        val normalized = value.trim().replace('T', ' ').removeSuffix("Z")
        val patterns = listOf("yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd HH:mm", "yyyyMMddHHmmss")
        for (pattern in patterns) runCatching {
            val date = LocalDateTime.parse(normalized, DateTimeFormatter.ofPattern(pattern))
            return date.atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("yyyyMMdd")) to
                date.atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("HHmmss"))
        }
        return null
    }
}
