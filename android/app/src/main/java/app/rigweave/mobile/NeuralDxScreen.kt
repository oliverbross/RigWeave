package app.rigweave.mobile

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private val DxBg = Color(0xFF111519)
private val DxPanel = Color(0xFF1B2228)
private val DxRaised = Color(0xFF283139)
private val DxInk = Color(0xFFF4F0E7)
private val DxMuted = Color(0xFFA5ADB2)
private val DxAmber = Color(0xFFE9A72B)
private val DxGreen = Color(0xFF42C77B)
private val DxRed = Color(0xFFE4544D)
private val DxCyan = Color(0xFF43C7D9)
private val DxYellow = Color(0xFFF4C94E)
private val DxBands = listOf("ALL", "160m", "80m", "60m", "40m", "30m", "20m", "17m", "15m", "12m", "10m", "6m", "4m", "2m", "70cm", "23cm", "QO-100")

@Composable
fun NeuralDxScreen(
    controller: NeuralDxController,
    features: FeatureController,
    database: QsoDatabase,
    wavelog: WavelogController,
    cty: CtyController,
    app: AppController,
    tune: (String) -> Unit,
    previousQsos: (AndroidDXSpot) -> Unit,
) {
    var page by remember { mutableStateOf(NeuralDxPage.COCKPIT) }
    val stationId = wavelog.stationId.takeIf { wavelog.logMode == LogMode.WAVELOG }
    val stationGrid = wavelog.selectedStation?.grid?.ifBlank { null } ?: app.stationGrid
    val stationCall = wavelog.selectedStation?.callsign?.ifBlank { null } ?: app.stationCallsign.ifBlank { features.clusterCallsign }

    LaunchedEffect(features.liveSpots, stationId, cty.dataRevision) {
        controller.ingest(features.liveSpots, stationId, cty)
    }
    LaunchedEffect(stationGrid, stationCall, stationId) {
        if (controller.lastRefreshEpoch == 0L || Instant.now().epochSecond - controller.lastRefreshEpoch > 15 * 60) {
            controller.refresh(stationCall, stationGrid, stationId, features.liveSpots)
            if (!features.solar.valid) features.refreshSolar()
        }
    }

    Column(Modifier.fillMaxSize().background(DxBg).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f)) {
                Text("NEURAL DX WATCHER", color = DxInk, fontSize = 24.sp, fontWeight = FontWeight.Black)
                Text("v12.1 native · ${features.clusterStatus}", color = DxMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            DxMetric("SPOTS", features.liveSpots.size.toString(), DxCyan)
            DxMetric("SFI", features.solar.flux.takeIf { features.solar.valid }?.roundToInt()?.toString() ?: "—", DxAmber)
            DxMetric("A", features.solar.aIndex.takeIf { features.solar.valid }?.roundToInt()?.toString() ?: "—",
                if (features.solar.aIndex >= 30) DxRed else DxAmber)
            DxMetric("KP", features.solar.kpIndex.takeIf { features.solar.valid }?.let { "%.1f".format(Locale.US, it) } ?: "—",
                if (features.solar.kpIndex >= 5) DxRed else DxGreen)
            Button({ controller.refresh(stationCall, stationGrid, stationId, features.liveSpots, true); features.refreshSolar() },
                enabled = !controller.refreshing, modifier = Modifier.heightIn(min = 48.dp)) {
                Icon(Icons.Outlined.Refresh, null); Spacer(Modifier.width(5.dp)); Text(if (controller.refreshing) "REFRESHING" else "REFRESH")
            }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            NeuralDxPage.entries.forEach { item ->
                FilterChip(page == item, { page = item }, { Text(item.label, fontWeight = FontWeight.Bold) },
                    leadingIcon = { Icon(dxPageIcon(item), null, modifier = Modifier.size(18.dp)) }, modifier = Modifier.heightIn(min = 48.dp))
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(controller.status, color = if (controller.status.startsWith("All")) DxGreen else DxMuted,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text("QTH ${stationGrid.ifBlank { "NOT SET" }} · ${if (wavelog.logMode == LogMode.WAVELOG) "WAVELOG" else "LOCAL LOG"}",
                color = DxYellow, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
        }
        HorizontalDivider(color = Color(0xFF465159))
        when (page) {
            NeuralDxPage.COCKPIT -> DxCockpit(controller, features, database, wavelog, cty, tune, previousQsos, Modifier.weight(1f))
            NeuralDxPage.MAP -> DxMap(controller,features.liveSpots, cty, stationGrid, tune, previousQsos, Modifier.weight(1f))
            NeuralDxPage.INSIGHT -> DxInsightPage(controller, features, Modifier.weight(1f))
            NeuralDxPage.WORLD -> DxWorldPage(controller, features, Modifier.weight(1f))
            NeuralDxPage.BRIEFING -> DxBriefingPage(controller, features, Modifier.weight(1f))
            NeuralDxPage.SATELLITES -> DxSatellitesPage(controller, Modifier.weight(1f))
            NeuralDxPage.WEATHER -> DxWeatherPage(controller, features, Modifier.weight(1f))
        }
    }
}

@Composable private fun DxCockpit(controller: NeuralDxController, features: FeatureController, database: QsoDatabase,
    wavelog: WavelogController, cty: CtyController, tune: (String) -> Unit, previousQsos: (AndroidDXSpot) -> Unit,
    modifier: Modifier) {
    var mode by remember { mutableStateOf("COCKPIT") }; var band by remember { mutableStateOf("ALL") }
    var radioMode by remember { mutableStateOf("ALL") }; var watchOnly by remember { mutableStateOf(false) }
    var newOnly by remember { mutableStateOf(false) }; var selected by remember { mutableStateOf<AndroidDXSpot?>(null) }
    var watchSearch by remember { mutableStateOf("") }
    var manual by remember { mutableStateOf(false) }; var statuses by remember { mutableStateOf(emptyMap<String, SpotLogStatus>()) }
    val stationId = wavelog.stationId.takeIf { wavelog.logMode == LogMode.WAVELOG }
    LaunchedEffect(features.liveSpots, stationId, database.changeToken(), cty.dataRevision) {
        statuses = withContext(Dispatchers.IO) { database.spotStatuses(features.liveSpots.map { spot ->
            val entity = cty.lookup(spot.callsign); SpotLogIdentity(spot.id, spot.callsign, spot.band, spot.mode,
                entity?.dxcc.orEmpty(), entity?.country.orEmpty().ifBlank { spot.country }) }, stationId) }
    }
    val rows = features.liveSpots.filter { spot ->
        (band == "ALL" || spot.band == band) && (radioMode == "ALL" || spot.mode == radioMode) &&
            (!watchOnly || spot.watchlisted) && (!newOnly || statuses[spot.id]?.dxccStatus in setOf("ATNO", "W/NB", "C/NB"))
    }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            listOf("CLASSIC", "SMART", "COCKPIT").forEach { FilterChip(mode == it, { mode = it }, { Text(it) }) }
            DxSelect("BAND", band, DxBands) { band = it }
            DxSelect("MODE", radioMode, listOf("ALL") + features.liveSpots.map { it.mode }.filter(String::isNotBlank).distinct()) { radioMode = it }
            FilterChip(watchOnly, { watchOnly = !watchOnly }, { Text("★ WATCHLIST") })
            FilterChip(newOnly, { newOnly = !newOnly }, { Text("NEW DXCC") })
            OutlinedButton({ manual = true }, modifier = Modifier.heightIn(min = 48.dp)) { Icon(Icons.Outlined.Podcasts, null); Spacer(Modifier.width(5.dp)); Text("SEND SPOT") }
            Text("${rows.size} LIVE", color = DxCyan, fontWeight = FontWeight.Black)
        }
        if (mode == "COCKPIT") Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DxSpotFeed(rows, statuses, cty, selected = { selected = it }, previousQsos, Modifier.weight(1.55f).fillMaxHeight())
            LazyColumn(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item { DxSection("ACTIVE BANDS · 24H") { controller.bandActivity.entries.take(12).forEach { DxBar(it.key, it.value, controller.bandActivity.values.maxOrNull() ?: 1) } } }
                item { DxSection("PERSONALIZED PREDICTIONS") {
                    val measured=controller.predictions.firstOrNull()?.measuredReliability
                    Text(measured?.let{"Measured reliability · $it% over verified 30-day windows"}?:"Measured reliability · learning until 5 windows are verified",color=measured?.let{DxGreen}?:DxMuted,fontSize=12.sp)
                    controller.predictions.take(6).forEach { p -> DxLine("${p.callsign} · ${p.band} ${p.mode}", "${p.probability}%", if (p.probability >= 70) DxGreen else DxYellow) }
                    if (controller.predictions.isEmpty()) DxEmpty("Learning from live spots…")
                } }
                item { DxSection("MY SIGNAL · PSK REPORTER") {
                    Text("${controller.mySignal.callsign.ifBlank{"Station callsign"}} · ${controller.mySignal.reports.size} receivers · 5-minute source cache",color=DxMuted,fontSize=12.sp)
                    controller.mySignal.reports.take(7).forEach { report -> DxLine("${report.callsign} · ${report.band} ${report.mode}", "${report.snr?.let{"$it dB"}?:"—"} · ${report.distanceKm?.let{"$it km"}?:report.locator}", DxCyan) }
                    if (controller.mySignal.reports.isEmpty()) DxEmpty(controller.mySignal.error.ifBlank { "No recent PSK Reporter receptions" })
                } }
                item { DxSection("WATCHLIST TRACKING") {
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){OutlinedTextField(watchSearch,{watchSearch=it.uppercase()},label={Text("Search watchlist")},singleLine=true,modifier=Modifier.weight(1f));TextButton({features.setWatchlist("")},modifier=Modifier.heightIn(min=48.dp)){Text("PURGE")}}
                    features.watchSpots.filter{watchSearch.isBlank()||it.callsign.contains(watchSearch,true)}.take(8).forEach { DxLine(it.callsign, "${it.band} ${it.mode} · ${ageLabel(it.receivedEpoch)}", DxYellow) }
                    if (features.watchSpots.isEmpty()) DxEmpty("No watchlist activity yet")
                } }
                item { DxSection("LOG OPPORTUNITIES · QSL / LoTW") {
                    val opportunities=rows.filter{statuses[it.id]?.dxccStatus in setOf("ATNO","W/NB","C/NB","W")}.distinctBy{cty.lookup(it.callsign)?.dxcc.orEmpty().ifBlank{it.country}}.take(8)
                    opportunities.forEach{spot->val state=statuses[spot.id];DxLine("${spot.callsign} · ${spot.band}","${state?.dxccStatus?:"—"} · ${cty.lookup(spot.callsign)?.country.orEmpty().ifBlank{spot.country}}",if(state?.dxccStatus=="ATNO")DxRed else DxYellow)}
                    if(opportunities.isEmpty())DxEmpty("No unconfirmed or new DXCC opportunity in the live feed")
                } }
                item { DxHeatmap(controller.heatmap6m) }
            }
        } else if (mode == "SMART") DxSmartFeed(rows, statuses, cty, { selected = it }, previousQsos, Modifier.weight(1f))
        else DxSpotTable(rows, statuses, cty, { selected = it }, previousQsos, Modifier.weight(1f))
    }
    selected?.let { DxSpotDialog(it, cty, statuses[it.id], { selected = null }, { tune("FA%011d;".format(it.frequencyHz)); selected = null }) }
    if (manual) ManualSpotDialog(features) { manual = false }
}

