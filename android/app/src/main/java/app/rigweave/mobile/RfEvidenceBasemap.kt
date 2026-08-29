// SPDX-License-Identifier: GPL-3.0-only
package app.rigweave.mobile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import kotlinx.coroutines.delay
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleOpacity
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineOpacity
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

private const val RF_OBSERVED_SOURCE = "rf-observed-source"
private const val RF_HISTORICAL_SOURCE = "rf-historical-source"
private const val RF_OUTLOOK_SOURCE = "rf-outlook-source"
private const val RF_ENDPOINT_SOURCE = "rf-endpoint-source"
private const val RF_STATION_SOURCE = "rf-station-source"

private data class RfMapFeatures(
    val observed: FeatureCollection,
    val historical: FeatureCollection,
    val outlook: FeatureCollection,
    val endpoints: FeatureCollection,
    val station: FeatureCollection,
)

@Composable
internal fun RfEvidenceBasemap(
    rows: List<RfObservation>,
    longPath: Boolean,
    globe: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val mapView = remember {
        MapLibre.getInstance(context.applicationContext)
        MapView(context).apply { onCreate(null) }
    }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var styleReady by remember { mutableStateOf(false) }
    var mapMessage by remember { mutableStateOf("LOADING MAP…") }
    val features = remember(rows, longPath) { buildRfMapFeatures(rows.takeLast(4_096), longPath) }

    DisposableEffect(mapView, lifecycle) {
        var started = false
        var resumed = false
        var destroyed = false
        var disposed = false
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
            ready.setMinZoomPreference(.65)
            ready.setMaxZoomPreference(10.0)
            ready.cameraPosition = CameraPosition.Builder()
                .target(LatLng(if (globe) 12.0 else -5.0, if (globe) 12.0 else 30.0))
                .zoom(if (globe) .85 else 1.15)
                .build()
            ready.setStyle(Style.Builder().fromUri(OPEN_FREE_MAP_DARK_STYLE)) { style ->
                if (disposed) return@setStyle
                installRfMapLayers(style)
                updateRfMapSources(style, features)
                styleReady = true
                mapMessage = ""
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

    LaunchedEffect(map, styleReady, features) {
        val style = map?.style ?: return@LaunchedEffect
        if (styleReady) updateRfMapSources(style, features)
    }
    LaunchedEffect(map, styleReady) {
        if (map == null || styleReady) return@LaunchedEffect
        delay(8_000)
        if (!styleReady) mapMessage = "MAP UNAVAILABLE · check network and try this view again"
    }

    Box(modifier.clip(RoundedCornerShape(8.dp)).background(Color(0xFF07151B))
        .border(1.dp, Color(0xFF32434C), RoundedCornerShape(8.dp))
        .semantics { contentDescription = if (globe) "MapLibre global RF evidence view" else "MapLibre RF evidence map" }) {
        AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())
        Surface(color = Color(0xD9111C22), shape = RoundedCornerShape(5.dp),
            modifier = Modifier.align(Alignment.TopStart).padding(8.dp)) {
            Text(if (globe) "GLOBAL · MAPLIBRE · ${rows.size} RF observations" else "RF MAP · MAPLIBRE · ${rows.size} observations",
                color = Color(0xFFEAF0ED), fontSize = 10.sp, modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp))
        }
        if (mapMessage.isNotBlank()) {
            Surface(color = Color(0xE6111C22), shape = RoundedCornerShape(7.dp), modifier = Modifier.align(Alignment.Center)) {
                Text(mapMessage, color = Color(0xFFE9A72B), fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
            }
        }
    }
}

private fun installRfMapLayers(style: Style) {
    listOf(RF_OBSERVED_SOURCE, RF_HISTORICAL_SOURCE, RF_OUTLOOK_SOURCE, RF_ENDPOINT_SOURCE, RF_STATION_SOURCE)
        .forEach { source -> if (style.getSource(source) == null) style.addSource(GeoJsonSource(source, emptyRfFeatures())) }
    style.addLayer(LineLayer("rf-observed-lines", RF_OBSERVED_SOURCE).withProperties(
        lineColor("#42C77B"), lineOpacity(.72f), lineWidth(2.2f)))
    style.addLayer(LineLayer("rf-historical-lines", RF_HISTORICAL_SOURCE).withProperties(
        lineColor("#A5ADB2"), lineOpacity(.48f), lineWidth(1.5f)))
    style.addLayer(LineLayer("rf-outlook-lines", RF_OUTLOOK_SOURCE).withProperties(
        lineColor("#F4C94E"), lineOpacity(.62f), lineWidth(1.8f)))
    style.addLayer(CircleLayer("rf-endpoints", RF_ENDPOINT_SOURCE).withProperties(
        circleColor("#42C77B"), circleRadius(4f), circleOpacity(.9f),
        circleStrokeColor("#07151B"), circleStrokeWidth(1.2f)))
    style.addLayer(CircleLayer("rf-station", RF_STATION_SOURCE).withProperties(
        circleColor("#E9A72B"), circleRadius(6f), circleOpacity(1f),
        circleStrokeColor("#F4F0E7"), circleStrokeWidth(1.5f)))
}

private fun updateRfMapSources(style: Style, features: RfMapFeatures) {
    (style.getSource(RF_OBSERVED_SOURCE) as? GeoJsonSource)?.setGeoJson(features.observed)
    (style.getSource(RF_HISTORICAL_SOURCE) as? GeoJsonSource)?.setGeoJson(features.historical)
    (style.getSource(RF_OUTLOOK_SOURCE) as? GeoJsonSource)?.setGeoJson(features.outlook)
    (style.getSource(RF_ENDPOINT_SOURCE) as? GeoJsonSource)?.setGeoJson(features.endpoints)
    (style.getSource(RF_STATION_SOURCE) as? GeoJsonSource)?.setGeoJson(features.station)
}

private fun buildRfMapFeatures(rows: List<RfObservation>, longPath: Boolean): RfMapFeatures {
    val lines = mutableMapOf(
        RfEvidenceClass.OBSERVED to mutableListOf<Feature>(),
        RfEvidenceClass.HISTORICAL to mutableListOf(),
        RfEvidenceClass.OUTLOOK to mutableListOf(),
    )
    val endpoints = mutableListOf<Feature>()
    rows.forEach { row ->
        splitRfMapPath(greatCircle(row.transmitterLatitude, row.transmitterLongitude,
            row.receiverLatitude, row.receiverLongitude, longPath, 72)).forEachIndexed { index, segment ->
            if (segment.size >= 2) {
                val feature = Feature.fromGeometry(LineString.fromLngLats(segment.map { Point.fromLngLat(it.longitude, it.latitude) }))
                feature.addStringProperty("id", "${row.id}-$index")
                feature.addStringProperty("callsign", row.callsign)
                lines.getValue(row.evidence).add(feature)
            }
        }
        endpoints += Feature.fromGeometry(Point.fromLngLat(row.transmitterLongitude, row.transmitterLatitude)).apply {
            addStringProperty("id", row.id)
            addStringProperty("callsign", row.callsign)
        }
    }
    val station = rows.lastOrNull()?.let { row ->
        listOf(Feature.fromGeometry(Point.fromLngLat(row.receiverLongitude, row.receiverLatitude)))
    }.orEmpty()
    return RfMapFeatures(
        FeatureCollection.fromFeatures(lines.getValue(RfEvidenceClass.OBSERVED)),
        FeatureCollection.fromFeatures(lines.getValue(RfEvidenceClass.HISTORICAL)),
        FeatureCollection.fromFeatures(lines.getValue(RfEvidenceClass.OUTLOOK)),
        FeatureCollection.fromFeatures(endpoints),
        FeatureCollection.fromFeatures(station),
    )
}

internal fun splitRfMapPath(points: List<GeoArcPoint>): List<List<GeoArcPoint>> {
    if (points.isEmpty()) return emptyList()
    val segments = mutableListOf<MutableList<GeoArcPoint>>(mutableListOf(points.first()))
    points.drop(1).forEach { point ->
        val active = segments.last()
        if (kotlin.math.abs(point.longitude - active.last().longitude) > 180.0) segments += mutableListOf(point)
        else active += point
    }
    return segments.filter { it.size >= 2 }
}

private fun emptyRfFeatures(): FeatureCollection = FeatureCollection.fromFeatures(emptyList())
