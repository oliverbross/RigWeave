package app.rigweave.mobile

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.delay
import org.maplibre.android.MapLibre
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.annotations.PolylineOptions
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin

private val SatAmber = Color(0xFFE9A72B)
private val SatPanel = Color(0xFF1B2228)
private val SatInk = Color(0xFFF4F0E7)
private val SatMuted = Color(0xFFA5ADB2)
private val SatGood = Color(0xFF42C77B)
private val SatBad = Color(0xFFE4544D)
private val satelliteSections = listOf("NEXT PASSES", "LIVE TRACK", "SKY PLOT", "STATUS", "TIMERS", "CATALOGUE")

@Composable
internal fun SatelliteOperationsScreen(
    controller: SatelliteOperationsController,
    stationCallsign: String,
    stationGrid: String,
    activationGrid: String?,
    mutations: QsoMutationCoordinator,
    wavelog: WavelogController,
    callbook: CallbookController,
    progress: ProgressController,
    openLogbook: () -> Unit,
    tuneReceive: (Long, String?) -> Unit,
    normalLogger: (SatellitePassRow) -> Unit,
) {
    val context = LocalContext.current
    var section by rememberSaveable { mutableStateOf("NEXT PASSES") }
    var fastDraft by remember { mutableStateOf<String?>(null) }
    var tunePreview by remember { mutableStateOf<SatellitePassRow?>(null) }
    var manualGrid by rememberSaveable(controller.observerGrid) { mutableStateOf(controller.observerGrid.ifBlank { stationGrid }) }
    var hours by rememberSaveable { mutableIntStateOf(controller.windowHours) }
    var minimum by rememberSaveable { mutableStateOf(controller.minimumElevation.toString()) }
    var mode by rememberSaveable { mutableStateOf(controller.modeFilter) }
    var favouritesOnly by rememberSaveable { mutableStateOf(controller.favouritesOnly) }
    var utc by rememberSaveable { mutableStateOf(controller.utc) }
    var now by remember { mutableLongStateOf(Instant.now().epochSecond) }
    var gpsPoint by remember { mutableStateOf<GeoPoint?>(null) }
    val gps = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) satelliteLastKnownPoint(context)?.let { gpsPoint = it; manualGrid = maidenheadGrid(it.latitude, it.longitude) }
    }
    LaunchedEffect(section, controller.selectedPass) {
        while (section in setOf("NEXT PASSES", "LIVE TRACK")) {
            now = Instant.now().epochSecond
            if (section == "LIVE TRACK" && controller.selectedPass != null) controller.updateLivePoint(now)
            delay(1_000)
        }
    }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            satelliteSections.forEach { item -> FilterChip(section == item, { section = item }, { Text(item) }) }
        }
        SatelliteProviderStrip(controller.elements.metadata)
        when (section) {
            "NEXT PASSES" -> NextPasses(controller, stationGrid, activationGrid, manualGrid, { manualGrid = it }, hours, { hours = it }, minimum, { minimum = it }, mode, { mode = it }, favouritesOnly, { favouritesOnly = it }, utc, { utc = it }, now,
                requestGps = { if (ContextCompat.checkSelfPermission(context,Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED) satelliteLastKnownPoint(context)?.let { gpsPoint=it;manualGrid=maidenheadGrid(it.latitude,it.longitude) } else gps.launch(Manifest.permission.ACCESS_FINE_LOCATION) },
                apply = { controller.updatePreferences(manualGrid,hours,minimum.toDoubleOrNull()?:10.0,favouritesOnly,utc,mode);controller.predict() },
                openTrack = { controller.select(it); section="LIVE TRACK" }, openSky = { controller.select(it);section="SKY PLOT" },
                prepare = { fastDraft=controller.prepareDraft(it) }, normalLogger = normalLogger, tune = { tunePreview=it },
                logbook = { progress.requestLogbook(LogbookFilter(propagation="SAT",satellite=it.satellite.name));openLogbook() })
            "LIVE TRACK" -> LiveTrack(controller, stationGrid, now)
            "SKY PLOT" -> SkyPlotPanel(controller, minimum.toDoubleOrNull() ?: controller.minimumElevation)
            "STATUS" -> SatelliteStatusPanel(controller) { row -> progress.requestLogbook(LogbookFilter(propagation="SAT",satellite=row.name.substringBefore('_')));openLogbook() }
            "TIMERS" -> SatelliteTimersPanel(controller)
            "CATALOGUE" -> SatelliteCataloguePanel(controller) { selected -> controller.passes.firstOrNull { it.satellite.noradId == selected.noradId }?.let { controller.select(it);section="LIVE TRACK" } }
        }
        Text(controller.message, color = SatMuted, style = MaterialTheme.typography.bodySmall)
    }
    fastDraft?.let { draft -> FastEntryDialog(mutations,wavelog,callbook,stationCallsign,{_,_->controller.predict()},draft){fastDraft=null} }
    tunePreview?.let { row ->
        val frequency = row.transponder?.downlinkLowHz
        val shifted = frequency?.let { hz -> controller.livePoint?.let { NativeSatellite.shiftedFrequencyHz(hz,it.rangeRateKmS) } ?: hz }
        AlertDialog(onDismissRequest={tunePreview=null},title={Text("Receive tune preview")},text={Column(verticalArrangement=Arrangement.spacedBy(6.dp)){
            Text("${row.satellite.name} · ${row.transponder?.mode.orEmpty()}")
            Text("Catalog downlink ${frequency?.let(::formatSatelliteFrequency) ?: "unavailable"}")
            Text("Current receive preview ${shifted?.let(::formatSatelliteFrequency) ?: "unavailable"}")
            Text("This is receive-only and runs only after your explicit confirmation. It does not set TX, PTT, TUNE, or background Doppler follow.",color=SatMuted)
        }},confirmButton={Button({shifted?.let{tuneReceive(it,row.transponder?.mode)};tunePreview=null},enabled=shifted!=null){Text("TUNE RECEIVE")}},dismissButton={TextButton({tunePreview=null}){Text("CANCEL")}})
    }
}

