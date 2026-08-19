package app.rigweave.mobile

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale

internal enum class ProgressPeriod(val label: String) {
    DAYS_30("30 days"), DAYS_90("90 days"), MONTHS_12("12 months"), YEAR("This year"), ALL("All time")
}
internal enum class ProgressMode(val label: String) { ALL("All"), CW("CW"), PHONE("Phone"), DIGITAL("Digital") }
internal data class ProgressFilters(
    val allStations: Boolean = false, val stationProfileId: String = "", val stationCallsign: String = "",
    val period: ProgressPeriod = ProgressPeriod.ALL, val band: String = "", val mode: ProgressMode = ProgressMode.ALL,
)

internal enum class ProgressGoalMetric(val label: String) {
    TOTAL_QSOS("Total QSOs"), DXCC_WORKED("Unique DXCC-style worked"),
    DXCC_CONFIRMED("LoTW/QSL-confirmed DXCC-style"), QRP_DXCC("QRP DXCC-style worked"),
    POTA_HUNTED("POTA parks hunted locally"), POTA_ACTIVATED("POTA parks activated locally"),
    POTA_SUCCESSFUL("Successful local POTA activations"), P2P("P2P QSOs"),
    SOTA_HUNTED("SOTA summits worked locally"), WWFF_HUNTED("WWFF references worked locally"),
    STATES("U.S. states worked locally"), CQ_ZONES("CQ zones worked locally"),
}
internal data class ProgressGoal(
    val id: String, val metric: ProgressGoalMetric, val target: Int, val name: String = metric.label,
    val band: String = "", val mode: ProgressMode = ProgressMode.ALL, val deadline: String = "",
)
internal data class GoalProgress(val goal: ProgressGoal, val current: Int) {
    val remaining get() = (goal.target - current).coerceAtLeast(0)
    val percent get() = if (goal.target <= 0) 0 else (current * 100 / goal.target).coerceIn(0, 100)
}
internal data class ProgressCoverage(val available: Int, val total: Int) { val label get() = "Based on $available of $total QSOs" }
internal data class ProgressCount(val worked: Int, val confirmed: Int)
internal data class ProgressBucket(val label: String, val count: Int)
internal data class ProgressHeatCell(val day: Int, val hour: Int, val count: Int)
internal data class ProgressContactPoint(val grid: String, val latitude: Double, val longitude: Double)
internal data class SatelliteAnalytics(
    val qsos: Int = 0, val satellites: Int = 0, val grids: Int = 0, val confirmed: Int = 0,
    val bySatellite: Map<String, Int> = emptyMap(), val byMode: Map<String, Int> = emptyMap(),
)
internal data class AntennaAnalytics(
    val path: String, val qsos: Int, val confirmed: Int, val averageDistanceKm: Double?, val bestDistanceKm: Double?,
)
internal enum class NeedTarget { DX, PORTABLE, LOGBOOK }
internal data class ProgressNeed(
    val id: String, val title: String, val detail: String, val reasons: List<String>, val priority: Int,
    val target: NeedTarget, val dxSpot: AndroidDXSpot? = null, val portableSpot: PortableSpot? = null,
)
internal data class ActivationSummary(
    val sessionId: String, val ownParks: Set<String>, val qsos: Int, val uniqueCalls: Int, val p2p: Int,
    val startedAt: Long, val endedAt: Long, val bestDistanceKm: Double?,
) {
    val durationMinutes get() = if (qsos > 1) ((endedAt - startedAt).coerceAtLeast(0) / 60) else 0
    val qsoRate get() = if (durationMinutes > 0) qsos * 60.0 / durationMinutes else null
}
internal data class PortableProgress(
    val potaHunted: Set<String> = emptySet(), val potaActivated: Set<String> = emptySet(),
    val sotaHunted: Set<String> = emptySet(), val wwffHunted: Set<String> = emptySet(),
    val sotaAssociations: Set<String> = emptySet(), val sotaRegions: Set<String> = emptySet(),
    val p2pQsos: Int = 0, val successfulActivations: Int = 0, val nextPotaMilestone: Int = 10,
    val bestRoverDay: Pair<String, Int>? = null, val activations: List<ActivationSummary> = emptyList(),
    val portableByBand: Map<String, Int> = emptyMap(), val portableByMode: Map<String, Int> = emptyMap(),
)
internal data class ProgressSnapshot(
    val filteredQsos: List<Qso> = emptyList(), val totalQsos: Int = 0, val uniqueCalls: Int = 0,
    val dxcc: ProgressCount = ProgressCount(0, 0), val countries: Int = 0, val grids: Int = 0,
    val longestDistanceKm: Double? = null, val qrpQsos: Int = 0, val syncAttention: Int = 0,
    val coverage: Map<String, ProgressCoverage> = emptyMap(), val activity: List<ProgressBucket> = emptyList(),
    val bands: Map<String, Int> = emptyMap(), val modes: Map<String, Int> = emptyMap(),
    val heatmap: List<ProgressHeatCell> = emptyList(), val distance: List<ProgressBucket> = emptyList(),
    val contacts: List<ProgressContactPoint> = emptyList(), val needs: List<ProgressNeed> = emptyList(),
    val unconfirmedDxcc: List<Pair<String, List<Qso>>> = emptyList(),
    val states: ProgressCount = ProgressCount(0, 0), val zones: ProgressCount = ProgressCount(0, 0),
    val dxccByMode: Map<String, ProgressCount> = emptyMap(), val dxccByBand: Map<String, ProgressCount> = emptyMap(),
    val qrpDxcc: Int = 0, val portable: PortableProgress = PortableProgress(), val goals: List<GoalProgress> = emptyList(),
    val operators: Map<String, Int> = emptyMap(), val years: Map<String, Int> = emptyMap(),
    val months: Map<String, Int> = emptyMap(), val confirmations: Map<String, Int> = emptyMap(),
    val satellite: SatelliteAnalytics = SatelliteAnalytics(), val antennas: List<AntennaAnalytics> = emptyList(),
)

