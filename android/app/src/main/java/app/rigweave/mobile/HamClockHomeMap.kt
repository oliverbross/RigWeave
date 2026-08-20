package app.rigweave.mobile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.rigweave.mobile.hamclock.HamClockBasemap
import app.rigweave.mobile.hamclock.HamClockDxTarget
import app.rigweave.mobile.hamclock.HamClockDxTargetSource
import app.rigweave.mobile.hamclock.HamClockMapLayerId
import app.rigweave.mobile.hamclock.HamClockMapLayerAvailability
import app.rigweave.mobile.hamclock.HamClockMapPreference
import app.rigweave.mobile.hamclock.HamClockMapRenderKind
import app.rigweave.mobile.hamclock.HamClockMapSelection
import app.rigweave.mobile.hamclock.hamClockMapLayerRegistry
import com.google.gson.JsonObject
import kotlinx.coroutines.delay
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleOpacity
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.fillColor
import org.maplibre.android.style.layers.PropertyFactory.fillOpacity
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineOpacity
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon

internal data class HamClockMapPoint(
    val id: String,
    val layerId: String,
    val title: String,
    val detail: String,
    val latitude: Double,
    val longitude: Double,
    val color: String,
    val selection: HamClockMapSelection = HamClockMapSelection.NONE,
)

internal data class HamClockMapLine(
    val id: String,
    val layerId: String,
    val title: String,
    val detail: String,
    val segments: List<List<GeoPoint>>,
    val color: String,
    val selection: HamClockMapSelection = HamClockMapSelection.NONE,
)

internal data class HamClockMapFill(
    val id: String,
    val layerId: String,
    val title: String,
    val detail: String,
    val ring: List<GeoPoint>,
    val color: String,
)

internal data class HamClockMapSnapshot(
    val points: List<HamClockMapPoint> = emptyList(),
    val lines: List<HamClockMapLine> = emptyList(),
    val fills: List<HamClockMapFill> = emptyList(),
    val generatedAtEpoch: Long = 0,
)

internal data class HamClockResolvedTarget(
    val callsign: String,
    val grid: String,
    val point: GeoPoint,
    val source: HamClockDxTargetSource,
    val detail: String,
)

internal fun resolveHamClockTarget(
    stored: HamClockDxTarget?,
    automatic: AndroidDXSpot?,
): HamClockResolvedTarget? {
    val storedPoint = stored?.let { value ->
        value.latitude?.let { latitude -> value.longitude?.let { longitude -> GeoPoint(latitude, longitude) } }
            ?: maidenheadCenter(value.grid)
    }
    if (stored != null && storedPoint != null && (stored.locked || automatic == null)) {
        return HamClockResolvedTarget(
            stored.callsign.ifBlank { stored.grid.ifBlank { "Manual target" } },
            stored.grid,
            storedPoint,
            stored.source,
            if (stored.locked) "Locked manual target" else "Stored manual target",
        )
    }
    return automatic?.takeIf { it.latitude != 0.0 || it.longitude != 0.0 }?.let {
        HamClockResolvedTarget(
            it.callsign,
            "",
            GeoPoint(it.latitude, it.longitude),
            HamClockDxTargetSource.AUTOMATIC,
            listOf(it.country, it.band, it.mode).filter(String::isNotBlank).joinToString(" · "),
        )
    } ?: storedPoint?.let {
        HamClockResolvedTarget(stored?.callsign.orEmpty(), stored?.grid.orEmpty(), it,
            stored?.source ?: HamClockDxTargetSource.MANUAL, "Stored target")
    }
}

