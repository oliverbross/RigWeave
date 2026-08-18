package app.rigweave.mobile

/*
THESIS: A native ham-radio operations clock: one glance from station state to workable RF activity.
OWN-WORLD: RigWeave Flightline instrumentation, with OpenHamClock's pinned dashboard density and map-first hierarchy.
STORY: Identify the station and time, read propagation, inspect live paths, then act through the existing DX or Portable workspaces.
FIRST VIEWPORT: UTC/local clocks, CAT truth, solar indices, world activity, live DX, PSK reception, portable activity, and next passes.
FORM: Wide screens use a fixed three-column console; compact screens preserve the same priority in a vertical operating stack.
*/

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Hiking
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private val HcBg = Color(0xFF081015)
private val HcPanel = Color(0xFF111C22)
private val HcRaised = Color(0xFF182831)
private val HcLine = Color(0xFF32434C)
private val HcInk = Color(0xFFEAF0ED)
private val HcMuted = Color(0xFF91A1A9)
private val HcAmber = Color(0xFFF0AD35)
private val HcGreen = Color(0xFF43D17C)
private val HcCyan = Color(0xFF42C7D8)
private val HcRed = Color(0xFFE65B54)
private val HcYellow = Color(0xFFF3D054)
private val HcClock = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.US)
private val HcDate = DateTimeFormatter.ofPattern("EEE dd MMM", Locale.US)

@Composable
internal fun HamClockHomeScreen(
    radio: RadioState,
    app: AppController,
    features: FeatureController,
    neuralDx: NeuralDxController,
    portable: PortableController,
    wavelog: WavelogController,
    cty: CtyController,
    send: (String) -> Unit,
    openDx: () -> Unit,
    openPortable: () -> Unit,
    openProgress: () -> Unit,
) {
    val stationId = wavelog.stationId.takeIf { wavelog.logMode == LogMode.WAVELOG }
    val stationGrid = wavelog.selectedStation?.grid?.ifBlank { null } ?: app.stationGrid
    val stationCall = wavelog.selectedStation?.callsign?.ifBlank { null }
        ?: app.stationCallsign.ifBlank { features.clusterCallsign }
    var now by remember { mutableStateOf(Instant.now()) }
    var showPaths by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        while (true) { now = Instant.now(); delay(1_000) }
    }
    LaunchedEffect(features.liveSpots, stationId, cty.dataRevision) {
        neuralDx.ingest(features.liveSpots, stationId, cty, stationCall)
    }
    LaunchedEffect(stationGrid, stationCall, stationId) {
        while (true) {
            val epoch = Instant.now().epochSecond
            portable.markForegroundAge(epoch)
            if (neuralDx.lastRefreshEpoch == 0L || epoch - neuralDx.lastRefreshEpoch > 15 * 60) {
                neuralDx.refresh(stationCall, stationGrid, stationId, features.liveSpots)
            }
            if (!features.solar.valid || epoch - features.solar.observedEpoch > 60 * 60) features.refreshSolar()
            val potaAge = portable.providerStatus(PortableProgram.POTA).fetchedAt
            val wwffAge = portable.providerStatus(PortableProgram.WWFF).fetchedAt
            if (potaAge == 0L || wwffAge == 0L || epoch - minOf(potaAge, wwffAge) > 5 * 60) portable.refreshAll()
            delay(60_000)
        }
    }
    LaunchedEffect(portable.pota.spots.size, portable.wwffSpots.size, portable.sotaSpots.size,
        radio.frequencyHz, stationGrid, portable.lastQsoRevision) {
        portable.refreshOpportunities(now.epochSecond, radio.frequencyHz, stationGrid)
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(HcBg).testTag("openhamclock-home")) {
        val wide = maxWidth >= 960.dp && maxHeight >= 700.dp
        if (wide) {
            Column(Modifier.fillMaxSize().padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                HamClockHeader(now, stationCall, stationGrid, radio, features, neuralDx) {
                    neuralDx.refresh(stationCall, stationGrid, stationId, features.liveSpots, true)
                    features.refreshSolar(); portable.refreshAll()
                }
                Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.widthIn(min = 208.dp, max = 250.dp).weight(.22f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        StationPanel(stationCall, stationGrid, radio, wavelog, app, send, Modifier.weight(.86f))
                        WeatherPanel(neuralDx.weather, Modifier.weight(1.02f))
                        BandConditionsPanel(neuralDx.bandActivity, openProgress, Modifier.weight(1.12f))
                    }
                    Column(Modifier.weight(.56f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        MapPanel(features.liveSpots, stationGrid, cty, showPaths, { showPaths = it }, openDx, Modifier.weight(1f))
                        PropagationStrip(neuralDx, Modifier.heightIn(min = 98.dp, max = 118.dp))
                    }
                    Column(Modifier.widthIn(min = 250.dp, max = 310.dp).weight(.25f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        DxPanel(features.liveSpots, features.clusterStatus, openDx, Modifier.weight(1.15f))
                        SignalPanel(neuralDx.mySignal, openDx, Modifier.weight(.9f))
                        PortablePanel(portable, openPortable, Modifier.weight(.92f))
                        SatellitePanel(neuralDx.passes, neuralDx.status, neuralDx.lastRefreshEpoch, openDx, Modifier.weight(.86f))
                    }
                }
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item { HamClockHeader(now, stationCall, stationGrid, radio, features, neuralDx) {
                    neuralDx.refresh(stationCall, stationGrid, stationId, features.liveSpots, true)
                    features.refreshSolar(); portable.refreshAll()
                } }
                item { MapPanel(features.liveSpots, stationGrid, cty, showPaths, { showPaths = it }, openDx, Modifier.height(if (maxWidth < 500.dp) 290.dp else 390.dp)) }
                if (maxWidth < 600.dp) {
                    item { StationPanel(stationCall, stationGrid, radio, wavelog, app, send, Modifier.fillMaxWidth()) }
                    item { WeatherPanel(neuralDx.weather, Modifier.fillMaxWidth()) }
                } else item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StationPanel(stationCall, stationGrid, radio, wavelog, app, send, Modifier.weight(1f))
                    WeatherPanel(neuralDx.weather, Modifier.weight(1f))
                } }
                item { DxPanel(features.liveSpots, features.clusterStatus, openDx, Modifier.fillMaxWidth()) }
                item { SignalPanel(neuralDx.mySignal, openDx, Modifier.fillMaxWidth()) }
                item { PortablePanel(portable, openPortable, Modifier.fillMaxWidth()) }
                item { BandConditionsPanel(neuralDx.bandActivity, openProgress, Modifier.fillMaxWidth()) }
                item { SatellitePanel(neuralDx.passes, neuralDx.status, neuralDx.lastRefreshEpoch, openDx, Modifier.fillMaxWidth()) }
                item { PropagationStrip(neuralDx, Modifier.fillMaxWidth()) }
            }
        }
    }
}

