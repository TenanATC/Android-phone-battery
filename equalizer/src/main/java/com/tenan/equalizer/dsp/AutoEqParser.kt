package com.tenan.equalizer.dsp

/**
 * A headphone correction parsed from text exported by AutoEq (autoeq.app) or
 * written for Equalizer APO.
 */
data class ParsedCorrection(
    val curve: ResponseCurve,
    val summary: String,
    /** The profile's own preamp. Not applied: auto-headroom and the limiter cover clipping. */
    val preampDb: Double?,
)

/**
 * Reads the two AutoEq export formats:
 *
 * - **GraphicEQ** (the "Wavelet" / "GraphicEQ" export):
 *   `GraphicEQ: 20 -6.2; 21 -6.1; …`
 * - **ParametricEQ** (the "Equalizer APO" export, also used by Poweramp):
 *   `Preamp: -6.4 dB` then lines like `Filter 1: ON LSC Fc 105 Hz Gain 6.2 dB Q 0.70`
 */
object AutoEqParser {
    private val GRAPHIC = Regex("""GraphicEQ\s*:(.*)""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val PREAMP = Regex("""Preamp\s*:\s*([-+]?\d+(?:\.\d+)?)\s*dB""", RegexOption.IGNORE_CASE)
    private val FILTER = Regex(
        """Filter\s*\d*\s*:\s*(ON|OFF)\s+([A-Z]+)\s+Fc\s+(\d+(?:\.\d+)?)\s*Hz""" +
            """(?:\s+Gain\s+([-+]?\d+(?:\.\d+)?)\s*dB)?(?:\s+Q\s+(\d+(?:\.\d+)?))?""",
        RegexOption.IGNORE_CASE,
    )

    fun parse(text: String): Result<ParsedCorrection> {
        val preamp = PREAMP.find(text)?.groupValues?.get(1)?.toDoubleOrNull()

        GRAPHIC.find(text)?.let { match ->
            val points = match.groupValues[1].split(';').mapNotNull { pair ->
                val parts = pair.trim().split(Regex("""\s+"""))
                if (parts.size < 2) return@mapNotNull null
                val hz = parts[0].toDoubleOrNull() ?: return@mapNotNull null
                val db = parts[1].toDoubleOrNull() ?: return@mapNotNull null
                if (hz > 0) hz to db else null
            }
            if (points.size < 2) return failure("GraphicEQ line has fewer than two frequency/gain pairs")
            return Result.success(
                ParsedCorrection(PointCurve(points), "GraphicEQ, ${points.size} points", preamp)
            )
        }

        val filters = FILTER.findAll(text).mapNotNull { m ->
            if (!m.groupValues[1].equals("ON", ignoreCase = true)) return@mapNotNull null
            val type = filterType(m.groupValues[2]) ?: return@mapNotNull null
            val hz = m.groupValues[3].toDouble()
            val gain = m.groupValues[4].toDoubleOrNull() ?: 0.0
            val q = m.groupValues[5].toDoubleOrNull() ?: Filter.DEFAULT_Q
            if (hz <= 0 || q <= 0) null else Filter(type, hz, gain, q)
        }.toList()
        if (filters.isEmpty()) {
            return failure("No AutoEq filters found. Paste a GraphicEQ or ParametricEQ profile.")
        }
        return Result.success(
            ParsedCorrection(
                filters.sum(),
                "Parametric, ${filters.size} filter${if (filters.size == 1) "" else "s"}",
                preamp,
            )
        )
    }

    private fun filterType(code: String): FilterType? = when (code.uppercase()) {
        "PK", "PEQ", "MODAL" -> FilterType.PEAK
        "LS", "LSC", "LSQ" -> FilterType.LOW_SHELF
        "HS", "HSC", "HSQ" -> FilterType.HIGH_SHELF
        "LP", "LPQ" -> FilterType.LOW_PASS
        "HP", "HPQ" -> FilterType.HIGH_PASS
        else -> null
    }

    private fun failure(message: String) = Result.failure<ParsedCorrection>(IllegalArgumentException(message))
}