internal fun buildHamClockMapSnapshot(
    stationCall: String,
    stationGrid: String,
    dxSpots: List<AndroidDXSpot>,
    signal: NeuralMySignal,
    portableSpots: List<PortableSpot>,
    satellites: List<SatellitePosition>,
    recentQsos: List<HamClockRecentQso>,
    lightning: NeuralLightning,
    target: HamClockResolvedTarget?,
    now: java.time.Instant,
): HamClockMapSnapshot {
    val station = maidenheadCenter(stationGrid)
    val points = mutableListOf<HamClockMapPoint>()
    val lines = mutableListOf<HamClockMapLine>()
    val fills = mutableListOf<HamClockMapFill>()
    station?.let {
        points += HamClockMapPoint("de", HamClockMapLayerId.DE_STATION,
            stationCall.ifBlank { "DE station" }, stationGrid.ifBlank { "Configured station" },
            it.latitude, it.longitude, "#42c7d8")
    }
    dxSpots.asSequence().filter { it.latitude != 0.0 || it.longitude != 0.0 }.take(160).forEach { spot ->
        val detail = "${spot.country.ifBlank { "Unknown" }} · ${spot.band} ${spot.mode} · ${spot.frequencyHz / 1000} kHz"
        points += HamClockMapPoint(spot.id, HamClockMapLayerId.DX_SPOTS, spot.callsign, detail,
            spot.latitude, spot.longitude, if (spot.watchlisted) "#f3d054" else "#42c7d8", HamClockMapSelection.DX)
        station?.let { origin ->
            val path = greatCirclePath(LatLng(origin.latitude, origin.longitude), LatLng(spot.latitude, spot.longitude), 28)
            lines += HamClockMapLine(spot.id, HamClockMapLayerId.DX_PATHS, spot.callsign, detail,
                splitAtDateline(path).map { segment -> segment.map { GeoPoint(it.latitude, it.longitude) } },
                "#42c7d8", HamClockMapSelection.DX)
        }
    }
    target?.let { selected ->
        points += HamClockMapPoint("target", HamClockMapLayerId.SELECTED_TARGET, selected.callsign,
            "${selected.source.name.lowercase().replaceFirstChar(Char::uppercase)} · ${selected.detail}",
            selected.point.latitude, selected.point.longitude, "#f0ad35", HamClockMapSelection.TARGET)
        station?.let { origin ->
            val path = greatCirclePath(LatLng(origin.latitude, origin.longitude),
                LatLng(selected.point.latitude, selected.point.longitude), 40)
            lines += HamClockMapLine("target", HamClockMapLayerId.SELECTED_TARGET, selected.callsign,
                selected.detail, splitAtDateline(path).map { segment -> segment.map { GeoPoint(it.latitude, it.longitude) } },
                "#f0ad35", HamClockMapSelection.TARGET)
        }
    }
    signal.reports.asSequence().filter { it.latitude != null && it.longitude != null }.take(60).forEachIndexed { index, report ->
        val latitude = report.latitude ?: return@forEachIndexed
        val longitude = report.longitude ?: return@forEachIndexed
        val detail = "${report.locator} · ${report.band} ${report.mode} · ${report.snr?.let { "$it dB" } ?: "SNR unavailable"}"
        points += HamClockMapPoint("${report.callsign}-${report.epoch}-$index", HamClockMapLayerId.PSK_REPORTER,
            report.callsign, detail, latitude, longitude, "#43d17c", HamClockMapSelection.DX)
        station?.let { origin ->
            val path = greatCirclePath(LatLng(origin.latitude, origin.longitude), LatLng(latitude, longitude), 20)
            lines += HamClockMapLine("${report.callsign}-${report.epoch}-$index", HamClockMapLayerId.PSK_REPORTER,
                report.callsign, detail, splitAtDateline(path).map { segment -> segment.map { GeoPoint(it.latitude, it.longitude) } },
                "#43d17c", HamClockMapSelection.DX)
        }
    }
    portableSpots.asSequence().filter { it.latitude != null && it.longitude != null }.take(160).forEach { spot ->
        points += HamClockMapPoint(spot.id, HamClockMapLayerId.PORTABLE, spot.callsign,
            "${spot.references.joinToString { it.code }} · ${spot.band} ${spot.mode}",
            spot.latitude!!, spot.longitude!!, "#f3d054", HamClockMapSelection.PORTABLE)
    }
    satellites.take(40).forEach { satellite ->
        points += HamClockMapPoint(satellite.norad.toString(), HamClockMapLayerId.SATELLITES, satellite.name,
            "${satellite.altitudeKm.toInt()} km · elevation ${satellite.elevation.toInt()}°",
            satellite.latitude, satellite.longitude, "#b783f5", HamClockMapSelection.SATELLITE)
    }
    recentQsos.take(120).forEach { qso ->
        maidenheadCenter(qso.grid)?.let { location ->
            points += HamClockMapPoint(qso.id, HamClockMapLayerId.LOGGED_QSOS, qso.callsign,
                "${qso.grid} · ${qso.band} ${qso.mode} · ${qso.country}",
                location.latitude, location.longitude, "#91a1a9", HamClockMapSelection.QSO)
        }
    }
    val grayline = greylineArea(now)
    fills += HamClockMapFill("night", HamClockMapLayerId.GRAYLINE, "Night region", "Local UTC astronomy",
        grayline.points.map { GeoPoint(it.latitude, it.longitude) }, "#13243b")
    val terminator = terminatorPoints(now)
    lines += HamClockMapLine("terminator", HamClockMapLayerId.GRAYLINE, "Grayline", "Current UTC terminator",
        splitAtDateline(terminator).map { segment -> segment.map { GeoPoint(it.latitude, it.longitude) } },
        "#f0ad35")
    val sun = sunPosition(now)
    points += HamClockMapPoint("sun", HamClockMapLayerId.SUN, "Sun", "Approximate subsolar point",
        sun.first, sun.second, "#f3d054")
    points += HamClockMapPoint("moon", HamClockMapLayerId.MOON, "Moon", "Approximate sublunar point",
        -sun.first, if (sun.second > 0) sun.second - 180 else sun.second + 180, "#eaf0ed")
    var gridIndex = 0
    for (longitude in -160..160 step 20) {
        lines += HamClockMapLine("grid-${gridIndex++}", HamClockMapLayerId.GRID, "Maidenhead field grid",
            "20° longitude field boundary", listOf(listOf(GeoPoint(-80.0, longitude.toDouble()), GeoPoint(80.0, longitude.toDouble()))),
            "#91a1a9")
    }
    for (latitude in -70..70 step 10) {
        lines += HamClockMapLine("grid-${gridIndex++}", HamClockMapLayerId.GRID, "Maidenhead field grid",
            "10° latitude field boundary", listOf(listOf(GeoPoint(latitude.toDouble(), -180.0), GeoPoint(latitude.toDouble(), 180.0))),
            "#91a1a9")
    }
    lightning.strikes.take(120).forEachIndexed { index, strike ->
        points += HamClockMapPoint("${strike.epoch}-$index", HamClockMapLayerId.LIGHTNING, "Lightning",
            "${strike.distanceKm.toInt()} km · ${strike.bearing}", strike.latitude, strike.longitude,
            "#e65b54", HamClockMapSelection.WEATHER)
    }
    return boundedHamClockMapSnapshot(HamClockMapSnapshot(points, lines, fills, now.epochSecond))
}

