package com.tenan.batteryanalyzer.data

/**
 * One point-in-time reading of the battery, either sampled in the background
 * or taken live while the app is open.
 */
data class BatterySnapshot(
    val timestamp: Long,
    /** 0-100 */
    val level: Int,
    val isCharging: Boolean,
    /** BatteryManager.BATTERY_PLUGGED_* or 0 when on battery */
    val plugged: Int,
    /** Degrees Celsius */
    val temperatureC: Float,
    /** Millivolts */
    val voltageMv: Int,
    /** BatteryManager.BATTERY_HEALTH_* */
    val health: Int,
    /** Whether the screen was interactive when sampled */
    val screenOn: Boolean,
    /** Instantaneous current in microamps; positive = charging. 0 if unsupported. */
    val currentNowUa: Int = 0,
    /** Remaining charge in microamp-hours, -1 if unsupported */
    val chargeCounterUah: Int = -1,
    /** Battery cycle count (API 34+), -1 if unavailable */
    val cycleCount: Int = -1,
)
