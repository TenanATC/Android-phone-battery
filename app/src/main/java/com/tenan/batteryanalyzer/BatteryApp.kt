package com.tenan.batteryanalyzer

import android.app.Application
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import com.tenan.batteryanalyzer.work.BatteryChangeSampler
import com.tenan.batteryanalyzer.work.BatterySampleWorker

class BatteryApp : Application() {
    override fun onCreate() {
        super.onCreate()
        BatterySampleWorker.schedule(this)

        // Dense sampling while the process lives; the periodic worker is the
        // fallback for when it does not. ACTION_BATTERY_CHANGED cannot be
        // declared in the manifest, so it is registered at runtime.
        ContextCompat.registerReceiver(
            this,
            BatteryChangeSampler(),
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }
}