@Composable private fun DxSpotFeed(rows: List<AndroidDXSpot>, statuses: Map<String, SpotLogStatus>, cty: CtyController,
    selected: (AndroidDXSpot) -> Unit, previous: (AndroidDXSpot) -> Unit, modifier: Modifier) {
    DxSection("DX FEED · DISTANCE / SCORE", modifier) {
        LazyColumn(Modifier.fillMaxSize()) {
            items(rows, key = { it.id }) { spot ->
                val entity = cty.lookup(spot.callsign); val state = statuses[spot.id]
                Row(Modifier.fillMaxWidth().heightIn(min = 58.dp).clickable(role = Role.Button) { selected(spot) }.padding(horizontal = 8.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(spot.callsign, color = if (spot.watchlisted) DxYellow else DxCyan, fontSize = 17.sp, fontWeight = FontWeight.Black,
                        modifier = Modifier.width(92.dp).clickable { previous(spot) }, maxLines = 1)
                    Column(Modifier.weight(1f)) { Text(entity?.country.orEmpty().ifBlank { spot.country }.ifBlank { "Unknown DXCC" }, color = DxInk, maxLines = 1)
                        Text("${spot.band} · ${spot.mode} · ${formatMHz(spot.frequencyHz)} · ${spot.spotter}", color = DxMuted, fontFamily = FontFamily.Monospace, fontSize = 12.sp) }
                    Text(state?.callStatus.orEmpty(), color = DxGreen, fontWeight = FontWeight.Bold)
                    Text(state?.dxccStatus.orEmpty(), color = if (state?.dxccStatus == "ATNO") DxRed else DxYellow, fontWeight = FontWeight.Black)
                    Text(spot.distanceKm.takeIf { it > 0 }?.let { "$it km" } ?: "—", color = DxMuted, modifier = Modifier.width(62.dp))
                    Text(spot.score.toString(), color = scoreColor(spot.score), fontSize = 18.sp, fontWeight = FontWeight.Black, modifier = Modifier.width(34.dp))
                }
                HorizontalDivider(color = Color(0xFF303940))
            }
        }
    }
}

@Composable private fun DxSmartFeed(rows: List<AndroidDXSpot>, statuses: Map<String, SpotLogStatus>, cty: CtyController,
    selected: (AndroidDXSpot) -> Unit, previous: (AndroidDXSpot) -> Unit, modifier: Modifier) {
    LazyColumn(modifier, verticalArrangement = Arrangement.spacedBy(7.dp)) { items(rows.sortedByDescending { it.score }, key = { it.id }) { spot ->
        val entity = cty.lookup(spot.callsign); val state = statuses[spot.id]
        Card({ selected(spot) }, colors = CardDefaults.cardColors(containerColor = DxPanel), modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).clickable { previous(spot) }) { Text(spot.callsign, color = if (spot.watchlisted) DxYellow else DxCyan, fontSize = 20.sp, fontWeight = FontWeight.Black)
                    Text(entity?.country.orEmpty().ifBlank { spot.country }, color = DxInk); Text(spot.reason.ifBlank { spot.comment }.ifBlank { "Live cluster observation" }, color = DxMuted, maxLines = 2) }
                Column(horizontalAlignment = Alignment.End) { Text("${spot.score}", color = scoreColor(spot.score), fontSize = 28.sp, fontWeight = FontWeight.Black)
                    Text("${spot.band} ${spot.mode}", color = DxAmber); Text("${state?.callStatus.orEmpty()} · ${state?.dxccStatus.orEmpty()}", color = DxYellow) }
            }
        }
    } }
}

