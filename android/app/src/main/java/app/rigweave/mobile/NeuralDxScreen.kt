package app.rigweave.mobile

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.ContentScale
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
import java.net.HttpURLConnection
import java.net.URL
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
        controller.ingest(features.liveSpots, stationId, cty, stationCall)
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
            NeuralDxPage.MAP -> DxMap(controller,controller.enrichedSpots.ifEmpty { features.liveSpots }, cty, stationGrid, tune, previousQsos, Modifier.weight(1f))
            NeuralDxPage.INSIGHT -> DxInsightPage(controller, features, Modifier.weight(1f))
            NeuralDxPage.WORLD -> DxWorldPage(controller, features, Modifier.weight(1f))
            NeuralDxPage.BRIEFING -> DxBriefingPage(controller, features, Modifier.weight(1f))
            NeuralDxPage.SATELLITES -> DxSatellitesPage(controller, stationGrid, Modifier.weight(1f))
            NeuralDxPage.WEATHER -> DxWeatherPage(controller, features, stationGrid, Modifier.weight(1f))
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
    DxSection("DX FEED · WORKED STATUS · DISTANCE · SCORE", modifier) {
        DxSpotHeader()
        LazyColumn(Modifier.fillMaxSize()) { items(rows, key = { it.id }) { spot ->
            DxSpotRow(spot, statuses[spot.id], cty, selected, previous)
        } }
    }
}

@Composable private fun DxSmartFeed(rows: List<AndroidDXSpot>, statuses: Map<String, SpotLogStatus>, cty: CtyController,
    selected: (AndroidDXSpot) -> Unit, previous: (AndroidDXSpot) -> Unit, modifier: Modifier) {
    DxSection("SMART PRIORITY · HIGHEST OPPORTUNITY FIRST", modifier) {
        DxSpotHeader()
        LazyColumn(Modifier.fillMaxSize()) { items(rows.sortedByDescending { it.score }, key = { it.id }) { spot ->
            DxSpotRow(spot, statuses[spot.id], cty, selected, previous, smart = true)
        } }
    }
}

@Composable private fun DxSpotTable(rows: List<AndroidDXSpot>, statuses: Map<String, SpotLogStatus>, cty: CtyController,
    selected: (AndroidDXSpot) -> Unit, previous: (AndroidDXSpot) -> Unit, modifier: Modifier) {
    DxSection("CLASSIC CLUSTER TABLE · ${rows.size} LIVE", modifier) {
        DxSpotHeader()
        LazyColumn(Modifier.fillMaxSize()) { items(rows, key = { it.id }) { spot ->
            DxSpotRow(spot, statuses[spot.id], cty, selected, previous)
        } }
    }
}

@Composable private fun DxSpotHeader() = Row(Modifier.fillMaxWidth().height(38.dp).background(DxRaised).padding(horizontal=6.dp),verticalAlignment=Alignment.CenterVertically) {
    DxFlexCell("UTC",.62f,DxInk,true);DxFlexCell("CALL",.86f,DxInk,true);DxFlexCell("BAND",.5f,DxInk,true);DxFlexCell("MODE",.55f,DxInk,true)
    DxFlexCell("FREQ MHz",.76f,DxInk,true);DxFlexCell("COUNTRY / DXCC",1.42f,DxInk,true);DxFlexCell("CQ",.36f,DxInk,true)
    DxFlexCell("DX DE",.82f,DxInk,true);DxFlexCell("CS",.34f,DxInk,true);DxFlexCell("DS",.46f,DxInk,true);DxFlexCell("KM",.52f,DxInk,true)
    DxFlexCell("SCORE",.48f,DxInk,true);DxFlexCell("COMMENT / REASON",1.72f,DxInk,true)
}

