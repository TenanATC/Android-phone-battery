package com.tenan.batteryanalyzer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tenan.batteryanalyzer.ui.AppUsageScreen
import com.tenan.batteryanalyzer.ui.BatteryViewModel
import com.tenan.batteryanalyzer.ui.DashboardScreen
import com.tenan.batteryanalyzer.ui.HistoryScreen
import com.tenan.batteryanalyzer.ui.SuggestionsScreen
import com.tenan.batteryanalyzer.ui.theme.BatteryAnalyzerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BatteryAnalyzerTheme {
                BatteryAnalyzerApp()
            }
        }
    }
}

private data class Tab(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BatteryAnalyzerApp(viewModel: BatteryViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var selectedTab by remember { mutableIntStateOf(0) }

    // Refresh whenever the app comes to the foreground (e.g. returning from
    // the Usage access settings screen).
    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

    val tabs = listOf(
        Tab("Battery", Icons.Filled.BatteryFull),
        Tab("History", Icons.Filled.ShowChart),
        Tab("Advice", Icons.Filled.Lightbulb),
        Tab("Apps", Icons.Filled.Apps),
    )

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Battery Analyzer") })
        },
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { padding ->
        val contentModifier = Modifier.padding(padding)
        when (selectedTab) {
            0 -> DashboardScreen(state, contentModifier)
            1 -> HistoryScreen(state, viewModel::setHistoryWindow, contentModifier)
            2 -> SuggestionsScreen(state, contentModifier)
            else -> AppUsageScreen(state, contentModifier)
        }
    }
}
