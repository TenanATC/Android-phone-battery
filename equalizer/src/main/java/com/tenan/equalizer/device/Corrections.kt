package com.tenan.equalizer.device

import com.tenan.equalizer.dsp.AutoEqParser
import com.tenan.equalizer.dsp.Filter
import com.tenan.equalizer.dsp.FilterType
import com.tenan.equalizer.dsp.ResponseCurve
import com.tenan.equalizer.dsp.sum

/** Which headphone correction a device profile uses. */
sealed interface CorrectionSpec {
    data object None : CorrectionSpec
    data class BuiltIn(val id: String) : CorrectionSpec
    /** An AutoEq / Equalizer APO profile the user pasted in. */
    data class Imported(val name: String, val text: String) : CorrectionSpec
}

data class BuiltInCorrection(
    val id: String,
    val name: String,
    val description: String,
    val filters: List<Filter>,
) {
    val curve: ResponseCurve get() = filters.sum()
}

/**
 * Starting-point corrections.
 *
 * The Beats curves are deliberately mild (no filter beyond ±3.5 dB). They
 * counter the Beats house sound — a raised bass shelf, some low-mid warmth and
 * a dip in the 2–4 kHz presence region — and are not derived from measurements
 * of a single unit. For a measurement-based correction of an exact model,
 * import its AutoEq profile, which replaces these.
 */
object Corrections {
    const val BEATS_IN_EAR = "beats_in_ear"
    const val BEATS_EARHOOK = "beats_earhook"
    const val BEATS_ON_EAR = "beats_on_ear"
    const val BEATS_OVER_EAR = "beats_over_ear"
    const val SPEAKER_CLARITY = "speaker_clarity"

    val BUILT_IN: List<BuiltInCorrection> = listOf(
        BuiltInCorrection(
            BEATS_IN_EAR, "Beats in-ear → neutral",
            "Studio Buds, Studio Buds +, Fit Pro, Solo Buds, Flex. Trims the bass shelf and " +
                "low-mid warmth, restores presence.",
            listOf(
                Filter(FilterType.LOW_SHELF, 105.0, -3.0, 0.7),
                Filter(FilterType.PEAK, 250.0, -1.5, 1.0),
                Filter(FilterType.PEAK, 3000.0, 2.5, 1.4),
                Filter(FilterType.PEAK, 8000.0, -1.5, 2.0),
            ),
        ),
        BuiltInCorrection(
            BEATS_EARHOOK, "Powerbeats → neutral",
            "Powerbeats Pro, Powerbeats Pro 2. Lighter bass trim (earhook fit already leaks " +
                "some bass), more presence.",
            listOf(
                Filter(FilterType.LOW_SHELF, 105.0, -2.0, 0.7),
                Filter(FilterType.PEAK, 2500.0, 3.0, 1.2),
                Filter(FilterType.PEAK, 7000.0, -2.0, 2.0),
            ),
        ),
        BuiltInCorrection(
            BEATS_ON_EAR, "Beats on-ear → neutral",
            "Solo 4, Solo Pro, Solo3. Stronger bass trim, clears 400 Hz boxiness.",
            listOf(
                Filter(FilterType.LOW_SHELF, 90.0, -3.5, 0.7),
                Filter(FilterType.PEAK, 400.0, -1.5, 1.0),
                Filter(FilterType.PEAK, 3500.0, 2.0, 1.4),
            ),
        ),
        BuiltInCorrection(
            BEATS_OVER_EAR, "Beats over-ear → neutral",
            "Studio Pro, Studio3. Trims sub-bass and upper-bass, restores presence.",
            listOf(
                Filter(FilterType.LOW_SHELF, 90.0, -3.0, 0.7),
                Filter(FilterType.PEAK, 200.0, -1.0, 1.0),
                Filter(FilterType.PEAK, 3000.0, 2.0, 1.4),
            ),
        ),
        BuiltInCorrection(
            SPEAKER_CLARITY, "Phone speaker clarity",
            "Rolls off bass a phone speaker cannot reproduce (it only adds distortion) and " +
                "lifts voices.",
            listOf(
                Filter(FilterType.HIGH_PASS, 150.0, q = 0.7),
                Filter(FilterType.PEAK, 3000.0, 2.0, 1.0),
            ),
        ),
    )

    fun builtIn(id: String): BuiltInCorrection? = BUILT_IN.firstOrNull { it.id == id }

    fun suggestedFor(model: BeatsModel): BuiltInCorrection? = when (model.family) {
        BeatsFamily.IN_EAR -> builtIn(BEATS_IN_EAR)
        BeatsFamily.EARHOOK -> builtIn(BEATS_EARHOOK)
        BeatsFamily.ON_EAR -> builtIn(BEATS_ON_EAR)
        BeatsFamily.OVER_EAR -> builtIn(BEATS_OVER_EAR)
        BeatsFamily.SPEAKER, BeatsFamily.UNKNOWN -> null
    }

    fun resolve(spec: CorrectionSpec): ResponseCurve? = when (spec) {
        CorrectionSpec.None -> null
        is CorrectionSpec.BuiltIn -> builtIn(spec.id)?.curve
        is CorrectionSpec.Imported -> AutoEqParser.parse(spec.text).getOrNull()?.curve
    }

    fun label(spec: CorrectionSpec): String = when (spec) {
        CorrectionSpec.None -> "None"
        is CorrectionSpec.BuiltIn -> builtIn(spec.id)?.name ?: "None"
        is CorrectionSpec.Imported -> spec.name
    }
}
