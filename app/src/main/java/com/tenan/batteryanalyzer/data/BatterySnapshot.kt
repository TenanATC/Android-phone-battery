package com.tenan.batteryanalyzer.data

import android.os.BatteryManager

/**
 * One point-in-time reading of the battery, either sampled in the background
 * or taken live while the app is open.
 */
data class BatterySnapshot(
    val timestamp: Long,
    /** 0-100 */
    val level: Int,
    /** BatteryManager.BATTERY_PLUGGED_* or 0 when running on battery */
    val plugged: Int,
    /** BatteryManager.BATTERY_STATUS_*, or -1 for rows recorded before v2 */
    val status: Int,
    /** Degrees Celsius */
    val temperatureC: Float,
    /** Millivolts */
    val voltageMv: Int,
    /** BatteryManager.BATTERY_HEALTH_* */
    val health: Int,
    /** Whether the screen was interactive when sampled */
    val screenOn: Boolean,
    /** Instantaneous current in microamps; sign convention varies by OEM. 0 if unsupported. */
    val currentNowUa: Int = 0,
    /** Remaining charge in microamp-hours, -1 if unsupported */
    val chargeCounterUah: Int = -1,
    /** Battery cycle count (API 34+), -1 if unavailable */
    val cycleCount: Int = -1,
) {
    /**
     * True when a power source is physically connected.
     *
     * This — not EXTRA_STATUS — is the authoritative "on the cable" signal.
     * BATTERY_STATUS_FULL means the battery is full, not that power is
     * attached: it latches at the top of a charge and can persist in the
     * sticky broadcast after unplugging. BATTERY_STATUS_NOT_CHARGING is
     * reported while plugged in with charging deliberately paused (Samsung's
     * "Protect battery", thermal throttling). Deriving charge state from
     * status therefore paints discharges as charges and vice versa.
     */
    val isPlugged: Boolean get() = plugged != 0

    /** True only while the battery is actually gaining charge. */
    val isActivelyCharging: Boolean
        get() = isPlugged && status == BatteryManager.BATTERY_STATUS_CHARGING

    /** Plugged in, but the system has stopped charging (cap reached, full, too hot). */
    val isPluggedNotCharging: Boolean get() = isPlugged && !isActivelyCharging
}
