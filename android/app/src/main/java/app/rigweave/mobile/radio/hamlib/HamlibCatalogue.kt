package app.rigweave.mobile.radio.hamlib

import java.util.Locale

data class HamlibManufacturerGroup(
    val manufacturer: String,
    val models: List<HamlibModelDescriptor>,
)

private val preferredManufacturers = listOf(
    "Elecraft", "Icom", "Kenwood", "Yaesu", "FlexRadio", "Xiegu", "Ten-Tec", "Alinco",
)

fun hamlibManufacturerGroups(models: List<HamlibModelDescriptor>): List<HamlibManufacturerGroup> =
    models.groupBy { it.manufacturer.trim().ifBlank { "Other" } }
        .map { (manufacturer, entries) ->
            HamlibManufacturerGroup(
                manufacturer = manufacturer,
                models = entries.sortedWith(
                    compareBy<HamlibModelDescriptor, String>(String.CASE_INSENSITIVE_ORDER) { it.model }
                        .thenBy { it.id },
                ),
            )
        }
        .sortedWith(compareBy<HamlibManufacturerGroup> {
            preferredManufacturers.indexOfFirst { preferred -> preferred.equals(it.manufacturer, ignoreCase = true) }
                .takeIf { index -> index >= 0 } ?: Int.MAX_VALUE
        }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.manufacturer })

fun searchHamlibModels(
    models: List<HamlibModelDescriptor>,
    query: String,
    maximum: Int = 60,
): List<HamlibModelDescriptor> {
    val needle = query.trim().lowercase(Locale.US)
    if (needle.isBlank()) return emptyList()
    needle.toIntOrNull()?.let { exactId ->
        models.firstOrNull { it.id == exactId }?.let { return listOf(it) }
    }
    return models.asSequence()
        .filter {
            it.label.lowercase(Locale.US).contains(needle) ||
                it.backend.lowercase(Locale.US).contains(needle) ||
                it.portType.lowercase(Locale.US).contains(needle)
        }
        .sortedWith(
            compareBy<HamlibModelDescriptor, String>(String.CASE_INSENSITIVE_ORDER) { it.manufacturer }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.model },
        )
        .take(maximum.coerceIn(1, 200))
        .toList()
}
