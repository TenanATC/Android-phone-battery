package com.tenan.batteryanalyzer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tenan.batteryanalyzer.ui.components.BatteryChart

@Composable
fun HistoryScreen(
    state: UiState,
    onWindowChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for ((hours, label) in listOf(24 to "24h", 48 to "48h", 168 to "7d")) {
                FilterChip(
                    selected = state.historyWindowHours == hours,
                    onClick = { onWindowChange(hours) },
                    label = { Text(label) },
                )
            }
        }

        Card {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "Battery level",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                BatteryChart(history = state.history, windowHours = state.historyWindowHours)
            }
        }

        Card {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Patterns", style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary)
                StatRow("Deep discharges (<15%)", "${state.stats.deepDischargeCount}× this week")
                StatRow(
                    "Time at 100% while plugged",
                    "%.1f h".format(state.stats.hoursAtFullWhilePlugged)
                )
                StatRow(
                    "Hottest reading",
                    state.stats.maxTemperatureC?.let { "%.1f °C".format(it) } ?: "—"
                )
                StatRow(
                    "Avg temp while plugged in",
                    state.stats.avgChargingTemperatureC?.let { "%.1f °C".format(it) } ?: "—"
                )
            }
        }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}
