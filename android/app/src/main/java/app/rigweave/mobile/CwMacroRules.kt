package app.rigweave.mobile

const val CW_MACRO_COUNT = 6
const val CW_MACRO_LABEL_MAX = 11
const val CW_MACRO_TEXT_MAX = 24
const val CQ_REPEAT_MIN_SECONDS = 1
const val CQ_REPEAT_MAX_SECONDS = 5

private val defaultCwMacroLabels = listOf("CQ", "EXCH", "TU", "", "", "")
private const val cwMacroSafeCharacters = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789 .,?/=+-@"

fun defaultCwMacroLabel(index: Int): String = defaultCwMacroLabels.getOrNull(index).orEmpty()

fun sanitizeCwMacroLabel(value: String): String = value.uppercase()
    .filter { it.isLetterOrDigit() || it == ' ' || it == '-' || it == '/' }
    .take(CW_MACRO_LABEL_MAX)

fun sanitizeCwMacroText(value: String): String = value.substringBefore(';').substringBefore('\n').substringBefore('\r').uppercase()
    .filter { it in cwMacroSafeCharacters }
    .take(CW_MACRO_TEXT_MAX)

fun isCwMacroMode(mode: String): Boolean = mode.trim().uppercase().replace('_', '-') in setOf("CW", "CW-R", "CWR")

fun cwMacroCommand(text: String): String? = sanitizeCwMacroText(text).takeIf(String::isNotBlank)?.let { "KY $it;" }