@Composable private fun NextPasses(
    controller:SatelliteOperationsController,stationGrid:String,activationGrid:String?,manualGrid:String,changeGrid:(String)->Unit,
    hours:Int,changeHours:(Int)->Unit,minimum:String,changeMinimum:(String)->Unit,mode:String,changeMode:(String)->Unit,
    favouritesOnly:Boolean,changeFavourites:(Boolean)->Unit,utc:Boolean,changeUtc:(Boolean)->Unit,now:Long,requestGps:()->Unit,apply:()->Unit,
    openTrack:(SatellitePassRow)->Unit,openSky:(SatellitePassRow)->Unit,prepare:(SatellitePassRow)->Unit,normalLogger:(SatellitePassRow)->Unit,tune:(SatellitePassRow)->Unit,logbook:(SatellitePassRow)->Unit,
) {
    val context=LocalContext.current
    LazyColumn(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        item { ElevatedCard(colors=CardDefaults.elevatedCardColors(containerColor=SatPanel)){Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(7.dp)){
            Text("OBSERVER & PASS FILTER",color=SatAmber,fontWeight=FontWeight.Bold)
            Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)){
                controller.observerProfiles(stationGrid,activationGrid).forEach{profile->AssistChip({changeGrid(profile.grid)},{Text(profile.label)})}
                AssistChip(requestGps,{Text("CURRENT GPS")},leadingIcon={Icon(Icons.Outlined.MyLocation,null)})
            }
            OutlinedTextField(manualGrid,changeGrid,label={Text("Manual Maidenhead grid")},singleLine=true,modifier=Modifier.fillMaxWidth())
            Row(horizontalArrangement=Arrangement.spacedBy(7.dp),verticalAlignment=Alignment.CenterVertically){
                listOf(6,12,24,48).forEach{value->FilterChip(hours==value,{changeHours(value)},{Text("${value}h")})}
                OutlinedTextField(minimum,changeMinimum,label={Text("Min °")},singleLine=true,modifier=Modifier.width(100.dp))
                OutlinedTextField(mode,changeMode,label={Text("Mode/transponder")},singleLine=true,modifier=Modifier.width(180.dp))
            }
            Row(horizontalArrangement=Arrangement.spacedBy(7.dp)){FilterChip(favouritesOnly,{changeFavourites(!favouritesOnly)},{Text("FAVOURITES")});FilterChip(!favouritesOnly,{changeFavourites(false)},{Text("ALL")});FilterChip(utc,{changeUtc(true)},{Text("UTC")});FilterChip(!utc,{changeUtc(false)},{Text("LOCAL")});Button(apply){Text("PREDICT")}}
        }}}
        if(controller.busy)item{LinearProgressIndicator(Modifier.fillMaxWidth())}
        if(controller.passes.isEmpty()&&!controller.busy)item{Text(controller.message,color=SatMuted)}
        items(controller.passes,key={"${it.satellite.noradId}-${it.pass.aos}"}){row->
            ElevatedCard(colors=CardDefaults.elevatedCardColors(containerColor=SatPanel)){Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(5.dp)){
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Column{Text(row.satellite.name,color=SatInk,fontWeight=FontWeight.Bold);Text("NORAD ${row.satellite.noradId} · ${row.satellite.elements.source}",color=SatMuted)};Text(countdown(row.pass.aos,now),color=if(row.pass.aos<=now&&row.pass.los>=now)SatGood else SatAmber,fontWeight=FontWeight.Bold)}
                Text("AOS ${satelliteTime(row.pass.aos,utc)} · TCA ${satelliteTime(row.pass.tca,utc)} · LOS ${satelliteTime(row.pass.los,utc)}",color=SatInk)
                Text("Max ${"%.1f°".format(Locale.US,row.pass.maximumElevationDeg)} · ${(row.pass.durationSeconds/60)}m ${row.pass.durationSeconds%60}s · az ${"%.0f°".format(row.pass.aosAzimuthDeg)} → ${"%.0f°".format(row.pass.losAzimuthDeg)}",color=SatMuted)
                row.transponder?.let{Text("${it.mode} · ↓ ${it.downlinkLowHz?.let(::formatSatelliteFrequency)?:"—"} · ↑ ${it.uplinkLowHz?.let(::formatSatelliteFrequency)?:"—"} · ${if(it.manual)"LOCAL OVERRIDE" else "SatNOGS catalogue, not operational proof"}",color=SatMuted)}
                val ageDays=(now-row.satellite.elementEpoch)/86400.0;if(ageDays>7)Text("ELEMENT AGE ${"%.1f".format(Locale.US,ageDays)} DAYS · verify before use",color=SatBad)
                Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(4.dp)){
                    TextButton({openTrack(row)}){Text("FLIGHTPATH")};TextButton({openSky(row)}){Text("SKY PLOT")};TextButton({shareSatelliteIcs(context,row)}){Text("CALENDAR / SHARE")};TextButton({prepare(row)}){Text("FAST ENTRY")};TextButton({normalLogger(row)}){Text("NORMAL LOGGER")};TextButton({tune(row)},enabled=row.transponder?.downlinkLowHz!=null){Text("TUNE PREVIEW")};TextButton({logbook(row)}){Text("HISTORY")}
                }
            }}
        }
    }
}

