package com.tenan.equalizer.dsp

import kotlin.math.log10
import kotlin.math.roundToInt

/**
 * The concrete numbers an effect engine applies to one audio session.
 *
 * The EQ curve is the sum of: the ten sliders (smoothed), the bass and treble
 * shelves, and an optional headphone correction. Keeping the correction
 * separate from the sliders is what lets a per-app preset (say "Podcast" for
 * Podbean) and a per-device correction (say Beats Fit Pro) apply together.
 */
class ProcessingPlan(
    /** Total EQ response, excluding preamp. */
    val curve: ResponseCurve,
    /** [curve] sampled at [Bands.FINE_HZ] and clamped — what DynamicsProcessing applies. */
    val fineGainsDb: FloatArray,
    /** Input gain after auto-headroom. */
    val preampDb: Float,
    val leftTrimDb: Float,
    val rightTrimDb: Float,
    val limiter: Boolean,
    /** Virtualizer strength in the platform's 0..1000 units. */
    val virtualizerStrength: Int,
    /** LoudnessEnhancer target gain in millibels. */
    val loudnessMb: Int,
) {
    val peakGainDb: Float get() = fineGainsDb.max()

    companion object {
        const val MAX_ABS_DB = 24f
        const val BASS_SHELF_HZ = 105.0
        const val TREBLE_SHELF_HZ = 8000.0

        /**
         * @param bandsOverride slider gains to use instead of the device's own,
         *   for an app with its own preset.
         */
        fun build(
            settings: EqSettings,
            bandsOverride: List<Float>? = null,
            correction: ResponseCurve? = null,
        ): ProcessingPlan {
            val curve = totalCurve(settings, bandsOverride, correction)
            val fine = FloatArray(Bands.FINE_HZ.size) { i ->
                curve.dbAt(Bands.FINE_HZ[i]).toFloat().coerceIn(-MAX_ABS_DB, MAX_ABS_DB)
            }
            val peak = fine.max().coerceAtLeast(0f)
            val preamp = if (settings.autoHeadroom) minOf(settings.preampDb, -peak) else settings.preampDb
            val (left, right) = balanceTrims(settings.balance)
            return ProcessingPlan(
                curve = curve,
                fineGainsDb = fine,
                preampDb = preamp,
                leftTrimDb = left,
                rightTrimDb = right,
                limiter = settings.limiter,
                virtualizerStrength = (settings.virtualizer.coerceIn(0f, 1f) * 1000).roundToInt(),
                loudnessMb = (settings.loudnessDb.coerceIn(0f, 10f) * 100).roundToInt(),
            )
        }

        fun totalCurve(
            settings: EqSettings,
            bandsOverride: List<Float>? = null,
            correction: ResponseCurve? = null,
        ): ResponseCurve {
            val bands = bandsOverride ?: settings.bandsDb
            val parts = mutableListOf<ResponseCurve>(
                PointCurve(Bands.GRAPHIC_HZ.zip(bands.map { it.toDouble() }), smooth = true),
            )
            if (settings.bassDb != 0f) {
                parts += Filter(FilterType.LOW_SHELF, BASS_SHELF_HZ, settings.bassDb.toDouble())
            }
            if (settings.trebleDb != 0f) {
                parts += Filter(FilterType.HIGH_SHELF, TREBLE_SHELF_HZ, settings.trebleDb.toDouble())
            }
            if (correction != null) parts += correction
            return parts.sum()
        }

        /**
         * Balance attenuates the opposite channel only, so centre is unity on
         * both sides and full right mutes the left.
         */
        fun balanceTrims(balance: Float): Pair<Float, Float> {
            val b = balance.coerceIn(-1f, 1f)
            fun trim(amplitude: Float) = (20 * log10(amplitude.coerceAtLeast(0.001f).toDouble())).toFloat()
            return trim(minOf(1f, 1f - b)) to trim(minOf(1f, 1f + b))
        }
    }
}
