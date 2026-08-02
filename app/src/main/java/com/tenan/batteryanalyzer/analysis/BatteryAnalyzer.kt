package com.tenan.batteryanalyzer.analysis

import com.tenan.batteryanalyzer.data.BatterySnapshot
import java.util.concurrent.TimeUnit

/** Aggregated drain metrics computed from the sampled history. */
data class DrainStats(
    /** %/hour while discharging with the screen on; null until enough data */
    val screenOnDrainPerHour: Double?,
    /** %/hour while discharging with the screen off */
    val screenOffDrainPerHour: Double?,
    /** %/hour across all discharge segments in the window */
    val overallDrainPerHour: Double?,
    /** Hours of use left at the overall rate, from the current level */
    val estimatedHoursRemaining: Double?,
    /** Number of times the battery dipped below 15% in the window */
    val deepDischargeCount: Int,
    /** Hours spent plugged in at 100% (battery aging stress) */
    val hoursAtFullWhilePlugged: Double,
    /** Highest temperature seen in the window, °C */
    val maxTemperatureC: Float?,
    /** Average temperature while plugged in, °C */
    val avgChargingTemperatureC: Float?,
    /** Number of samples the stats are based on */
    val sampleCount: Int,
    /** Samples recorded in the last 24h — a proxy for how healthy sampling is */
    val samplesLast24h: Int,
)

object BatteryAnalyzer {

    /** Ignore gaps longer than this between samples (phone off, app killed). */
    private val MAX_SEGMENT_GAP = TimeUnit.HOURS.toMillis(2)
    private val MIN_SEGMENT_LENGTH = TimeUnit.MINUTES.toMillis(5)

    fun analyze(history: List<BatterySnapshot>, currentLevel: Int?): DrainStats {
        var screenOnDrop = 0.0
        var screenOnMs = 0L
        var screenOffDrop = 0.0
        var screenOffMs = 0L
        var totalDrop = 0.0
        var totalMs = 0L
        var fullPluggedMs = 0L

        for (i in 1 until history.size) {
            val a = history[i - 1]
            val b = history[i]
            val dt = b.timestamp - a.timestamp
            if (dt <= MIN_SEGMENT_LENGTH || dt > MAX_SEGMENT_GAP) continue

            if (a.isPlugged && b.isPlugged && a.level >= 100) {
                fullPluggedMs += dt
            }

            // Discharge segments only: cable disconnected at both ends, and the
            // level did not rise. `isPlugged` is used rather than the battery
            // status flag, which reports FULL/NOT_CHARGING while still on power.
            if (a.isPlugged || b.isPlugged || b.level > a.level) continue
            val drop = (a.level - b.level).toDouble()
            totalDrop += drop
            totalMs += dt
            if (a.screenOn && b.screenOn) {
                screenOnDrop += drop
                screenOnMs += dt
            } else if (!a.screenOn && !b.screenOn) {
                screenOffDrop += drop
                screenOffMs += dt
            }
        }

        fun rate(drop: Double, ms: Long, minHours: Double): Double? {
            val hours = ms / 3_600_000.0
            return if (hours >= minHours) drop / hours else null
        }

        val overall = rate(totalDrop, totalMs, minHours = 1.0)
        val deepDischarges = countDeepDischarges(history)
        val temps = history.map { it.temperatureC }.filter { it > 0f }
        val chargingTemps = history.filter { it.isPlugged && it.temperatureC > 0f }
            .map { it.temperatureC }
        val dayAgo = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(1)

        return DrainStats(
            screenOnDrainPerHour = rate(screenOnDrop, screenOnMs, minHours = 0.5),
            screenOffDrainPerHour = rate(screenOffDrop, screenOffMs, minHours = 1.0),
            overallDrainPerHour = overall,
            estimatedHoursRemaining = if (overall != null && overall > 0.1 && currentLevel != null) {
                currentLevel / overall
            } else null,
            deepDischargeCount = deepDischarges,
            hoursAtFullWhilePlugged = fullPluggedMs / 3_600_000.0,
            maxTemperatureC = temps.maxOrNull(),
            avgChargingTemperatureC = if (chargingTemps.isNotEmpty()) {
                (chargingTemps.sum() / chargingTemps.size)
            } else null,
            sampleCount = history.size,
            samplesLast24h = history.count { it.timestamp >= dayAgo },
        )
    }

    /** Counts distinct excursions below 15% (entering low territory once = one event). */
    private fun countDeepDischarges(history: List<BatterySnapshot>): Int {
        var count = 0
        var below = false
        for (s in history) {
            if (!below && s.level < 15 && !s.isPlugged) {
                count++
                below = true
            } else if (s.level >= 20 || s.isPlugged) {
                below = false
            }
        }
        return count
    }
}
