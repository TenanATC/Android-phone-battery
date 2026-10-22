package com.tenan.equalizer.device

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.Spatializer
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class SpatialAudioStatus(val available: Boolean, val enabled: Boolean, val headTracking: Boolean)

/**
 * Tracks the current media output (so each device gets its own profile) and,
 * for Bluetooth headphones such as Beats, their battery level.
 */
class OutputDeviceMonitor(private val context: Context) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _current = MutableStateFlow(OutputDevice.SPEAKER)
    val current: StateFlow<OutputDevice> = _current

    /** Battery percentage of the current Bluetooth output, when the headset reports it. */
    private val _battery = MutableStateFlow<Int?>(null)
    val battery: StateFlow<Int?> = _battery

    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) = refresh()
        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) = refresh()
    }

    private val batteryReceiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(context: Context, intent: Intent) {
            val device = IntentCompat.getParcelableExtra(
                intent, BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java
            )
            val level = intent.getIntExtra(EXTRA_BATTERY_LEVEL, -1)
            val name = try { device?.name } catch (e: SecurityException) { null }
            if (level in 0..100 && name != null && name == _current.value.name) {
                _battery.value = level
            }
        }
    }

    fun start() {
        audioManager.registerAudioDeviceCallback(deviceCallback, mainHandler)
        // A protected system broadcast; delivered only with BLUETOOTH_CONNECT.
        ContextCompat.registerReceiver(
            context, batteryReceiver, IntentFilter(ACTION_BATTERY_LEVEL_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        refresh()
    }

    fun refresh() {
        val outputs = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).mapNotNull(::classify)
        val picked = OutputDevice.pickMediaOutput(outputs)
        if (picked != _current.value) {
            _current.value = picked
            _battery.value = null
        }
        if (picked.kind == OutputKind.BLUETOOTH) readBattery(picked.name)?.let { _battery.value = it }
    }

    fun hasBluetoothPermission(): Boolean =
        Build.VERSION.SDK_INT < 31 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED

    fun spatialAudio(): SpatialAudioStatus? {
        if (Build.VERSION.SDK_INT < 32) return null
        val spatializer: Spatializer = audioManager.spatializer
        return SpatialAudioStatus(
            available = spatializer.isAvailable,
            enabled = spatializer.isEnabled,
            headTracking = spatializer.isHeadTrackerAvailable,
        )
    }

    private fun classify(info: AudioDeviceInfo): OutputDevice? {
        val name = info.productName?.toString().orEmpty()
        val kind = when (info.type) {
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_BLE_HEADSET,
            AudioDeviceInfo.TYPE_BLE_SPEAKER,
            AudioDeviceInfo.TYPE_BLE_BROADCAST -> OutputKind.BLUETOOTH
            AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_USB_DEVICE,
            AudioDeviceInfo.TYPE_USB_ACCESSORY -> OutputKind.USB
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> OutputKind.WIRED
            AudioDeviceInfo.TYPE_HEARING_AID -> OutputKind.HEARING_AID
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> OutputKind.SPEAKER
            // Earpiece, SCO (calls), HDMI, telephony etc. are not media outputs we profile.
            else -> return null
        }
        return OutputDevice(kind, if (kind == OutputKind.SPEAKER) "" else name)
    }

    /**
     * Android tracks the battery level Beats and most headsets report, but
     * only exposes it through a hidden method; read it reflectively and treat
     * any failure as "not reported".
     */
    @SuppressLint("MissingPermission")
    private fun readBattery(deviceName: String): Int? {
        if (!hasBluetoothPermission()) return null
        return try {
            val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            val adapter = manager?.adapter ?: return null
            val device = adapter.bondedDevices?.firstOrNull { it.name == deviceName } ?: return null
            val level = BluetoothDevice::class.java.getMethod("getBatteryLevel").invoke(device) as? Int
            level?.takeIf { it in 0..100 }
        } catch (e: Exception) {
            null
        }
    }

    private companion object {
        const val ACTION_BATTERY_LEVEL_CHANGED = "android.bluetooth.device.action.BATTERY_LEVEL_CHANGED"
        const val EXTRA_BATTERY_LEVEL = "android.bluetooth.device.extra.BATTERY_LEVEL"
    }
}