@Composable private fun SatelliteProviderStrip(meta:SatelliteProviderMetadata){Column(verticalArrangement=Arrangement.spacedBy(3.dp)){Surface(color=SatPanel,shape=RoundedCornerShape(8.dp),modifier=Modifier.fillMaxWidth()){Row(Modifier.padding(8.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){AssistChip({}, {Text(meta.state.name.replace('_',' '))},enabled=false);Text(meta.source,color=SatInk,modifier=Modifier.weight(1f));Text(meta.fetchedAt.takeIf{it>0}?.let{satelliteTime(it,true)}?:"No cache",color=SatMuted)}};if(meta.lastError.isNotBlank())Text("Last refresh ${meta.lastError}; ${if(meta.state==SatelliteCacheState.OFFLINE_CACHE)"last-good cache retained" else "no valid fallback"}",color=SatBad);if(meta.manualOverride)Text("MANUAL override active · provider validation is not claimed",color=SatAmber)}}

@Composable private fun LiveTrack(controller:SatelliteOperationsController,stationGrid:String,now:Long){val row=controller.selectedPass;if(row==null){Text("Choose FLIGHTPATH on a pass first.",color=SatMuted);return};Column(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(7.dp)){if(controller.elements.metadata.state!=SatelliteCacheState.CURRENT)Text("${controller.elements.metadata.state.name.replace('_',' ')} · coordinates are a stale/offline prediction, not live provider truth",color=SatBad,fontWeight=FontWeight.Bold);controller.livePoint?.let{Text("${row.satellite.name} · Az ${"%.1f°".format(it.azimuthDeg)} · El ${"%.1f°".format(it.elevationDeg)} · Range ${"%.0f km".format(it.rangeKm)} · ${countdown(row.pass.los,now)} to LOS",color=SatInk)};SatelliteFlightpathMap(controller.groundTrack,controller.livePoint,maidenheadCenter(controller.observerGrid.ifBlank{stationGrid}),row.pass,Modifier.fillMaxWidth().weight(1f));Text("Map © OpenStreetMap contributors · propagation: pinned local SGP4",color=SatMuted,style=MaterialTheme.typography.bodySmall)}}

@Composable private fun SkyPlotPanel(controller:SatelliteOperationsController,minimum:Double){val row=controller.selectedPass;if(row==null){Text("Choose SKY PLOT on a pass first.",color=SatMuted);return};Column(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(7.dp)){Text("${row.satellite.name} · north-up observer sky",color=SatInk,fontWeight=FontWeight.Bold);SatelliteSkyPlot(controller.skyTrack,controller.livePoint,minimum,Modifier.fillMaxWidth().weight(1f));Text("AOS ${satelliteTime(row.pass.aos,controller.utc)} · TCA ${satelliteTime(row.pass.tca,controller.utc)} · LOS ${satelliteTime(row.pass.los,controller.utc)}",color=SatMuted)}}

@Composable private fun SatelliteSkyPlot(samples:List<OrbitalPoint>,current:OrbitalPoint?,minimum:Double,modifier:Modifier){Canvas(modifier.background(SatPanel)){val center=Offset(size.width/2,size.height/2);val radius=minOf(size.width,size.height)*.43f;listOf(0,30,60).forEach{el->drawCircle(SatMuted.copy(alpha=.45f),radius*((90-el)/90f),center,style=Stroke(1.5f))};drawCircle(SatAmber.copy(alpha=.55f),radius*((90-minimum.coerceIn(0.0,90.0))/90.0).toFloat(),center,style=Stroke(2f));drawLine(SatMuted,Offset(center.x,center.y-radius),Offset(center.x,center.y+radius));drawLine(SatMuted,Offset(center.x-radius,center.y),Offset(center.x+radius,center.y));fun pos(p:OrbitalPoint):Offset{val r=radius*((90-p.elevationDeg.coerceIn(0.0,90.0))/90.0).toFloat();val angle=Math.toRadians(p.azimuthDeg-90);return Offset(center.x+(cos(angle)*r).toFloat(),center.y+(sin(angle)*r).toFloat())};samples.zipWithNext().forEach{(a,b)->if(a.elevationDeg>=0||b.elevationDeg>=0)drawLine(SatAmber,pos(a),pos(b),3f)};samples.firstOrNull()?.let{drawCircle(SatGood,7f,pos(it))};samples.maxByOrNull(OrbitalPoint::elevationDeg)?.let{drawCircle(SatAmber,8f,pos(it))};samples.lastOrNull()?.let{drawCircle(SatBad,7f,pos(it))};current?.takeIf{it.elevationDeg>=0}?.let{drawCircle(Color.White,9f,pos(it))}}}

@Composable private fun SatelliteStatusPanel(controller:SatelliteOperationsController,history:(AmsatStatusSummary)->Unit){var search by rememberSaveable{mutableStateOf("")};var favouritesOnly by rememberSaveable{mutableStateOf(false)};val favouriteNames=controller.elements.rows.filter{it.noradId in controller.favourites}.map{it.name.substringBefore(' ').uppercase(Locale.US)};val rows=controller.status.rows.filter{row->row.displayName.contains(search,true)&&(!favouritesOnly||favouriteNames.any{row.name.uppercase(Locale.US).contains(it)})};Column(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(7.dp)){SatelliteProviderStrip(controller.status.metadata);Row(horizontalArrangement=Arrangement.spacedBy(7.dp),verticalAlignment=Alignment.CenterVertically){OutlinedTextField(search,{search=it},label={Text("Search status")},singleLine=true,modifier=Modifier.weight(1f));FilterChip(favouritesOnly,{favouritesOnly=!favouritesOnly},{Text("FAVOURITES")})};LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(6.dp)){items(rows.take(250),key={it.name}){row->ElevatedCard(colors=CardDefaults.elevatedCardColors(containerColor=SatPanel)){Column(Modifier.padding(10.dp)){Text(row.displayName,color=SatInk,fontWeight=FontWeight.Bold);Text(listOf("Heard","Crew Active","Telemetry Only","Not Heard").joinToString(" · "){"$it ${row.counts[it]?:0}"},color=SatMuted);Text("24-hour community timeline · latest ${row.latestReportEpoch?.let{satelliteTime(it,true)}?:"none"} · not orbital/operational authority",color=SatMuted);row.latestReporters.take(3).forEach{Text("Reporter · $it",color=SatMuted)};val next=controller.passes.firstOrNull{it.satellite.name.substringBefore(' ').let{n->row.name.contains(n,true)}};if(next!=null)Text("Next local prediction ${satelliteTime(next.pass.aos,controller.utc)} · ${"%.0f°".format(next.pass.maximumElevationDeg)}",color=SatAmber);TextButton({history(row)}){Text("SATELLITE LOGBOOK")}}}}}}}

