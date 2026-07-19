package com.tenan.batteryanalyzer.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.tenan.batteryanalyzer.data.BatteryHistoryStore
import com.tenan.batteryanalyzer.data.BatteryReader
import java.util.concurrent.TimeUnit

/**
 * Samples the battery every 15 minutes (WorkManager's minimum period) into the
 * local history store. Survives reboots; costs effectively nothing itself
 * because reading the sticky battery broadcast doesn't wake the radio or GPS.
 */
class BatterySampleWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val snapshot = BatteryReader.read(applicationContext) ?: return Result.success()
        BatteryHistoryStore.get(applicationContext).insert(snapshot)
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "battery_sampler"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<BatterySampleWorker>(
                15, TimeUnit.MINUTES
            ).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request
            )
        }
    }
}