@Composable private fun DxSpotRow(spot:AndroidDXSpot,status:SpotLogStatus?,cty:CtyController,selected:(AndroidDXSpot)->Unit,
    previous:(AndroidDXSpot)->Unit,smart:Boolean=false){
    val entity=cty.lookup(spot.callsign);val country=entity?.country.orEmpty().ifBlank{spot.country}.ifBlank{"Unknown"}
    Row(Modifier.fillMaxWidth().height(48.dp).clickable(role=Role.Button){selected(spot)}
        .background(if(smart&&spot.score>=75)DxGreen.copy(alpha=.08f) else if(spot.receivedEpoch%2L==0L)DxPanel else DxBg).padding(horizontal=6.dp),verticalAlignment=Alignment.CenterVertically){
        DxFlexCell(utcSeconds(spot.receivedEpoch),.62f,DxInk);Box(Modifier.weight(.86f).fillMaxHeight().clickable{previous(spot)},contentAlignment=Alignment.CenterStart){Text(spot.callsign,color=if(spot.watchlisted)DxYellow else DxCyan,fontWeight=FontWeight.Black,maxLines=1,overflow=TextOverflow.Ellipsis)}
        DxFlexCell(spot.band,.5f,DxInk);DxFlexCell(spot.mode,.55f,DxInk);DxFlexCell(formatMHz(spot.frequencyHz),.76f,DxAmber)
        DxFlexCell(country,1.42f,DxInk);DxFlexCell(entity?.cqZone.orEmpty().ifBlank{spot.cqZone.takeIf{it>0}?.toString().orEmpty()},.36f,DxInk)
        DxFlexCell(spot.spotter,.82f,DxMuted);DxFlexCell(status?.callStatus.orEmpty(),.34f,DxGreen,true);DxFlexCell(status?.dxccStatus.orEmpty(),.46f,if(status?.dxccStatus=="ATNO")DxRed else DxYellow,true)
        DxFlexCell(spot.distanceKm.takeIf{it>0}?.toString().orEmpty(),.52f,DxMuted);DxFlexCell(spot.score.toString(),.48f,scoreColor(spot.score),true)
        DxFlexCell(spot.reason.ifBlank{spot.comment},1.72f,DxMuted)
    };HorizontalDivider(color=Color(0xFF303940))
}

@Composable private fun RowScope.DxFlexCell(text:String,weight:Float,color:Color,bold:Boolean=false){Text(text,color=color,fontWeight=if(bold)FontWeight.Black else FontWeight.Medium,fontFamily=FontFamily.Monospace,fontSize=12.sp,maxLines=1,overflow=TextOverflow.Ellipsis,modifier=Modifier.weight(weight).padding(horizontal=3.dp))}

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
            if(hearsMe)DxReceiverMap(controller.mySignal.reports,stationGrid,Modifier.weight(2.2f).fillMaxHeight())else DxWorldCanvas(filtered,stationGrid,false,Modifier.weight(2.2f).fillMaxHeight())
            DxSection(if(hearsMe)"RECEIVERS / SPOTTERS" else "MAP OBSERVATIONS",Modifier.weight(1f).fillMaxHeight()){
                Row(Modifier.fillMaxWidth().height(34.dp).background(DxRaised).padding(horizontal=5.dp),verticalAlignment=Alignment.CenterVertically){DxFlexCell(if(hearsMe)"RX CALL" else "DX",.8f,DxInk,true);DxFlexCell("BAND / MODE",.9f,DxInk,true);DxFlexCell(if(hearsMe)"SNR / KM" else "MHz / COUNTRY",1.2f,DxInk,true)}
                if(hearsMe)LazyColumn(Modifier.fillMaxSize()){items(controller.mySignal.reports,key={it.callsign}){r->Row(Modifier.fillMaxWidth().height(44.dp).padding(horizontal=5.dp),verticalAlignment=Alignment.CenterVertically){DxFlexCell("${r.callsign} ${r.locator}",.8f,DxGreen,true);DxFlexCell("${r.band} ${r.mode}",.9f,DxInk);DxFlexCell("${r.snr?.let{"$it dB"}?:"—"} · ${r.distanceKm?.let{"$it km"}?:"—"}",1.2f,DxAmber)};HorizontalDivider(color=Color(0xFF303940))}}
                else LazyColumn(Modifier.fillMaxSize()){items(filtered,key={it.id}){spot->Row(Modifier.fillMaxWidth().height(44.dp).clickable{selected=spot}.padding(horizontal=5.dp),verticalAlignment=Alignment.CenterVertically){Box(Modifier.weight(.8f).fillMaxHeight().clickable{previous(spot)},contentAlignment=Alignment.CenterStart){Text(spot.callsign,color=DxCyan,fontWeight=FontWeight.Black,maxLines=1)};DxFlexCell("${spot.band} ${spot.mode}",.9f,DxInk);DxFlexCell("${formatMHz(spot.frequencyHz)} · ${cty.lookup(spot.callsign)?.country.orEmpty().ifBlank{spot.country}}",1.2f,DxAmber)};HorizontalDivider(color=Color(0xFF303940))}}
            }
        }
    }
    selected?.let{DxSpotDialog(it,cty,null,{selected=null},{tune("FA%011d;".format(it.frequencyHz));selected=null})}
}

