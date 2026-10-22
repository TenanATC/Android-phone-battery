package com.tenan.equalizer.engine

import android.media.audiofx.DynamicsProcessing
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.Virtualizer
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.tenan.equalizer.dsp.Bands
import com.tenan.equalizer.dsp.ProcessingPlan
import kotlin.math.roundToInt

data class EngineStatus(
    val engine: String,
    val virtualizer: Boolean,
    val loudness: Boolean,
    val error: String? = null,
)

/**
 * The audio effects attached to one audio session.
 *
 * On Android 9+ the EQ is a DynamicsProcessing pre-EQ at 31 bands with a
 * per-channel input gain (preamp + balance) and a limiter — the engine used by
 * the best-regarded Android EQ apps. Older phones, or sessions where
 * DynamicsProcessing cannot attach, fall back to the platform Equalizer, which
 * is sampled from the same response curve. Each effect is optional: a phone
 * that lacks a Virtualizer still gets the EQ.
 */
class EffectChain private constructor(val sessionId: Int) {
    private var dynamics: DynamicsProcessing? = null
    private var equalizer: Equalizer? = null
    private var virtualizer: Virtualizer? = null
    private var loudness: LoudnessEnhancer? = null
    private var lastError: String? = null

    val status: EngineStatus
        get() = EngineStatus(
            engine = when {
                dynamics != null -> "DynamicsProcessing · ${Bands.FINE_HZ.size} bands"
                equalizer != null -> "Equalizer · ${equalizer?.numberOfBands} bands"
                else -> "Not attached"
            },
            virtualizer = virtualizer != null,
            loudness = loudness != null,
            error = lastError,
        )

    private fun open() {
        if (Build.VERSION.SDK_INT >= 28) {
            dynamics = attempt("DynamicsProcessing") { createDynamics() }
        }
        if (dynamics == null) {
            equalizer = attempt("Equalizer") { Equalizer(PRIORITY, sessionId) }
        }
        virtualizer = attempt("Virtualizer") { Virtualizer(PRIORITY, sessionId) }
        loudness = attempt("LoudnessEnhancer") { LoudnessEnhancer(sessionId) }
    }

    fun apply(plan: ProcessingPlan) {
        dynamics?.let { dp -> attempt("apply DynamicsProcessing") { applyDynamics(dp, plan) } }
        equalizer?.let { eq -> attempt("apply Equalizer") { applyEqualizer(eq, plan) } }
        virtualizer?.let { v ->
            attempt("apply Virtualizer") {
                val on = plan.virtualizerStrength > 0
                if (on && v.strengthSupported) v.setStrength(plan.virtualizerStrength.toShort())
                v.setEnabled(on)
            }
        }
        loudness?.let { l ->
            attempt("apply LoudnessEnhancer") {
                l.setTargetGain(plan.loudnessMb)
                l.setEnabled(plan.loudnessMb > 0)
            }
        }
    }

    fun release() {
        listOfNotNull(dynamics, equalizer, virtualizer, loudness).forEach {
            try {
                it.release()
            } catch (e: RuntimeException) {
                Log.w(TAG, "release failed for session $sessionId", e)
            }
        }
        dynamics = null
        equalizer = null
        virtualizer = null
        loudness = null
    }

    @RequiresApi(28)
    private fun createDynamics(): DynamicsProcessing {
        val bandCount = Bands.FINE_HZ.size
        val config = DynamicsProcessing.Config.Builder(
            DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
            CHANNELS,
            /* preEqInUse = */ true, bandCount,
            /* mbcInUse = */ false, 0,
            /* postEqInUse = */ false, 0,
            /* limiterInUse = */ true,
        )
            .setPreEqAllChannelsTo(preEq(FloatArray(bandCount)))
            .setLimiterAllChannelsTo(limiter(enabled = true))
            .build()
        return DynamicsProcessing(PRIORITY, sessionId, config).also { it.setEnabled(true) }
    }

    @RequiresApi(28)
    private fun applyDynamics(dp: DynamicsProcessing, plan: ProcessingPlan) {
        dp.setPreEqAllChannelsTo(preEq(plan.fineGainsDb))
        dp.setInputGainbyChannel(0, plan.preampDb + plan.leftTrimDb)
        dp.setInputGainbyChannel(1, plan.preampDb + plan.rightTrimDb)
        dp.setLimiterAllChannelsTo(limiter(plan.limiter))
        if (!dp.enabled) dp.setEnabled(true)
    }

    @RequiresApi(28)
    private fun preEq(gainsDb: FloatArray): DynamicsProcessing.Eq {
        val eq = DynamicsProcessing.Eq(true, true, gainsDb.size)
        gainsDb.forEachIndexed { i, gain ->
            eq.setBand(i, DynamicsProcessing.EqBand(true, Bands.FINE_CUTOFF_HZ[i], gain))
        }
        return eq
    }

    /** A brick-wall-ish safety limiter just under full scale. */
    @RequiresApi(28)
    private fun limiter(enabled: Boolean) = DynamicsProcessing.Limiter(
        /* inUse = */ true, enabled, /* linkGroup = */ 0,
        /* attackTime ms = */ 1f, /* releaseTime ms = */ 60f,
        /* ratio = */ 10f, /* threshold dB = */ -1f, /* postGain dB = */ 0f,
    )

    /** The legacy Equalizer has no preamp, so the preamp is folded into each band. */
    private fun applyEqualizer(eq: Equalizer, plan: ProcessingPlan) {
        val (minMb, maxMb) = eq.bandLevelRange.let { it[0].toInt() to it[1].toInt() }
        for (band in 0 until eq.numberOfBands) {
            val centreHz = eq.getCenterFreq(band.toShort()) / 1000.0
            val db = plan.curve.dbAt(centreHz) + plan.preampDb
            val mb = (db * 100).roundToInt().coerceIn(minMb, maxMb)
            eq.setBandLevel(band.toShort(), mb.toShort())
        }
        if (!eq.enabled) eq.setEnabled(true)
    }

    private inline fun <T> attempt(what: String, block: () -> T): T? = try {
        block()
    } catch (e: Exception) {
        // UnsupportedOperationException, IllegalStateException, RuntimeException
        // from the audio server: the effect is missing on this phone or the
        // session is gone. Keep going with whatever did attach.
        Log.w(TAG, "$what failed for session $sessionId", e)
        lastError = "$what: ${e.javaClass.simpleName}"
        null
    }

    companion object {
        private const val TAG = "EffectChain"
        /** Outrank a player's own effects on the same session. */
        private const val PRIORITY = Int.MAX_VALUE
        private const val CHANNELS = 2

        fun open(sessionId: Int): EffectChain = EffectChain(sessionId).also { it.open() }
    }
}