internal val canonicalUsStates = setOf(
    "AL","AK","AZ","AR","CA","CO","CT","DE","FL","GA","HI","ID","IL","IN","IA","KS","KY","LA","ME","MD","MA",
    "MI","MN","MS","MO","MT","NE","NV","NH","NJ","NM","NY","NC","ND","OH","OK","OR","PA","RI","SC","SD","TN",
    "TX","UT","VT","VA","WA","WV","WI","WY",
)
internal fun progressModeFamily(raw: String): String = when (modeFamily(raw)) {
    "CW" -> "CW"; "SSB", "FM", "AM" -> "PHONE"; "FT8", "FT4", "RTTY", "DIGITAL" -> "DIGITAL"; else -> "OTHER"
}
internal fun isAwardConfirmed(qso: Qso) =
    qso.lotwReceived.trim().uppercase(Locale.US) in setOf("Y", "V") ||
        qso.qslReceived.trim().uppercase(Locale.US) in setOf("Y", "V")
private fun qsoBand(qso: Qso) = qso.band.trim().lowercase(Locale.US).ifBlank { bandForFrequency(qso.frequencyHz) }
private fun qsoMode(qso: Qso) = progressModeFamily(qso.submode.ifBlank { qso.mode })

internal fun filterProgressQsos(qsos: List<Qso>, filters: ProgressFilters, now: Long): List<Qso> {
    val start = when (filters.period) {
        ProgressPeriod.DAYS_30 -> now - 30 * 86_400L
        ProgressPeriod.DAYS_90 -> now - 90 * 86_400L
        ProgressPeriod.MONTHS_12 -> Instant.ofEpochSecond(now).atZone(ZoneOffset.UTC).minusMonths(12).toEpochSecond()
        ProgressPeriod.YEAR -> LocalDate.ofInstant(Instant.ofEpochSecond(now), ZoneOffset.UTC).withDayOfYear(1)
            .atStartOfDay().toEpochSecond(ZoneOffset.UTC)
        ProgressPeriod.ALL -> Long.MIN_VALUE
    }
    return qsos.filter { qso ->
        val stationMatches = filters.allStations ||
            filters.stationProfileId.isNotBlank() && qso.stationProfileId == filters.stationProfileId ||
            filters.stationProfileId.isBlank() && filters.stationCallsign.isNotBlank() &&
                qso.stationCallsign.equals(filters.stationCallsign, true) ||
            filters.stationProfileId.isBlank() && filters.stationCallsign.isBlank()
        stationMatches && qso.createdAt >= start &&
            (filters.band.isBlank() || qsoBand(qso) == filters.band.lowercase(Locale.US)) &&
            (filters.mode == ProgressMode.ALL || qsoMode(qso) == filters.mode.name)
    }
}