@Composable
private fun HamClockHeader(now: Instant, call: String, grid: String, radio: RadioState,
    features: FeatureController, neuralDx: NeuralDxController, refresh: () -> Unit) {
    val utc = now.atZone(ZoneOffset.UTC)
    val local = now.atZone(ZoneId.systemDefault())
    Surface(color = HcPanel, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth().border(1.dp, HcLine, RoundedCornerShape(8.dp))) {
        BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
            if (maxWidth < 760.dp) Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HeaderIdentity(call, grid, Modifier.weight(1f))
                    StatusPill(if (radio.connected) "CAT LIVE" else "CAT OFFLINE", radio.connected)
                    SyncButton(neuralDx.refreshing, refresh)
                }
                HeaderReadouts(utc.format(HcClock), utc.format(HcDate), local.format(HcClock), local.format(HcDate), features)
            } else Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                HeaderIdentity(call, grid, Modifier.weight(1f))
                ClockReadout("UTC", utc.format(HcClock), utc.format(HcDate))
                ClockReadout("LOCAL", local.format(HcClock), local.format(HcDate))
                SolarMetrics(features)
                StatusPill(if (radio.connected) "CAT LIVE" else "CAT OFFLINE", radio.connected)
                SyncButton(neuralDx.refreshing, refresh)
            }
        }
    }
}

@Composable private fun HeaderIdentity(call: String, grid: String, modifier: Modifier) = Column(modifier) {
    Text("RIGWEAVE · OPEN HAM CLOCK", color = HcAmber, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp, fontSize = 15.sp,
        maxLines = 1, overflow = TextOverflow.Ellipsis)
    Text("${call.ifBlank { "CALL NOT SET" }} · ${grid.ifBlank { "GRID NOT SET" }}", color = HcInk,
        fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

@Composable private fun HeaderReadouts(utc: String, utcDate: String, local: String, localDate: String, features: FeatureController) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        ClockReadout("UTC", utc, utcDate); ClockReadout("LOCAL", local, localDate); SolarMetrics(features)
    }
}