@Composable private fun DxSpotTable(rows: List<AndroidDXSpot>, statuses: Map<String, SpotLogStatus>, cty: CtyController,
    selected: (AndroidDXSpot) -> Unit, previous: (AndroidDXSpot) -> Unit, modifier: Modifier) {
    val columns = listOf("UTC" to 72.dp, "CALL" to 100.dp, "BAND" to 58.dp, "MODE" to 62.dp, "FREQ" to 92.dp,
        "COUNTRY" to 190.dp, "CQ" to 40.dp, "DX DE" to 94.dp, "CS" to 44.dp, "DS" to 58.dp, "COMMENT" to 220.dp)
    Box(modifier.fillMaxWidth().border(1.dp, Color(0xFF465159), RoundedCornerShape(9.dp)).horizontalScroll(rememberScrollState())) {
        Column(Modifier.width(columns.sumOf { it.second.value.toInt() }.dp)) {
            Row(Modifier.background(DxRaised).height(46.dp), verticalAlignment = Alignment.CenterVertically) { columns.forEach { DxCell(it.first, it.second, DxInk, true) } }
            LazyColumn { items(rows, key = { it.id }) { spot -> val entity=cty.lookup(spot.callsign);val s=statuses[spot.id]
                Row(Modifier.height(52.dp).fillMaxWidth().clickable { selected(spot) }.background(if (spot.receivedEpoch % 2L == 0L) DxPanel else DxBg), verticalAlignment=Alignment.CenterVertically) {
                    DxCell(DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneOffset.UTC).format(Instant.ofEpochSecond(spot.receivedEpoch)),72.dp,DxInk)
                    Box(Modifier.width(100.dp).fillMaxHeight().clickable { previous(spot) },contentAlignment=Alignment.CenterStart){Text(spot.callsign,color=if(spot.watchlisted)DxYellow else DxCyan,fontWeight=FontWeight.Black,maxLines=1)}
                    DxCell(spot.band,58.dp,DxInk);DxCell(spot.mode,62.dp,DxInk);DxCell(formatMHz(spot.frequencyHz),92.dp,DxAmber)
                    DxCell(entity?.country.orEmpty().ifBlank{spot.country},190.dp,DxInk);DxCell(entity?.cqZone.orEmpty().ifBlank{spot.cqZone.toString()},40.dp,DxInk)
                    DxCell(spot.spotter,94.dp,DxMuted);DxCell(s?.callStatus.orEmpty(),44.dp,DxGreen,true);DxCell(s?.dxccStatus.orEmpty(),58.dp,if(s?.dxccStatus=="ATNO")DxRed else DxYellow,true)
                    DxCell(spot.comment,220.dp,DxMuted)
                }
            } }
        }
    }
}

@Composable private fun DxMap(controller:NeuralDxController,rows: List<AndroidDXSpot>, cty: CtyController, stationGrid: String, tune: (String) -> Unit,
    previous: (AndroidDXSpot) -> Unit, modifier: Modifier) {
    var minutes by remember { mutableIntStateOf(60) }; var limit by remember { mutableIntStateOf(250) }
    var band by remember { mutableStateOf("ALL") }; var mode by remember { mutableStateOf("ALL") }; var hearsMe by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<AndroidDXSpot?>(null) }; val now=Instant.now().epochSecond
    val filtered=rows.filter{now-it.receivedEpoch<=minutes*60&&(band=="ALL"||it.band==band)&&(mode=="ALL"||it.mode==mode)}.take(limit)
    Column(modifier,verticalArrangement=Arrangement.spacedBy(8.dp)){
        Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(7.dp),verticalAlignment=Alignment.CenterVertically){
            DxSelect("WINDOW","${minutes}m",listOf("15m","30m","60m","180m","360m")){minutes=it.removeSuffix("m").toInt()}
            DxSelect("LIMIT",limit.toString(),listOf("50","100","250","500","1000")){limit=it.toInt()}
            DxSelect("BAND",band,DxBands){band=it};DxSelect("MODE",mode,listOf("ALL")+rows.map{it.mode}.distinct()){mode=it}
            FilterChip(hearsMe,{hearsMe=!hearsMe},{Text("WHO HEARS ME")});Text("${filtered.size} OBSERVATIONS",color=DxCyan,fontWeight=FontWeight.Black)
        }
        Row(Modifier.fillMaxSize(),horizontalArrangement=Arrangement.spacedBy(8.dp)){
            if(hearsMe)DxReceiverMap(controller.mySignal.reports,stationGrid,Modifier.weight(1.65f).fillMaxHeight())else DxWorldCanvas(filtered,stationGrid,false,Modifier.weight(1.65f).fillMaxHeight())
            DxSection(if(hearsMe)"RECEIVERS / SPOTTERS" else "MAP OBSERVATIONS",Modifier.weight(1f).fillMaxHeight()){
                if(hearsMe)LazyColumn{items(controller.mySignal.reports,key={it.callsign}){report->Row(Modifier.fillMaxWidth().heightIn(min=54.dp).padding(6.dp),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(report.callsign,color=DxGreen,fontWeight=FontWeight.Black);Text(report.locator,color=DxMuted)};Text("${report.band} ${report.mode}\n${report.snr?.let{"$it dB"}?:"—"} · ${report.distanceKm?.let{"$it km"}?:"—"}",color=DxAmber,fontFamily=FontFamily.Monospace)}}}else LazyColumn{items(filtered,key={it.id}){spot->Row(Modifier.fillMaxWidth().heightIn(min=54.dp).clickable{selected=spot}.padding(6.dp),verticalAlignment=Alignment.CenterVertically){
                    Column(Modifier.weight(1f).clickable{previous(spot)}){Text(if(hearsMe)spot.spotter else spot.callsign,color=DxCyan,fontWeight=FontWeight.Black);Text(cty.lookup(spot.callsign)?.country.orEmpty().ifBlank{spot.country},color=DxMuted,maxLines=1)}
                    Text("${spot.band} ${spot.mode}\n${formatMHz(spot.frequencyHz)}",color=DxAmber,fontFamily=FontFamily.Monospace)
                };HorizontalDivider(color=Color(0xFF303940))}}
            }
        }
    }
    selected?.let{DxSpotDialog(it,cty,null,{selected=null},{tune("FA%011d;".format(it.frequencyHz));selected=null})}
}

