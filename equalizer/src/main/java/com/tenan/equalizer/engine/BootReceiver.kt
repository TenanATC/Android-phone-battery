package com.tenan.equalizer.engine

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.tenan.equalizer.eqApp

/** Brings the EQ back after a reboot or an app update if it was on. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED ->
                if (context.eqApp.repository.config.value.enabled) EqService.start(context)
        }
    }
}
