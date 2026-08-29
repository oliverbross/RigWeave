package app.rigweave.mobile.radio.hamlib

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HamlibCatalogueTest {
    @Test fun preferredManufacturersLeadTheCatalogueAndModelsAreSorted() {
        val groups = hamlibManufacturerGroups(listOf(
            model(40, "Yaesu", "FT-991A"), model(20, "Kenwood", "TS-590"),
            model(11, "Elecraft", "KX3"), model(10, "Elecraft", "K4"),
            model(99, "Acme", "Future Rig"),
        ))

        assertEquals(listOf("Elecraft", "Kenwood", "Yaesu", "Acme"), groups.map { it.manufacturer })
        assertEquals(listOf("K4", "KX3"), groups.first().models.map { it.model })
    }

    @Test fun searchMatchesManufacturerModelBackendPortAndExactId() {
        val models = listOf(
            model(10, "Elecraft", "K4", backend = "elecraft", port = "serial"),
            model(20, "Icom", "IC-7610", backend = "icom", port = "network"),
        )

        assertEquals(listOf(10), searchHamlibModels(models, "k4").map { it.id })
        assertEquals(listOf(20), searchHamlibModels(models, "network").map { it.id })
        assertEquals(listOf(10), searchHamlibModels(models, "10").map { it.id })
        assertTrue(searchHamlibModels(models, "missing").isEmpty())
    }

    private fun model(
        id: Int,
        manufacturer: String,
        name: String,
        backend: String = manufacturer.lowercase(),
        port: String = "serial",
    ) = HamlibModelDescriptor(
        id = id, manufacturer = manufacturer, model = name, backend = backend, backendId = 1,
        driverVersion = "1", status = "stable", portType = port, serialRateMin = 1_200,
        serialRateMax = 115_200, serialDataBits = 8, serialStopBits = 1, serialParity = 0,
        serialHandshake = 0, timeoutMs = 1_000, retry = 2,
        capabilities = HamlibCapabilitySnapshot(
            modes = emptySet(), vfos = emptySet(), ranges = emptyList(), filters = emptyList(),
            readableLevels = emptySet(), writableLevels = emptySet(), readableFunctions = emptySet(),
            writableFunctions = emptySet(), readableParameters = emptySet(), writableParameters = emptySet(),
            targetableVfo = 0, maxRitHz = 0, maxXitHz = 0, maxIfShiftHz = 0, pttType = 0,
        ),
    )
}
