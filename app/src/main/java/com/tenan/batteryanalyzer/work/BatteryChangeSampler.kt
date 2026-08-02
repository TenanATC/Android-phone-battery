package com.tenan.batteryanalyzer.work

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.tenan.batteryanalyzer.data.BatteryHistoryStore
import com.tenan.batteryanalyzer.data.BatteryReader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/**
 * Records a sample whenever the system broadcasts a battery change, for as
 * long as this process is alive.
 *
 * WorkManager's 15-minute period is a floor, not a promise — aggressive OEM
 * background management (One UI's app sleep in particular) defers it for hours,
 * which leaves the history sparse enough that the chart has to interpolate
 * across large holes. This receiver costs nothing extra: ACTION_BATTERY_CHANGED
 * is already being broadcast, and reading it wakes no radio or sensor.
 *
 * The broadcast fires very frequently, so writes are throttled to a level or
 * plug-state change, or [MIN_INTERVAL_MS] since the last write.
 */
class BatteryChangeSampler : BroadcastReceiver() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile private var lastInsertMs = 0L
    @Volatile private var lastLevel = -1
    @Volatile private var lastPlugged = -1

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        scope.launch {
            runCatching {
                val snapshot = BatteryReader.fromIntent(app, intent) ?: return@runCatching
                val interesting = snapshot.level != lastLevel || snapshot.plugged != lastPlugged
                if (!interesting && snapshot.timestamp - lastInsertMs < MIN_INTERVAL_MS) {
                    return@runCatching
                }
                lastLevel = snapshot.level
                lastPlugged = snapshot.plugged
                lastInsertMs = snapshot.timestamp
                BatteryHistoryStore.get(app).insert(snapshot)
            }
        }
    }

    private companion object {
        val MIN_INTERVAL_MS = TimeUnit.MINUTES.toMillis(5)
    }
}