@Composable private fun SatelliteTimersPanel(controller:SatelliteOperationsController){Column(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(7.dp)){SatelliteProviderStrip(controller.timers.metadata);Text("Optional adapter. Invalid, timed-out, or non-functional rows are never promoted to active timers and do not block pass prediction.",color=SatMuted);LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(6.dp)){items(controller.timers.rows,key={it.id}){row->ElevatedCard(colors=CardDefaults.elevatedCardColors(containerColor=SatPanel)){Column(Modifier.padding(10.dp)){Text(row.satellite.ifBlank{"Satellite timer"},color=SatInk,fontWeight=FontWeight.Bold);Text("${row.label} · ${satelliteTime(row.startEpoch,true)} → ${satelliteTime(row.endEpoch,true)}",color=SatMuted);Text(if(row.functional)"VALIDATED ACTIVE WINDOW" else "INACTIVE",color=if(row.functional)SatGood else SatBad)}}}}}}

@Composable
private fun SatelliteCataloguePanel(
    controller: SatelliteOperationsController,
    track: (SatelliteCatalogueEntry) -> Unit,
) {
    val context = LocalContext.current
    var search by rememberSaveable { mutableStateOf("") }
    var manual by remember { mutableStateOf(false) }
    var transponderFor by remember { mutableStateOf<SatelliteCatalogueEntry?>(null) }
    var remove by remember { mutableStateOf<SatelliteCatalogueEntry?>(null) }
    var removeTransponder by remember { mutableStateOf<SatelliteTransponder?>(null) }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            OutlinedTextField(search, { search = it }, label = { Text("Name / alias / NORAD") }, singleLine = true, modifier = Modifier.weight(1f))
            OutlinedButton({ controller.refresh(true) }, enabled = !controller.busy) { Text("REFRESH") }
            OutlinedButton({ manual = true }) { Text("ADD MANUAL TLE") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            TextButton({ context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(SatelliteProviderRepository.CELESTRAK_URL))) }) { Text("CELESTRAK SOURCE") }
            TextButton({ context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(SatelliteProviderRepository.SATNOGS_URL))) }) { Text("SATNOGS SOURCE") }
        }
        Text(SatelliteProviderRepository.SATNOGS_ATTRIBUTION, color = SatMuted, style = MaterialTheme.typography.bodySmall)
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(
                controller.elements.rows.filter { it.name.contains(search, true) || it.noradId.toString().contains(search) }.take(500),
                key = { it.noradId },
            ) { row ->
                ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = SatPanel)) {
                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text(row.name, color = SatInk, fontWeight = FontWeight.Bold)
                                Text("NORAD ${row.noradId} · ${row.elements.source} · epoch ${satelliteTime(row.elementEpoch, true)}", color = SatMuted)
                            }
                            IconButton({ controller.toggleFavourite(row.noradId) }) {
                                Icon(if (row.noradId in controller.favourites) Icons.Outlined.Star else Icons.Outlined.StarBorder, "Favourite", tint = SatAmber)
                            }
                        }
                        controller.transpondersFor(row.noradId).take(6).forEach { transponder ->
                            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text(
                                "${if (transponder.manual) "LOCAL" else "SatNOGS"} · ${transponder.typeLabel()} · ${transponder.mode} · " +
                                    "↓ ${transponder.downlinkLowHz?.let(::formatSatelliteFrequency) ?: "—"} · " +
                                    "↑ ${transponder.uplinkLowHz?.let(::formatSatelliteFrequency) ?: "—"} · ${transponder.providerStatus.ifBlank { "status unknown" }}",
                                color = SatMuted,modifier=Modifier.weight(1f),
                            );if(transponder.manual)TextButton({removeTransponder=transponder}){Text("REMOVE LOCAL")}}
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            TextButton(
                                onClick = {
                                    controller.passes.firstOrNull { it.satellite.noradId == row.noradId }?.let {
                                        controller.select(it)
                                        track(row)
                                    }
                                },
                                enabled = controller.passes.any { it.satellite.noradId == row.noradId },
                            ) { Text("TRACK") }
                            TextButton({ transponderFor = row }) { Text("LOCAL TRANSPONDER") }
                            if (row.manual) TextButton({ remove = row }) { Text("REMOVE OVERRIDE") }
                        }
                    }
                }
            }
        }
    }
    if(manual)ManualElementsDialog({manual=false}){id,name,one,two->controller.saveManualElements(id,name,one,two).also{if(it)manual=false}}
    transponderFor?.let{row->ManualTransponderDialog(row,{transponderFor=null}){controller.saveManualTransponder(it);transponderFor=null}}
    remove?.let{row->AlertDialog(onDismissRequest={remove=null},title={Text("Remove manual elements?")},text={Text("${row.name} will fall back to a validated provider row if one is cached.")},confirmButton={Button({controller.removeManualElements(row.noradId);remove=null}){Text("REMOVE")}},dismissButton={TextButton({remove=null}){Text("CANCEL")}})}
    removeTransponder?.let{row->AlertDialog(onDismissRequest={removeTransponder=null},title={Text("Remove local transponder?")},text={Text("The local override will be removed; provider rows remain unchanged.")},confirmButton={Button({controller.removeManualTransponder(row.id);removeTransponder=null}){Text("REMOVE")}},dismissButton={TextButton({removeTransponder=null}){Text("CANCEL")}})}
}