@Composable private fun DxInsightPage(controller: NeuralDxController, features: FeatureController, modifier: Modifier){
    val tactical=controller.insight.recommendations+controller.predictions.map{"${it.callsign} · ${it.country} · ${it.probability}% ${it.model} · ${it.reason}"}
    Row(modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){
        Column(Modifier.weight(1.1f).fillMaxHeight(),verticalArrangement=Arrangement.spacedBy(8.dp)){
            DxSection("AI OPERATOR REPORT · ${controller.insight.source}",Modifier.weight(1.25f)){Text(controller.insight.title,color=DxCyan,fontSize=20.sp,fontWeight=FontWeight.Black);Column(Modifier.weight(1f).verticalScroll(rememberScrollState())){Text(controller.insight.report,color=DxInk,fontSize=15.sp,lineHeight=21.sp);if(controller.insight.bullets.isNotEmpty()){Spacer(Modifier.height(12.dp));Text("EVIDENCE / SIGNALS",color=DxAmber,fontWeight=FontWeight.Black);controller.insight.bullets.forEachIndexed{i,v->DxLine("${i+1}",v,DxCyan)}};controller.predictions.take(5).forEach{p->DxLine("${p.callsign} · ${p.band}","${p.probability}%",if(p.probability>=70)DxGreen else DxYellow)}};if(controller.insight.error.isNotBlank())Text(controller.insight.error,color=DxYellow)}
            DxSection("LOG PERFORMANCE",Modifier.weight(.75f)){Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceAround){DxMiniValue("QSOs",controller.insight.log.qsos.toString());DxMiniValue("CALLS",controller.insight.log.calls.toString());DxMiniValue("DXCC",controller.insight.log.dxccs.toString());DxMiniValue("CONF",controller.insight.log.confirmedDxccs.toString())};HorizontalDivider(color=Color(0xFF374047));Text("Measured only from ${controller.insight.source.lowercase()} log scope · QSL/LoTW confirmation",color=DxMuted,fontSize=12.sp)}
        }
        DxSection("TACTICAL OPPORTUNITIES · NOW",Modifier.weight(1.15f).fillMaxHeight()){
            LazyColumn(Modifier.fillMaxSize()){items(tactical.ifEmpty{listOf("No current recommendation")}){v->val idx=tactical.indexOf(v);Row(Modifier.fillMaxWidth().heightIn(min=48.dp).padding(horizontal=5.dp),verticalAlignment=Alignment.CenterVertically){Text("${idx+1}",color=scoreColor(features.liveSpots.getOrNull(idx)?.score?:0),fontSize=22.sp,fontWeight=FontWeight.Black,modifier=Modifier.width(34.dp));Text(v,color=if(tactical.isNotEmpty())DxInk else DxMuted,modifier=Modifier.weight(1f),maxLines=2)};HorizontalDivider(color=Color(0xFF303940))}}
        }
        Column(Modifier.weight(1f).fillMaxHeight(),verticalArrangement=Arrangement.spacedBy(8.dp)){
            DxSection("BAND ANALYZER",Modifier.weight(1f)){controller.insight.log.bands.entries.take(10).forEach{DxBar(it.key,it.value,controller.insight.log.bands.values.maxOrNull()?:1)};if(controller.insight.log.bands.isEmpty())DxEmpty("No log distribution yet")}
            DxSection("MODE ANALYZER",Modifier.weight(.72f)){controller.insight.log.modes.entries.take(7).forEach{DxBar(it.key,it.value,controller.insight.log.modes.values.maxOrNull()?:1)}}
            DxSection("MODEL / DATA HONESTY",Modifier.weight(.55f)){DxLine("Predictions",controller.predictions.size.toString(),DxCyan);DxLine("Live samples",features.liveSpots.size.toString(),DxGreen);Text("Missing measurements stay unavailable; optional AI never replaces local evidence.",color=DxMuted,fontSize=12.sp)}
        }
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
            DxWorldAnomalyCanvas(rows,greyline,Modifier.weight(2.2f).fillMaxHeight())
            DxSection("FORECAST / ANOMALIES",Modifier.weight(1f).fillMaxHeight()){
                Row(Modifier.fillMaxWidth().height(34.dp).background(DxRaised).padding(horizontal=5.dp),verticalAlignment=Alignment.CenterVertically){DxFlexCell("CELL",.8f,DxInk,true);DxFlexCell("OBS / EXP",.8f,DxInk,true);DxFlexCell("RATIO",.5f,DxInk,true);DxFlexCell("CALLS",1.2f,DxInk,true)}
                LazyColumn(Modifier.fillMaxSize()){items(rows){cell->Row(Modifier.fillMaxWidth().height(46.dp).padding(horizontal=5.dp),verticalAlignment=Alignment.CenterVertically){DxFlexCell("${hemisphere(cell.latitude,"N","S")} ${hemisphere(cell.longitude,"E","W")}",.8f,DxCyan,true);DxFlexCell("${cell.observed} / ${cell.expected?.let{"%.1f".format(it)}?:"—"}",.8f,DxInk);DxFlexCell(cell.anomalyRatio?.let{"×%.1f".format(it)}?:"LEARN",.5f,if((cell.anomalyRatio?:0.0)>=1.8)DxRed else DxYellow,true);DxFlexCell(cell.calls.joinToString(),1.2f,DxMuted)};HorizontalDivider(color=Color(0xFF303940))}}
            }
        }
    }
}