private data class HamClockSelectedFeature(
    val title: String,
    val detail: String,
    val selection: HamClockMapSelection,
)

internal fun boundedHamClockMapSnapshot(snapshot: HamClockMapSnapshot): HamClockMapSnapshot {
    fun maximum(layerId: String) = hamClockMapLayerRegistry.firstOrNull { it.id == layerId }?.maximumObjectCount ?: 0
    return snapshot.copy(
        points = snapshot.points.groupBy { it.layerId }.flatMap { (id, rows) -> rows.take(maximum(id)) },
        lines = snapshot.lines.groupBy { it.layerId }.flatMap { (id, rows) -> rows.take(maximum(id)) },
        fills = snapshot.fills.groupBy { it.layerId }.flatMap { (id, rows) -> rows.take(maximum(id)) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HamClockHomeMap(
    snapshot: HamClockMapSnapshot,
    preference: HamClockMapPreference,
    station: GeoPoint?,
    lowDataMode: Boolean,
    onPreferenceChange: (HamClockMapPreference) -> Unit,
    onOpenSelection: (HamClockMapSelection) -> Unit,
    modifier: Modifier = Modifier,
) {
    val bounded = remember(snapshot) { boundedHamClockMapSnapshot(snapshot) }
    if (lowDataMode) {
        HamClockMapDataView(bounded, preference, onOpenSelection, modifier)
        return
    }
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val mapView = remember {
        MapLibre.getInstance(context.applicationContext)
        MapView(context).apply { onCreate(null) }
    }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var styleReady by remember { mutableStateOf(false) }
    var mapError by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<HamClockSelectedFeature?>(null) }
    var pendingCamera by remember { mutableStateOf<CameraPosition?>(null) }
    val currentPreference by rememberUpdatedState(preference)
    val currentPreferenceChange by rememberUpdatedState(onPreferenceChange)
    val currentOpenSelection by rememberUpdatedState(onOpenSelection)

    DisposableEffect(mapView, lifecycle) {
        var started = false
        var resumed = false
        var destroyed = false
        fun start() { if (!started && !destroyed) { mapView.onStart(); started = true } }
        fun resume() { start(); if (!resumed && !destroyed) { mapView.onResume(); resumed = true } }
        fun pause() { if (resumed && !destroyed) { mapView.onPause(); resumed = false } }
        fun stop() { pause(); if (started && !destroyed) { mapView.onStop(); started = false } }
        fun destroy() { if (!destroyed) { stop(); mapView.onDestroy(); destroyed = true } }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> start()
                Lifecycle.Event.ON_RESUME -> resume()
                Lifecycle.Event.ON_PAUSE -> pause()
                Lifecycle.Event.ON_STOP -> stop()
                Lifecycle.Event.ON_DESTROY -> destroy()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) start()
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) resume()
        var disposed = false
        mapView.getMapAsync { ready ->
            if (disposed) return@getMapAsync
            map = ready
            ready.uiSettings.apply {
                isAttributionEnabled = true
                isLogoEnabled = true
                isCompassEnabled = true
                isZoomGesturesEnabled = true
                isScrollGesturesEnabled = true
                isRotateGesturesEnabled = true
                isTiltGesturesEnabled = false
            }
            ready.setMinZoomPreference(.8)
            ready.setMaxZoomPreference(12.0)
            ready.cameraPosition = CameraPosition.Builder()
                .target(LatLng(preference.centerLatitude, preference.centerLongitude))
                .zoom(preference.zoom)
                .build()
            ready.addOnCameraMoveStartedListener { reason ->
                val active = currentPreference
                if (reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE && active.followStation) {
                    currentPreferenceChange(active.copy(followStation = false))
                }
            }
            ready.addOnCameraIdleListener { pendingCamera = ready.cameraPosition }
            ready.addOnMapClickListener { coordinate ->
                val layerIds = activeLayerIds(preference)
                val hit = ready.queryRenderedFeatures(ready.projection.toScreenLocation(coordinate), *layerIds.toTypedArray())
                    .firstOrNull()
                selected = hit?.let { feature ->
                    HamClockSelectedFeature(
                        feature.getStringProperty("title") ?: "Map item",
                        feature.getStringProperty("detail") ?: "",
                        runCatching { HamClockMapSelection.valueOf(feature.getStringProperty("selection") ?: "NONE") }
                            .getOrDefault(HamClockMapSelection.NONE),
                    )
                }
                hit != null
            }
        }
        onDispose {
            disposed = true
            lifecycle.removeObserver(observer)
            map = null
            styleReady = false
            destroy()
        }
    }

    LaunchedEffect(map, preference.basemap) {
        val ready = map ?: return@LaunchedEffect
        styleReady = false
        mapError = ""
        if (preference.basemap == HamClockBasemap.SATELLITE || preference.basemap == HamClockBasemap.TERRAIN) {
            mapError = "${preference.basemap.name.lowercase().replaceFirstChar(Char::uppercase)} basemap is unavailable: no lawful configured tile source"
            return@LaunchedEffect
        }
        runCatching {
            ready.setStyle(Style.Builder().fromJson(hamClockStyleJson(preference.basemap))) { style ->
                installHamClockLayers(style, preference)
                styleReady = true
            }
        }.onFailure { mapError = it.message ?: "Map style unavailable" }
    }

    LaunchedEffect(map, styleReady, bounded, preference.layers) {
        val style = map?.style ?: return@LaunchedEffect
        if (!styleReady) return@LaunchedEffect
        updateHamClockSources(style, bounded, preference)
    }

    LaunchedEffect(preference.followStation, station, map, styleReady) {
        if (preference.followStation && station != null && styleReady) {
            map?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(station.latitude, station.longitude), preference.zoom), 500)
        }
    }

    LaunchedEffect(pendingCamera) {
        val camera = pendingCamera ?: return@LaunchedEffect
        delay(600)
        if (camera == pendingCamera) {
            val target = camera.target ?: return@LaunchedEffect
            onPreferenceChange(preference.copy(
                centerLatitude = target.latitude.coerceIn(-85.0, 85.0),
                centerLongitude = target.longitude.coerceIn(-180.0, 180.0),
                zoom = camera.zoom.coerceIn(.8, 12.0),
            ))
        }
    }

    Box(modifier.clip(RoundedCornerShape(8.dp)).background(Color(0xFF07151B))
        .border(1.dp, Color(0xFF32434C), RoundedCornerShape(8.dp))
        .semantics { contentDescription = "Interactive HamClock world activity map" }) {
        AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())
        Surface(color = Color(0xD9111C22), shape = RoundedCornerShape(5.dp),
            modifier = Modifier.align(Alignment.TopStart).padding(8.dp)) {
            Text("MAP · ${preference.basemap.name} · bounded GeoJSON", color = Color(0xFFEAF0ED),
                fontSize = 10.sp, modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp))
        }
        IconButton(onClick = {
            station?.let {
                val reset = preference.copy(followStation = true, centerLatitude = it.latitude, centerLongitude = it.longitude)
                onPreferenceChange(reset)
                map?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(it.latitude, it.longitude), reset.zoom), 500)
            }
        }, enabled = station != null, modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp).size(48.dp)) {
            Icon(Icons.Outlined.MyLocation, contentDescription = "Reset map to station", tint = Color(0xFF42C7D8))
        }
        if (mapError.isNotBlank()) {
            HamClockMapFallback(mapError, bounded, preference, onOpenSelection, Modifier.fillMaxSize())
        }
    }
    selected?.let { item ->
        ModalBottomSheet(onDismissRequest = { selected = null }, containerColor = Color(0xFF111C22)) {
            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(item.title, color = Color(0xFFEAF0ED), fontSize = 18.sp)
                Text(item.detail.ifBlank { "No additional detail" }, color = Color(0xFF91A1A9))
                if (item.selection != HamClockMapSelection.NONE) {
                    TextButton(onClick = { selected = null; currentOpenSelection(item.selection) }) { Text("Open workspace") }
                } else TextButton(onClick = { selected = null }) { Text("Close") }
                if (item.selection != HamClockMapSelection.NONE) {
                    TextButton(onClick = { selected = null }) { Text("Close") }
                }
            }
        }
    }
}