@Composable private fun DxInsightPage(controller: NeuralDxController, features: FeatureController, modifier: Modifier){
    LazyColumn(modifier,verticalArrangement=Arrangement.spacedBy(8.dp)){
        item{DxSection("AI OPERATOR REPORT · ${controller.insight.source}"){Text(controller.insight.title,color=DxCyan,fontSize=20.sp,fontWeight=FontWeight.Black);Spacer(Modifier.height(7.dp));Text(controller.insight.report,color=DxInk,fontSize=16.sp,lineHeight=23.sp);if(controller.insight.error.isNotBlank())Text(controller.insight.error,color=DxYellow)}}
        item{Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){DxSummaryCard("QSOs",controller.insight.log.qsos.toString(),Modifier.weight(1f));DxSummaryCard("CALLS",controller.insight.log.calls.toString(),Modifier.weight(1f));DxSummaryCard("DXCC",controller.insight.log.dxccs.toString(),Modifier.weight(1f));DxSummaryCard("CONFIRMED",controller.insight.log.confirmedDxccs.toString(),Modifier.weight(1f))}}
        item{Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){
            DxSection("TACTICAL OPPORTUNITIES",Modifier.weight(1f)){controller.insight.recommendations.forEachIndexed{i,v->DxLine("${i+1}",v,scoreColor(features.liveSpots.getOrNull(i)?.score?:0))};if(controller.insight.recommendations.isEmpty())DxEmpty("No current recommendation")}
            DxSection("LOG DISTRIBUTION",Modifier.weight(1f)){Text("Bands",color=DxAmber,fontWeight=FontWeight.Bold);controller.insight.log.bands.entries.take(8).forEach{DxLine(it.key,it.value.toString(),DxCyan)};Text("Modes",color=DxAmber,fontWeight=FontWeight.Bold);controller.insight.log.modes.entries.take(6).forEach{DxLine(it.key,it.value.toString(),DxGreen)}}
        }}
        item{DxSection("ANALYSIS HONESTY"){Text("The local report is deterministic and uses only the configured log, live cluster, CTY.DAT, solar and weather caches. Missing data remains marked unavailable. Optional Perplexity Sonar-Pro enrichment never replaces local measurements.",color=DxMuted)}}
    }
}

@Composable private fun DxWorldPage(controller: NeuralDxController, features: FeatureController, modifier: Modifier){
    var anomaliesOnly by remember{mutableStateOf(false)};var greyline by remember{mutableStateOf(true)}
    val rows=controller.world.filter{!anomaliesOnly||(it.anomalyRatio?:0.0)>=1.8}
    Column(modifier,verticalArrangement=Arrangement.spacedBy(8.dp)){
        Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(7.dp),verticalAlignment=Alignment.CenterVertically){
            DxSelect("BAND",controller.worldBand,DxBands){controller.setWorldFilter(controller.worldWindowMinutes,it)}
            DxSelect("WINDOW","${controller.worldWindowMinutes}m",listOf("15m","30m","60m","180m","360m")){controller.setWorldFilter(it.removeSuffix("m").toInt(),controller.worldBand)}
            FilterChip(anomaliesOnly,{anomaliesOnly=!anomaliesOnly},{Text("ANOMALIES ONLY")});FilterChip(greyline,{greyline=!greyline},{Text("GREY LINE")})
            Text("${rows.size} ACTIVE CELLS",color=DxCyan,fontWeight=FontWeight.Black)
        }
        Row(Modifier.fillMaxSize(),horizontalArrangement=Arrangement.spacedBy(8.dp)){
            DxWorldAnomalyCanvas(rows,greyline,Modifier.weight(1.6f).fillMaxHeight())
            DxSection("FORECAST / ANOMALIES",Modifier.weight(1f).fillMaxHeight()){
                LazyColumn{items(rows){cell->Column(Modifier.fillMaxWidth().padding(vertical=6.dp)){Row{Text("${hemisphere(cell.latitude,"N","S")} ${hemisphere(cell.longitude,"E","W")}",color=DxCyan,fontWeight=FontWeight.Black,modifier=Modifier.weight(1f));Text(cell.anomalyRatio?.let{"×%.1f".format(it)}?:"LEARNING",color=if((cell.anomalyRatio?:0.0)>=1.8)DxRed else DxYellow,fontWeight=FontWeight.Black)};Text("${cell.observed} observed · ${cell.expected?.let{"%.1f expected".format(it)}?:"baseline incomplete"} · ${cell.confidence}",color=DxMuted);Text(cell.calls.joinToString(),color=DxInk,maxLines=1)};HorizontalDivider(color=Color(0xFF303940))}}
            }
        }
    }
}

@Composable private fun DxBriefingPage(controller:NeuralDxController,features:FeatureController,modifier:Modifier){
    var expanded by remember{mutableStateOf<String?>(null)}
    LazyColumn(modifier,verticalArrangement=Arrangement.spacedBy(8.dp)){
        item{Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text("12-hour DX intelligence cache",color=DxMuted,modifier=Modifier.weight(1f));FilterChip(controller.briefingDxMode,{controller.saveSettings(controller.notificationsEnabled,controller.ntfyUrl,controller.ntfyToken,controller.perplexityKey,!controller.briefingDxMode)},{Text("DX MODE · CALLS")})}}
        items(controller.briefing,key={it.id}){source->DxSection("${source.name} · ${source.items.size} ITEMS"){
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text("${source.site} · ${if(source.stale)"STALE CACHE" else ageLabel(source.updatedEpoch)}",color=if(source.stale)DxYellow else DxMuted,modifier=Modifier.weight(1f),maxLines=1);IconButton({controller.moveBriefingSource(source.id,-1)},modifier=Modifier.size(48.dp)){Icon(Icons.Outlined.KeyboardArrowUp,"Move source up")};IconButton({controller.moveBriefingSource(source.id,1)},modifier=Modifier.size(48.dp)){Icon(Icons.Outlined.KeyboardArrowDown,"Move source down")}}
            if(source.error.isNotBlank())Text(source.error,color=DxYellow)
            source.items.take(8).forEach{item->Column(Modifier.fillMaxWidth().clickable{expanded=if(expanded==item.link)null else item.link}.padding(vertical=7.dp)){Text(item.title,color=DxCyan,fontWeight=FontWeight.Bold,maxLines=if(expanded==item.link)4 else 2);if(item.published.isNotBlank())Text(item.published,color=DxMuted,fontSize=12.sp);if(expanded==item.link){Text(item.summary,color=DxInk);if(controller.briefingDxMode&&item.callsigns.isNotEmpty())Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(5.dp)){item.callsigns.forEach{call->AssistChip({features.setWatchlist(features.watchlistText+"\n"+call)},{Text("★ $call")})}}}};HorizontalDivider(color=Color(0xFF303940))}
            if(source.items.isEmpty())DxEmpty("No cached items; refresh when online")
        }}
    }
}

