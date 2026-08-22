package app.rigweave.mobile

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

enum class HealthState { HEALTHY, ATTENTION, DEGRADED, PAUSED, UNAVAILABLE }

data class SystemHealthCard(
    val id: String,
    val title: String,
    val state: HealthState,
    val summary: String,
    val counts: Map<String, Long> = emptyMap(),
    val sourceAgeSeconds: Long? = null,
    val safeActions: List<String> = emptyList(),
)

data class SystemHealthSnapshot(
    val generatedAt: Long,
    val schemas: Map<String, Int>,
    val databaseBytes: Map<String, Long>,
    val cards: List<SystemHealthCard>,
    val operatingGeneration: Long,
)

internal fun buildSystemHealthSnapshot(
    context: Context,
    operatingContext: OperatingContextSnapshot,
    qso: StabilitySnapshot?,
    wavelogStatus: String,
    wavelogPending: Int,
    syncAttention: Int,
    clusterStatus: String,
    neuralStatus: String,
    groupsStatus: String,
    groupsMessages: Int,
    groupsDrafts: Int,
    digiMode: String,
    digiRxActive: Boolean,
    digiStatus: String,
): SystemHealthSnapshot {
    fun databaseBytes(name: String) = listOf("", "-wal", "-shm").sumOf { suffix ->
        context.getDatabasePath(name + suffix).takeIf { it.isFile }?.length() ?: 0L
    }
    val bytes = mapOf(
        "rigweave.sqlite" to (qso?.databaseBytes ?: databaseBytes("rigweave.sqlite")),
        "neural-dx.sqlite" to databaseBytes("neural-dx.sqlite"),
        "rigweave-digi.sqlite" to databaseBytes("rigweave-digi.sqlite"),
        "rigweave-groupsio.sqlite" to databaseBytes("rigweave-groupsio.sqlite"),
    )
    val projection = qso?.projection
    val cards = listOf(
        SystemHealthCard("storage", "Storage", if (projection?.state == ProjectionState.READY) HealthState.HEALTHY else HealthState.ATTENTION,
            "QSO schema 13 · projection contract 2 · Neural 5 · Digi 2 · Groups.io 2",
            mapOf("qso_canonical" to (projection?.canonicalRows?.toLong() ?: 0), "qso_projected" to (projection?.projectionRows?.toLong() ?: 0)),
            safeActions = listOf("verify projection", "repair/rebuild projection")),
        SystemHealthCard("wavelog", "Wavelog", if (wavelogPending + syncAttention == 0) HealthState.HEALTHY else HealthState.ATTENTION,
            wavelogStatus.take(180), mapOf("outbox" to wavelogPending.toLong(), "attention" to syncAttention.toLong()),
            safeActions = listOf("retry/reconcile", "pause/resume binding", "open exact conflict/outbox")),
        SystemHealthCard("providers", "Providers", if (operatingContext.networkAvailable.value) HealthState.HEALTHY else HealthState.DEGRADED,
            "Cluster ${clusterStatus.take(80)} · Neural ${neuralStatus.take(80)}",
            safeActions = listOf("refresh", "clear re-fetchable cache", "open source attribution")),
        SystemHealthCard("groupsio", "Groups.io", if (groupsDrafts == 0) HealthState.HEALTHY else HealthState.ATTENTION,
            groupsStatus.take(180), mapOf("cached_messages" to groupsMessages.toLong(), "drafts_attention" to groupsDrafts.toLong()),
            safeActions = listOf("open exact draft/outbox", "resume archive")),
        SystemHealthCard("radio_digi", "Radio / Digi", if (operatingContext.connected.value) HealthState.HEALTHY else HealthState.UNAVAILABLE,
            "${operatingContext.radioModel.value} · $digiMode · ${if (digiRxActive) "RX active" else "RX stopped"} · ${digiStatus.take(120)}",
            safeActions = listOf("open Digi diagnostics")),
    )
    return SystemHealthSnapshot(System.currentTimeMillis(), mapOf("qso" to 13, "projection" to 2, "neural" to 5, "digi" to 2, "groupsio" to 2),
        bytes, cards, operatingContext.generation)
}

object SanitizedSupportBundle {
    fun build(snapshot: SystemHealthSnapshot, upstreamPins: Map<String, String>, packageSummary: Map<String, Long>): ByteArray {
        val root = JSONObject()
            .put("format", "RIGWEAVE_SANITIZED_SUPPORT_V1")
            .put("generated_at", snapshot.generatedAt)
            .put("build", JSONObject().put("version", BuildConfig.VERSION_NAME).put("code", BuildConfig.VERSION_CODE))
            .put("schemas", JSONObject(snapshot.schemas))
            .put("database_bytes", JSONObject(snapshot.databaseBytes))
            .put("operating_generation", snapshot.operatingGeneration)
            .put("upstream_pins", JSONObject(upstreamPins))
            .put("package_bytes", JSONObject(packageSummary))
            .put("cards", JSONArray().apply { snapshot.cards.forEach { card -> put(JSONObject()
                .put("id", card.id).put("state", card.state.name).put("summary", sanitize(card.summary))
                .put("counts", JSONObject(card.counts)).put("source_age_seconds", card.sourceAgeSeconds)
                .put("safe_actions", JSONArray(card.safeActions))) } })
            .put("privacy", "No credentials, callsigns, QSO payloads/comments, Groups.io messages, Digi transcripts, provider bodies, or private paths")
        return ByteArrayOutputStream().use { output ->
            ZipOutputStream(output).use { zip ->
                zip.putNextEntry(ZipEntry("rigweave-support.json"))
                zip.write(root.toString(2).toByteArray())
                zip.closeEntry()
            }
            output.toByteArray()
        }
    }

    private fun sanitize(value: String) = value.replace(Regex("(?i)(token|api[_ -]?key|password|secret)\\s*[:=]\\s*\\S+"), "$1=[redacted]")
        .replace(Regex("/[^\\s]+"), "[private-path]").take(240)
}
