package com.tenan.equalizer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.BatteryStd
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tenan.equalizer.data.EqConfig
import com.tenan.equalizer.device.BeatsCatalog
import com.tenan.equalizer.device.BeatsModel
import com.tenan.equalizer.device.CorrectionSpec
import com.tenan.equalizer.device.Corrections
import com.tenan.equalizer.device.OutputKind

@Composable
fun DeviceScreen(
    state: EqUiState,
    viewModel: EqViewModel,
    onRequestBluetooth: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showImport by remember { mutableStateOf(false) }
    val beats = state.device.beats

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Current output.
        Card {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    when (state.device.kind) {
                        OutputKind.BLUETOOTH -> Icons.Filled.Bluetooth
                        OutputKind.USB -> Icons.Filled.Usb
                        OutputKind.SPEAKER -> Icons.AutoMirrored.Filled.VolumeUp
                        else -> Icons.Filled.Headphones
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Column(Modifier.padding(start = 16.dp).weight(1f)) {
                    Text("Playing through", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Text(state.device.displayName, style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (state.config.autoSwitch) "Has its own EQ profile; switches automatically on connect."
                        else "Sharing one profile across all devices.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (beats != null) BeatsCard(beats, state, viewModel, onRequestBluetooth)

        CorrectionCard(state, viewModel, onImport = { showImport = true })

        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Profiles", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                SwitchRow(
                    "Per-device profiles",
                    "Remember a separate EQ for each headphone, speaker and car, and load it when that device connects.",
                    state.config.autoSwitch,
                    viewModel::setAutoSwitch,
                )
                val saved = state.config.profiles.values.filter { it.key != EqConfig.DEFAULT_PROFILE_KEY || !state.config.autoSwitch }
                if (saved.isEmpty()) {
                    Text(
                        "No saved profiles yet — adjust the EQ and it is saved for the current device.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                saved.sortedBy { it.name.lowercase() }.forEach { p ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(p.name, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                (state.config.preset(p.settings.presetId)?.name ?: "Custom curve") +
                                    " · correction: " + Corrections.label(p.correction),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (p.key != state.profile.key) {
                            TextButton(onClick = { viewModel.deleteProfile(p.key) }) { Text("Forget") }
                        }
                    }
                }
            }
        }

        state.env.spatialAudio?.let { spatial ->
            Card {
                Column(Modifier.padding(16.dp)) {
                    Text("Android Spatial Audio", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Text(
                        when {
                            !spatial.available -> "Not available for the current output."
                            spatial.enabled -> "On" + if (spatial.headTracking) " · head tracking available" else ""
                            else -> "Available but off. Turn it on in the device's Bluetooth settings."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        "Spatial Audio is controlled by Android; this EQ is applied before it and works either way.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    if (showImport) {
        ImportDialog(
            defaultName = state.device.displayName,
            onDismiss = { showImport = false },
            onImport = { name, text ->
                viewModel.importAutoEq(name, text).also { if (it == null) showImport = false }
            },
        )
    }
}

@Composable
private fun BeatsCard(
    model: BeatsModel,
    state: EqUiState,
    viewModel: EqViewModel,
    onRequestBluetooth: () -> Unit,
) {
    val suggested = Corrections.suggestedFor(model)
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(model.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(
                model.family.label + if (model.hasAnc) " · noise cancelling" else "",
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.BatteryStd, contentDescription = null)
                Text(
                    when {
                        !state.env.bluetoothGranted -> "Battery: allow Bluetooth access to read it"
                        state.battery != null -> "Battery: ${state.battery}%"
                        else -> "Battery: not reported yet"
                    },
                    modifier = Modifier.padding(start = 8.dp).weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (!state.env.bluetoothGranted) {
                    TextButton(onClick = onRequestBluetooth) { Text("Allow") }
                } else {
                    TextButton(onClick = viewModel::refreshBattery) { Text("Refresh") }
                }
            }
            if (suggested != null && state.profile.correction != CorrectionSpec.BuiltIn(suggested.id)) {
                Button(onClick = { viewModel.setCorrection(CorrectionSpec.BuiltIn(suggested.id)) }) {
                    Text("Apply ${suggested.name}")
                }
            }
            Text("Tips for your ${model.name}", style = MaterialTheme.typography.labelLarge)
            BeatsCatalog.tips(model).forEach { tip ->
                Text("• $tip", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun CorrectionCard(state: EqUiState, viewModel: EqViewModel, onImport: () -> Unit) {
    val current = state.profile.correction
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Headphone correction", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text(
                "Applied underneath your EQ for this device, so presets sound as intended on these headphones.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            CorrectionOption("None", null, current == CorrectionSpec.None) {
                viewModel.setCorrection(CorrectionSpec.None)
            }
            Corrections.BUILT_IN.forEach { c ->
                CorrectionOption(c.name, c.description, current == CorrectionSpec.BuiltIn(c.id)) {
                    viewModel.setCorrection(CorrectionSpec.BuiltIn(c.id))
                }
            }
            if (current is CorrectionSpec.Imported) {
                CorrectionOption(current.name, "Imported AutoEq profile", selected = true) {}
            }
            Text(
                "Built-in Beats curves are mild starting points for the Beats house sound, not " +
                    "per-unit measurements. For a measured correction of your exact model, import its AutoEq profile.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            OutlinedButton(onClick = onImport, modifier = Modifier.padding(top = 4.dp)) {
                Text("Import AutoEq profile")
            }
        }
    }
}

@Composable
private fun CorrectionOption(title: String, subtitle: String?, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Column(Modifier.padding(start = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            subtitle?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ImportDialog(
    defaultName: String,
    onDismiss: () -> Unit,
    onImport: (name: String, text: String) -> String?,
) {
    var name by remember { mutableStateOf(defaultName) }
    var text by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val clipboard = LocalClipboardManager.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Import AutoEq profile") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "On autoeq.app, choose your exact headphones (e.g. Beats Fit Pro), pick the " +
                        "\"Wavelet\" (GraphicEQ) or \"Equalizer APO\" (ParametricEQ) export, copy " +
                        "the text, and paste it here.",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(value = name, onValueChange = { name = it.take(40) }, label = { Text("Name") }, singleLine = true)
                OutlinedTextField(
                    value = text,
                    onValueChange = {
                        text = it
                        error = null
                    },
                    label = { Text("Profile text") },
                    minLines = 4,
                    maxLines = 8,
                    isError = error != null,
                    supportingText = { error?.let { Text(it) } },
                    modifier = Modifier.heightIn(min = 120.dp),
                )
                TextButton(onClick = { clipboard.getText()?.text?.let { text = it } }) { Text("Paste from clipboard") }
            }
        },
        confirmButton = {
            TextButton(onClick = { error = onImport(name, text) }, enabled = text.isNotBlank()) { Text("Import") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