private fun normalizedRefs(primary: String, values: List<String>, normalizer: (String) -> String) =
    (values + primary).map(normalizer).filter(String::isNotBlank).toSet()
private fun progressDxcc(qso: Qso, lookup: (String) -> AndroidCtyRecord?) =
    qso.dxcc.trim().uppercase(Locale.US).ifBlank { lookup(qso.callsign)?.dxcc.orEmpty().trim().uppercase(Locale.US) }
private fun nextPotaMilestone(value: Int): Int =
    listOf(10,20,30,40,50,75,100,200,300,400,500,600,700,800,900,1_000).firstOrNull { it > value }
        ?: ((value / 500) + 1) * 500
private fun activityBuckets(rows: List<Qso>, period: ProgressPeriod): List<ProgressBucket> =
    rows.groupingBy { qso ->
        val date = Instant.ofEpochSecond(qso.createdAt).atZone(ZoneOffset.UTC).toLocalDate()
        when (period) {
            ProgressPeriod.DAYS_30, ProgressPeriod.DAYS_90 -> date.toString()
            ProgressPeriod.MONTHS_12, ProgressPeriod.YEAR -> "%04d-%02d".format(date.year, date.monthValue)
            ProgressPeriod.ALL -> date.withDayOfMonth(1).toString().take(7)
        }
    }.eachCount().toSortedMap().map { ProgressBucket(it.key, it.value) }
private fun distanceBuckets(rows: List<Qso>): List<ProgressBucket> {
    val counts = IntArray(5)
    rows.map(Qso::distanceKm).filter { it > 0 }.forEach {
        counts[when { it < 500 -> 0; it < 2_000 -> 1; it < 5_000 -> 2; it < 10_000 -> 3; else -> 4 }]++
    }
    return listOf("<500","500–2k","2–5k","5–10k","10k+").mapIndexed { index, label -> ProgressBucket(label, counts[index]) }
}
private fun portableProgress(rows: List<Qso>, sotaSummits: Map<String, SotaSummit>): PortableProgress {
    val potaHunted = rows.flatMap { normalizedRefs(it.potaRef, it.potaRefs, ::normalizePotaReference) }.toSet()
    val potaActivated = rows.flatMap { normalizedRefs(it.myPotaRef, it.myPotaRefs, ::normalizePotaReference) }.toSet()
    val sotaHunted = rows.map { normalizeSotaReference(it.sotaRef) }.filter(String::isNotBlank).toSet()
    val wwffHunted = rows.map { normalizeWwffReference(it.wwffRef) }.filter(String::isNotBlank).toSet()
    val portableRows = rows.filter { it.potaRef.isNotBlank() || it.potaRefs.isNotEmpty() || it.sotaRef.isNotBlank() ||
        it.wwffRef.isNotBlank() || it.myPotaRef.isNotBlank() || it.myPotaRefs.isNotEmpty() }
    val sessions = rows.filter { it.activationSessionId.isNotBlank() }.groupBy(Qso::activationSessionId).map { (id, qsos) ->
        ActivationSummary(id, qsos.flatMap { normalizedRefs(it.myPotaRef, it.myPotaRefs, ::normalizePotaReference) }.toSet(),
            qsos.size, qsos.map { it.callsign.uppercase(Locale.US) }.distinct().size,
            qsos.count { normalizedRefs(it.myPotaRef,it.myPotaRefs,::normalizePotaReference).isNotEmpty() &&
                normalizedRefs(it.potaRef,it.potaRefs,::normalizePotaReference).isNotEmpty() },
            qsos.minOf(Qso::createdAt), qsos.maxOf(Qso::createdAt), qsos.map(Qso::distanceKm).filter { it > 0 }.maxOrNull())
    }.sortedByDescending(ActivationSummary::startedAt)
    val perParkDay = rows.flatMap { qso ->
        normalizedRefs(qso.myPotaRef,qso.myPotaRefs,::normalizePotaReference).map { ref ->
            Triple(ref, Instant.ofEpochSecond(qso.createdAt).atZone(ZoneOffset.UTC).toLocalDate().toString(), qso.id)
        }
    }.groupBy { it.first to it.second }
    val rover = perParkDay.keys.groupingBy { it.second }.eachCount().maxByOrNull { it.value }?.toPair()
    val joinedSummits = sotaHunted.mapNotNull(sotaSummits::get)
    return PortableProgress(potaHunted,potaActivated,sotaHunted,wwffHunted,
        joinedSummits.map(SotaSummit::association).filter(String::isNotBlank).toSet(),
        joinedSummits.map(SotaSummit::region).filter(String::isNotBlank).toSet(),
        rows.count { normalizedRefs(it.myPotaRef,it.myPotaRefs,::normalizePotaReference).isNotEmpty() &&
            normalizedRefs(it.potaRef,it.potaRefs,::normalizePotaReference).isNotEmpty() },
        perParkDay.count { it.value.map { row -> row.third }.distinct().size >= 10 }, nextPotaMilestone(potaHunted.size),
        rover,sessions,portableRows.groupingBy(::qsoBand).eachCount().filterKeys(String::isNotBlank),
        portableRows.groupingBy(::qsoMode).eachCount())
}
private fun metricValue(metric: ProgressGoalMetric, s: ProgressSnapshot) = when (metric) {
    ProgressGoalMetric.TOTAL_QSOS -> s.totalQsos; ProgressGoalMetric.DXCC_WORKED -> s.dxcc.worked
    ProgressGoalMetric.DXCC_CONFIRMED -> s.dxcc.confirmed; ProgressGoalMetric.QRP_DXCC -> s.qrpDxcc
    ProgressGoalMetric.POTA_HUNTED -> s.portable.potaHunted.size; ProgressGoalMetric.POTA_ACTIVATED -> s.portable.potaActivated.size
    ProgressGoalMetric.POTA_SUCCESSFUL -> s.portable.successfulActivations; ProgressGoalMetric.P2P -> s.portable.p2pQsos
    ProgressGoalMetric.SOTA_HUNTED -> s.portable.sotaHunted.size; ProgressGoalMetric.WWFF_HUNTED -> s.portable.wwffHunted.size
    ProgressGoalMetric.STATES -> s.states.worked; ProgressGoalMetric.CQ_ZONES -> s.zones.worked
}

