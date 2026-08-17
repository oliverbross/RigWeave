package app.rigweave.mobile

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import org.maplibre.android.MapLibre
import org.maplibre.android.annotations.Icon
import org.maplibre.android.annotations.IconFactory
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.annotations.PolygonOptions
import org.maplibre.android.annotations.PolylineOptions
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private val MapInk = ComposeColor(0xFFF4F0E7)
private val MapMuted = ComposeColor(0xFFB4BDC2)
private val MapCyan = ComposeColor(0xFF43C7D9)
private val MapGreen = ComposeColor(0xFF42C77B)
private val MapAmber = ComposeColor(0xFFE9A72B)
private val MapYellow = ComposeColor(0xFFF4C94E)
private val MapRed = ComposeColor(0xFFE4544D)

private enum class NeuralBasemap(val label: String, val styleJson: String) {
    SATELLITE("ESRI SATELLITE", satelliteStyleJson()),
    DARK("CARTO DARK", darkStyleJson()),
}

private data class MapMarker(
    val latitude: Double,
    val longitude: Double,
    val title: String,
    val detail: String = "",
    val color: Int,
    val sizeDp: Int = 12,
    val ring: Boolean = false,
)

private data class MapPath(val points: List<LatLng>, val color: Int, val widthDp: Float = 1.5f)
private data class MapArea(val points: List<LatLng>, val fill: Int, val stroke: Int)

@Composable
private fun NativeNeuralMap(
    markers: List<MapMarker>,
    paths: List<MapPath>,
    areas: List<MapArea>,
    center: LatLng,
    zoom: Double,
    basemap: NeuralBasemap,
    label: String,
    description: String,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val density = context.resources.displayMetrics.density
    val mapView = remember(basemap) {
        MapLibre.getInstance(context.applicationContext)
        MapView(context).apply { onCreate(null) }
    }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var styleReady by remember { mutableStateOf(false) }

    DisposableEffect(mapView, lifecycle) {
        var started = false
        var resumed = false
        var destroyed = false
        fun start() {
            if (!started && !destroyed) {
                mapView.onStart()
                started = true
            }
        }
        fun resume() {
            start()
            if (!resumed && !destroyed) {
                mapView.onResume()
                resumed = true
            }
        }
        fun pause() {
            if (resumed && !destroyed) {
                mapView.onPause()
                resumed = false
            }
        }
        fun stop() {
            pause()
            if (started && !destroyed) {
                mapView.onStop()
                started = false
            }
        }
        fun destroy() {
            if (!destroyed) {
                stop()
                mapView.onDestroy()
                destroyed = true
            }
        }
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
        mapView.getMapAsync { readyMap ->
            if (disposed) return@getMapAsync
            map = readyMap
            readyMap.uiSettings.apply {
                isAttributionEnabled = false
                isLogoEnabled = false
                isCompassEnabled = true
                isZoomGesturesEnabled = true
                isScrollGesturesEnabled = true
                isRotateGesturesEnabled = true
                isTiltGesturesEnabled = false
            }
            readyMap.setMinZoomPreference(0.8)
            readyMap.setMaxZoomPreference(12.0)
            readyMap.cameraPosition = CameraPosition.Builder().target(center).zoom(zoom).build()
            readyMap.setStyle(Style.Builder().fromJson(basemap.styleJson)) { styleReady = true }
        }
        onDispose {
            disposed = true
            lifecycle.removeObserver(observer)
            map = null
            styleReady = false
            destroy()
        }
    }

    LaunchedEffect(map, styleReady, markers, paths, areas) {
        val readyMap = map ?: return@LaunchedEffect
        if (!styleReady) return@LaunchedEffect
        readyMap.clear()
        areas.forEach { area ->
            readyMap.addPolygon(PolygonOptions().addAll(area.points).fillColor(area.fill).strokeColor(area.stroke))
        }
        paths.forEach { path ->
            readyMap.addPolyline(PolylineOptions().addAll(path.points).color(path.color).width(path.widthDp * density))
        }
        markers.forEach { marker ->
            readyMap.addMarker(
                MarkerOptions()
                    .position(LatLng(marker.latitude, marker.longitude))
                    .title(marker.title)
                    .snippet(marker.detail)
                    .icon(mapMarkerIcon(context, marker.color, marker.sizeDp, marker.ring))
            )
        }
    }

    Box(
        modifier.clip(RoundedCornerShape(10.dp))
            .background(ComposeColor(0xFF06151C))
            .border(1.dp, ComposeColor(0xFF3D474E), RoundedCornerShape(10.dp))
            .semantics { contentDescription = description }
    ) {
        AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())
        Surface(
            color = ComposeColor(0xE6192228),
            shape = RoundedCornerShape(6.dp),
            modifier = Modifier.align(Alignment.TopStart).padding(10.dp),
        ) {
            Text("$label · ${basemap.label}", color = MapInk, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp))
        }
        Surface(
            color = ComposeColor(0xD9111519),
            shape = RoundedCornerShape(5.dp),
            modifier = Modifier.align(Alignment.TopStart).padding(start = 10.dp, top = 48.dp),
        ) {
            Text(
                if (basemap == NeuralBasemap.SATELLITE) "Imagery and reference labels © Esri"
                else "© CARTO · © OpenStreetMap contributors",
                color = MapMuted,
                fontSize = 9.sp,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
            )
        }
        Surface(
            color = ComposeColor(0xE6192228),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.align(Alignment.BottomEnd).padding(10.dp),
        ) {
            IconButton(
                onClick = { map?.animateCamera(CameraUpdateFactory.newLatLngZoom(center, zoom), 550) },
                modifier = Modifier.size(48.dp),
            ) {
                Icon(Icons.Outlined.MyLocation, contentDescription = "Reset map view", tint = MapCyan)
            }
        }
    }
}

