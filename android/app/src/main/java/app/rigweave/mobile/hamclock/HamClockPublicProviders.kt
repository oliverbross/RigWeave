package app.rigweave.mobile.hamclock

import java.io.File

/** One integration point for the OpenHamClock-equivalent public providers. */
internal class HamClockPublicProviders(
    cacheDirectory: File,
    http: HamClockHttpClient = HamClockUrlConnectionClient(),
) {
    val contests = ContestCalendarProvider(cacheDirectory, http)
    val dxpeditions = DxpeditionScheduleProvider(cacheDirectory, http)
    val solarCelestial = SolarCelestialProvider(cacheDirectory, http)
}
