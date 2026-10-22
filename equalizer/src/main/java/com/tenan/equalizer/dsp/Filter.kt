package com.tenan.equalizer.dsp

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

enum class FilterType { PEAK, LOW_SHELF, HIGH_SHELF, LOW_PASS, HIGH_PASS }

/**
 * One parametric EQ filter, evaluated as an RBJ "Audio EQ Cookbook" biquad —
 * the same definition Equalizer APO and AutoEq use, so an imported AutoEq
 * profile is reproduced as its author computed it.
 */
data class Filter(
    val type: FilterType,
    val frequencyHz: Double,
    val gainDb: Double = 0.0,
    val q: Double = DEFAULT_Q,
) : ResponseCurve {

    private val coefficients: DoubleArray by lazy { design() }

    override fun dbAt(hz: Double): Double {
        val c = coefficients
        val (b0, b1, b2) = Triple(c[0], c[1], c[2])
        val (a0, a1, a2) = Triple(c[3], c[4], c[5])
        val w = 2 * PI * hz / SAMPLE_RATE
        val c1 = cos(w)
        val s1 = sin(w)
        val c2 = cos(2 * w)
        val s2 = sin(2 * w)
        val numRe = b0 + b1 * c1 + b2 * c2
        val numIm = -(b1 * s1 + b2 * s2)
        val denRe = a0 + a1 * c1 + a2 * c2
        val denIm = -(a1 * s1 + a2 * s2)
        val num = numRe * numRe + numIm * numIm
        val den = denRe * denRe + denIm * denIm
        if (num <= 0 || den <= 0) return MIN_DB
        return (10 * log10(num / den)).coerceAtLeast(MIN_DB)
    }

    private fun design(): DoubleArray {
        val a = 10.0.pow(gainDb / 40)
        val w0 = 2 * PI * frequencyHz.coerceIn(1.0, SAMPLE_RATE / 2 - 1) / SAMPLE_RATE
        val cw = cos(w0)
        val alpha = sin(w0) / (2 * q.coerceAtLeast(0.01))
        val sqA = 2 * sqrt(a) * alpha
        return when (type) {
            FilterType.PEAK -> doubleArrayOf(
                1 + alpha * a, -2 * cw, 1 - alpha * a,
                1 + alpha / a, -2 * cw, 1 - alpha / a,
            )
            FilterType.LOW_SHELF -> doubleArrayOf(
                a * ((a + 1) - (a - 1) * cw + sqA),
                2 * a * ((a - 1) - (a + 1) * cw),
                a * ((a + 1) - (a - 1) * cw - sqA),
                (a + 1) + (a - 1) * cw + sqA,
                -2 * ((a - 1) + (a + 1) * cw),
                (a + 1) + (a - 1) * cw - sqA,
            )
            FilterType.HIGH_SHELF -> doubleArrayOf(
                a * ((a + 1) + (a - 1) * cw + sqA),
                -2 * a * ((a - 1) + (a + 1) * cw),
                a * ((a + 1) + (a - 1) * cw - sqA),
                (a + 1) - (a - 1) * cw + sqA,
                2 * ((a - 1) - (a + 1) * cw),
                (a + 1) - (a - 1) * cw - sqA,
            )
            FilterType.LOW_PASS -> doubleArrayOf(
                (1 - cw) / 2, 1 - cw, (1 - cw) / 2,
                1 + alpha, -2 * cw, 1 - alpha,
            )
            FilterType.HIGH_PASS -> doubleArrayOf(
                (1 + cw) / 2, -(1 + cw), (1 + cw) / 2,
                1 + alpha, -2 * cw, 1 - alpha,
            )
        }
    }

    companion object {
        const val SAMPLE_RATE = 48000.0
        const val DEFAULT_Q = 0.707
        private const val MIN_DB = -60.0
    }
}
