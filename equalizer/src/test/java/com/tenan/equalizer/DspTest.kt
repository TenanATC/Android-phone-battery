package com.tenan.equalizer

import com.tenan.equalizer.dsp.Bands
import com.tenan.equalizer.dsp.EqSettings
import com.tenan.equalizer.dsp.Filter
import com.tenan.equalizer.dsp.FilterType
import com.tenan.equalizer.dsp.PointCurve
import com.tenan.equalizer.dsp.Presets
import com.tenan.equalizer.dsp.ProcessingPlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DspTest {

    @Test
    fun smoothCurvePassesThroughPointsAndNeverOvershoots() {
        val gains = listOf(0.0, 0.0, 6.0, 6.0, 0.0, -6.0, -6.0, 0.0, 12.0, 0.0)
        val curve = PointCurve(Bands.GRAPHIC_HZ.zip(gains), smooth = true)
        Bands.GRAPHIC_HZ.forEachIndexed { i, hz -> assertEquals(gains[i], curve.dbAt(hz), 1e-9) }
        var hz = 20.0
        while (hz < 20000) {
            val db = curve.dbAt(hz)
            assertTrue("overshoot at $hz Hz: $db", db in -6.0 - 1e-9..12.0 + 1e-9)
            hz *= 1.01
        }
        // Between two equal sliders the curve stays flat.
        assertEquals(6.0, curve.dbAt(177.0), 1e-9)
    }

    @Test
    fun curveIsHeldFlatBeyondTheOuterPoints() {
        val curve = PointCurve(listOf(100.0 to 3.0, 1000.0 to -2.0))
        assertEquals(3.0, curve.dbAt(20.0), 1e-9)
        assertEquals(-2.0, curve.dbAt(20000.0), 1e-9)
        // Linear in log-frequency: the geometric midpoint is the arithmetic mean.
        assertEquals(0.5, curve.dbAt(316.227766), 1e-6)
    }

    @Test
    fun peakFilterHitsItsGainAtCentreAndIsFlatFarAway() {
        val peak = Filter(FilterType.PEAK, 1000.0, 6.0, 1.0)
        assertEquals(6.0, peak.dbAt(1000.0), 0.01)
        assertEquals(0.0, peak.dbAt(20.0), 0.05)
        assertEquals(0.0, peak.dbAt(20000.0), 0.1)
    }

    @Test
    fun shelvesReachTheirGainOnTheirSideOnly() {
        val low = Filter(FilterType.LOW_SHELF, 105.0, 6.0)
        assertEquals(6.0, low.dbAt(20.0), 0.2)
        assertEquals(3.0, low.dbAt(105.0), 0.2)
        assertEquals(0.0, low.dbAt(5000.0), 0.05)

        val high = Filter(FilterType.HIGH_SHELF, 8000.0, -4.0)
        assertEquals(0.0, high.dbAt(100.0), 0.05)
        assertEquals(-4.0, high.dbAt(20000.0), 0.5)
    }

    @Test
    fun highPassRemovesBass() {
        val hp = Filter(FilterType.HIGH_PASS, 150.0, q = 0.707)
        assertTrue(hp.dbAt(30.0) < -20)
        assertEquals(-3.0, hp.dbAt(150.0), 0.1)
        assertEquals(0.0, hp.dbAt(5000.0), 0.05)
    }

    @Test
    fun flatSettingsProduceANeutralPlan() {
        val plan = ProcessingPlan.build(EqSettings())
        plan.fineGainsDb.forEach { assertEquals(0f, it, 1e-6f) }
        assertEquals(0f, plan.preampDb, 1e-6f)
        assertEquals(0f, plan.leftTrimDb, 1e-6f)
        assertEquals(0f, plan.rightTrimDb, 1e-6f)
        assertEquals(0, plan.virtualizerStrength)
        assertEquals(0, plan.loudnessMb)
    }

    @Test
    fun autoHeadroomCancelsThePeakBoost() {
        val settings = EqSettings().withPreset(Presets.builtIn("bass")!!).copy(preampDb = 2f)
        val plan = ProcessingPlan.build(settings)
        assertEquals(6f, plan.peakGainDb, 0.01f)
        assertEquals(-6f, plan.preampDb, 0.01f)

        val manual = ProcessingPlan.build(settings.copy(autoHeadroom = false))
        assertEquals(2f, manual.preampDb, 1e-6f)

        // A preamp already low enough is left alone.
        val quiet = ProcessingPlan.build(settings.copy(preampDb = -9f))
        assertEquals(-9f, quiet.preampDb, 1e-6f)
    }

    @Test
    fun appBandsReplaceSlidersButCorrectionAndToneStillApply() {
        val settings = EqSettings().withPreset(Presets.builtIn("bass")!!).copy(trebleDb = 4f)
        val voice = Presets.builtIn(Presets.VOICE_ID)!!.bandsDb
        val correction = Filter(FilterType.PEAK, 1000.0, -3.0, 1.0)
        val curve = ProcessingPlan.totalCurve(settings, voice, correction)
        // 31 Hz: the voice preset's -6, not the bass preset's +6.
        assertEquals(-6.0, curve.dbAt(31.0), 0.1)
        // 1 kHz: voice +1, correction -3, treble shelf contributes ~0.
        assertEquals(-2.0, curve.dbAt(1000.0), 0.15)
        // 16 kHz: voice 0 plus most of the 4 dB treble shelf.
        assertTrue(curve.dbAt(16000.0) > 3.0)
    }

    @Test
    fun fineGainsAreClamped() {
        val loud = EqSettings(bandsDb = List(10) { 12f }, bassDb = 12f, trebleDb = 12f)
        val plan = ProcessingPlan.build(loud)
        plan.fineGainsDb.forEach { assertTrue(it <= ProcessingPlan.MAX_ABS_DB) }
    }

    @Test
    fun balanceAttenuatesOnlyTheOppositeChannel() {
        val (l0, r0) = ProcessingPlan.balanceTrims(0f)
        assertEquals(0f, l0, 1e-6f)
        assertEquals(0f, r0, 1e-6f)
        val (l, r) = ProcessingPlan.balanceTrims(0.5f)
        assertEquals(-6.02f, l, 0.01f)
        assertEquals(0f, r, 1e-6f)
        val (lFull, _) = ProcessingPlan.balanceTrims(1f)
        assertEquals(-60f, lFull, 0.01f)
    }

    @Test
    fun withBandMarksTheCurveCustomAndClamps() {
        val s = EqSettings().withBand(3, 40f)
        assertEquals(Bands.MAX_GAIN_DB, s.bandsDb[3], 0f)
        assertEquals(null, s.presetId)
    }

    @Test
    fun fineCutoffsAscendAndStayBelowNyquist() {
        val cutoffs = Bands.FINE_CUTOFF_HZ
        for (i in 1 until cutoffs.size) assertTrue(cutoffs[i] > cutoffs[i - 1])
        assertTrue(cutoffs.last() < 22050f)
        assertEquals(Bands.FINE_HZ.size, cutoffs.size)
    }

    @Test
    fun allBuiltInPresetsAreWithinSliderRange() {
        Presets.BUILT_IN.forEach { p ->
            p.bandsDb.forEach { assertTrue("${p.name}: $it", it in Bands.MIN_GAIN_DB..Bands.MAX_GAIN_DB) }
        }
        assertEquals(Presets.BUILT_IN.size, Presets.BUILT_IN.map { it.id }.toSet().size)
    }
}
