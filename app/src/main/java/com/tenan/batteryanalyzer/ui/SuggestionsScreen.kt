package com.tenan.batteryanalyzer.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.tenan.batteryanalyzer.analysis.Severity
import com.tenan.batteryanalyzer.analysis.Suggestion

@Composable
fun SuggestionsScreen(state: UiState, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (state.suggestions.isEmpty() && !state.loading) {
            item {
                Text(
                    "No issues found right now — your settings already look battery-friendly. " +
                        "Check back once more history has accumulated.",
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
        items(state.suggestions, key = { it.id }) { suggestion ->
            SuggestionCard(suggestion)
        }
    }
}

@Composable
private fun SuggestionCard(s: Suggestion) {
    val context = LocalContext.current
    Card {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SeverityBadge(s.severity)
                Text(
                    s.title,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
            }
            Text(
                s.detail,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            s.settingsAction?.let { action ->
                AssistChip(
                    onClick = {
                        try {
                            context.startActivity(
                                Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        } catch (e: ActivityNotFoundException) {
                            // Some OEM builds hide stock settings screens; nothing to do.
                        }
                    },
                    label = { Text("Open setting") },
                )
            }
        }
    }
}

@Composable
private fun SeverityBadge(severity: Severity) {
    val (label, color) = when (severity) {
        Severity.HIGH -> "High impact" to MaterialTheme.colorScheme.error
        Severity.MEDIUM -> "Medium" to MaterialTheme.colorScheme.tertiary
        Severity.LOW -> "Low" to MaterialTheme.colorScheme.secondary
        Severity.INFO -> "Info" to MaterialTheme.colorScheme.outline
    }
    SuggestionChip(
        onClick = {},
        enabled = false,
        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
        colors = androidx.compose.material3.SuggestionChipDefaults.suggestionChipColors(
            disabledLabelColor = color,
        ),
        border = androidx.compose.material3.SuggestionChipDefaults.suggestionChipBorder(
            enabled = false,
            disabledBorderColor = color.copy(alpha = 0.5f),
        ),
    )
}
