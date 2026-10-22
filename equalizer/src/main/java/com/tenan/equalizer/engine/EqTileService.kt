package com.tenan.equalizer.engine

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.tenan.equalizer.MainActivity
import com.tenan.equalizer.eqApp

/** Quick Settings toggle for the equalizer. */
class EqTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        render()
    }

    override fun onClick() {
        super.onClick()
        val repository = eqApp.repository
        val enable = !repository.config.value.enabled
        repository.setEnabled(enable)
        // EqApp starts the service when the switch flips on; if Android
        // refuses that from here, open the app so the start happens in the
        // foreground instead.
        if (enable && !EqService.start(this)) openApp()
        render()
    }

    private fun render() {
        val tile = qsTile ?: return
        val config = eqApp.repository.config.value
        tile.state = if (config.enabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        if (Build.VERSION.SDK_INT >= 29) {
            tile.subtitle = if (config.enabled) eqApp.outputs.current.value.displayName else "Off"
        }
        tile.updateTile()
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(
                PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
            )
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
