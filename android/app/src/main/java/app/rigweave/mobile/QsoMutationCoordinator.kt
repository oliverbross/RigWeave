package app.rigweave.mobile

const val DEFAULT_LOCAL_STATION = "LOCAL_DEFAULT"

class QsoMutationCoordinator(
    private val database: QsoDatabase,
    private val store: WavelogSyncStore = WavelogSyncStore(database),
) {
    fun save(qso: Qso, origin: QsoOrigin = QsoOrigin.OPERATOR): Boolean = database.transaction { saveInTransaction(qso, origin) }

    fun saveBatch(rows: List<Qso>, origin: QsoOrigin): Pair<Int, Int> = database.transaction {
        var added = 0; var skipped = 0
        rows.forEach { if (saveInTransaction(it, origin)) added++ else skipped++ }
        added to skipped
    }

    private fun saveInTransaction(qso: Qso, origin: QsoOrigin): Boolean {
        val inserted = database.save(qso, origin)
        if (inserted) recordMutation(qso, origin, WavelogOperation.CREATE)
        return inserted
    }

    fun update(qso: Qso, origin: QsoOrigin = QsoOrigin.OPERATOR) = database.transaction {
        database.updateLocal(qso)
        recordMutation(qso, origin, WavelogOperation.UPDATE)
    }

    fun delete(id: String, origin: QsoOrigin = QsoOrigin.OPERATOR) = database.transaction {
        val qso = database.qso(id) ?: return@transaction
        val binding = store.activeBinding()?.takeIf { applies(it, qso) }
        if (origin != QsoOrigin.REMOTE_SYNC && binding != null) {
            val link = store.link(binding.id, id)
            if (link == null) {
                store.cancelUnsentCreate(binding.id, id)
            } else {
                store.saveTombstone(WavelogTombstone(binding.id, id, link.remoteQsoId, link.baselineHash,
                    System.currentTimeMillis() / 1_000), link.baselineCanonical)
                if (binding.state == WavelogBindingState.ENABLED && binding.capabilities.canDeleteQsos) {
                    store.enqueue(binding.id, id, WavelogOperation.DELETE, CanonicalQso(emptyMap()))
                }
            }
        }
        database.deleteLocal(id)
    }

    fun importADIF(text: String): Pair<Int, Int> {
        val (rows, invalid) = database.parseADIF(text)
        val result = saveBatch(rows, QsoOrigin.IMPORT)
        return result.first to result.second + invalid
    }

    fun localStationIds(): List<String> = listOf(DEFAULT_LOCAL_STATION) + database.all().map(Qso::stationProfileId)
        .filter(String::isNotBlank).distinct().sorted()

    fun isMapped(qso: Qso): Boolean = store.activeBinding()?.let { applies(it, qso) } == true

    private fun recordMutation(qso: Qso, origin: QsoOrigin, operation: WavelogOperation) {
        if (origin == QsoOrigin.REMOTE_SYNC) return
        val binding = store.activeBinding()?.takeIf { applies(it, qso) } ?: return
        val canonical = WavelogCanonicalizer.fromAdif(database.toADIF(qso))
        if (binding.state == WavelogBindingState.ENABLED) {
            val actual = if (operation == WavelogOperation.UPDATE && store.link(binding.id, qso.id) == null)
                WavelogOperation.CREATE else operation
            store.enqueue(binding.id, qso.id, actual, canonical)
        } else {
            store.enqueue(binding.id, qso.id, operation, canonical, WavelogOutboxState.BLOCKED,
                "Read-only Wavelog binding; local divergence recorded")
        }
    }

    private fun applies(binding: WavelogBinding, qso: Qso): Boolean =
        (binding.localStationProfileId == DEFAULT_LOCAL_STATION && qso.stationProfileId.isBlank()) ||
            (binding.localStationProfileId.isNotBlank() && binding.localStationProfileId == qso.stationProfileId)
}