internal fun buildProgressSnapshot(
    qsos: List<Qso>, filters: ProgressFilters, goals: List<ProgressGoal> = emptyList(),
    dxSpots: List<AndroidDXSpot> = emptyList(), portableSpots: List<PortableSpot> = emptyList(),
    sotaSummits: Map<String, SotaSummit> = emptyMap(),
    syncAttention: Int = 0, now: Long = Instant.now().epochSecond,
    ctyLookup: (String) -> AndroidCtyRecord? = { null },
): ProgressSnapshot {
    val rows = filterProgressQsos(qsos, filters, now)
    val dxccByQso = rows.associateWith { progressDxcc(it, ctyLookup) }
    val workedDxcc = dxccByQso.values.filter(String::isNotBlank).toSet()
    val confirmedDxcc = dxccByQso.filter { isAwardConfirmed(it.key) }.values.filter(String::isNotBlank).toSet()
    val states = rows.map { it.state.trim().uppercase(Locale.US) }.filter(canonicalUsStates::contains).toSet()
    val confirmedStates = rows.filter(::isAwardConfirmed).map { it.state.trim().uppercase(Locale.US) }.filter(canonicalUsStates::contains).toSet()
    val zones = rows.mapNotNull { it.cqZone.trim().toIntOrNull()?.takeIf { z -> z in 1..40 } }.toSet()
    val confirmedZones = rows.filter(::isAwardConfirmed).mapNotNull { it.cqZone.trim().toIntOrNull()?.takeIf { z -> z in 1..40 } }.toSet()
    val byMode = listOf("CW","PHONE","DIGITAL").associateWith { family ->
        val familyRows = rows.filter { qsoMode(it) == family }
        ProgressCount(familyRows.map { dxccByQso.getValue(it) }.filter(String::isNotBlank).distinct().size,
            familyRows.filter(::isAwardConfirmed).map { dxccByQso.getValue(it) }.filter(String::isNotBlank).distinct().size)
    }
    val byBand = rows.map(::qsoBand).filter { it.isNotBlank() && it != "60m" }.distinct().associateWith { band ->
        val bandRows = rows.filter { qsoBand(it) == band }
        ProgressCount(bandRows.map { dxccByQso.getValue(it) }.filter(String::isNotBlank).distinct().size,
            bandRows.filter(::isAwardConfirmed).map { dxccByQso.getValue(it) }.filter(String::isNotBlank).distinct().size)
    }
    val portable = portableProgress(rows, sotaSummits)
    val needs = buildList {
        dxSpots.forEach { spot ->
            val resolved = ctyLookup(spot.callsign)
            val dxcc = resolved?.dxcc.orEmpty().trim().uppercase(Locale.US)
            if (dxcc.isBlank()) return@forEach
            val reasons = buildList {
                if (dxcc !in workedDxcc) add("NEW DXCC ENTITY") else {
                    val entityRows = rows.filter { dxccByQso[it] == dxcc }
                    if (entityRows.none { qsoBand(it) == spot.band.lowercase(Locale.US) }) add("NEW DXCC ON BAND")
                    if (entityRows.none { qsoMode(it) == progressModeFamily(spot.mode) }) add("NEW DXCC MODE")
                }
                resolved?.cqZone?.toIntOrNull()?.takeIf { it in 1..40 && it !in zones }?.let { add("NEEDED CQ ZONE") }
            }
            if (reasons.isNotEmpty()) add(ProgressNeed("dx:${spot.id}",spot.callsign,
                "${resolved?.country.orEmpty()} · ${spot.band} · ${spot.mode}",reasons,reasons.size*20+spot.score.coerceIn(0,20),
                NeedTarget.DX,dxSpot=spot))
        }
        portableSpots.filter { it.activeAt(now) }.forEach { spot ->
            val reasons = spot.references.mapNotNull { ref -> when (ref.program) {
                PortableProgram.POTA -> "NEW POTA PARK".takeIf { normalizePotaReference(ref.code) !in portable.potaHunted }
                PortableProgram.SOTA -> null
                PortableProgram.WWFF -> "NEW WWFF REFERENCE".takeIf { normalizeWwffReference(ref.code) !in portable.wwffHunted }
            } }.distinct()
            if (reasons.isNotEmpty()) add(ProgressNeed("portable:${spot.id}",spot.callsign,
                "${spot.primary.code} · ${spot.band} · ${spot.mode}",reasons,reasons.size*20,
                NeedTarget.PORTABLE,portableSpot=spot))
        }
    }.sortedByDescending(ProgressNeed::priority)
    val coverage = linkedMapOf(
        "DXCC" to rows.count { it.dxcc.isNotBlank() }, "CQ zone" to rows.count { it.cqZone.toIntOrNull()?.let { zone -> zone in 1..40 } == true },
        "U.S. state" to rows.count { it.state.uppercase(Locale.US) in canonicalUsStates },
        "Grid" to rows.count { maidenheadCenter(it.grid) != null }, "Distance" to rows.count { it.distanceKm > 0 },
        "TX power" to rows.count { it.txPowerW > 0 },
        "Station profile" to rows.count { it.stationProfileId.isNotBlank() || it.stationCallsign.isNotBlank() },
        "Portable reference" to rows.count { it.potaRef.isNotBlank() || it.potaRefs.isNotEmpty() || it.sotaRef.isNotBlank() || it.wwffRef.isNotBlank() },
    ).mapValues { ProgressCoverage(it.value,rows.size) }
    val contacts = rows.mapNotNull { q -> maidenheadCenter(q.grid)?.let { ProgressContactPoint(q.grid.uppercase(Locale.US),it.latitude,it.longitude) } }
        .distinctBy(ProgressContactPoint::grid)
    val base = ProgressSnapshot(rows,rows.size,rows.map { it.callsign.uppercase(Locale.US) }.filter(String::isNotBlank).distinct().size,
        ProgressCount(workedDxcc.size,confirmedDxcc.size),
        rows.map(Qso::country).map(String::trim).filter(String::isNotBlank).distinctBy { it.uppercase(Locale.US) }.size,
        rows.map(Qso::grid).map(String::trim).filter(String::isNotBlank).distinctBy { it.uppercase(Locale.US) }.size,
        rows.map(Qso::distanceKm).filter { it > 0 }.maxOrNull(),rows.count { it.txPowerW in 1..5 },syncAttention,
        coverage,activityBuckets(rows,filters.period),rows.groupingBy(::qsoBand).eachCount().filterKeys(String::isNotBlank),
        rows.groupingBy(::qsoMode).eachCount(),
        rows.groupingBy { val d=Instant.ofEpochSecond(it.createdAt).atZone(ZoneOffset.UTC); d.dayOfWeek.value-1 to d.hour }
            .eachCount().map { ProgressHeatCell(it.key.first,it.key.second,it.value) },
        distanceBuckets(rows),contacts,needs,
        dxccByQso.filterValues(String::isNotBlank).entries.groupBy { it.value }.filter { it.key !in confirmedDxcc }
            .map { it.key to it.value.map { entry -> entry.key } }.sortedBy { it.first },
        ProgressCount(states.size,confirmedStates.size),ProgressCount(zones.size,confirmedZones.size),byMode,byBand,
        rows.filter { it.txPowerW in 1..5 }.map { dxccByQso.getValue(it) }.filter(String::isNotBlank).distinct().size,portable)
    fun received(value: String) = value.trim().uppercase(Locale.US) in setOf("Y", "V")
    val satelliteRows = rows.filter { qso -> qso.band.equals("SAT", true) || qso.propagationMode.equals("SAT", true) ||
        qso.extraAdifFields["SAT_NAME"].orEmpty().isNotBlank() }
    val satellite = SatelliteAnalytics(
        qsos = satelliteRows.size,
        satellites = satelliteRows.map { it.extraAdifFields["SAT_NAME"].orEmpty().uppercase(Locale.US) }.filter(String::isNotBlank).distinct().size,
        grids = satelliteRows.map(Qso::grid).filter(String::isNotBlank).distinctBy { it.uppercase(Locale.US) }.size,
        confirmed = satelliteRows.count(::isAwardConfirmed),
        bySatellite = satelliteRows.map { it.extraAdifFields["SAT_NAME"].orEmpty().uppercase(Locale.US).ifBlank { "UNKNOWN" } }.groupingBy { it }.eachCount(),
        byMode = satelliteRows.map { it.extraAdifFields["SAT_MODE"].orEmpty().uppercase(Locale.US).ifBlank { it.submode.ifBlank { it.mode } } }.groupingBy { it }.eachCount(),
    )
    val antennas = rows.filter { it.antennaPath.isNotBlank() }.groupBy { it.antennaPath.trim().uppercase(Locale.US) }.map { (path, values) ->
        val distances = values.map(Qso::distanceKm).filter { it > 0 }
        AntennaAnalytics(path, values.size, values.count(::isAwardConfirmed), distances.average().takeUnless(Double::isNaN), distances.maxOrNull())
    }.sortedByDescending(AntennaAnalytics::qsos)
    return base.copy(
        goals = goals.take(4).map { GoalProgress(it,metricValue(it.metric,base)) },
        operators = rows.map { it.operatorCallsign.trim().uppercase(Locale.US).ifBlank { "UNKNOWN" } }.groupingBy { it }.eachCount(),
        years = rows.groupingBy { Instant.ofEpochSecond(it.createdAt).atZone(ZoneOffset.UTC).year.toString() }.eachCount().toSortedMap(),
        months = rows.groupingBy { Instant.ofEpochSecond(it.createdAt).atZone(ZoneOffset.UTC).toLocalDate().toString().take(7) }.eachCount().toSortedMap(),
        confirmations = linkedMapOf(
            "LoTW" to rows.count { received(it.lotwReceived) }, "Paper QSL" to rows.count { received(it.qslReceived) },
            "eQSL" to rows.count { received(it.eqslReceived) }, "QRZ" to rows.count { received(it.qrzReceived) },
            "Club Log" to rows.count { received(it.clublogReceived) }, "DCL" to rows.count { received(it.dclReceived) },
        ),
        satellite = satellite, antennas = antennas,
    )
}