@Composable
internal fun DxWorldCanvas(rows: List<AndroidDXSpot>, stationGrid: String, hearsMe: Boolean, modifier: Modifier) {
    val qth = maidenheadCenter(stationGrid)
    val markers = buildList {
        qth?.let { add(qthMarker(it)) }
        rows.forEach { spot ->
            if (spot.latitude != 0.0 || spot.longitude != 0.0) {
                add(MapMarker(
                    spot.latitude, spot.longitude, spot.callsign,
                    "${spot.band} ${spot.mode} · ${"%.3f".format(spot.frequencyHz / 1_000_000.0)} MHz · ${spot.country}",
                    when { spot.watchlisted -> colorInt(MapYellow); hearsMe -> colorInt(MapGreen); else -> colorInt(MapCyan) },
                    if (spot.watchlisted) 15 else 11,
                    spot.watchlisted,
                ))
            }
        }
    }
    NativeNeuralMap(markers, emptyList(), emptyList(), LatLng(20.0, 0.0), 1.35, NeuralBasemap.SATELLITE,
        "LIVE DX", "Interactive world map with ${rows.size} DX observations", modifier)
}

@Composable
internal fun DxReceiverMap(rows: List<SignalReport>, stationGrid: String, modifier: Modifier) {
    val qth = maidenheadCenter(stationGrid)
    val markers = buildList {
        qth?.let { add(qthMarker(it)) }
        rows.forEach { row ->
            val lat = row.latitude ?: return@forEach
            val lon = row.longitude ?: return@forEach
            add(MapMarker(lat, lon, row.callsign, "${row.band} ${row.mode} · ${row.snr?.let { "$it dB" } ?: "SNR —"} · ${row.distanceKm?.let { "$it km" } ?: row.locator}", colorInt(MapGreen), 12))
        }
    }
    val paths = if (qth == null) emptyList() else rows.mapNotNull { row ->
        val lat = row.latitude ?: return@mapNotNull null
        val lon = row.longitude ?: return@mapNotNull null
        MapPath(listOf(LatLng(qth.latitude, qth.longitude), LatLng(lat, lon)), colorInt(MapGreen.copy(alpha = .52f)), 1.35f)
    }
    NativeNeuralMap(markers, paths, emptyList(), LatLng(20.0, 0.0), 1.35, NeuralBasemap.SATELLITE,
        "WHO HEARS ME", "Interactive map with ${rows.size} PSK Reporter receivers", modifier)
}

