package com.tenan.equalizer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tenan.equalizer.data.AttachMode
import com.tenan.equalizer.device.Corrections
import com.tenan.equalizer.dsp.Bands
import com.tenan.equalizer.dsp.EqSettings
import com.tenan.equalizer.dsp.PresetCategory
import com.tenan.equalizer.dsp.Presets
import com.tenan.equalizer.ui.components.BandSlider
import com.tenan.equalizer.ui.components.ResponseGraph
import com.tenan.equalizer.ui.components.formatDb
import kotlin.math.roundToInt

@Composable
fun EqualizerScreen(
    state: EqUiState,
    viewModel: EqViewModel,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val settings = state.settings
    var showSave by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        state.env.openedFrom?.let { from ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Opened from $from. " + if (state.config.enabled) {
                            "Your EQ applies to its audio."
                        } else {
                            "Turn the equalizer on to apply it."
                        },
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    TextButton(onClick = viewModel::dismissOpenedFrom) { Text("OK") }
                }
            }
        }

        // Master switch and where the EQ is acting.
        Card {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Equalizer", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text(
                        statusLine(state),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "Profile: ${state.profile.name}" +
                            (Corrections.label(state.profile.correction).takeIf { it != "None" }
                                ?.let { " · correction: $it" } ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Switch(checked = state.config.enabled, onCheckedChange = onToggle)
            }
        }

        Card {
            Column(Modifier.padding(vertical = 12.dp)) {
                ResponseGraph(
                    total = state.totalCurve,
                    correction = state.correctionCurve,
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
                if (state.correctionCurve != null) {
                    Text(
                        "Dashed: headphone correction (included in the solid curve)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 16.dp, top = 4.dp),
                    )
                }

                LazyRow(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    items(state.config.allPresets, key = { it.id }) { preset ->
                        FilterChip(
                            selected = settings.presetId == preset.id,
                            onClick = { viewModel.applyPreset(preset) },
                            label = { Text(preset.name) },
                        )
                    }
                }

                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 8.dp),
                ) {
                    Bands.GRAPHIC_HZ.forEachIndexed { i, hz ->
                        BandSlider(
                            label = Bands.label(hz),
                            valueDb = settings.bandsDb[i],
                            range = Bands.MIN_GAIN_DB..Bands.MAX_GAIN_DB,
                            onChange = { viewModel.setBand(i, it) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                Row(Modifier.padding(horizontal = 8.dp)) {
                    TextButton(onClick = { viewModel.applyPreset(Presets.builtIn(Presets.FLAT_ID)!!) }) {
                        Text("Reset")
                    }
                    TextButton(onClick = { showSave = true }) { Text("Save as preset") }
                    val custom = state.config.preset(settings.presetId)
                        ?.takeIf { it.category == PresetCategory.CUSTOM }
                    if (custom != null) {
                        TextButton(onClick = { viewModel.deleteCustomPreset(custom.id) }) {
                            Text("Delete \"${custom.name}\"")
                        }
                    }
                }
            }
        }

        ToneCard(settings, state, viewModel)
    }

    if (showSave) {
        SavePresetDialog(
            onDismiss = { showSave = false },
            onSave = {
                viewModel.saveCustomPreset(it)
                showSave = false
            },
        )
    }
}

@Composable
private fun ToneCard(settings: EqSettings, state: EqUiState, viewModel: EqViewModel) {
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Tone & output", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            LabeledSlider("Bass boost", "${formatDb(settings.bassDb)} dB", settings.bassDb, 0f..12f) { v ->
                viewModel.updateSettings { it.copy(bassDb = v.half()) }
            }
            LabeledSlider("Treble", "${formatDb(settings.trebleDb)} dB", settings.trebleDb, -6f..12f) { v ->
                viewModel.updateSettings { it.copy(trebleDb = v.half()) }
            }
            LabeledSlider(
                "Virtualizer (stereo width)", "${(settings.virtualizer * 100).roundToInt()}%",
                settings.virtualizer, 0f..1f,
            ) { v -> viewModel.updateSettings { it.copy(virtualizer = v) } }
            LabeledSlider("Loudness", "${formatDb(settings.loudnessDb)} dB", settings.loudnessDb, 0f..10f) { v ->
                viewModel.updateSettings { it.copy(loudnessDb = v.half()) }
            }
            LabeledSlider("Balance", balanceLabel(settings.balance), settings.balance, -1f..1f) { v ->
                // Snap to centre so it is easy to undo.
                val snapped = if (kotlin.math.abs(v) < 0.04f) 0f else v
                viewModel.updateSettings { it.copy(balance = snapped) }
            }
            LabeledSlider("Preamp", "${formatDb(settings.preampDb)} dB", settings.preampDb, -12f..6f) { v ->
                viewModel.updateSettings { it.copy(preampDb = v.half()) }
            }
            SwitchRow(
                "Auto headroom",
                "Lowers the preamp by the biggest boost (now ${formatDb(state.peakDb)} dB → preamp " +
                    "${formatDb(state.effectivePreampDb)} dB) so boosts never clip.",
                settings.autoHeadroom,
            ) { on -> viewModel.updateSettings { it.copy(autoHeadroom = on) } }
            SwitchRow(
                "Limiter",
                "Catches peaks just below full scale. Needs Android 9+.",
                settings.limiter,
            ) { on -> viewModel.updateSettings { it.copy(limiter = on) } }
        }
    }
}

@Composable
internal fun LabeledSlider(
    label: String,
    valueLabel: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(valueLabel, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        }
        Slider(value = value, onValueChange = onChange, valueRange = range)
    }
}

@Composable
internal fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun SavePresetDialog(onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save preset") },
        text = {
            OutlinedTextField(value = name, onValueChange = { name = it.take(32) }, label = { Text("Name") }, singleLine = true)
        },
        confirmButton = {
            TextButton(onClick = { onSave(name) }, enabled = name.isNotBlank()) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun statusLine(state: EqUiState): String {
    if (!state.config.enabled) return "Off"
    return when (state.config.mode) {
        AttachMode.GLOBAL -> "On · all apps (global mix)"
        AttachMode.PER_APP -> when (val n = state.sessions.size) {
            0 -> "On · start playback in any app"
            1 -> "On · ${state.sessions.first().appName}"
            else -> "On · $n apps"
        }
    }
}

private fun balanceLabel(b: Float): String {
    val pct = (kotlin.math.abs(b) * 100).roundToInt()
    return when {
        pct == 0 -> "Centre"
        b < 0 -> "L $pct%"
        else -> "R $pct%"
    }
}

private fun Float.half(): Float = (this * 2).roundToInt() / 2f