@Composable private fun DxBriefingPage(controller:NeuralDxController,features:FeatureController,modifier:Modifier){
    var expanded by remember{mutableStateOf<String?>(null)}
    Column(modifier,verticalArrangement=Arrangement.spacedBy(8.dp)){
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text("LIVE DX INTELLIGENCE · 12-HOUR RESILIENT CACHE · ${controller.briefing.sumOf{it.items.size}} STORIES",color=DxMuted,modifier=Modifier.weight(1f),maxLines=1);FilterChip(controller.briefingDxMode,{controller.saveSettings(controller.notificationsEnabled,controller.ntfyUrl,controller.ntfyToken,controller.perplexityKey,!controller.briefingDxMode)},{Text("DX CALL EXTRACTION")})}
        Row(Modifier.fillMaxSize(),horizontalArrangement=Arrangement.spacedBy(8.dp)){
            Column(Modifier.weight(1f).fillMaxHeight(),verticalArrangement=Arrangement.spacedBy(8.dp)){controller.briefing.filterIndexed{i,_->i%2==0}.forEach{source->DxBriefSource(source,controller,features,expanded,{expanded=it},Modifier.weight(1f))}}
            Column(Modifier.weight(1f).fillMaxHeight(),verticalArrangement=Arrangement.spacedBy(8.dp)){controller.briefing.filterIndexed{i,_->i%2==1}.forEach{source->DxBriefSource(source,controller,features,expanded,{expanded=it},Modifier.weight(1f))}}
        }
    }
}

@Composable private fun DxBriefSource(source:BriefingSource,controller:NeuralDxController,features:FeatureController,expanded:String?,setExpanded:(String?)->Unit,modifier:Modifier){
    DxSection("${source.name.uppercase()} · ${source.items.size} ITEMS",modifier){Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text("${source.site} · ${if(source.stale)"STALE CACHE" else ageLabel(source.updatedEpoch)}",color=if(source.stale)DxYellow else DxMuted,modifier=Modifier.weight(1f),maxLines=1);IconButton({controller.moveBriefingSource(source.id,-1)},Modifier.size(48.dp)){Icon(Icons.Outlined.KeyboardArrowUp,"Move source up")};IconButton({controller.moveBriefingSource(source.id,1)},Modifier.size(48.dp)){Icon(Icons.Outlined.KeyboardArrowDown,"Move source down")}}
        if(source.error.isNotBlank())Text(source.error,color=DxYellow)
        LazyColumn(Modifier.fillMaxSize()){items(source.items.take(12).withIndex().toList(),key={"${source.id}:${it.index}:${it.value.link}"}){indexed->val item=indexed.value;Row(Modifier.fillMaxWidth().heightIn(min=58.dp).clickable{setExpanded(if(expanded==item.link)null else item.link)}.padding(vertical=4.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){DxBriefImage(item.imageUrl,item.title,Modifier.width(82.dp).height(50.dp));Column(Modifier.weight(1f)){Text(item.title,color=DxCyan,fontWeight=FontWeight.Bold,maxLines=if(expanded==item.link)3 else 1,overflow=TextOverflow.Ellipsis);Text(listOf(item.published.take(22),item.callsigns.joinToString()).filter{it.isNotBlank()}.joinToString(" · "),color=DxMuted,fontSize=11.sp,maxLines=1);if(expanded==item.link)Text(item.summary,color=DxInk,fontSize=12.sp,maxLines=3)};if(controller.briefingDxMode&&item.callsigns.isNotEmpty())TextButton({features.setWatchlist(features.watchlistText+"\n"+item.callsigns.first())},modifier=Modifier.heightIn(min=48.dp)){Text("★ ${item.callsigns.first()}")}};HorizontalDivider(color=Color(0xFF303940))}}
        if(source.items.isEmpty())DxEmpty("No cached items; refresh when online")
    }
}