@Composable
internal fun DxWorldAnomalyCanvas(rows: List<NeuralWorldCell>, greyline: Boolean, modifier: Modifier) {
    var currentTime by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(greyline) {
        currentTime = Instant.now()
        while (greyline) {
            delay(60_000)
            currentTime = Instant.now()
        }
    }
    val cellMarkers = buildList {
        rows.forEach { cell ->
            val ratio = cell.anomalyRatio ?: 1.0
            val hue = when { ratio >= 2.5 -> MapRed; ratio >= 1.8 -> MapYellow; else -> MapCyan }
            val expected = cell.expected?.let { "%.1f".format(it) } ?: "—"
            val calls = cell.calls.take(4).joinToString().ifBlank { "No representative calls" }
            add(MapMarker(
                cell.latitude,
                cell.longitude,
                "Propagation ${"%.1f".format(ratio)}×",
                "Observed ${cell.observed} · Expected $expected · ${cell.confidence}\n$calls",
                colorInt(hue),
                (11 + cell.observed.coerceIn(0, 8)).coerceAtMost(17),
                ring = true,
            ))
        }
    }
    val paths = if (greyline) listOf(greylinePath(currentTime)) else emptyList()
    val areas = if (greyline) listOf(greylineArea(currentTime)) else emptyList()
    val sun = if (greyline) {
        val pos = sunPosition(currentTime)
        listOf(MapMarker(pos.first, pos.second, "Sun", "Current subsolar point", colorInt(MapYellow), 15, true))
    } else emptyList()
    NativeNeuralMap(cellMarkers + sun, paths, areas, LatLng(20.0, 0.0), 1.35, NeuralBasemap.SATELLITE,
        "PROPAGATION SIGNALS", "Interactive world propagation signals and grey-line map", modifier)
}

@Composable
internal fun DxSatelliteMap(rows: List<SatellitePosition>, stationGrid: String, selectedNorad: Int?, modifier: Modifier) {
    val qth = maidenheadCenter(stationGrid)
    val footprintSatellite = selectedNorad?.let { norad -> rows.firstOrNull { it.norad == norad } }
        ?: rows.firstOrNull(SatellitePosition::visible)
    val markers = buildList {
        qth?.let { add(qthMarker(it)) }
        rows.forEach { satellite ->
            add(MapMarker(
                satellite.latitude, satellite.longitude, satellite.name,
                "Az ${satellite.azimuth.roundToInt()}° · El ${satellite.elevation.roundToInt()}° · ${satellite.altitudeKm.roundToInt()} km",
                colorInt(if (satellite.visible) MapGreen else MapCyan), if (satellite.visible) 16 else 13,
                satellite.norad == footprintSatellite?.norad,
            ))
        }
    }
    val footprintCircle = footprintSatellite?.let { satellite ->
        geodesicCircle(satellite.latitude, satellite.longitude, satellite.footprintKm)
    }.orEmpty()
    val footprintPaths = footprintSatellite?.let {
        splitAtDateline(footprintCircle)
            .map { MapPath(it, colorInt(MapGreen.copy(alpha = .75f)), 1.7f) }
    }.orEmpty()
    val footprintAreas = splitPolygonAtDateline(footprintCircle).map { polygon ->
        MapArea(polygon, Color.rgb(12, 58, 39), colorInt(MapGreen.copy(alpha = .75f)))
    }
    val footprintName = footprintSatellite?.name?.let { " · $it FOOTPRINT" }.orEmpty()
    NativeNeuralMap(markers, footprintPaths, footprintAreas, LatLng(20.0, 0.0), 1.35, NeuralBasemap.SATELLITE,
        "LIVE ORBITS$footprintName", "Interactive satellite map with ${rows.size} tracked spacecraft and selected radio footprint", modifier)
}