@Composable
internal fun HamClockMapDataView(
    snapshot: HamClockMapSnapshot,
    preference: HamClockMapPreference,
    onOpenSelection: (HamClockMapSelection) -> Unit,
    modifier: Modifier = Modifier,
) {
    val visible = preference.layers.filter { it.visible }.associateBy { it.id }
    LazyColumn(modifier.background(Color(0xFF07151B)).padding(8.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        hamClockMapLayerRegistry.forEach { spec ->
            val pref = visible[spec.id] ?: return@forEach
            item(key = "header-${spec.id}") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text(spec.title, color = Color(0xFFEAF0ED), fontSize = 12.sp)
                        Text("${spec.sourceLabel} · ${spec.lowDataRepresentation}", color = Color(0xFF91A1A9), fontSize = 9.sp)
                    }
                    Text("${(pref.opacity * 100).toInt()}%", color = Color(0xFF42C7D8), fontSize = 9.sp)
                }
                if (spec.availability == HamClockMapLayerAvailability.UNAVAILABLE) {
                    Text("Unavailable · ${spec.unavailableReason}", color = Color(0xFFF0AD35), fontSize = 10.sp)
                }
            }
            val points = snapshot.points.filter { it.layerId == spec.id }
            items(points, key = { "${spec.id}-${it.id}" }) { row ->
                Surface(color = Color(0xFF182831), shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(7.dp)) {
                        Text(row.title, color = Color(0xFFEAF0ED), fontSize = 11.sp)
                        Text(row.detail, color = Color(0xFF91A1A9), fontSize = 9.sp)
                        if (row.selection != HamClockMapSelection.NONE) {
                            TextButton(onClick = { onOpenSelection(row.selection) }) { Text("Open") }
                        }
                    }
                }
            }
            val lineCount = snapshot.lines.count { it.layerId == spec.id }
            val fillCount = snapshot.fills.count { it.layerId == spec.id }
            if (lineCount + fillCount > 0) item(key = "summary-${spec.id}") {
                Text("$lineCount paths · $fillCount areas", color = Color(0xFF91A1A9), fontSize = 9.sp)
            }
        }
    }
}