@Composable private fun DxSatellitesPage(controller:NeuralDxController,stationGrid:String,modifier:Modifier){
    var selectedNorad by remember{mutableStateOf<Int?>(null)};var detailsNorad by remember{mutableStateOf<Int?>(null)};var search by remember{mutableStateOf("")};var window by remember{mutableIntStateOf(24)}
    val positions=controller.satellites;val catalog=controller.satelliteCatalogue.filter{search.isBlank()||it.name.contains(search,true)||it.norad.toString().contains(search)}.take(80)
    var tab by remember{mutableStateOf("LIVE")}
    Column(modifier,verticalArrangement=Arrangement.spacedBy(8.dp)){
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp),verticalAlignment=Alignment.CenterVertically){listOf("LIVE","PASSES","CATALOG").forEach{FilterChip(tab==it,{tab=it},{Text(it)})};if(tab=="PASSES")listOf(4,12,24).forEach{FilterChip(window==it,{window=it},{Text("${it}h")})};Text("${positions.count{it.visible}} VISIBLE · ${positions.size} TRACKED · ${controller.passes.size} PASSES",color=DxCyan,fontWeight=FontWeight.Black)}
        Row(Modifier.fillMaxSize(),horizontalArrangement=Arrangement.spacedBy(8.dp)){
            DxSatelliteMap(positions,stationGrid,selectedNorad,Modifier.weight(2.25f).fillMaxHeight())
            DxSection(when(tab){"PASSES"->"PASSES ABOVE QTH";"CATALOG"->"FOLLOWED / AMSAT CATALOG";else->"LIVE POSITIONS"},Modifier.weight(1f).fillMaxHeight()){
                when(tab){
                    "PASSES"-> {Row(Modifier.fillMaxWidth().height(34.dp).background(DxRaised)){DxFlexCell("SATELLITE",1f,DxInk,true);DxFlexCell("AOS–LOS",1f,DxInk,true);DxFlexCell("MAX EL",.55f,DxInk,true)};LazyColumn(Modifier.fillMaxSize()){items(controller.passes.filter{it.aosEpoch<=Instant.now().epochSecond+window*3600L}.take(40)){p->Row(Modifier.fillMaxWidth().height(46.dp),verticalAlignment=Alignment.CenterVertically){DxFlexCell(p.name,1f,DxCyan,true);DxFlexCell("${utcTime(p.aosEpoch)}–${utcTime(p.losEpoch)}",1f,DxInk);DxFlexCell("${p.maxElevation.roundToInt()}°",.55f,if(p.maxElevation>=45)DxGreen else if(p.maxElevation>=20)DxYellow else DxAmber,true)};HorizontalDivider(color=Color(0xFF303940))}}}
                    "CATALOG"->{OutlinedTextField(search,{search=it},label={Text("Search ${controller.satelliteCatalogue.size} satellites")},singleLine=true,modifier=Modifier.fillMaxWidth());LazyColumn(Modifier.fillMaxSize()){items(catalog,key={it.norad}){o->val followed=o.norad in controller.followedNorads;Row(Modifier.fillMaxWidth().heightIn(min=48.dp).clickable{controller.setFollowed(o.norad,!followed)},verticalAlignment=Alignment.CenterVertically){Checkbox(followed,null);Text(o.name,color=DxInk,modifier=Modifier.weight(1f),maxLines=1);Text(o.norad.toString(),color=DxMuted)}}}}
                    else->{Row(Modifier.fillMaxWidth().height(34.dp).background(DxRaised)){DxFlexCell("SATELLITE",1f,DxInk,true);DxFlexCell("AZ / EL",.65f,DxInk,true);DxFlexCell("ALT / RANGE",.9f,DxInk,true)};LazyColumn(Modifier.fillMaxSize()){items(positions,key={it.norad}){p->Row(Modifier.fillMaxWidth().height(48.dp).clickable{selectedNorad=p.norad;detailsNorad=p.norad;controller.refreshSatelliteTransmitters(p.norad)},verticalAlignment=Alignment.CenterVertically){DxFlexCell(p.name,1f,if(p.norad==selectedNorad)DxYellow else if(p.visible)DxGreen else DxCyan,true);DxFlexCell("${p.azimuth.roundToInt()}° / ${p.elevation.roundToInt()}°",.65f,DxAmber);DxFlexCell("${p.altitudeKm.roundToInt()} / ${p.rangeKm.roundToInt()} km",.9f,DxInk)};HorizontalDivider(color=Color(0xFF303940))}}}
                }
            }
        }
    }
    detailsNorad?.let{norad->val pos=positions.firstOrNull{it.norad==norad};AlertDialog(onDismissRequest={detailsNorad=null},title={Text(pos?.name?:"Satellite $norad")},text={Column(Modifier.heightIn(max=520.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(6.dp)){pos?.let{Text("Lat %.2f° · Lon %.2f° · Alt %.0f km".format(it.latitude,it.longitude,it.altitudeKm));Text("Az %.0f° · El %.0f° · Range %.0f km · Footprint %.0f km".format(it.azimuth,it.elevation,it.rangeKm,it.footprintKm))};HorizontalDivider();Text("SatNOGS transmitters",fontWeight=FontWeight.Bold);controller.transmitters[norad]?.forEach{t->Text("${t.description}\n↓ ${t.downlink} · ↑ ${t.uplink} · ${t.mode}",color=DxMuted)}?:Text("Loading frequencies…",color=DxMuted)}},confirmButton={TextButton({detailsNorad=null}){Text("Close")}})}
}