@Composable private fun ManualElementsDialog(dismiss:()->Unit,save:(Long,String,String,String)->Boolean){var id by remember{mutableStateOf("")};var name by remember{mutableStateOf("")};var one by remember{mutableStateOf("")};var two by remember{mutableStateOf("")};var error by remember{mutableStateOf("")};AlertDialog(onDismissRequest=dismiss,title={Text("Manual TLE override")},text={Column(verticalArrangement=Arrangement.spacedBy(6.dp)){OutlinedTextField(id,{id=it.filter(Char::isDigit)},label={Text("NORAD ID")});OutlinedTextField(name,{name=it},label={Text("Satellite name")});OutlinedTextField(one,{one=it},label={Text("TLE line 1")});OutlinedTextField(two,{two=it},label={Text("TLE line 2")});Text("Saved as MANUAL; provider validation is not claimed.",color=SatAmber);if(error.isNotBlank())Text(error,color=SatBad)}},confirmButton={Button({if(!save(id.toLongOrNull()?:0,name,one,two))error="Invalid NORAD ID or TLE lines"}){Text("SAVE MANUAL")}},dismissButton={TextButton(dismiss){Text("CANCEL")}})}

@Composable private fun ManualTransponderDialog(satellite:SatelliteCatalogueEntry,dismiss:()->Unit,save:(SatelliteTransponder)->Unit){var down by remember{mutableStateOf("")};var up by remember{mutableStateOf("")};var mode by remember{mutableStateOf("")};AlertDialog(onDismissRequest=dismiss,title={Text("Local transponder · ${satellite.name}")},text={Column(verticalArrangement=Arrangement.spacedBy(6.dp)){OutlinedTextField(down,{down=it.filter(Char::isDigit)},label={Text("Downlink Hz")});OutlinedTextField(up,{up=it.filter(Char::isDigit)},label={Text("Uplink Hz")});OutlinedTextField(mode,{mode=it.uppercase()},label={Text("Mode")});Text("Local override is visibly distinguished and does not claim active service.",color=SatAmber)}},confirmButton={Button({save(SatelliteTransponder("manual-${satellite.noradId}-${down}",satellite.noradId,"Local operator override",up.toLongOrNull(),null,down.toLongOrNull(),null,mode,mode,false,true,"local override",Instant.now().toString(),true))},enabled=down.toLongOrNull()!=null||up.toLongOrNull()!=null){Text("SAVE LOCAL")}},dismissButton={TextButton(dismiss){Text("CANCEL")}})}