@Composable private fun SolarMetrics(features: FeatureController) {
    Metric("SFI", features.solar.flux.takeIf { features.solar.valid }?.roundToInt()?.toString() ?: "—", HcAmber)
    Metric("A", features.solar.aIndex.takeIf { features.solar.valid }?.roundToInt()?.toString() ?: "—",
        if (features.solar.aIndex >= 30) HcRed else HcYellow)
    Metric("KP", features.solar.kpIndex.takeIf { features.solar.valid }?.let { "%.1f".format(Locale.US, it) } ?: "—",
        if (features.solar.kpIndex >= 5) HcRed else HcGreen)
}

@Composable private fun SyncButton(refreshing: Boolean, refresh: () -> Unit) {
    Button(refresh, enabled = !refreshing, modifier = Modifier.heightIn(min = 48.dp)) {
        Icon(Icons.Outlined.Refresh, null, Modifier.size(18.dp)); Spacer(Modifier.width(5.dp)); Text(if (refreshing) "…" else "SYNC")
    }
}

@Composable private fun ClockReadout(label: String, time: String, date: String) = Column(horizontalAlignment = Alignment.End) {
    Text("$label  $time", color = HcInk, fontFamily = FontFamily.Monospace, fontSize = 20.sp, fontWeight = FontWeight.Black)
    Text(date.uppercase(), color = HcMuted, fontSize = 10.sp)
}

@Composable private fun Metric(label: String, value: String, color: Color) = Column(horizontalAlignment = Alignment.CenterHorizontally) {
    Text(label, color = HcMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
    Text(value, color = color, fontFamily = FontFamily.Monospace, fontSize = 17.sp, fontWeight = FontWeight.Black)
}

@Composable private fun StatusPill(text: String, good: Boolean) {
    val color = if (good) HcGreen else HcRed
    Surface(color = color.copy(alpha = .14f), shape = RoundedCornerShape(4.dp)) {
        Text(text, color = color, fontSize = 10.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp))
    }
}

@Composable private fun Module(title: String, subtitle: String = "", onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(color = HcPanel, shape = RoundedCornerShape(7.dp), modifier = modifier.border(1.dp, HcLine, RoundedCornerShape(7.dp))) {
        Column(Modifier.fillMaxSize().padding(9.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth().heightIn(min = 32.dp).then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
                verticalAlignment = Alignment.CenterVertically) {
                Text(title.uppercase(), color = HcAmber, fontWeight = FontWeight.Black, fontSize = 12.sp, modifier = Modifier.weight(1f))
                if (subtitle.isNotBlank()) Text(subtitle, color = HcMuted, fontFamily = FontFamily.Monospace, fontSize = 10.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            HorizontalDivider(color = HcLine)
            content()
        }
    }
}

@Composable private fun MapPanel(rows: List<AndroidDXSpot>, stationGrid: String, cty: CtyController, showPaths: Boolean,
    setPaths: (Boolean) -> Unit, openDx: () -> Unit, modifier: Modifier) {
    Module("World activity", "${rows.size} SPOTS", openDx, modifier) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            FilterChip(showPaths, { setPaths(!showPaths) }, { Text("REPORTING PATHS") })
            Text("AMBER REPORTER  →  CYAN DX", color = HcMuted, fontSize = 10.sp, modifier = Modifier.padding(top = 12.dp))
        }
        Box(Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(4.dp)).background(HcRaised)) {
            DxWorldCanvas(rows, stationGrid, false, cty, showPaths, Modifier.fillMaxSize())
            if (rows.isEmpty()) Text("Waiting for live DX cluster spots", color = HcMuted, modifier = Modifier.align(Alignment.Center))
        }
    }
}

