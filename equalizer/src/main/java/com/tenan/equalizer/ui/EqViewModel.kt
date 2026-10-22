package com.tenan.equalizer.ui

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tenan.equalizer.EqApp
import com.tenan.equalizer.data.AttachMode
import com.tenan.equalizer.data.DeviceProfile
import com.tenan.equalizer.data.EqConfig
import com.tenan.equalizer.device.CorrectionSpec
import com.tenan.equalizer.device.Corrections
import com.tenan.equalizer.device.OutputDevice
import com.tenan.equalizer.device.SpatialAudioStatus
import com.tenan.equalizer.dsp.AutoEqParser
import com.tenan.equalizer.dsp.EqSettings
import com.tenan.equalizer.dsp.Preset
import com.tenan.equalizer.dsp.ProcessingPlan
import com.tenan.equalizer.dsp.ResponseCurve
import com.tenan.equalizer.engine.EngineStatus
import com.tenan.equalizer.engine.EqService
import com.tenan.equalizer.session.AppCategory
import com.tenan.equalizer.session.KnownApps
import com.tenan.equalizer.session.SessionSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

data class SessionRow(
    val sessionId: Int,
    val appName: String,
    val packageName: String?,
    /** Null for the global output mix. */
    val source: SessionSource?,
    val status: EngineStatus?,
    val appPresetName: String?,
)

data class AppRow(
    val packageName: String,
    val name: String,
    val category: AppCategory?,
    val suggestedPresetId: String?,
)

/** Facts about the phone that change outside the app; refreshed on resume. */
data class Environment(
    val dumpGranted: Boolean = false,
    val bluetoothGranted: Boolean = true,
    val notificationsGranted: Boolean = true,
    val ignoringBatteryOptimizations: Boolean = true,
    val spatialAudio: SpatialAudioStatus? = null,
    val audioApps: List<AppRow> = emptyList(),
    /** Set when a player's "Equalizer" button opened the app. */
    val openedFrom: String? = null,
)

data class EqUiState(
    val config: EqConfig = EqConfig(),
    val device: OutputDevice = OutputDevice.SPEAKER,
    val profile: DeviceProfile = EqConfig().profileFor(OutputDevice.SPEAKER),
    val battery: Int? = null,
    val totalCurve: ResponseCurve = ResponseCurve.FLAT,
    val correctionCurve: ResponseCurve? = null,
    val peakDb: Float = 0f,
    val effectivePreampDb: Float = 0f,
    val sessions: List<SessionRow> = emptyList(),
    val env: Environment = Environment(),
) {
    val settings: EqSettings get() = profile.settings
}

class EqViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as EqApp
    private val repository = app.repository
    private val env = MutableStateFlow(Environment())
    // Filled from both the main thread and the IO app scan.
    private val labels = ConcurrentHashMap<String, String>()

    private val deviceFlow = combine(app.outputs.current, app.outputs.battery) { d, b -> d to b }

    val state: StateFlow<EqUiState> = combine(
        repository.config, deviceFlow, app.sessions.sessions, app.engineStatus, env,
    ) { config, (device, battery), sessions, status, environment ->
        val profile = config.profileFor(device)
        val correction = Corrections.resolve(profile.correction)
        val plan = ProcessingPlan.build(profile.settings, null, correction)
        val rows = when (config.mode) {
            AttachMode.GLOBAL -> listOf(
                SessionRow(EqService.GLOBAL_SESSION, "All apps (global mix)", null, null,
                    status[EqService.GLOBAL_SESSION], null)
            )
            AttachMode.PER_APP -> sessions.values.sortedBy { it.sessionId }.map { s ->
                SessionRow(
                    s.sessionId, label(s.packageName), s.packageName, s.source, status[s.sessionId],
                    s.packageName?.let { config.appPresets[it] }?.let { config.preset(it)?.name },
                )
            }
        }
        EqUiState(
            config = config,
            device = device,
            profile = profile,
            battery = battery,
            totalCurve = plan.curve,
            correctionCurve = correction,
            peakDb = plan.peakGainDb,
            effectivePreampDb = plan.preampDb,
            sessions = rows,
            env = environment,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EqUiState())

    fun refreshEnvironment() {
        app.outputs.refresh()
        viewModelScope.launch {
            val ctx = getApplication<Application>()
            val apps = withContext(Dispatchers.IO) { installedAudioApps() }
            val power = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
            env.update {
                it.copy(
                    dumpGranted = granted(Manifest.permission.DUMP),
                    bluetoothGranted = app.outputs.hasBluetoothPermission(),
                    notificationsGranted = Build.VERSION.SDK_INT < 33 ||
                        granted(Manifest.permission.POST_NOTIFICATIONS),
                    ignoringBatteryOptimizations = power.isIgnoringBatteryOptimizations(ctx.packageName),
                    spatialAudio = app.outputs.spatialAudio(),
                    audioApps = apps,
                )
            }
        }
    }

    fun onOpenedFromPlayer(packageName: String?, sessionId: Int) {
        // The control-panel intent carries the player's session; treat it as
        // an announcement in case its broadcast was missed.
        if (sessionId > 0) app.sessions.announce(sessionId, packageName)
        env.update { it.copy(openedFrom = packageName?.let(::label) ?: "your player") }
    }

    fun dismissOpenedFrom() = env.update { it.copy(openedFrom = null) }

    fun setEnabled(enabled: Boolean) {
        repository.setEnabled(enabled)
        if (enabled) EqService.start(getApplication())
    }

    // Edits target the live output device, not the (possibly stale) UI state.
    private val device: OutputDevice get() = app.outputs.current.value

    fun updateSettings(transform: (EqSettings) -> EqSettings) = repository.updateSettings(device, transform)

    fun setBand(index: Int, db: Float) = updateSettings { it.withBand(index, db) }

    fun applyPreset(preset: Preset) = updateSettings { it.withPreset(preset) }

    fun saveCustomPreset(name: String) {
        val preset = repository.saveCustomPreset(name, repository.config.value.profileFor(device).settings.bandsDb)
        applyPreset(preset)
    }

    fun deleteCustomPreset(id: String) = repository.deleteCustomPreset(id)

    fun setCorrection(spec: CorrectionSpec) = repository.setCorrection(device, spec)

    /** Null on success, otherwise a message explaining what is wrong with the text. */
    fun importAutoEq(name: String, text: String): String? {
        AutoEqParser.parse(text).exceptionOrNull()?.let { return it.message ?: "Could not read that profile" }
        setCorrection(CorrectionSpec.Imported(name.ifBlank { "AutoEq profile" }.trim(), text))
        return null
    }

    fun setMode(mode: AttachMode) = repository.update { it.copy(mode = mode) }

    fun setAutoSwitch(on: Boolean) = repository.update { it.copy(autoSwitch = on) }

    fun setAppPreset(packageName: String, presetId: String?) = repository.setAppPreset(packageName, presetId)

    fun deleteProfile(key: String) = repository.deleteProfile(key)

    fun refreshBattery() = app.outputs.refresh()

    private fun granted(permission: String) =
        ContextCompat.checkSelfPermission(getApplication(), permission) == PackageManager.PERMISSION_GRANTED

    private fun label(packageName: String?): String {
        if (packageName == null) return "Unknown app"
        return labels.getOrPut(packageName) {
            KnownApps.find(packageName)?.name ?: try {
                val pm = getApplication<Application>().packageManager
                pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
            } catch (e: PackageManager.NameNotFoundException) {
                packageName
            }
        }
    }

    /** Installed major audio apps, plus any other app that has had a preset or a session. */
    private fun installedAudioApps(): List<AppRow> {
        val pm = getApplication<Application>().packageManager
        fun installed(pkg: String) = try {
            pm.getApplicationInfo(pkg, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
        val known = KnownApps.ALL.filter { installed(it.packageName) }
            .map { AppRow(it.packageName, it.name, it.category, it.suggestedPresetId) }
        val knownPackages = known.map { it.packageName }.toSet()
        val extra = (repository.config.value.appPresets.keys +
            app.sessions.sessions.value.values.mapNotNull { it.packageName })
            .filter { it !in knownPackages && it != getApplication<Application>().packageName && installed(it) }
            .distinct()
            .map { AppRow(it, label(it), null, null) }
        return known + extra.sortedBy { it.name.lowercase() }
    }
}