@Composable private fun DxWeatherPage(controller:NeuralDxController,features:FeatureController,stationGrid:String,modifier:Modifier){
    var cond by remember{mutableStateOf("HF")};var zone by remember{mutableStateOf("EUROPE")};val weather=controller.weather
    val hfPct=when{!controller.wspr.available->null;controller.wspr.hf.sumOf{it.spots}>=25->85;controller.wspr.hf.sumOf{it.spots}>=8->65;else->42}
    val vhfSnr=controller.wspr.vhf.mapNotNull{it.averageSnr}.average().takeIf{it.isFinite()};val vhfPct=vhfSnr?.let{((it+25)*4).roundToInt().coerceIn(0,100)}?:weather.tropoIndex?.times(6)
    val global=listOfNotNull(hfPct,vhfPct).average().takeIf{it.isFinite()}?.roundToInt()
    Column(modifier,verticalArrangement=Arrangement.spacedBy(8.dp)){
        Row(Modifier.fillMaxWidth().weight(1f),horizontalArrangement=Arrangement.spacedBy(8.dp)){
            DxSection("GLOBAL SYNTHESIS",Modifier.weight(1f)){DxGauge(global,"GLOBAL");Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceAround){DxMiniValue("HF",hfPct?.let{"$it%"}?:"—");DxMiniValue("VHF",vhfPct?.let{"$it%"}?:"—")};Text("Heuristic visual reference, not a calibrated physical measurement.",color=DxMuted,fontSize=12.sp)}
            DxSection("ELECTRICAL ACTIVITY",Modifier.weight(1f)){val strikes=controller.lightning.strikes;Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text("REGIONAL LIGHTNING",color=DxCyan,fontWeight=FontWeight.Black,modifier=Modifier.weight(1f));Text(if(controller.lightning.connected)"LIVE · ${strikes.size}" else "UNAVAILABLE",color=if(controller.lightning.connected)DxGreen else DxYellow,fontWeight=FontWeight.Black)};DxLightningMap(strikes,stationGrid,Modifier.fillMaxWidth().weight(1f));strikes.firstOrNull()?.let{DxLine("Nearest/latest","${it.distanceKm} km ${it.bearing} · ${ageLabel(it.epoch)}",if(it.distanceKm<50)DxRed else DxYellow)};if(!controller.lightning.connected)Text(controller.lightning.error.ifBlank{"Connecting to the regional feed; zero is never fabricated."},color=DxMuted);Text("Source: ${controller.lightning.source}",color=DxMuted,fontSize=11.sp)}
            DxSection("CURRENT CONDITIONS",Modifier.weight(1f)){Row{listOf("HF","VHF/UHF").forEach{FilterChip(cond==it,{cond=it},{Text(it)})}};if(cond=="HF"){DxLine("Temperature",weather.temperatureC?.let{"%.1f °C".format(it)}?:"—",if((weather.temperatureC?:0.0)>=30)DxRed else DxInk);DxLine("Pressure",weather.pressureHpa?.let{"%.0f hPa".format(it)}?:"—",if((weather.pressureHpa?:1100.0)<1000)DxRed else DxInk);DxLine("2h trend",weather.pressureTrend,DxYellow);DxLine("Humidity",weather.humidityPercent?.let{"$it%"}?:"—",DxInk);DxLine("Wind",weather.windKmh?.let{"%.0f km/h · %03d°".format(it,weather.windDirection?:0)}?:"—",DxInk);DxLine("Precipitation",weather.precipitationMm?.let{"$it mm"}?:"—",DxInk)}else{DxLine("Tropo / ducting",weather.ductingRisk?:"—",if(weather.ductingRisk=="HIGH")DxGreen else DxYellow);DxLine("Tropo index",weather.tropoIndex?.let{"$it / 10"}?:"—",DxInk);DxLine("850 hPa temp",weather.temperature850C?.let{"%.1f °C".format(it)}?:"—",DxInk);DxLine("300 hPa wind",weather.wind300Kmh?.let{"%.0f km/h".format(it)}?:"—",DxInk);DxLine("CAPE",weather.cape?.let{"%.0f J/kg".format(it)}?:"—",DxInk);val two=controller.wspr.vhf.firstOrNull{it.band=="2m"};DxLine("WSPR 2m",two?.let{"${it.spots} · ${it.averageSnr?.let{n->"%.1f dB".format(n)}?:"—"}"}?:"NO BEACON DATA",if(two!=null)DxGreen else DxMuted)}}
        }
        Row(Modifier.fillMaxWidth().weight(1f),horizontalArrangement=Arrangement.spacedBy(8.dp)){
            DxSection("NOISE / QRN",Modifier.weight(1f)){val total=controller.wspr.hf.sumOf{it.spots};val strikes=controller.lightning.strikes;val closest=strikes.minOfOrNull{it.distanceKm};val qrn=when{closest!=null&&closest<50->92;closest!=null&&closest<120->76;closest!=null&&closest<300->58;else->hfPct};val level=when{closest!=null&&closest<50->"SEVERE QRN";closest!=null&&closest<120->"HIGH QRN";total>=40->"CALM / ACTIVE";total>=12->"ELEVATED";total>0->"SPARSE";else->"—"};DxGauge(qrn,level);Text(when{strikes.isNotEmpty()->"${strikes.size} lightning strikes within 300 km in the last hour; nearest ${closest} km.";total>0->"$total nearby WSPR receptions in the 30-minute source window.";else->"No live lightning or WSPR measurement; no noise value is inferred."},color=DxMuted)}
            DxSection("QUICK VOACAP · $zone",Modifier.weight(1.3f)){DxSelect("PATH",zone,listOf("EUROPE","AMERICAS","ASIA","OCEANIA","AFRICA")){zone=it};listOf("80m","40m","30m","20m","17m","15m","12m","10m").forEach{band->val rel=voacapReliability(band,zone,features.solar.flux,features.solar.kpIndex);DxBar(band,rel,100)};Text("Single-path heuristic; it may differ from global observations.",color=DxMuted,fontSize=12.sp)}
            DxSection("BAND ACTIVITY · 24H",Modifier.weight(1f)){controller.bandActivity.entries.take(12).forEach{DxBar(it.key,it.value,controller.bandActivity.values.maxOrNull()?:1)};if(controller.bandActivity.isEmpty())DxEmpty("Cluster history is still learning")}
        }
        DxSection("VHF / UHF / SHF BEACONS",Modifier.fillMaxWidth().weight(1f)){
            Text(controller.beaconStatus,color=DxMuted,fontSize=12.sp)
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)){
                Column(Modifier.weight(1f)){Text("RECEIVED FROM CLUSTER",color=DxCyan,fontWeight=FontWeight.Bold);if(controller.beacons.isEmpty())DxEmpty("No nearby beacon report; this does not mean no opening.")else controller.beacons.take(12).forEach{b->DxLine("${if(b.known)"★" else "·"} ${b.callsign} · ${b.band}","${formatMHz(b.frequencyHz)} · ${b.ageMinutes}m · ${b.spotter}",if(b.known)DxGreen else DxCyan)}}
                Column(Modifier.weight(1f)){Text("REFERENCE · IN INDICATIVE RANGE",color=DxCyan,fontWeight=FontWeight.Bold);controller.beaconReference.filter{it.inTypicalRange}.take(12).forEach{b->DxLine("${b.callsign} · ${b.band}","${"%.4f".format(Locale.US,b.frequencyMHz)} · ${b.distanceKm} km ${b.bearing}",DxInk)};if(controller.beaconReference.none{it.inTypicalRange})DxEmpty("Reference will populate after the monthly source refresh.")}
            }
        }
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

