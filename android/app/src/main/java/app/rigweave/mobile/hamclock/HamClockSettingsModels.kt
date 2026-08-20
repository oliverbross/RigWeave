package app.rigweave.mobile.hamclock

/** Stable panel identifiers. New panels can use their own stable string without a schema migration. */
object HamClockPanelId {
    const val STATION = "station"
    const val WEATHER = "weather"
    const val BAND_ACTIVITY = "band_activity"
    const val PSK_REPORTER = "psk_reporter"
    const val DX_EXPEDITIONS = "dxpeditions"
    const val DX_CLUSTER = "dx_cluster"
    const val SOLAR = "solar"
    const val DX_TARGET = "dx_target"
    const val VOACAP = "voacap"
    const val PORTABLE = "portable"
    const val SATELLITES = "satellites"
    const val CONTESTS = "contests"
    const val MAP = "map"
    const val ANALOG_CLOCK = "analog_clock"
}

object HamClockMapLayerId {
    const val DE_STATION = "de_station"
    const val DX_SPOTS = "dx_spots"
    const val DX_PATHS = "dx_paths"
    const val SELECTED_TARGET = "selected_target"
    const val PSK_REPORTER = "psk_reporter"
    const val PORTABLE = "portable"
    const val SATELLITES = "satellites"
    const val GRAYLINE = "grayline"
    const val SUN = "sun"
    const val MOON = "moon"
    const val GRID = "grid"
    const val AURORA = "aurora"
    const val LOGGED_QSOS = "logged_qsos"
    const val LIGHTNING = "lightning"
    const val RBN = "rbn"
    const val WSPR_EXPANDED = "wspr_expanded"
    const val IBP = "ibp"
    const val MUF = "muf"
    const val PROPAGATION_HEATMAP = "propagation_heatmap"
    const val WEATHER_RADAR = "weather_radar"
    const val WWBOTA = "wwbota"
}

enum class HamClockBasemap { DARK, LIGHT, SATELLITE, TERRAIN }
enum class HamClockDensity { COMPACT, COMFORTABLE, LARGE_TOUCH }
enum class HamClockTimeZoneMode { UTC, LOCAL, BOTH }
enum class HamClockHourFormat { H12, H24 }
enum class HamClockUnitSystem { METRIC, IMPERIAL }
enum class HamClockPskDirection { HEARD, HEARING, BOTH }
enum class HamClockDxTargetSource { MANUAL, AUTOMATIC }

data class HamClockPanelPreference(
    val id: String,
    val visible: Boolean = true,
    val order: Int = 0,
    val column: Int = 0,
    val columnSpan: Int = 1,
    val rowSpan: Int = 1,
    val collapsed: Boolean = false,
)

data class HamClockMapLayerPreference(
    val id: String,
    val visible: Boolean = true,
    val opacity: Float = 1f,
)

data class HamClockMapPreference(
    val basemap: HamClockBasemap = HamClockBasemap.DARK,
    val followStation: Boolean = true,
    val centerLatitude: Double = 0.0,
    val centerLongitude: Double = 0.0,
    val zoom: Double = 1.2,
    val layers: List<HamClockMapLayerPreference> = defaultHamClockMapLayers(),
)

/** Empty filter sets mean all values, avoiding duplicated provider-specific configuration. */
data class HamClockSpotFilter(
    val bands: Set<String> = emptySet(),
    val modes: Set<String> = emptySet(),
    val continents: Set<String> = emptySet(),
    val callQuery: String = "",
    val minimumSnr: Int? = null,
)

data class HamClockClusterPreference(
    val enabled: Boolean = true,
    val windowMinutes: Int = 30,
    val refreshSeconds: Int = 30,
    val maximumSpots: Int = 100,
    val filter: HamClockSpotFilter = HamClockSpotFilter(),
)

data class HamClockPskPreference(
    val enabled: Boolean = true,
    val direction: HamClockPskDirection = HamClockPskDirection.BOTH,
    val windowMinutes: Int = 15,
    val refreshSeconds: Int = 60,
    val maximumReports: Int = 250,
    val filter: HamClockSpotFilter = HamClockSpotFilter(),
)

data class HamClockPortablePreference(
    val enabledPrograms: Set<String> = setOf("POTA", "WWFF", "SOTA", "WWBOTA"),
    val windowMinutes: Int = 30,
    val maximumSpots: Int = 100,
    val favouritesOnly: Boolean = false,
    val showPaths: Boolean = false,
)

data class HamClockSatellitePreference(
    val trackedNoradIds: Set<Int> = emptySet(),
    val passWindowHours: Int = 24,
    val minimumElevationDegrees: Int = 10,
    val showTracks: Boolean = true,
    val showFootprints: Boolean = true,
    val showDoppler: Boolean = false,
)

data class HamClockDxTarget(
    val callsign: String = "",
    val grid: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val locked: Boolean = false,
    val source: HamClockDxTargetSource = HamClockDxTargetSource.MANUAL,
)

data class HamClockDisplayPreference(
    val density: HamClockDensity = HamClockDensity.COMPACT,
    val timeZoneMode: HamClockTimeZoneMode = HamClockTimeZoneMode.BOTH,
    val hourFormat: HamClockHourFormat = HamClockHourFormat.H24,
    val unitSystem: HamClockUnitSystem = HamClockUnitSystem.METRIC,
    val lowDataMode: Boolean = false,
    val immersive: Boolean = false,
)

data class HamClockUserSettings(
    val panels: List<HamClockPanelPreference> = defaultHamClockPanels(),
    val map: HamClockMapPreference = HamClockMapPreference(),
    val cluster: HamClockClusterPreference = HamClockClusterPreference(),
    val pskReporter: HamClockPskPreference = HamClockPskPreference(),
    val portable: HamClockPortablePreference = HamClockPortablePreference(),
    val satellites: HamClockSatellitePreference = HamClockSatellitePreference(),
    val dxTarget: HamClockDxTarget? = null,
    val display: HamClockDisplayPreference = HamClockDisplayPreference(),
)

data class HamClockNamedProfile(
    val id: String,
    val name: String,
    val settings: HamClockUserSettings,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
)

data class HamClockSettingsDocument(
    val version: Int = HamClockSettingsCodec.CURRENT_VERSION,
    val settings: HamClockUserSettings = HamClockUserSettings(),
    val activeProfileId: String? = null,
    val profiles: List<HamClockNamedProfile> = emptyList(),
)

data class HamClockImportResult(
    val version: Int,
    val profileCount: Int,
    val activeProfileId: String?,
)

fun defaultHamClockPanels(): List<HamClockPanelPreference> = defaultPanelsFromRegistry()

fun defaultHamClockMapLayers(): List<HamClockMapLayerPreference> = defaultLayersFromRegistry()
