package com.tenan.equalizer.dsp

import kotlin.math.ln

/** A frequency response: gain in dB at a given frequency. */
fun interface ResponseCurve {
    fun dbAt(hz: Double): Double

    companion object {
        val FLAT = ResponseCurve { 0.0 }
    }
}

operator fun ResponseCurve.plus(other: ResponseCurve): ResponseCurve =
    ResponseCurve { dbAt(it) + other.dbAt(it) }

fun List<ResponseCurve>.sum(): ResponseCurve =
    if (isEmpty()) ResponseCurve.FLAT else ResponseCurve { hz -> sumOf { it.dbAt(hz) } }

/**
 * A curve through (Hz, dB) points, interpolated in log-frequency and held flat
 * beyond the first and last point.
 *
 * With [smooth] set, interpolation is monotone cubic (Fritsch–Butland): it
 * rounds the corners of a ten-slider curve the way a real filter bank would,
 * without ever overshooting a slider — a boost between two sliders never
 * exceeds the higher of them. Without it, interpolation is linear, which suits
 * dense data such as an AutoEq GraphicEQ profile.
 */
class PointCurve(points: List<Pair<Double, Double>>, private val smooth: Boolean = false) : ResponseCurve {
    private val xs: DoubleArray
    private val ys: DoubleArray
    private val slopes: DoubleArray

    init {
        val sorted = points.filter { it.first > 0 }.sortedBy { it.first }.distinctBy { it.first }
        require(sorted.isNotEmpty()) { "A curve needs at least one point" }
        xs = DoubleArray(sorted.size) { ln(sorted[it].first) }
        ys = DoubleArray(sorted.size) { sorted[it].second }
        slopes = if (smooth) monotoneSlopes(xs, ys) else DoubleArray(0)
    }

    override fun dbAt(hz: Double): Double {
        if (hz <= 0) return ys.first()
        val x = ln(hz)
        if (x <= xs.first()) return ys.first()
        if (x >= xs.last()) return ys.last()
        val found = xs.binarySearch(x)
        if (found >= 0) return ys[found]
        val hi = -found - 1
        val lo = hi - 1
        val h = xs[hi] - xs[lo]
        val t = (x - xs[lo]) / h
        if (!smooth) return ys[lo] + t * (ys[hi] - ys[lo])
        val t2 = t * t
        val t3 = t2 * t
        return (2 * t3 - 3 * t2 + 1) * ys[lo] +
            (t3 - 2 * t2 + t) * h * slopes[lo] +
            (-2 * t3 + 3 * t2) * ys[hi] +
            (t3 - t2) * h * slopes[hi]
    }

    private companion object {
        fun monotoneSlopes(xs: DoubleArray, ys: DoubleArray): DoubleArray {
            val n = xs.size
            if (n < 2) return DoubleArray(n)
            val h = DoubleArray(n - 1) { xs[it + 1] - xs[it] }
            val d = DoubleArray(n - 1) { (ys[it + 1] - ys[it]) / h[it] }
            val m = DoubleArray(n)
            // Flat ends: the curve is held flat beyond the outer sliders, so a
            // zero end slope joins it without a kink.
            for (k in 1 until n - 1) {
                m[k] = if (d[k - 1] * d[k] <= 0) {
                    0.0
                } else {
                    val w1 = 2 * h[k] + h[k - 1]
                    val w2 = h[k] + 2 * h[k - 1]
                    (w1 + w2) / (w1 / d[k - 1] + w2 / d[k])
                }
            }
            return m
        }
    }
}