@Composable private fun DxSatellitesPage(controller:NeuralDxController,modifier:Modifier){
    var selected by remember{mutableStateOf<Int?>(null)};var search by remember{mutableStateOf("")};var window by remember{mutableIntStateOf(24)}
    val positions=controller.satellites;val catalog=controller.satelliteCatalogue.filter{search.isBlank()||it.name.contains(search,true)||it.norad.toString().contains(search)}.take(80)
    Column(modifier,verticalArrangement=Arrangement.spacedBy(8.dp)){
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){
            DxSatelliteMap(positions,Modifier.weight(1.5f).height(260.dp));DxSection("LIVE POSITIONS",Modifier.weight(1f).height(260.dp)){LazyColumn{items(positions,key={it.norad}){p->Row(Modifier.fillMaxWidth().heightIn(min=48.dp).clickable{selected=p.norad;controller.refreshSatelliteTransmitters(p.norad)}.padding(5.dp),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(p.name,color=if(p.visible)DxGreen else DxCyan,fontWeight=FontWeight.Black,maxLines=1);Text("${p.norad} · ${p.altitudeKm.roundToInt()} km",color=DxMuted)};Text("AZ ${p.azimuth.roundToInt()}°\nEL ${p.elevation.roundToInt()}°",color=DxAmber,fontFamily=FontFamily.Monospace)}}}}
            }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){
            DxSection("PASSES ABOVE QTH",Modifier.weight(1.4f).height(250.dp)){Row(horizontalArrangement=Arrangement.spacedBy(5.dp)){listOf(4,12,24).forEach{FilterChip(window==it,{window=it},{Text("${it}h")})}};LazyColumn{items(controller.passes.filter{it.aosEpoch<=Instant.now().epochSecond+window*3600L}.take(25)){p->Row(Modifier.fillMaxWidth().padding(vertical=5.dp)){Text(p.name,color=DxCyan,fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f),maxLines=1);Text("${utcTime(p.aosEpoch)}–${utcTime(p.losEpoch)} · ${p.maxElevation.roundToInt()}°",color=if(p.maxElevation>=45)DxGreen else if(p.maxElevation>=20)DxYellow else DxAmber,fontFamily=FontFamily.Monospace)}}}}
            DxSection("FOLLOWED / AMSAT CATALOG",Modifier.weight(1f).height(250.dp)){OutlinedTextField(search,{search=it},label={Text("Search satellites")},singleLine=true,modifier=Modifier.fillMaxWidth());LazyColumn{items(catalog,key={it.norad}){o->val followed=o.norad in controller.followedNorads;Row(Modifier.fillMaxWidth().heightIn(min=48.dp).clickable{controller.setFollowed(o.norad,!followed)}.padding(5.dp),verticalAlignment=Alignment.CenterVertically){Checkbox(followed,null);Text(o.name,color=DxInk,modifier=Modifier.weight(1f),maxLines=1);Text(o.norad.toString(),color=DxMuted)}}}}
        }
    }
    selected?.let{norad->val pos=positions.firstOrNull{it.norad==norad};AlertDialog(onDismissRequest={selected=null},title={Text(pos?.name?:"Satellite $norad")},text={Column(Modifier.heightIn(max=520.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(6.dp)){pos?.let{Text("Lat %.2f° · Lon %.2f° · Alt %.0f km".format(it.latitude,it.longitude,it.altitudeKm));Text("Az %.0f° · El %.0f° · Range %.0f km · Footprint %.0f km".format(it.azimuth,it.elevation,it.rangeKm,it.footprintKm))};HorizontalDivider();Text("SatNOGS transmitters",fontWeight=FontWeight.Bold);controller.transmitters[norad]?.forEach{t->Text("${t.description}\n↓ ${t.downlink} · ↑ ${t.uplink} · ${t.mode}",color=DxMuted)}?:Text("Loading frequencies…",color=DxMuted)}},confirmButton={TextButton({selected=null}){Text("Close")}})}
}

