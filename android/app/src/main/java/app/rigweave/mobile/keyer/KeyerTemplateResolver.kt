package app.rigweave.mobile.keyer

import app.rigweave.mobile.CW_MACRO_TEXT_MAX
import app.rigweave.mobile.sanitizeCwMacroText

data class KeyerTemplateResolution(val text: String = "", val error: KeyerFailureReason? = null, val detail: String = "")

object KeyerTemplateResolver {
    private val token = Regex("\\{([A-Z_]+)(\\?)?}", RegexOption.IGNORE_CASE)

    fun resolve(template: String, context: KeyerContextSnapshot): KeyerTemplateResolution {
        val values = mapOf(
            "MYCALL" to context.myCall, "CALL" to context.call, "RST" to context.rst,
            "RST_SENT" to context.rstSent, "RST_RECV" to context.rstRecv, "SERIAL" to context.serial,
            "EXCHANGE" to context.exchange, "GRID" to context.grid, "REFERENCE" to context.reference,
            "MODE" to context.mode.name, "BAND" to context.band,
        )
        var failure: KeyerTemplateResolution? = null
        val expanded = token.replace(template) { match ->
            val name = match.groupValues[1].uppercase()
            val optional = match.groupValues[2] == "?"
            val value = values[name]
            when {
                value == null -> { failure = KeyerTemplateResolution(error = KeyerFailureReason.CwTextInvalid, detail = "Unknown token {$name}"); "" }
                value.isBlank() && !optional -> { failure = KeyerTemplateResolution(error = KeyerFailureReason.CwTextInvalid, detail = "Required token {$name} is missing"); "" }
                else -> value
            }
        }
        failure?.let { return it }
        val unresolved = Regex("\\{[^}]+}").find(expanded)?.value
        if (unresolved != null) return KeyerTemplateResolution(error = KeyerFailureReason.CwTextInvalid, detail = "Unknown token $unresolved")
        val normalized = expanded.trim().replace(Regex("\\s+"), " ")
        val sanitized = sanitizeCwMacroText(normalized)
        if (sanitized.isBlank()) return KeyerTemplateResolution(error = KeyerFailureReason.CwTextInvalid, detail = "Resolved CW message is blank")
        if (normalized.uppercase() != sanitized || normalized.length > CW_MACRO_TEXT_MAX) {
            return KeyerTemplateResolution(error = KeyerFailureReason.BackendCapacityExceeded,
                detail = "Resolved CW message exceeds the verified $CW_MACRO_TEXT_MAX character KY command limit or contains unsupported characters")
        }
        return KeyerTemplateResolution(text = sanitized)
    }
}