@Composable
internal fun DxLightningMap(rows: List<LightningStrike>, stationGrid: String, modifier: Modifier) {
    val qth = maidenheadCenter(stationGrid)
    val now = Instant.now().epochSecond
    val markers = buildList {
        qth?.let { add(qthMarker(it)) }
        rows.take(200).forEach { strike ->
            val fresh = now - strike.epoch < 300
            add(MapMarker(strike.latitude, strike.longitude, "Lightning · ${strike.distanceKm} km ${strike.bearing}",
                if (fresh) "Fresh strike · under 5 minutes" else "${((now - strike.epoch) / 60).coerceAtLeast(0)} minutes ago",
                colorInt(if (fresh) MapRed else MapYellow), if (fresh) 14 else 10, fresh))
        }
    }
    val radius = qth?.let { splitAtDateline(geodesicCircle(it.latitude, it.longitude, 300.0)).map { segment -> MapPath(segment, colorInt(MapAmber.copy(alpha = .78f)), 1.4f) } }.orEmpty()
    val center = qth?.let { LatLng(it.latitude, it.longitude) }
        ?: rows.firstOrNull()?.let { LatLng(it.latitude, it.longitude) }
        ?: LatLng(20.0, 0.0)
    NativeNeuralMap(markers, radius, emptyList(), center, if (qth != null) 4.7 else 1.35, NeuralBasemap.DARK,
        "REGIONAL LIGHTNING · 300 KM", "Interactive regional lightning map with ${rows.size} strikes", modifier)
}

private fun qthMarker(point: GeoPoint) = MapMarker(point.latitude, point.longitude, "Your QTH", "Configured station location", colorInt(MapAmber), 16, true)