@Composable private fun DxWeatherPage(controller:NeuralDxController,features:FeatureController,modifier:Modifier){
    var cond by remember{mutableStateOf("HF")};var zone by remember{mutableStateOf("EUROPE")};val weather=controller.weather
    val hfPct=when{!controller.wspr.available->null;controller.wspr.hf.sumOf{it.spots}>=25->85;controller.wspr.hf.sumOf{it.spots}>=8->65;else->42}
    val vhfSnr=controller.wspr.vhf.mapNotNull{it.averageSnr}.average().takeIf{it.isFinite()};val vhfPct=vhfSnr?.let{((it+25)*4).roundToInt().coerceIn(0,100)}?:weather.tropoIndex?.times(6)
    val global=listOfNotNull(hfPct,vhfPct).average().takeIf{it.isFinite()}?.roundToInt()
    LazyColumn(modifier,verticalArrangement=Arrangement.spacedBy(8.dp)){
        item{Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){
            DxSection("GLOBAL SYNTHESIS",Modifier.weight(1f)){DxGauge(global,"GLOBAL");Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceAround){DxMiniValue("HF",hfPct?.let{"$it%"}?:"—");DxMiniValue("VHF",vhfPct?.let{"$it%"}?:"—")};Text("Heuristic visual reference, not a calibrated physical measurement.",color=DxMuted,fontSize=12.sp)}
            DxSection("ELECTRICAL ACTIVITY",Modifier.weight(1f)){val strikes=controller.lightning.strikes;Text("REGIONAL LIGHTNING",color=DxCyan,fontWeight=FontWeight.Black);DxLine("Live source",if(controller.lightning.connected)"CONNECTED" else "UNAVAILABLE",if(controller.lightning.connected)DxGreen else DxYellow);DxLine("Last hour",if(controller.lightning.connected)strikes.size.toString() else "—",DxInk);strikes.firstOrNull()?.let{DxLine("Nearest/latest","${it.distanceKm} km ${it.bearing} · ${ageLabel(it.epoch)}",if(it.distanceKm<50)DxRed else DxYellow)};if(!controller.lightning.connected)Text(controller.lightning.error.ifBlank{"Connecting to the community regional feed; zero is never fabricated."},color=DxMuted);Text("Source: ${controller.lightning.source}",color=DxMuted,fontSize=11.sp)}
            DxSection("CURRENT CONDITIONS",Modifier.weight(1f)){Row{listOf("HF","VHF/UHF").forEach{FilterChip(cond==it,{cond=it},{Text(it)})}};if(cond=="HF"){DxLine("Temperature",weather.temperatureC?.let{"%.1f °C".format(it)}?:"—",if((weather.temperatureC?:0.0)>=30)DxRed else DxInk);DxLine("Pressure",weather.pressureHpa?.let{"%.0f hPa".format(it)}?:"—",if((weather.pressureHpa?:1100.0)<1000)DxRed else DxInk);DxLine("2h trend",weather.pressureTrend,DxYellow);DxLine("Humidity",weather.humidityPercent?.let{"$it%"}?:"—",DxInk);DxLine("Wind",weather.windKmh?.let{"%.0f km/h · %03d°".format(it,weather.windDirection?:0)}?:"—",DxInk);DxLine("Precipitation",weather.precipitationMm?.let{"$it mm"}?:"—",DxInk)}else{DxLine("Tropo / ducting",weather.ductingRisk?:"—",if(weather.ductingRisk=="HIGH")DxGreen else DxYellow);DxLine("Tropo index",weather.tropoIndex?.let{"$it / 10"}?:"—",DxInk);DxLine("850 hPa temp",weather.temperature850C?.let{"%.1f °C".format(it)}?:"—",DxInk);DxLine("300 hPa wind",weather.wind300Kmh?.let{"%.0f km/h".format(it)}?:"—",DxInk);DxLine("CAPE",weather.cape?.let{"%.0f J/kg".format(it)}?:"—",DxInk);val two=controller.wspr.vhf.firstOrNull{it.band=="2m"};DxLine("WSPR 2m",two?.let{"${it.spots} · ${it.averageSnr?.let{n->"%.1f dB".format(n)}?:"—"}"}?:"NO BEACON DATA",if(two!=null)DxGreen else DxMuted)}}
        }}
        item{Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){
            DxSection("NOISE / QRN",Modifier.weight(1f)){val total=controller.wspr.hf.sumOf{it.spots};val strikes=controller.lightning.strikes;val closest=strikes.minOfOrNull{it.distanceKm};val qrn=when{closest!=null&&closest<50->92;closest!=null&&closest<120->76;closest!=null&&closest<300->58;else->hfPct};val level=when{closest!=null&&closest<50->"SEVERE QRN";closest!=null&&closest<120->"HIGH QRN";total>=40->"CALM / ACTIVE";total>=12->"ELEVATED";total>0->"SPARSE";else->"—"};DxGauge(qrn,level);Text(when{strikes.isNotEmpty()->"${strikes.size} lightning strikes within 300 km in the last hour; nearest ${closest} km.";total>0->"$total nearby WSPR receptions in the 30-minute source window.";else->"No live lightning or WSPR measurement; no noise value is inferred."},color=DxMuted)}
            DxSection("QUICK VOACAP · $zone",Modifier.weight(1.3f)){DxSelect("PATH",zone,listOf("EUROPE","AMERICAS","ASIA","OCEANIA","AFRICA")){zone=it};listOf("80m","40m","30m","20m","17m","15m","12m","10m").forEach{band->val rel=voacapReliability(band,zone,features.solar.flux,features.solar.kpIndex);DxBar(band,rel,100)};Text("Single-path heuristic; it may differ from global observations.",color=DxMuted,fontSize=12.sp)}
            DxSection("BAND ACTIVITY · 24H",Modifier.weight(1f)){controller.bandActivity.entries.take(12).forEach{DxBar(it.key,it.value,controller.bandActivity.values.maxOrNull()?:1)};if(controller.bandActivity.isEmpty())DxEmpty("Cluster history is still learning")}
        }}
        item{DxSection("VHF / UHF / SHF BEACONS"){
            Text(controller.beaconStatus,color=DxMuted,fontSize=12.sp)
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)){
                Column(Modifier.weight(1f)){Text("RECEIVED FROM CLUSTER",color=DxCyan,fontWeight=FontWeight.Bold);if(controller.beacons.isEmpty())DxEmpty("No nearby beacon report; this does not mean no opening.")else controller.beacons.take(12).forEach{b->DxLine("${if(b.known)"★" else "·"} ${b.callsign} · ${b.band}","${formatMHz(b.frequencyHz)} · ${b.ageMinutes}m · ${b.spotter}",if(b.known)DxGreen else DxCyan)}}
                Column(Modifier.weight(1f)){Text("REFERENCE · IN INDICATIVE RANGE",color=DxCyan,fontWeight=FontWeight.Bold);controller.beaconReference.filter{it.inTypicalRange}.take(12).forEach{b->DxLine("${b.callsign} · ${b.band}","${"%.4f".format(Locale.US,b.frequencyMHz)} · ${b.distanceKm} km ${b.bearing}",DxInk)};if(controller.beaconReference.none{it.inTypicalRange})DxEmpty("Reference will populate after the monthly source refresh.")}
            }
        }}
    }
}

