package com.tenan.batteryanalyzer.data

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager

/** Reads the current battery state from the system's sticky broadcast + BatteryManager. */
object BatteryReader {

    fun read(context: Context): BatterySnapshot? {
        val intent = context.registerReceiver(
            null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        ) ?: return null

        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        if (level < 0 || scale <= 0) return null

        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL

        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager

        val cycleCount = if (Build.VERSION.SDK_INT >= 34) {
            intent.getIntExtra(BatteryManager.EXTRA_CYCLE_COUNT, -1)
        } else -1

        return BatterySnapshot(
            timestamp = System.currentTimeMillis(),
            level = (level * 100) / scale,
            isCharging = isCharging,
            plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0),
            temperatureC = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10f,
            voltageMv = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0),
            health = intent.getIntExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_UNKNOWN),
            screenOn = powerManager.isInteractive,
            currentNowUa = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW),
            chargeCounterUah = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER),
            cycleCount = cycleCount,
        )
    }

    /**
     * Estimated full-charge capacity in mAh, derived from the fuel gauge's charge
     * counter and the current level. Returns null when the kernel doesn't expose
     * a charge counter or the level is too low for a stable estimate.
     */
    fun estimateFullCapacityMah(snapshot: BatterySnapshot): Int? {
        if (snapshot.chargeCounterUah <= 0 || snapshot.level < 20) return null
        return ((snapshot.chargeCounterUah / 1000.0) * 100.0 / snapshot.level).toInt()
    }
}