@Composable
private fun HamClockMapFallback(
    error: String,
    snapshot: HamClockMapSnapshot,
    preference: HamClockMapPreference,
    onOpenSelection: (HamClockMapSelection) -> Unit,
    modifier: Modifier,
) {
    Column(modifier.background(Color(0xF20B1419)).padding(12.dp)) {
        Text("Map unavailable", color = Color(0xFFF0AD35))
        Text(error, color = Color(0xFF91A1A9), fontSize = 10.sp)
        HamClockMapDataView(snapshot, preference, onOpenSelection, Modifier.weight(1f).fillMaxWidth())
    }
}

private fun activeLayerIds(preference: HamClockMapPreference): List<String> {
    val visible = preference.layers.filter { it.visible }.map { it.id }.toSet()
    return hamClockMapLayerRegistry.filter { it.id in visible && it.availability != HamClockMapLayerAvailability.UNAVAILABLE }
        .flatMap { spec -> spec.renderKinds.map { "${spec.sourcePrefix}-${it.name.lowercase()}-layer" } }
}

private fun installHamClockLayers(style: Style, preference: HamClockMapPreference) {
    hamClockMapLayerRegistry.filter { it.availability != HamClockMapLayerAvailability.UNAVAILABLE }.forEach { spec ->
        spec.renderKinds.forEach { kind ->
            val suffix = kind.name.lowercase()
            val sourceId = "${spec.sourcePrefix}-$suffix-source"
            val layerId = "${spec.sourcePrefix}-$suffix-layer"
            style.addSource(GeoJsonSource(sourceId, emptyFeatureCollection()))
            val opacity = preference.layers.firstOrNull { it.id == spec.id }?.opacity ?: spec.defaultOpacity
            when (kind) {
                HamClockMapRenderKind.POINT -> style.addLayer(CircleLayer(layerId, sourceId).withProperties(
                    circleColor(Expression.get("color")), circleRadius(5.5f), circleOpacity(opacity)))
                HamClockMapRenderKind.LINE -> style.addLayer(LineLayer(layerId, sourceId).withProperties(
                    lineColor(Expression.get("color")), lineWidth(1.7f), lineOpacity(opacity)))
                HamClockMapRenderKind.FILL -> style.addLayer(FillLayer(layerId, sourceId).withProperties(
                    fillColor(Expression.get("color")), fillOpacity(opacity * .5f)))
            }
        }
    }
}