@Composable private fun DxSection(title:String,modifier:Modifier=Modifier,content:@Composable ColumnScope.()->Unit){Column(modifier.background(DxPanel,RoundedCornerShape(10.dp)).border(1.dp,Color(0xFF3D474E),RoundedCornerShape(10.dp)).padding(10.dp),verticalArrangement=Arrangement.spacedBy(5.dp)){Text(title,color=DxAmber,fontSize=13.sp,fontWeight=FontWeight.Black);HorizontalDivider(color=Color(0xFF374047));content()}}
@Composable private fun DxMetric(label:String,value:String,color:Color){Column(Modifier.widthIn(min=64.dp).background(DxPanel,RoundedCornerShape(8.dp)).padding(horizontal=9.dp,vertical=5.dp),horizontalAlignment=Alignment.CenterHorizontally){Text(label,color=DxMuted,fontSize=10.sp,fontWeight=FontWeight.Bold);Text(value,color=color,fontSize=18.sp,fontWeight=FontWeight.Black,fontFamily=FontFamily.Monospace)}}
@Composable private fun DxSummaryCard(label:String,value:String,modifier:Modifier){Column(modifier.background(DxPanel,RoundedCornerShape(9.dp)).padding(12.dp),horizontalAlignment=Alignment.CenterHorizontally){Text(value,color=DxCyan,fontSize=28.sp,fontWeight=FontWeight.Black);Text(label,color=DxMuted,fontWeight=FontWeight.Bold)}}
@Composable private fun DxLine(label:String,value:String,color:Color=DxInk){Row(Modifier.fillMaxWidth().heightIn(min=30.dp),verticalAlignment=Alignment.CenterVertically){Text(label,color=DxMuted,modifier=Modifier.weight(1f),maxLines=1,overflow=TextOverflow.Ellipsis);Text(value,color=color,fontWeight=FontWeight.Bold,maxLines=1)}}
@Composable private fun DxMiniValue(label:String,value:String){Column(horizontalAlignment=Alignment.CenterHorizontally){Text(value,color=DxCyan,fontSize=21.sp,fontWeight=FontWeight.Black);Text(label,color=DxMuted,fontSize=11.sp)}}
@Composable private fun DxEmpty(text:String){Text(text,color=DxMuted,fontStyle=androidx.compose.ui.text.font.FontStyle.Italic,modifier=Modifier.padding(vertical=8.dp))}
@Composable private fun DxBar(label:String,value:Int,maxValue:Int){Row(Modifier.fillMaxWidth().height(23.dp),verticalAlignment=Alignment.CenterVertically){Text(label,color=DxInk,modifier=Modifier.width(48.dp),fontWeight=FontWeight.Bold);Box(Modifier.weight(1f).height(8.dp).background(DxRaised,RoundedCornerShape(4.dp))){Box(Modifier.fillMaxWidth((value.toFloat()/maxValue.coerceAtLeast(1)).coerceIn(0f,1f)).fillMaxHeight().background(if(value.toFloat()/maxValue.coerceAtLeast(1)>.7f)DxGreen else DxCyan,RoundedCornerShape(4.dp)))};Text(value.toString(),color=DxMuted,modifier=Modifier.width(38.dp),fontFamily=FontFamily.Monospace)}}
@Composable private fun DxGauge(value:Int?,label:String){Box(Modifier.fillMaxWidth().height(74.dp),contentAlignment=Alignment.Center){Canvas(Modifier.fillMaxSize()){drawArc(DxRaised,180f,180f,false,style=Stroke(12.dp.toPx()));if(value!=null)drawArc(if(value>=70)DxGreen else if(value>=40)DxYellow else DxRed,180f,180f*value/100f,false,style=Stroke(12.dp.toPx()))};Column(horizontalAlignment=Alignment.CenterHorizontally,modifier=Modifier.padding(top=25.dp)){Text(value?.let{"$it%"}?:"—",color=DxInk,fontSize=22.sp,fontWeight=FontWeight.Black);Text(label,color=DxMuted,fontSize=10.sp)}}}
@Composable private fun DxHeatmap(rows:List<List<Int>>){DxSection("6m ACTIVITY HEATMAP · UTC"){val max=rows.flatten().maxOrNull()?.coerceAtLeast(1)?:1;Canvas(Modifier.fillMaxWidth().height(108.dp)){val cw=size.width/24;val ch=size.height/7;rows.forEachIndexed{r,line->line.forEachIndexed{c,v->drawRect(DxCyan.copy(alpha=.08f+.82f*v/max),Offset(c*cw,r*ch),androidx.compose.ui.geometry.Size(cw-1,ch-1))}}};Text("Mon → Sun · 00 → 23 UTC",color=DxMuted,fontSize=11.sp)}}
@Composable private fun DxCell(text:String,width:Dp,color:Color,bold:Boolean=false){Text(text,color=color,fontWeight=if(bold)FontWeight.Black else FontWeight.Medium,fontFamily=if(width<=100.dp)FontFamily.Monospace else FontFamily.Default,maxLines=1,overflow=TextOverflow.Ellipsis,modifier=Modifier.width(width).padding(horizontal=5.dp))}
@Composable private fun DxSelect(label:String,value:String,choices:List<String>,change:(String)->Unit){var open by remember{mutableStateOf(false)};Box{OutlinedButton({open=true},modifier=Modifier.heightIn(min=48.dp)){Text("$label · $value",fontWeight=FontWeight.Bold);Icon(Icons.Outlined.ArrowDropDown,null)};DropdownMenu(open,{open=false}){choices.distinct().forEach{choice->DropdownMenuItem({Text(choice)},onClick={change(choice);open=false},trailingIcon={if(choice==value)Icon(Icons.Outlined.Check,null)})}}}}

@Composable private fun DxWorldCanvas(rows:List<AndroidDXSpot>,stationGrid:String,hearsMe:Boolean,modifier:Modifier){val qth=maidenheadCenter(stationGrid);Canvas(modifier.background(Color(0xFF0B161B),RoundedCornerShape(10.dp)).border(1.dp,Color(0xFF3D474E),RoundedCornerShape(10.dp)).semantics{contentDescription="World map with ${rows.size} DX observations"}){for(i in 1 until 6)drawLine(Color(0xFF27404A),Offset(0f,size.height*i/6),Offset(size.width,size.height*i/6));for(i in 1 until 12)drawLine(Color(0xFF27404A),Offset(size.width*i/12,0f),Offset(size.width*i/12,size.height));qth?.let{drawCircle(DxAmber,7.dp.toPx(),geoOffset(it.latitude,it.longitude,size.width,size.height))};rows.forEach{spot->val lat=spot.latitude;val lon=-spot.longitude;if(lat!=0.0||lon!=0.0){val p=geoOffset(lat,lon,size.width,size.height);drawCircle(if(spot.watchlisted)DxYellow else if(hearsMe)DxGreen else DxCyan,if(spot.watchlisted)6.dp.toPx() else 4.dp.toPx(),p)}}}}
@Composable private fun DxReceiverMap(rows:List<SignalReport>,stationGrid:String,modifier:Modifier){val qth=maidenheadCenter(stationGrid);Canvas(modifier.background(Color(0xFF0B161B),RoundedCornerShape(10.dp)).border(1.dp,Color(0xFF3D474E),RoundedCornerShape(10.dp)).semantics{contentDescription="Who Hears Me map with ${rows.size} PSK Reporter receivers"}){for(i in 1 until 6)drawLine(Color(0xFF27404A),Offset(0f,size.height*i/6),Offset(size.width,size.height*i/6));for(i in 1 until 12)drawLine(Color(0xFF27404A),Offset(size.width*i/12,0f),Offset(size.width*i/12,size.height));val origin=qth?.let{geoOffset(it.latitude,it.longitude,size.width,size.height)};origin?.let{drawCircle(DxAmber,7.dp.toPx(),it)};rows.forEach{r->val lat=r.latitude;val lon=r.longitude;if(lat!=null&&lon!=null){val point=geoOffset(lat,lon,size.width,size.height);origin?.let{drawLine(DxGreen.copy(alpha=.35f),it,point,1.5.dp.toPx())};drawCircle(DxGreen,5.dp.toPx(),point)}}}}
@Composable private fun DxWorldAnomalyCanvas(rows:List<NeuralWorldCell>,greyline:Boolean,modifier:Modifier){Canvas(modifier.background(Color(0xFF0B161B),RoundedCornerShape(10.dp)).border(1.dp,Color(0xFF3D474E),RoundedCornerShape(10.dp)).semantics{contentDescription="World propagation anomaly map"}){val cw=size.width/12;val ch=size.height/6;for(r in 0 until 6)for(c in 0 until 12){drawRect(Color(0xFF18313A),Offset(c*cw,r*ch),androidx.compose.ui.geometry.Size(cw-1,ch-1))};rows.forEach{cell->val ratio=cell.anomalyRatio?:1.0;val color=when{ratio>=2.5->DxRed;ratio>=1.8->DxYellow;else->DxCyan};drawRect(color.copy(alpha=(.28+.18*ratio).toFloat().coerceAtMost(.9f)),Offset(cell.column*cw,cell.row*ch),androidx.compose.ui.geometry.Size(cw-1,ch-1));if(greyline&&cell.greyline)drawRect(DxAmber,Offset(cell.column*cw,cell.row*ch),androidx.compose.ui.geometry.Size(cw-1,ch-1),style=Stroke(2.dp.toPx()))}}}
@Composable private fun DxSatelliteMap(rows:List<SatellitePosition>,modifier:Modifier){Canvas(modifier.background(Color(0xFF0B161B),RoundedCornerShape(10.dp)).border(1.dp,Color(0xFF3D474E),RoundedCornerShape(10.dp)).semantics{contentDescription="Satellite world map"}){for(i in 1 until 6)drawLine(Color(0xFF27404A),Offset(0f,size.height*i/6),Offset(size.width,size.height*i/6));for(i in 1 until 12)drawLine(Color(0xFF27404A),Offset(size.width*i/12,0f),Offset(size.width*i/12,size.height));rows.forEach{p->val o=geoOffset(p.latitude,p.longitude,size.width,size.height);drawCircle(if(p.visible)DxGreen else DxCyan,6.dp.toPx(),o);drawCircle((if(p.visible)DxGreen else DxCyan).copy(alpha=.25f),(p.footprintKm/120).toFloat().coerceIn(8f,50f),o,style=Stroke(1.dp.toPx()))}}}