@Composable private fun SatelliteFlightpathMap(track:List<OrbitalPoint>,live:OrbitalPoint?,observer:GeoPoint?,pass:OrbitalPass,modifier:Modifier){val context=LocalContext.current;val lifecycle=LocalLifecycleOwner.current.lifecycle;val mapView=remember{MapLibre.getInstance(context.applicationContext);MapView(context).apply{onCreate(null)}};var map by remember{mutableStateOf<org.maplibre.android.maps.MapLibreMap?>(null)};var styled by remember{mutableStateOf(false)};DisposableEffect(mapView,lifecycle){val watcher=LifecycleEventObserver{_,event->when(event){Lifecycle.Event.ON_START->mapView.onStart();Lifecycle.Event.ON_RESUME->mapView.onResume();Lifecycle.Event.ON_PAUSE->mapView.onPause();Lifecycle.Event.ON_STOP->mapView.onStop();else->Unit}};lifecycle.addObserver(watcher);mapView.getMapAsync{ready->map=ready;ready.uiSettings.isAttributionEnabled=true;ready.setStyle(Style.Builder().fromJson(satelliteMapStyle())){styled=true}};onDispose{lifecycle.removeObserver(watcher);mapView.onPause();mapView.onStop();mapView.onDestroy();map=null}};AndroidView({mapView},modifier);LaunchedEffect(track,live,observer,styled){val ready=map?:return@LaunchedEffect;if(!styled)return@LaunchedEffect;ready.clear();datelineSegments(track).forEach{segment->if(segment.size>1)ready.addPolyline(PolylineOptions().addAll(segment.map{LatLng(it.latitudeDeg,it.longitudeDeg)}).color(android.graphics.Color.rgb(233,167,43)).width(3f))};observer?.let{ready.addMarker(MarkerOptions().position(LatLng(it.latitude,it.longitude)).title("Observer"))};live?.let{p->ready.addMarker(MarkerOptions().position(LatLng(p.latitudeDeg,p.longitudeDeg)).title("Satellite"));footprint(p).let{circle->ready.addPolyline(PolylineOptions().addAll(circle).color(android.graphics.Color.argb(150,66,199,123)).width(2f))}};track.minByOrNull{ kotlin.math.abs(it.epoch-pass.aos)}?.let{ready.addMarker(MarkerOptions().position(LatLng(it.latitudeDeg,it.longitudeDeg)).title("AOS"))};track.minByOrNull{kotlin.math.abs(it.epoch-pass.los)}?.let{ready.addMarker(MarkerOptions().position(LatLng(it.latitudeDeg,it.longitudeDeg)).title("LOS"))};val points=track.map{LatLng(it.latitudeDeg,it.longitudeDeg)}+listOfNotNull(observer?.let{LatLng(it.latitude,it.longitude)},live?.let{LatLng(it.latitudeDeg,it.longitudeDeg)});if(points.isNotEmpty())runCatching{ready.animateCamera(CameraUpdateFactory.newLatLngBounds(LatLngBounds.Builder().includes(points).build(),60))}}}

