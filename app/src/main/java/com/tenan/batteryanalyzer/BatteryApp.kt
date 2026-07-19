package com.tenan.batteryanalyzer

import android.app.Application
import com.tenan.batteryanalyzer.work.BatterySampleWorker

class BatteryApp : Application() {
    override fun onCreate() {
        super.onCreate()
        BatterySampleWorker.schedule(this)
    }
}