@Composable private fun DxSpotDialog(spot:AndroidDXSpot,cty:CtyController,status:SpotLogStatus?,dismiss:()->Unit,tune:()->Unit){val e=cty.lookup(spot.callsign);AlertDialog(onDismissRequest=dismiss,title={Text(spot.callsign)},text={Column(verticalArrangement=Arrangement.spacedBy(6.dp)){Text("${formatMHz(spot.frequencyHz)} MHz · ${spot.band} · ${spot.mode}",color=DxAmber,fontWeight=FontWeight.Bold);Text(e?.country.orEmpty().ifBlank{spot.country}.ifBlank{"Unknown DXCC"});Text("CQ ${e?.cqZone.orEmpty().ifBlank{spot.cqZone.toString()}} · ITU ${e?.ituZone.orEmpty().ifBlank{spot.ituZone.toString()}} · ${e?.continent.orEmpty().ifBlank{spot.continent}}",color=DxMuted);Text("Call ${status?.callStatus?:"—"} · DXCC ${status?.dxccStatus?:"—"}",color=DxYellow);Text("Score ${spot.score} · confidence ${spot.confidence} · ${spot.samples} samples");Text(spot.reason.ifBlank{spot.comment}.ifBlank{"No additional analysis"},color=DxMuted)}},confirmButton={Button(tune){Text("Tune VFO A")}},dismissButton={TextButton(dismiss){Text("Close")}})}
@Composable private fun ManualSpotDialog(features:FeatureController,dismiss:()->Unit){var call by remember{mutableStateOf("")};var freq by remember{mutableStateOf("")};var comment by remember{mutableStateOf("")};AlertDialog(onDismissRequest=dismiss,title={Text("Send DX cluster spot")},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){OutlinedTextField(call,{call=it.uppercase()},label={Text("Callsign")},singleLine=true);OutlinedTextField(freq,{freq=it.filter{c->c.isDigit()||c=='.'}},label={Text("Frequency kHz")},singleLine=true);OutlinedTextField(comment,{comment=it},label={Text("Comment")},singleLine=true);Text("Posts to the currently connected cluster only.",color=DxMuted)}},confirmButton={Button({features.postSpot(call,freq.toDoubleOrNull()?:0.0,comment);dismiss()},enabled=call.isNotBlank()&&freq.toDoubleOrNull()!=null){Text("Send spot")}},dismissButton={TextButton(dismiss){Text("Cancel")}})}

private fun dxPageIcon(page:NeuralDxPage)=when(page){NeuralDxPage.COCKPIT->Icons.Outlined.SpaceDashboard;NeuralDxPage.MAP->Icons.Outlined.Map;NeuralDxPage.INSIGHT->Icons.Outlined.Psychology;NeuralDxPage.WORLD->Icons.Outlined.Public;NeuralDxPage.BRIEFING->Icons.Outlined.Newspaper;NeuralDxPage.SATELLITES->Icons.Outlined.SatelliteAlt;NeuralDxPage.WEATHER->Icons.Outlined.Thunderstorm}
private fun scoreColor(score:Int)=when{score>=75->DxGreen;score>=55->DxYellow;else->DxMuted}
private fun formatMHz(hz:Long)="%.3f".format(Locale.US,hz/1_000_000.0)
private fun ageLabel(epoch:Long):String{val m=((Instant.now().epochSecond-epoch).coerceAtLeast(0)/60).toInt();return if(m<60)"${m}m" else "${m/60}h ${m%60}m"}
private fun utcTime(epoch:Long)=DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneOffset.UTC).format(Instant.ofEpochSecond(epoch))
private fun geoOffset(lat:Double,lon:Double,width:Float,height:Float)=Offset(((lon+180.0)/360.0*width).toFloat().coerceIn(0f,width),((90.0-lat)/180.0*height).toFloat().coerceIn(0f,height))
private fun hemisphere(value:Double,positive:String,negative:String)="%.0f°%s".format(kotlin.math.abs(value),if(value>=0)positive else negative)
private fun voacapReliability(band:String,zone:String,sfi:Float,kp:Float):Int{val base=mapOf("80m" to 58,"40m" to 70,"30m" to 76,"20m" to 82,"17m" to 72,"15m" to 64,"12m" to 50,"10m" to 42)[band]?:50;val flux=((sfi-80)/2).roundToInt().coerceIn(-15,25);val storm=(kp*5).roundToInt();val distance=when(zone){"EUROPE"->8;"AFRICA"->2;"ASIA"->-4;"AMERICAS"->-7;else->-8};return(base+flux-storm+distance).coerceIn(0,100)}
