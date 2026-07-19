package com.tenan.batteryanalyzer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import android.os.BatteryManager
import kotlin.math.abs

@Composable
fun DashboardScreen(state: UiState, modifier: Modifier = Modifier) {
    val snap = state.snapshot
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Hero: battery level gauge
        Card {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        progress = { (snap?.level ?: 0) / 100f },
                        modifier = Modifier.size(96.dp),
                        strokeWidth = 8.dp,
                    )
                    Text(
                        "${snap?.level ?: "--"}%",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Column(modifier = Modifier.padding(start = 20.dp)) {
                    Text(
                        when {
                            snap == null -> "Reading…"
                            snap.isCharging -> "Charging" + pluggedLabel(snap.plugged)
                            else -> "On battery"
                        },
                        style = MaterialTheme.typography.titleMedium,
                    )
                    state.stats.estimatedHoursRemaining?.let {
                        Text(
                            "≈ ${formatHours(it)} remaining at your average rate",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    snap?.let {
                        if (it.currentNowUa != 0 && it.currentNowUa != Int.MIN_VALUE) {
                            Text(
                                "Draw now: ${abs(it.currentNowUa) / 1000} mA",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        // Device profile card — the "tuned to your phone" anchor
        Card {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Your phone", style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary)
                Text(state.profile.displayName, style = MaterialTheme.typography.titleMedium)
                Text(
                    "Android ${state.profile.androidVersion} (API ${state.profile.sdkInt})",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "Suggestions are tailored to ${state.profile.manufacturer}'s settings layout.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }

        // Vitals grid
        Card {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Vitals", style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary)
                VitalRow("Temperature", snap?.let { "%.1f °C".format(it.temperatureC) } ?: "—")
                VitalRow("Voltage", snap?.let { "%.2f V".format(it.voltageMv / 1000f) } ?: "—")
                VitalRow("System health flag", snap?.let { healthLabel(it.health) } ?: "—")
                VitalRow(
                    "Charge cycles",
                    snap?.cycleCount?.takeIf { it > 0 }?.toString()
                        ?: "not reported by this phone"
                )
                VitalRow(
                    "Estimated capacity now",
                    state.estimatedCapacityMah?.let { "$it mAh" }
                        ?: "not reported by this phone"
                )
            }
        }

        // Drain summary
        Card {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Drain rates (last 7 days)", style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary)
                VitalRow("Screen on", state.stats.screenOnDrainPerHour?.let { "%.1f %%/hr".format(it) } ?: "collecting…")
                VitalRow("Screen off", state.stats.screenOffDrainPerHour?.let { "%.1f %%/hr".format(it) } ?: "collecting…")
                VitalRow("Overall", state.stats.overallDrainPerHour?.let { "%.1f %%/hr".format(it) } ?: "collecting…")
                VitalRow("Samples", state.stats.sampleCount.toString())
                Text(
                    "The background sampler records a point every 15 minutes. Rates firm up " +
                        "after a day or two of normal use.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun VitalRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

private fun pluggedLabel(plugged: Int): String = when (plugged) {
    BatteryManager.BATTERY_PLUGGED_AC -> " (AC)"
    BatteryManager.BATTERY_PLUGGED_USB -> " (USB)"
    BatteryManager.BATTERY_PLUGGED_WIRELESS -> " (wireless)"
    else -> ""
}

private fun healthLabel(health: Int): String = when (health) {
    BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
    BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheating"
    BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
    BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over-voltage"
    BatteryManager.BATTERY_HEALTH_COLD -> "Cold"
    BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "Failure"
    else -> "Unknown"
}

internal fun formatHours(hours: Double): String {
    val h = hours.toInt()
    val m = ((hours - h) * 60).toInt()
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}