@Composable private fun StationPanel(call: String, grid: String, radio: RadioState, wavelog: WavelogController,
    app: AppController, send: (String) -> Unit, modifier: Modifier) {
    Module("DE station", if (wavelog.logMode == LogMode.WAVELOG) "WAVELOG" else "LOCAL", modifier = modifier) {
        KeyValue("CALL", call.ifBlank { "NOT SET" }, HcCyan)
        KeyValue("GRID", grid.ifBlank { "NOT SET" }, HcInk)
        KeyValue("RADIO", radio.model, if (radio.connected) HcGreen else HcMuted)
        KeyValue("VFO A", if (radio.connected && radio.frequencyHz > 0) "%.3f MHz".format(Locale.US, radio.frequencyHz / 1_000_000.0) else "NO LIVE STATE", if (radio.connected) HcAmber else HcMuted)
        KeyValue("MODE", if (radio.connected) radio.mode else "—", HcInk)
        KeyValue("POWER", if (radio.connected && radio.powerW > 0) "${radio.powerW} W" else "—", HcInk)
        KeyValue("TX SAFETY", if (app.transmitArmed) "ARMED" else "SAFE / RX", if (app.transmitArmed) HcRed else HcGreen)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            FieldProfile.entries.forEach { profile ->
                FilterChip(app.fieldProfile == profile, { app.setProfile(profile) }, { Text(profile.name, fontSize = 9.sp) })
            }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            app.favoriteBands.forEach { value -> OutlinedButton({
                value.toDoubleOrNull()?.let { send("FA%011d;".format((it * 1_000_000).toLong())) }
            }, enabled = radio.connected, modifier = Modifier.heightIn(min = 48.dp)) { Text(value, fontFamily = FontFamily.Monospace, fontSize = 10.sp) } }
        }
    }
}

@Composable private fun WeatherPanel(weather: NeuralWeather, modifier: Modifier) {
    Module("Local weather", weather.source, modifier = modifier) {
        if (!weather.available) EmptyLine(weather.error.ifBlank { "Weather awaiting station grid and internet" }) else {
            KeyValue("TEMP", weather.temperatureC?.let { "%.1f °C".format(Locale.US, it) } ?: "—", HcCyan)
            KeyValue("HUMIDITY", weather.humidityPercent?.let { "$it%" } ?: "—", HcInk)
            KeyValue("PRESSURE", weather.pressureHpa?.let { "%.0f hPa".format(Locale.US, it) } ?: "—", HcInk)
            KeyValue("WIND", weather.windKmh?.let { "%.0f km/h".format(Locale.US, it) } ?: "—", HcInk)
            KeyValue("TROPO", weather.tropoIndex?.toString() ?: "—", when ((weather.tropoIndex ?: 0)) { in 6..Int.MAX_VALUE -> HcGreen; else -> HcMuted })
            KeyValue("DUCTING", weather.ductingRisk ?: "—", HcYellow)
        }
    }
}

@Composable private fun DxPanel(spots: List<AndroidDXSpot>, status: String, openDx: () -> Unit, modifier: Modifier) {
    Module("DX cluster", status.substringAfter("DX cluster ").uppercase(), openDx, modifier) {
        if (spots.isEmpty()) EmptyLine(status) else spots.take(6).forEach { spot ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(spot.callsign, color = if (spot.watchlisted) HcYellow else HcCyan, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
                    Text("${spot.country} · DX de ${spot.spotter}", color = HcMuted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("%.3f".format(Locale.US, spot.frequencyHz / 1_000_000.0), color = HcInk, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                    Text("${spot.band} · ${spot.mode}", color = HcMuted, fontSize = 10.sp)
                }
            }
        }
    }
}

@Composable private fun SignalPanel(signal: NeuralMySignal, openDx: () -> Unit, modifier: Modifier) {
    val reports = signal.reports
    Module("Who hears me", "PSK REPORTER", openDx, modifier) {
        if (!signal.available) EmptyLine(signal.error.ifBlank { "PSK Reporter unavailable or no station callsign set" })
        else if (reports.isEmpty()) EmptyLine("PSK Reporter returned no recent reception reports") else reports.take(5).forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                Text(row.callsign, color = HcGreen, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("${row.band} ${row.mode}", color = HcInk, fontFamily = FontFamily.Monospace)
                Spacer(Modifier.width(8.dp))
                Text(row.snr?.let { "$it dB" } ?: "—", color = HcMuted, fontFamily = FontFamily.Monospace)
            }
        }
    }
}