private val briefingImageCache=object:LruCache<String,ImageBitmap>(12*1024*1024){override fun sizeOf(key:String,value:ImageBitmap)=value.width*value.height*4}
@Composable private fun DxBriefImage(url:String,title:String,modifier:Modifier){val bitmap by produceState<ImageBitmap?>(null,url){value=null;if(url.startsWith("https://",true))value=withContext(Dispatchers.IO){briefingImageCache.get(url)?:runCatching{val c=URL(url).openConnection() as HttpURLConnection;c.connectTimeout=6000;c.readTimeout=9000;c.instanceFollowRedirects=true;try{val bytes=c.inputStream.use{it.readNBytes(2*1024*1024)};val b=BitmapFactory.Options().apply{inJustDecodeBounds=true};BitmapFactory.decodeByteArray(bytes,0,bytes.size,b);var s=1;while(b.outWidth/s>360||b.outHeight/s>240)s*=2;BitmapFactory.decodeByteArray(bytes,0,bytes.size,BitmapFactory.Options().apply{inSampleSize=s})?.asImageBitmap()?.also{briefingImageCache.put(url,it)}}finally{c.disconnect()}}.getOrNull()}}
    Surface(color=DxRaised,shape=RoundedCornerShape(6.dp),modifier=modifier){if(bitmap!=null)Image(bitmap!!,contentDescription="Briefing image for $title",contentScale=ContentScale.Crop,modifier=Modifier.fillMaxSize())else Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){Icon(Icons.Outlined.Newspaper,null,tint=DxMuted)}}}

