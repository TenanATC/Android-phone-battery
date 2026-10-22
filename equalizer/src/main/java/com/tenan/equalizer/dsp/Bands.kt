package com.tenan.equalizer.dsp

import kotlin.math.sqrt

/** Frequency grids shared by the UI, the response math and the effect engines. */
object Bands {
    /** The ten octave bands shown as sliders — the layout most Android EQ apps use. */
    val GRAPHIC_HZ = doubleArrayOf(31.0, 62.0, 125.0, 250.0, 500.0, 1000.0, 2000.0, 4000.0, 8000.0, 16000.0)

    const val MIN_GAIN_DB = -12f
    const val MAX_GAIN_DB = 12f

    /**
     * ISO 1/3-octave centres. DynamicsProcessing is driven at this resolution,
     * so the smoothed slider curve, the tone shelves and headphone corrections
     * are reproduced far more faithfully than ten flat steps would allow.
     */
    val FINE_HZ = doubleArrayOf(
        20.0, 25.0, 31.5, 40.0, 50.0, 63.0, 80.0, 100.0, 125.0, 160.0,
        200.0, 250.0, 315.0, 400.0, 500.0, 630.0, 800.0, 1000.0, 1250.0, 1600.0,
        2000.0, 2500.0, 3150.0, 4000.0, 5000.0, 6300.0, 8000.0, 10000.0, 12500.0, 16000.0,
        20000.0,
    )

    /**
     * Upper edge of each fine band, which is what DynamicsProcessing's EqBand
     * takes as its cutoff. Edges sit at the geometric midpoint between
     * neighbouring centres; the last band runs to just under 44.1 kHz Nyquist.
     */
    val FINE_CUTOFF_HZ: FloatArray = FloatArray(FINE_HZ.size) { i ->
        if (i == FINE_HZ.lastIndex) 22000f else sqrt(FINE_HZ[i] * FINE_HZ[i + 1]).toFloat()
    }

    fun label(hz: Double): String = when {
        hz < 1000 -> hz.toInt().toString()
        hz % 1000 == 0.0 -> "${(hz / 1000).toInt()}k"
        else -> "%.1fk".format(hz / 1000)
    }
}
