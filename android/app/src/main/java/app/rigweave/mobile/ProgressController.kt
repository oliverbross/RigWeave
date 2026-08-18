package app.rigweave.mobile

import android.content.Context
import android.util.AtomicFile
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

internal class ProgressGoalStore(context: Context) {
    private val file = AtomicFile(File(context.filesDir, "progress-goals.json"))
    var goals by mutableStateOf(load()); private set

    fun add(metric: ProgressGoalMetric, target: Int, name: String = metric.label) {
        if (goals.size >= 4 || target <= 0) return
        save(goals + ProgressGoal(UUID.randomUUID().toString(), metric, target, name.trim().ifBlank { metric.label }))
    }

    fun remove(id: String) = save(goals.filterNot { it.id == id })

    private fun save(value: List<ProgressGoal>) {
        val bytes = encodeProgressGoals(value).toByteArray()
        val output = file.startWrite()
        try { output.write(bytes); file.finishWrite(output); goals = value.take(4) }
        catch (error: Exception) { file.failWrite(output); throw error }
    }

    private fun load() = runCatching { decodeProgressGoals(file.readFully().toString(Charsets.UTF_8)) }.getOrDefault(emptyList())
}

internal fun encodeProgressGoals(value: List<ProgressGoal>) = JSONArray().apply {
    value.take(4).forEach { goal -> put(JSONObject()
        .put("id", goal.id).put("metric", goal.metric.name).put("target", goal.target)
        .put("name", goal.name).put("band", goal.band).put("mode", goal.mode.name).put("deadline", goal.deadline))
    }
}.toString()

internal fun decodeProgressGoals(raw: String): List<ProgressGoal> {
    val rows = JSONArray(raw)
    return buildList {
        for (index in 0 until rows.length().coerceAtMost(4)) {
            val row = rows.getJSONObject(index)
            add(ProgressGoal(row.getString("id"), ProgressGoalMetric.valueOf(row.getString("metric")),
                row.getInt("target"), row.optString("name"), row.optString("band"),
                ProgressMode.valueOf(row.optString("mode", ProgressMode.ALL.name)), row.optString("deadline")))
        }
    }
}

internal class ProgressController(context: Context, private val database: QsoDatabase) {
    val goalStore = ProgressGoalStore(context.applicationContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var refreshJob: Job? = null
    private var lastKey: Any? = null
    var snapshot by mutableStateOf(ProgressSnapshot()); private set
    var busy by mutableStateOf(false); private set
    var stationProfiles by mutableStateOf<List<String>>(emptyList()); private set
    var stationCallsigns by mutableStateOf<List<String>>(emptyList()); private set

    fun refresh(
        filters: ProgressFilters,
        dxSpots: List<AndroidDXSpot>,
        portableSpots: List<PortableSpot>,
        syncAttention: Int,
        cty: CtyController,
        sotaCatalogue: SotaCatalogue,
    ) {
        val key = listOf(database.changeToken(), filters, goalStore.goals, syncAttention, cty.dataRevision,
            dxSpots.size, dxSpots.maxOfOrNull(AndroidDXSpot::receivedEpoch),
            portableSpots.size, portableSpots.maxOfOrNull(PortableSpot::spottedAt))
        if (key == lastKey) return
        lastKey = key
        refreshJob?.cancel()
        refreshJob = scope.launch {
            busy = true
            val (rows, summits) = withContext(Dispatchers.IO) {
                val qsos = database.all()
                val references = qsos.map { normalizeSotaReference(it.sotaRef) }.filter(String::isNotBlank).toSet()
                qsos to sotaCatalogue.lookup(references)
            }
            val built = withContext(Dispatchers.Default) {
                buildProgressSnapshot(rows, filters, goalStore.goals, dxSpots, portableSpots, summits, syncAttention,
                    ctyLookup = cty::lookup)
            }
            stationProfiles = rows.map(Qso::stationProfileId).filter(String::isNotBlank).distinct().sorted()
            stationCallsigns = rows.map(Qso::stationCallsign).filter(String::isNotBlank).distinctBy(String::uppercase).sorted()
            snapshot = built
            busy = false
        }
    }

    fun goalsChanged() { lastKey = null }
    fun close() = scope.cancel()
}
