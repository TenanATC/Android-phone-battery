package com.tenan.equalizer

import android.app.Application
import android.content.Context
import com.tenan.equalizer.data.EqRepository
import com.tenan.equalizer.device.OutputDeviceMonitor
import com.tenan.equalizer.engine.EngineStatus
import com.tenan.equalizer.engine.EqService
import com.tenan.equalizer.session.SessionRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class EqApp : Application() {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    lateinit var repository: EqRepository
        private set
    lateinit var outputs: OutputDeviceMonitor
        private set
    val sessions = SessionRegistry()

    /** Published by [EqService]: what each attached session is running. */
    val engineStatus = MutableStateFlow<Map<Int, EngineStatus>>(emptyMap())

    @OptIn(kotlinx.coroutines.FlowPreview::class)
    override fun onCreate() {
        super.onCreate()
        repository = EqRepository(this)
        outputs = OutputDeviceMonitor(this).also { it.start() }
        sessions.restoreAnnounced(repository.loadAnnouncedSessions())

        appScope.launch {
            sessions.sessions.drop(1).debounce(500).collect {
                repository.saveAnnouncedSessions(sessions.announced())
            }
        }
        // The service follows the master switch wherever it is flipped: the
        // app, the Quick Settings tile, or the notification.
        appScope.launch {
            repository.config.map { it.enabled }.distinctUntilChanged().collect { enabled ->
                if (enabled) EqService.start(this@EqApp)
            }
        }
    }
}

val Context.eqApp: EqApp get() = applicationContext as EqApp
