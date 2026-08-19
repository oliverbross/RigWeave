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
    private val repository = LogIntelligenceRepository(database)
    val goalStore = ProgressGoalStore(context.applicationContext)
    private val preferences = context.applicationContext.getSharedPreferences("rigweave-log-intelligence", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var refreshJob: Job? = null
    private var pendingRefresh: (() -> Unit)? = null
    private var lastKey: Any? = null
    var snapshot by mutableStateOf(ProgressSnapshot()); private set
    var busy by mutableStateOf(false); private set
    var stationProfiles by mutableStateOf<List<String>>(emptyList()); private set
    var stationCallsigns by mutableStateOf<List<String>>(emptyList()); private set
    var operators by mutableStateOf<List<String>>(emptyList()); private set
    var submodes by mutableStateOf<List<String>>(emptyList()); private set
    var filters by mutableStateOf(loadFilters()); private set
    var selectedAward by mutableStateOf(runCatching {
        AwardKind.valueOf(preferences.getString("selected_award", AwardKind.DXCC.name).orEmpty())
    }.getOrDefault(AwardKind.DXCC)); private set
    var logbookRequest by mutableStateOf<LogbookFilter?>(null); private set

    fun requestLogbook(filter: LogbookFilter) { logbookRequest = filter }
    fun consumeLogbookRequest() { logbookRequest = null }

    fun updateFilters(value: ProgressFilters) {
        filters = value
        preferences.edit()
            .putBoolean("all_stations", value.allStations).putString("station_profile", value.stationProfileId)
            .putString("station_callsign", value.stationCallsign).putString("period", value.period.name)
            .putString("band", value.band).putString("mode", value.mode.name).putString("submode", value.submode)
            .putString("operator", value.operator).putString("confirmation", value.confirmationSource)
            .putString("portable_program", value.portableProgram).putBoolean("include_conflicted", value.includeConflicted)
            .putBoolean("include_deleted", value.includeDeleted).apply()
        lastKey = null
    }

    fun selectAward(value: AwardKind) {
        selectedAward = value
        preferences.edit().putString("selected_award", value.name).apply()
    }

    private fun loadFilters() = ProgressFilters(
        allStations = preferences.getBoolean("all_stations", false),
        stationProfileId = preferences.getString("station_profile", "").orEmpty(),
        stationCallsign = preferences.getString("station_callsign", "").orEmpty(),
        period = runCatching { ProgressPeriod.valueOf(preferences.getString("period", ProgressPeriod.ALL.name).orEmpty()) }.getOrDefault(ProgressPeriod.ALL),
        band = preferences.getString("band", "").orEmpty(),
        mode = runCatching { ProgressMode.valueOf(preferences.getString("mode", ProgressMode.ALL.name).orEmpty()) }.getOrDefault(ProgressMode.ALL),
        submode = preferences.getString("submode", "").orEmpty(), operator = preferences.getString("operator", "").orEmpty(),
        confirmationSource = preferences.getString("confirmation", "").orEmpty(),
        portableProgram = preferences.getString("portable_program", "").orEmpty(),
        includeConflicted = preferences.getBoolean("include_conflicted", false),
        includeDeleted = preferences.getBoolean("include_deleted", false),
    )

    fun refresh(
        filters: ProgressFilters,
        dxSpots: List<AndroidDXSpot>,
        portableSpots: List<PortableSpot>,
        syncAttention: Int,
        cty: CtyController,
        sotaCatalogue: SotaCatalogue,
    ) {
        val key = listOf(database.changeToken(), filters, goalStore.goals, syncAttention, cty.dataRevision,
            progressSpotIdentity(dxSpots, portableSpots))
        if (key == lastKey) return
        if (refreshJob?.isActive == true) {
            pendingRefresh = { refresh(filters, dxSpots, portableSpots, syncAttention, cty, sotaCatalogue) }
            return
        }
        lastKey = key
        refreshJob = scope.launch {
            busy = true
            try {
                val result=withContext(Dispatchers.IO){
                val built=StabilityDiagnostics.timedQuery("LOG_INTELLIGENCE", filters.hashCode().toUInt().toString(16), "PROJECTION_AGGREGATES", { it.totalQsos }) {
                    repository.snapshot(filters,goalStore.goals,dxSpots,portableSpots,syncAttention,cty::lookup)
                }
                    listOf(repository.stationProfiles(),repository.stationCallsigns(),repository.operators(),repository.submodes()) to built
                }
                stationProfiles=result.first[0];stationCallsigns=result.first[1];operators=result.first[2];submodes=result.first[3]
                snapshot=result.second
            } finally {
                busy = false
                refreshJob = null
                pendingRefresh.also { pendingRefresh = null }?.invoke()
            }
        }
    }

    fun goalsChanged() { lastKey = null }
    fun close() = scope.cancel()
}

internal fun progressSpotIdentity(dx: List<AndroidDXSpot>, portable: List<PortableSpot>): Long {
    var hash = -0x340d631b7bdddcdbL
    fun add(value: Any?) { value.toString().forEach { hash = (hash xor it.code.toLong()) * 0x100000001b3L }; hash = (hash xor 0xff) * 0x100000001b3L }
    dx.sortedBy(AndroidDXSpot::id).forEach { add(it.id);add(it.callsign);add(it.frequencyHz);add(it.mode);add(it.band);add(it.receivedEpoch);add(it.score) }
    portable.sortedBy(PortableSpot::id).forEach { row -> add(row.id);add(row.callsign);add(row.frequencyHz);add(row.mode);add(row.spottedAt);add(row.expiresAt);row.references.sortedBy { it.program.name+it.code }.forEach { add(it.program);add(it.code) } }
    return hash
}
