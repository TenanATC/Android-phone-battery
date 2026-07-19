package com.tenan.batteryanalyzer.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.util.concurrent.TimeUnit

@Composable
fun AppUsageScreen(state: UiState, modifier: Modifier = Modifier) {
    val context = LocalContext.current

    if (!state.hasUsagePermission) {
        Column(
            modifier = modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "See which apps consume your battery",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                "Android requires the special \"Usage access\" grant for per-app statistics. " +
                    "Everything is analyzed on this phone; nothing is uploaded anywhere. " +
                    "Find \"${context.applicationInfo.loadLabel(context.packageManager)}\" in " +
                    "the list and toggle it on, then come back.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = {
                context.startActivity(
                    Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }) {
                Text("Open Usage access settings")
            }
        }
        return
    }

    val maxMillis = state.topApps.maxOfOrNull { it.foregroundMillis } ?: 1L
    LazyColumn(
        modifier = modifier,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Text(
                "Foreground time, last 7 days",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
        items(state.topApps, key = { it.packageName }) { app ->
            Card {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(app.label, style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium)
                        Text(
                            formatDuration(app.foregroundMillis),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    LinearProgressIndicator(
                        progress = { app.foregroundMillis.toFloat() / maxMillis },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

private fun formatDuration(millis: Long): String {
    val hours = TimeUnit.MILLISECONDS.toHours(millis)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}