private fun updateHamClockSources(style: Style, snapshot: HamClockMapSnapshot, preference: HamClockMapPreference) {
    val visible = preference.layers.filter { it.visible }.map { it.id }.toSet()
    hamClockMapLayerRegistry.filter { it.availability != HamClockMapLayerAvailability.UNAVAILABLE }.forEach { spec ->
        spec.renderKinds.forEach { kind ->
            val sourceId = "${spec.sourcePrefix}-${kind.name.lowercase()}-source"
            val layerId = "${spec.sourcePrefix}-${kind.name.lowercase()}-layer"
            val opacity = preference.layers.firstOrNull { it.id == spec.id }?.opacity ?: spec.defaultOpacity
            style.getLayer(layerId)?.setProperties(when (kind) {
                HamClockMapRenderKind.POINT -> circleOpacity(opacity)
                HamClockMapRenderKind.LINE -> lineOpacity(opacity)
                HamClockMapRenderKind.FILL -> fillOpacity(opacity * .5f)
            })
            val features = if (spec.id !in visible) emptyList() else when (kind) {
                HamClockMapRenderKind.POINT -> snapshot.points.filter { it.layerId == spec.id }.map { pointFeature(it) }
                HamClockMapRenderKind.LINE -> snapshot.lines.filter { it.layerId == spec.id }.flatMap { lineFeatures(it) }
                HamClockMapRenderKind.FILL -> snapshot.fills.filter { it.layerId == spec.id }.mapNotNull { fillFeature(it) }
            }
            (style.getSource(sourceId) as? GeoJsonSource)?.setGeoJson(FeatureCollection.fromFeatures(features))
        }
    }
}