@Composable private fun DxSpotDialog(spot:AndroidDXSpot,cty:CtyController,status:SpotLogStatus?,dismiss:()->Unit,tune:()->Unit){val e=cty.lookup(spot.callsign);AlertDialog(onDismissRequest=dismiss,title={Text(spot.callsign)},text={Column(verticalArrangement=Arrangement.spacedBy(6.dp)){Text("${formatMHz(spot.frequencyHz)} MHz · ${spot.band} · ${spot.mode}",color=DxAmber,fontWeight=FontWeight.Bold);Text(e?.country.orEmpty().ifBlank{spot.country}.ifBlank{"Unknown DXCC"});Text("CQ ${e?.cqZone.orEmpty().ifBlank{spot.cqZone.toString()}} · ITU ${e?.ituZone.orEmpty().ifBlank{spot.ituZone.toString()}} · ${e?.continent.orEmpty().ifBlank{spot.continent}}",color=DxMuted);Text("Call ${status?.callStatus?:"—"} · DXCC ${status?.dxccStatus?:"—"}",color=DxYellow);Text("Score ${spot.score} · confidence ${spot.confidence} · ${spot.samples} samples");Text(spot.reason.ifBlank{spot.comment}.ifBlank{"No additional analysis"},color=DxMuted)}},confirmButton={Button(tune){Text("Tune VFO A")}},dismissButton={TextButton(dismiss){Text("Close")}})}
@Composable private fun ManualSpotDialog(features:FeatureController,dismiss:()->Unit){var call by remember{mutableStateOf("")};var freq by remember{mutableStateOf("")};var comment by remember{mutableStateOf("")};AlertDialog(onDismissRequest=dismiss,title={Text("Send DX cluster spot")},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){OutlinedTextField(call,{call=it.uppercase()},label={Text("Callsign")},singleLine=true);OutlinedTextField(freq,{freq=it.filter{c->c.isDigit()||c=='.'}},label={Text("Frequency kHz")},singleLine=true);OutlinedTextField(comment,{comment=it},label={Text("Comment")},singleLine=true);Text("Posts to the currently connected cluster only.",color=DxMuted)}},confirmButton={Button({features.postSpot(call,freq.toDoubleOrNull()?:0.0,comment);dismiss()},enabled=call.isNotBlank()&&freq.toDoubleOrNull()!=null){Text("Send spot")}},dismissButton={TextButton(dismiss){Text("Cancel")}})}

private fun dxPageIcon(page:NeuralDxPage)=when(page){NeuralDxPage.COCKPIT->Icons.Outlined.SpaceDashboard;NeuralDxPage.MAP->Icons.Outlined.Map;NeuralDxPage.INSIGHT->Icons.Outlined.Psychology;NeuralDxPage.WORLD->Icons.Outlined.Public;NeuralDxPage.BRIEFING->Icons.Outlined.Newspaper;NeuralDxPage.SATELLITES->Icons.Outlined.SatelliteAlt;NeuralDxPage.WEATHER->Icons.Outlined.Thunderstorm}
private fun scoreColor(score:Int)=when{score>=75->DxGreen;score>=55->DxYellow;else->DxMuted}
private fun formatMHz(hz:Long)="%.3f".format(Locale.US,hz/1_000_000.0)
private fun ageLabel(epoch:Long):String{val m=((Instant.now().epochSecond-epoch).coerceAtLeast(0)/60).toInt();return if(m<60)"${m}m" else "${m/60}h ${m%60}m"}
private fun utcTime(epoch:Long)=DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneOffset.UTC).format(Instant.ofEpochSecond(epoch))
private fun utcSeconds(epoch:Long)=DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneOffset.UTC).format(Instant.ofEpochSecond(epoch))
private fun hemisphere(value:Double,positive:String,negative:String)="%.0f°%s".format(kotlin.math.abs(value),if(value>=0)positive else negative)
private fun voacapReliability(band:String,zone:String,sfi:Float,kp:Float):Int{val base=mapOf("80m" to 58,"40m" to 70,"30m" to 76,"20m" to 82,"17m" to 72,"15m" to 64,"12m" to 50,"10m" to 42)[band]?:50;val flux=((sfi-80)/2).roundToInt().coerceIn(-15,25);val storm=(kp*5).roundToInt();val distance=when(zone){"EUROPE"->8;"AFRICA"->2;"ASIA"->-4;"AMERICAS"->-7;else->-8};return(base+flux-storm+distance).coerceIn(0,100)}
