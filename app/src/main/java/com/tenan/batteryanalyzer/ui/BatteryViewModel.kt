package com.tenan.batteryanalyzer.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tenan.batteryanalyzer.analysis.BatteryAnalyzer
import com.tenan.batteryanalyzer.analysis.DeviceProfile
import com.tenan.batteryanalyzer.analysis.DrainStats
import com.tenan.batteryanalyzer.analysis.Suggestion
import com.tenan.batteryanalyzer.analysis.SuggestionEngine
import com.tenan.batteryanalyzer.data.AppUsage
import com.tenan.batteryanalyzer.data.BatteryHistoryStore
import com.tenan.batteryanalyzer.data.BatteryReader
import com.tenan.batteryanalyzer.data.BatterySnapshot
import com.tenan.batteryanalyzer.data.UsageStatsReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

data class UiState(
    val loading: Boolean = true,
    val profile: DeviceProfile = DeviceProfile.detect(),
    val snapshot: BatterySnapshot? = null,
    val estimatedCapacityMah: Int? = null,
    val history: List<BatterySnapshot> = emptyList(),
    val stats: DrainStats = BatteryAnalyzer.analyze(emptyList(), null),
    val suggestions: List<Suggestion> = emptyList(),
    val topApps: List<AppUsage> = emptyList(),
    val hasUsagePermission: Boolean = false,
    /** History window in hours shown on the chart */
    val historyWindowHours: Int = 24,
)

class BatteryViewModel(app: Application) : AndroidViewModel(app) {

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state

    fun refresh() {
        val context = getApplication<Application>()
        viewModelScope.launch {
            val windowHours = _state.value.historyWindowHours
            val result = try {
                withContext(Dispatchers.IO) {
                        // Record the live reading too, so opening the app improves resolution.
                    snapshot?.let { BatteryHistoryStore.get(context).insert(it) }

                    val history = BatteryHistoryStore.get(context).samplesSince(
                        System.currentTimeMillis() - TimeUnit.DAYS.toMillis(7)
                    )
                    val stats = BatteryAnalyzer.analyze(history, snapshot?.level)
                    val hasUsage = UsageStatsReader.hasPermission(context)
                    val topApps = if (hasUsage) UsageStatsReader.topApps(context, days = 7) else emptyList()
                    val profile = DeviceProfile.detect()
                    val suggestions = SuggestionEngine.generate(
                        context, profile, snapshot, stats, topApps, hasUsage
                    )
                    UiState(
                        loading = false,
                        profile = profile,
                        snapshot = snapshot,
                        estimatedCapacityMah = snapshot?.let { BatteryReader.estimateFullCapacityMah(it) },
                        history = history,
                        stats = stats,
                        suggestions = suggestions,
                        topApps = topApps,
                        hasUsagePermission = hasUsage,
                        historyWindowHours = windowHours,
                    )
                }
            } catch (e: Exception) {
                // Never let a bad system read crash the app; show what we have.
                _state.value.copy(loading = false)
            }
            _state.value = result
        }
    }

    fun setHistoryWindow(hours: Int) {
        _state.value = _state.value.copy(historyWindowHours = hours)
    }
}
