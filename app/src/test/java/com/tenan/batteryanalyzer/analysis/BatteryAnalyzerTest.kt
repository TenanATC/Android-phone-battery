package com.tenan.batteryanalyzer.analysis

import android.os.BatteryManager
import com.tenan.batteryanalyzer.data.BatterySnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * These cover the charge-state semantics that the battery chart and the drain
 * maths both depend on. The regressions guarded here produced a chart that
 * painted discharges as charges.
 */
class BatteryAnalyzerTest {

    private val base = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(6)

    private fun sample(
        minutesFromBase: Long,
        level: Int,
        plugged: Int = 0,
        status: Int = BatteryManager.BATTERY_STATUS_DISCHARGING,
        screenOn: Boolean = false,
    ) = BatterySnapshot(
        timestamp = base + TimeUnit.MINUTES.toMillis(minutesFromBase),
        level = level,
        plugged = plugged,
        status = status,
        temperatureC = 30f,
        voltageMv = 4000,
        health = BatteryManager.BATTERY_HEALTH_GOOD,
        screenOn = screenOn,
    )

    /** A series 15 minutes apart, dropping [dropPerStep]% each step. */
    private fun discharging(
        steps: Int,
        startLevel: Int,
        dropPerStep: Int,
        plugged: Int = 0,
        status: Int = BatteryManager.BATTERY_STATUS_DISCHARGING,
        screenOn: Boolean = false,
    ) = (0 until steps).map { i ->
        sample(i * 15L, startLevel - i * dropPerStep, plugged, status, screenOn)
    }

    @Test
    fun `plugged state comes from the plug extra, not the status flag`() {
        val unpluggedButFull = sample(
            0, 100, plugged = 0, status = BatteryManager.BATTERY_STATUS_FULL
        )
        assertFalse("FULL while unplugged is not on power", unpluggedButFull.isPlugged)
        assertFalse(unpluggedButFull.isActivelyCharging)

        val pluggedAndPaused = sample(
            0, 85, plugged = BatteryManager.BATTERY_PLUGGED_AC,
            status = BatteryManager.BATTERY_STATUS_NOT_CHARGING,
        )
        assertTrue("NOT_CHARGING on a cable is still plugged in", pluggedAndPaused.isPlugged)
        assertFalse(pluggedAndPaused.isActivelyCharging)
        assertTrue(pluggedAndPaused.isPluggedNotCharging)
    }

    @Test
    fun `discharge is measured even when the phone reports status FULL`() {
        // The exact shape that used to be misread as charging: the status flag
        // latches at FULL after a completed charge while the cable is out.
        val history = discharging(
            steps = 9, startLevel = 100, dropPerStep = 2,
            status = BatteryManager.BATTERY_STATUS_FULL,
        )

        val stats = BatteryAnalyzer.analyze(history, currentLevel = 84)

        assertNotNull("a latched FULL status must not hide a real discharge", stats.overallDrainPerHour)
        assertEquals(8.0, stats.overallDrainPerHour!!, 0.01)
        assertEquals("nothing here was on a cable", 0.0, stats.hoursAtFullWhilePlugged, 0.001)
    }

    @Test
    fun `segments spanning an unplug are excluded from drain rates`() {
        val history = listOf(
            sample(0, 100, BatteryManager.BATTERY_PLUGGED_AC, BatteryManager.BATTERY_STATUS_FULL),
            // The unplug happens in here; this segment is neither a clean
            // charge nor a clean discharge and must not skew the rate.
            sample(15, 96),
        ) + discharging(steps = 9, startLevel = 96, dropPerStep = 2).map {
            it.copy(timestamp = it.timestamp + TimeUnit.MINUTES.toMillis(15))
        }

        val stats = BatteryAnalyzer.analyze(history, currentLevel = 80)

        // 8%/hr from the clean discharge only; including the 16%/hr unplug
        // segment would pull the average up.
        assertEquals(8.0, stats.overallDrainPerHour!!, 0.01)
    }

    @Test
    fun `time at full only counts while actually on a cable`() {
        val onCable = (0 until 4).map {
            sample(
                it * 30L, 100,
                plugged = BatteryManager.BATTERY_PLUGGED_AC,
                status = BatteryManager.BATTERY_STATUS_FULL,
            )
        }
        assertEquals(
            1.5,
            BatteryAnalyzer.analyze(onCable, 100).hoursAtFullWhilePlugged,
            0.01,
        )

        val offCable = (0 until 4).map {
            sample(it * 30L, 100, plugged = 0, status = BatteryManager.BATTERY_STATUS_FULL)
        }
        assertEquals(
            0.0,
            BatteryAnalyzer.analyze(offCable, 100).hoursAtFullWhilePlugged,
            0.001,
        )
    }

    @Test
    fun `screen on and screen off drain are separated`() {
        val screenOff = discharging(steps = 5, startLevel = 100, dropPerStep = 1, screenOn = false)
        val screenOn = (0 until 5).map { i ->
            sample(60 + i * 15L, 96 - i * 4, screenOn = true)
        }

        val stats = BatteryAnalyzer.analyze(screenOff + screenOn, currentLevel = 80)

        assertEquals(4.0, stats.screenOffDrainPerHour!!, 0.01)
        assertEquals(16.0, stats.screenOnDrainPerHour!!, 0.01)
    }

    @Test
    fun `deep discharges count excursions, not samples`() {
        val history = listOf(
            sample(0, 20),
            sample(15, 14),
            sample(30, 12),
            sample(45, 11),
            sample(60, 25, plugged = BatteryManager.BATTERY_PLUGGED_AC,
                status = BatteryManager.BATTERY_STATUS_CHARGING),
        )

        assertEquals(1, BatteryAnalyzer.analyze(history, 25).deepDischargeCount)
    }

    @Test
    fun `gaps longer than two hours do not contribute to drain`() {
        val history = listOf(
            sample(0, 100),
            // A six-hour hole: the phone was off or the sampler was asleep.
            sample(360, 40),
        )

        val stats = BatteryAnalyzer.analyze(history, 40)

        assertNull(stats.overallDrainPerHour)
    }

    @Test
    fun `empty history yields no rates rather than throwing`() {
        val stats = BatteryAnalyzer.analyze(emptyList(), null)

        assertNull(stats.overallDrainPerHour)
        assertNull(stats.estimatedHoursRemaining)
        assertEquals(0, stats.sampleCount)
        assertEquals(0, stats.samplesLast24h)
    }
}