private fun datelineSegments(points:List<OrbitalPoint>):List<List<OrbitalPoint>>{if(points.isEmpty())return emptyList();val rows=mutableListOf<MutableList<OrbitalPoint>>(mutableListOf(points.first()));points.drop(1).forEach{point->if(kotlin.math.abs(point.longitudeDeg-rows.last().last().longitudeDeg)>180)rows.add(mutableListOf());rows.last().add(point)};return rows.filter{it.isNotEmpty()}}
private fun footprint(point:OrbitalPoint):List<LatLng>{val earth=6371.0;val angle=Math.toDegrees(acos((earth/(earth+point.altitudeKm.coerceAtLeast(0.0))).coerceIn(-1.0,1.0)));return(0..72).map{i->val bearing=Math.toRadians(i*5.0);val lat1=Math.toRadians(point.latitudeDeg);val lon1=Math.toRadians(point.longitudeDeg);val distance=Math.toRadians(angle);val lat2=kotlin.math.asin(sin(lat1)*cos(distance)+cos(lat1)*sin(distance)*cos(bearing));val lon2=lon1+kotlin.math.atan2(sin(bearing)*sin(distance)*cos(lat1),cos(distance)-sin(lat1)*sin(lat2));LatLng(Math.toDegrees(lat2),((Math.toDegrees(lon2)+540)%360)-180)}}
private fun satelliteMapStyle()="""{"version":8,"sources":{"osm":{"type":"raster","tiles":["https://tile.openstreetmap.org/{z}/{x}/{y}.png"],"tileSize":256,"attribution":"© OpenStreetMap contributors"}},"layers":[{"id":"osm","type":"raster","source":"osm"}]}"""
private fun SatelliteTransponder.typeLabel()=if(manual)"LOCAL OVERRIDE" else if(alive&&providerStatus.equals("active",true))"catalogue active flag" else "catalogue ${providerStatus.ifBlank{"unknown"}}"
private fun satelliteTime(epoch:Long,utc:Boolean):String=DateTimeFormatter.ofPattern("dd MMM HH:mm:ss").withZone(if(utc)ZoneOffset.UTC else ZoneId.systemDefault()).format(Instant.ofEpochSecond(epoch))+(if(utc)"Z" else "")
private fun countdown(target:Long,now:Long):String{val seconds=target-now;val abs=kotlin.math.abs(seconds);val value="${abs/3600}h ${(abs%3600)/60}m ${abs%60}s";return if(seconds>=0)"T− $value" else "T+ $value"}
private fun formatSatelliteFrequency(hz:Long)="%.6f MHz".format(Locale.US,hz/1_000_000.0)
private fun shareSatelliteIcs(context:Context,row:SatellitePassRow){val stamp: (Long)->String={DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC).format(Instant.ofEpochSecond(it))};val text="BEGIN:VCALENDAR\r\nVERSION:2.0\r\nPRODID:-//RigWeave//Satellite Operations//EN\r\nBEGIN:VEVENT\r\nUID:${row.satellite.noradId}-${row.pass.aos}@rigweave\r\nDTSTAMP:${stamp(Instant.now().epochSecond)}\r\nDTSTART:${stamp(row.pass.aos)}\r\nDTEND:${stamp(row.pass.los)}\r\nSUMMARY:${row.satellite.name} satellite pass\r\nDESCRIPTION:Max elevation ${"%.1f".format(Locale.US,row.pass.maximumElevationDeg)} deg; local SGP4 prediction\r\nEND:VEVENT\r\nEND:VCALENDAR\r\n";context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply{type="text/calendar";putExtra(Intent.EXTRA_TEXT,text);putExtra(Intent.EXTRA_SUBJECT,"${row.satellite.name} pass")},"Add/share satellite pass"))}
private fun satelliteLastKnownPoint(context:Context):GeoPoint?{val manager=context.getSystemService(LocationManager::class.java);return manager.getProviders(true).mapNotNull{provider->runCatching{manager.getLastKnownLocation(provider)}.getOrNull()}.maxByOrNull{it.time}?.let{GeoPoint(it.latitude,it.longitude)}}