private fun pointFeature(item: HamClockMapPoint): Feature =
    feature(Point.fromLngLat(item.longitude, item.latitude), item.id, item.title, item.detail, item.color, item.selection)

private fun lineFeatures(item: HamClockMapLine): List<Feature> = item.segments.mapIndexedNotNull { index, segment ->
    val points = segment.map { Point.fromLngLat(it.longitude, it.latitude) }
    if (points.size < 2) null else feature(LineString.fromLngLats(points), "${item.id}-$index", item.title,
        item.detail, item.color, item.selection)
}

private fun fillFeature(item: HamClockMapFill): Feature? {
    val points = item.ring.map { Point.fromLngLat(it.longitude, it.latitude) }.toMutableList()
    if (points.size < 3) return null
    if (points.first() != points.last()) points += points.first()
    return feature(Polygon.fromLngLats(listOf(points)), item.id, item.title, item.detail, item.color, HamClockMapSelection.NONE)
}

private fun feature(
    geometry: org.maplibre.geojson.Geometry,
    id: String,
    title: String,
    detail: String,
    color: String,
    selection: HamClockMapSelection,
): Feature {
    val properties = JsonObject().apply {
        addProperty("title", title)
        addProperty("detail", detail)
        addProperty("color", color)
        addProperty("selection", selection.name)
    }
    return Feature.fromGeometry(geometry, properties, id)
}

private fun emptyFeatureCollection(): FeatureCollection = FeatureCollection.fromFeatures(emptyList())

private fun hamClockStyleJson(basemap: HamClockBasemap): String {
    val family = if (basemap == HamClockBasemap.LIGHT) "light" else "dark"
    val background = if (basemap == HamClockBasemap.LIGHT) "#e8edf0" else "#06151c"
    return """{
      "version":8,
      "name":"RigWeave HamClock ${family.replaceFirstChar(Char::uppercase)}",
      "sources":{
        "carto":{"type":"raster","tiles":["https://a.basemaps.cartocdn.com/${family}_nolabels/{z}/{x}/{y}.png"],"tileSize":256,"maxzoom":20,"attribution":"© CARTO © OpenStreetMap contributors"},
        "labels":{"type":"raster","tiles":["https://a.basemaps.cartocdn.com/${family}_only_labels/{z}/{x}/{y}.png"],"tileSize":256,"maxzoom":20,"attribution":"© CARTO © OpenStreetMap contributors"}
      },
      "layers":[
        {"id":"background","type":"background","paint":{"background-color":"$background"}},
        {"id":"carto","type":"raster","source":"carto"},
        {"id":"labels","type":"raster","source":"labels","paint":{"raster-opacity":0.92}}
      ]
    }""".trimIndent()
}