@Composable private fun PortablePanel(portable: PortableController, openPortable: () -> Unit, modifier: Modifier) {
    val opportunities = portable.rankedOpportunities
    val potaCount = portable.pota.spots.size
    val wwffCount = portable.wwffSpots.size
    Module("Portable activators", "POTA $potaCount · WWFF $wwffCount", openPortable, modifier) {
        val potaStatus = portable.providerStatus(PortableProgram.POTA)
        val wwffStatus = portable.providerStatus(PortableProgram.WWFF)
        if (opportunities.isEmpty()) EmptyLine(listOf(potaStatus, wwffStatus).joinToString(" · ") {
            it.error.ifBlank { "${it.kind.name.lowercase().replaceFirstChar(Char::uppercase)} · ${it.count} spots" }
        }) else opportunities.take(4).forEach { opportunity ->
            val spot = opportunity.spot
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(spot.callsign, color = HcYellow, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
                    Text(spot.references.joinToString { it.code }, color = HcMuted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text("%.3f".format(Locale.US, spot.frequencyHz / 1_000_000.0), color = HcInk, fontFamily = FontFamily.Monospace)
            }
        }
        Text("SOTA live feed requires provider approval", color = HcMuted, fontSize = 9.sp)
    }
}

@Composable private fun BandConditionsPanel(activity: Map<String, Int>, openProgress: () -> Unit, modifier: Modifier) {
    Module("Band activity", "NEURAL DX WINDOW", openProgress, modifier) {
        val bands = listOf("80m", "40m", "30m", "20m", "17m", "15m", "12m", "10m", "6m")
        bands.chunked(3).forEach { row -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            row.forEach { band ->
                val count = activity[band] ?: 0
                Surface(color = (if (count > 0) HcGreen else HcRaised).copy(alpha = if (count > 0) .18f else 1f),
                    shape = RoundedCornerShape(3.dp), modifier = Modifier.weight(1f)) {
                    Column(Modifier.padding(vertical = 5.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(band, color = if (count > 0) HcGreen else HcMuted, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                        Text(count.toString(), color = HcInk, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                    }
                }
            }
        } }
    }
}

@Composable private fun SatellitePanel(passes: List<SatellitePass>, sourceStatus: String, refreshedAt: Long, openDx: () -> Unit, modifier: Modifier) {
    Module("Satellite passes", "NEXT ${passes.size}", openDx, modifier) {
        if (passes.isEmpty()) EmptyLine(if (sourceStatus.contains("Satellites unavailable")) "Satellites unavailable · cached data retained"
            else if (refreshedAt == 0L) "Satellite source has not refreshed yet" else "No upcoming passes · refreshed ${ageLabel(refreshedAt)}")
        else passes.take(4).forEach { pass ->
            Row(Modifier.fillMaxWidth()) {
                Text(pass.name, color = HcCyan, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1)
                Text(Instant.ofEpochSecond(pass.aosEpoch).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm")),
                    color = HcInk, fontFamily = FontFamily.Monospace)
                Spacer(Modifier.width(8.dp)); Text("${pass.maxElevation.roundToInt()}°", color = HcGreen, fontFamily = FontFamily.Monospace)
            }
        }
    }
}

@Composable private fun PropagationStrip(neuralDx: NeuralDxController, modifier: Modifier) {
    Module("Propagation intelligence", neuralDx.status, modifier = modifier) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Metric("PREDICTIONS", neuralDx.predictions.size.toString(), HcCyan)
            Metric("WSPR HF", neuralDx.wspr.hf.size.toString(), HcGreen)
            Metric("WSPR VHF", neuralDx.wspr.vhf.size.toString(), HcGreen)
            Metric("WORLD CELLS", neuralDx.world.size.toString(), HcYellow)
            val next = neuralDx.predictions.maxByOrNull { it.probability }
            Column(Modifier.weight(1f)) {
                Text(next?.let { "${it.callsign} · ${it.band} ${it.mode} · ${it.probability}%" } ?: "Learning from live RF and log history",
                    color = HcInk, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(next?.reason ?: "Predictions appear only when measured evidence is available", color = HcMuted, fontSize = 10.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable private fun KeyValue(key: String, value: String, color: Color) = Row(Modifier.fillMaxWidth()) {
    Text(key, color = HcMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
    Text(value, color = color, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

@Composable private fun EmptyLine(text: String) {
    Box(Modifier.fillMaxWidth().background(HcRaised, RoundedCornerShape(4.dp)).padding(10.dp)) {
        Text(text, color = HcMuted, fontSize = 11.sp)
    }
}

private fun ageLabel(epoch: Long): String {
    val seconds = (Instant.now().epochSecond - epoch).coerceAtLeast(0)
    return when { seconds < 60 -> "NOW"; seconds < 3600 -> "${seconds / 60}m AGO"; else -> "${seconds / 3600}h AGO" }
}