private val markerIcons = ConcurrentHashMap<String, Icon>()
private fun mapMarkerIcon(context: android.content.Context, color: Int, sizeDp: Int, ring: Boolean): Icon {
    val density = context.resources.displayMetrics.density
    val key = "$color:$sizeDp:$ring:${(density * 10).roundToInt()}"
    return markerIcons.getOrPut(key) {
        val diameter = ((sizeDp + if (ring) 12 else 8) * density).roundToInt().coerceAtLeast(24)
        val bitmap = Bitmap.createBitmap(diameter, diameter, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val center = diameter / 2f
        if (ring) {
            paint.style = Paint.Style.FILL
            paint.color = withAlpha(color, 42)
            canvas.drawCircle(center, center, center - 1, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2f * density
            paint.color = withAlpha(color, 190)
            canvas.drawCircle(center, center, center - 2f * density, paint)
        }
        paint.style = Paint.Style.FILL
        paint.color = color
        canvas.drawCircle(center, center, sizeDp * density / 2f, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.5f * density
        paint.color = Color.WHITE
        canvas.drawCircle(center, center, sizeDp * density / 2f, paint)
        IconFactory.getInstance(context).fromBitmap(bitmap)
    }
}

private fun colorInt(color: ComposeColor): Int = Color.argb(
    (color.alpha * 255).roundToInt(), (color.red * 255).roundToInt(), (color.green * 255).roundToInt(), (color.blue * 255).roundToInt()
)

private fun withAlpha(color: Int, alpha: Int): Int = Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))

private fun cellPolygon(latitude: Double, longitude: Double): List<LatLng> {
    val south = (latitude - 15.0).coerceAtLeast(-85.0)
    val north = (latitude + 15.0).coerceAtMost(85.0)
    val west = (longitude - 15.0).coerceAtLeast(-180.0)
    val east = (longitude + 15.0).coerceAtMost(180.0)
    return listOf(LatLng(south, west), LatLng(south, east), LatLng(north, east), LatLng(north, west))
}

internal fun geodesicCircle(latitude: Double, longitude: Double, radiusKm: Double, steps: Int = 72): List<LatLng> {
    val angular = radiusKm / 6371.0088
    val lat1 = Math.toRadians(latitude)
    val lon1 = Math.toRadians(longitude)
    return (0..steps).map { step ->
        val bearing = 2.0 * PI * step / steps
        val lat2 = asin(sin(lat1) * cos(angular) + cos(lat1) * sin(angular) * cos(bearing))
        val lon2 = lon1 + atan2(sin(bearing) * sin(angular) * cos(lat1), cos(angular) - sin(lat1) * sin(lat2))
        LatLng(Math.toDegrees(lat2), ((Math.toDegrees(lon2) + 540.0) % 360.0) - 180.0)
    }
}

internal fun splitAtDateline(points: List<LatLng>): List<List<LatLng>> {
    if (points.isEmpty()) return emptyList()
    val segments = mutableListOf(mutableListOf(points.first()))
    points.zipWithNext().forEach { (previous, next) ->
        if (kotlin.math.abs(next.longitude - previous.longitude) > 180.0) segments += mutableListOf(next)
        else segments.last() += next
    }
    return segments.filter { it.size >= 2 }
}

internal fun splitPolygonAtDateline(points: List<LatLng>): List<List<LatLng>> {
    if (points.size < 3) return emptyList()
    val ring = if (points.first() == points.last()) points.dropLast(1) else points
    if (ring.size < 3) return emptyList()

    val unwrapped = ArrayList<LatLng>(ring.size)
    var previousLongitude = ring.first().longitude
    unwrapped += LatLng(ring.first().latitude, previousLongitude)
    ring.drop(1).forEach { point ->
        var longitude = point.longitude
        while (longitude - previousLongitude > 180.0) longitude -= 360.0
        while (longitude - previousLongitude < -180.0) longitude += 360.0
        unwrapped += LatLng(point.latitude, longitude)
        previousLongitude = longitude
    }

    val firstWindow = kotlin.math.floor((unwrapped.minOf { it.longitude } + 180.0) / 360.0).toInt()
    val lastWindow = kotlin.math.floor((unwrapped.maxOf { it.longitude } + 180.0) / 360.0).toInt()
    return (firstWindow..lastWindow).mapNotNull { window ->
        val west = -180.0 + window * 360.0
        val east = 180.0 + window * 360.0
        val clipped = clipPolygonLongitude(clipPolygonLongitude(unwrapped, west, keepGreater = true), east, keepGreater = false)
        if (clipped.size < 3) null else clipped.map { point ->
            LatLng(point.latitude, (point.longitude - window * 360.0).coerceIn(-180.0, 180.0))
        }
    }
}

private fun clipPolygonLongitude(points: List<LatLng>, boundary: Double, keepGreater: Boolean): List<LatLng> {
    if (points.isEmpty()) return emptyList()
    fun inside(point: LatLng) = if (keepGreater) point.longitude >= boundary else point.longitude <= boundary
    fun intersection(from: LatLng, to: LatLng): LatLng {
        val span = to.longitude - from.longitude
        val fraction = if (kotlin.math.abs(span) < 1e-9) 0.0 else (boundary - from.longitude) / span
        return LatLng(from.latitude + (to.latitude - from.latitude) * fraction, boundary)
    }
    val result = mutableListOf<LatLng>()
    var previous = points.last()
    var previousInside = inside(previous)
    points.forEach { current ->
        val currentInside = inside(current)
        if (currentInside != previousInside) result += intersection(previous, current)
        if (currentInside) result += current
        previous = current
        previousInside = currentInside
    }
    return result
}

private fun sunPosition(instant: Instant): Pair<Double, Double> {
    val utc = instant.atZone(java.time.ZoneOffset.UTC)
    val jd = instant.toEpochMilli() / 86_400_000.0 + 2_440_587.5
    val n = jd - 2_451_545.0
    val meanLongitude = ((280.460 + 0.9856474 * n) % 360.0 + 360.0) % 360.0
    val anomaly = Math.toRadians(((357.528 + 0.9856003 * n) % 360.0 + 360.0) % 360.0)
    val eclipticLongitude = meanLongitude + 1.915 * sin(anomaly) + 0.020 * sin(2 * anomaly)
    val obliquity = 23.439 - 0.0000004 * n
    val declination = Math.toDegrees(asin(sin(Math.toRadians(obliquity)) * sin(Math.toRadians(eclipticLongitude))))
    val rightAscension = Math.toDegrees(atan2(cos(Math.toRadians(obliquity)) * sin(Math.toRadians(eclipticLongitude)), cos(Math.toRadians(eclipticLongitude))))
    val equationOfTime = meanLongitude - rightAscension
    val utcHours = utc.hour + utc.minute / 60.0 + utc.second / 3600.0
    val longitude = ((-(utcHours - 12.0) * 15.0 + equationOfTime) % 360.0 + 540.0) % 360.0 - 180.0
    return declination to longitude
}

private fun terminatorPoints(instant: Instant): List<LatLng> {
    val (declination, sunLongitude) = sunPosition(instant)
    if (kotlin.math.abs(declination) < .001) return emptyList()
    return (-180..180 step 2).map { longitude ->
        val hourAngle = Math.toRadians(longitude - sunLongitude)
        val latitude = Math.toDegrees(kotlin.math.atan(-cos(hourAngle) / kotlin.math.tan(Math.toRadians(declination))))
        LatLng(latitude, longitude.toDouble())
    }
}

private fun greylineArea(instant: Instant): MapArea {
    val terminator = terminatorPoints(instant)
    val pole = if (sunPosition(instant).first >= 0) -85.0 else 85.0
    return MapArea(terminator + listOf(LatLng(pole, 180.0), LatLng(pole, -180.0)), Color.argb(92, 0, 2, 36), Color.TRANSPARENT)
}

private fun greylinePath(instant: Instant) = MapPath(terminatorPoints(instant), colorInt(MapYellow.copy(alpha = .9f)), 1.5f)

private fun satelliteStyleJson() = """
{
  "version": 8,
  "name": "RigWeave Satellite Operations",
  "sources": {
    "esri": {"type":"raster","tiles":["https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}"],"tileSize":256,"maxzoom":18,"attribution":"Tiles © Esri"},
    "labels": {"type":"raster","tiles":["https://server.arcgisonline.com/ArcGIS/rest/services/Reference/World_Boundaries_and_Places/MapServer/tile/{z}/{y}/{x}"],"tileSize":256,"maxzoom":18,"attribution":"Reference labels © Esri"}
  },
  "layers": [
    {"id":"background","type":"background","paint":{"background-color":"#06151c"}},
    {"id":"imagery","type":"raster","source":"esri","paint":{"raster-opacity":0.82,"raster-saturation":-0.32,"raster-contrast":0.16,"raster-brightness-max":0.72}},
    {"id":"labels","type":"raster","source":"labels","paint":{"raster-opacity":0.72}}
  ]
}
""".trimIndent()

private fun darkStyleJson() = """
{
  "version": 8,
  "name": "RigWeave Dark Weather",
  "sources": {
    "carto": {"type":"raster","tiles":["https://a.basemaps.cartocdn.com/dark_nolabels/{z}/{x}/{y}.png"],"tileSize":256,"maxzoom":20,"attribution":"© CARTO © OpenStreetMap contributors"},
    "labels": {"type":"raster","tiles":["https://a.basemaps.cartocdn.com/dark_only_labels/{z}/{x}/{y}.png"],"tileSize":256,"maxzoom":20,"attribution":"© CARTO © OpenStreetMap contributors"}
  },
  "layers": [
    {"id":"background","type":"background","paint":{"background-color":"#06151c"}},
    {"id":"carto","type":"raster","source":"carto","paint":{"raster-opacity":1.0,"raster-contrast":0.12}},
    {"id":"labels","type":"raster","source":"labels","paint":{"raster-opacity":0.9}}
  ]
}
""".trimIndent()
