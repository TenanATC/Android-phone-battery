package com.tenan.equalizer.ui

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.tenan.equalizer.data.AttachMode
import com.tenan.equalizer.session.MediaNotificationListener
import com.tenan.equalizer.session.SessionSource

@Composable
fun AppsScreen(state: EqUiState, viewModel: EqViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Live sessions.
        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Now equalising", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                when {
                    !state.config.enabled -> Hint("The equalizer is off.")
                    state.sessions.isEmpty() && state.musicActive -> {
                        Text(
                            "Something is playing, but that app hasn't announced its audio " +
                                "(Podbean doesn't), so Per-app mode can't reach it.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Button(onClick = { viewModel.setMode(AttachMode.GLOBAL) }) { Text("Switch to Global") }
                    }
                    state.sessions.isEmpty() -> Hint(
                        "No player has an audio session yet. Start playback in Spotify, Podbean or " +
                            "any other app. If an app plays but never shows up here, turn on " +
                            "advanced detection below or switch to Global mode."
                    )
                }
                if (state.config.enabled) {
                    state.sessions.forEach { row ->
                        Column {
                            Text(row.appName, style = MaterialTheme.typography.bodyMedium)
                            Hint(
                                listOfNotNull(
                                    when (row.source) {
                                        SessionSource.ANNOUNCED -> "announced by the app"
                                        SessionSource.DETECTED -> "found by advanced detection"
                                        null -> "global output mix"
                                    },
                                    "session ${row.sessionId}",
                                    row.status?.engine,
                                    row.appPresetName?.let { "preset: $it" },
                                ).joinToString(" · ")
                            )
                        }
                    }
                }
            }
        }

        // Per-app presets.
        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Per-app presets", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Hint(
                    "Give an app its own curve — e.g. Podcast / Voice for Podbean while Spotify " +
                        "keeps your music EQ. The device's headphone correction and tone settings still apply."
                )
                if (state.config.mode == AttachMode.GLOBAL) NotificationAccessNote(state)
                if (state.env.audioApps.isEmpty()) Hint("None of the major audio apps are installed.")
                state.env.audioApps.forEach { app ->
                    val current = state.config.appPresets[app.packageName]
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(app.name, style = MaterialTheme.typography.bodyMedium)
                            app.category?.let { Hint(it.label) }
                        }
                        PresetPicker(
                            selectedName = state.config.preset(current)?.name ?: "Device EQ",
                            options = listOf<Pair<String?, String>>(null to "Device EQ") +
                                state.config.allPresets.map { it.id to it.name },
                            suggestedId = app.suggestedPresetId,
                            onPick = { viewModel.setAppPreset(app.packageName, it) },
                        )
                    }
                }
            }
        }

        // How the EQ reaches other apps.
        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Connection mode", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                ModeOption(
                    "Per-app (recommended)",
                    "Attaches to each player's own audio. Supports per-app presets. Works with apps " +
                        "that announce their audio (Spotify and most players) and, with advanced " +
                        "detection, with every app.",
                    state.config.mode == AttachMode.PER_APP,
                ) { viewModel.setMode(AttachMode.PER_APP) }
                ModeOption(
                    "Global",
                    "Attaches to the phone's whole output mix. Reaches any app without setup, " +
                        "including Podbean. One curve at a time: with Notification access it switches " +
                        "to the playing app's preset. Some phones ignore the global mix.",
                    state.config.mode == AttachMode.GLOBAL,
                ) { viewModel.setMode(AttachMode.GLOBAL) }
            }
        }

        AdvancedDetectionCard(state)

        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Using it with your apps", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Hint(
                    "Spotify: Settings → Playback → Equalizer opens this app — or, on phones with a " +
                        "built-in equalizer, asks which to use or opens the built-in one. Either way " +
                        "Studio EQ still processes Spotify's audio; just don't enable both EQs at once."
                )
                Hint(
                    "Podbean and other podcast apps: start an episode, then check \"Now equalising\". " +
                        "If it's missing, use advanced detection or Global mode."
                )
                Hint(
                    "Most music players announce their audio and work with no setup; any app that " +
                        "doesn't is covered by advanced detection or Global mode."
                )
                if (!state.env.ignoringBatteryOptimizations) {
                    HorizontalDivider(Modifier.padding(vertical = 4.dp))
                    Hint(
                        "Some phones (notably Samsung) stop background services to save battery, which " +
                            "switches the EQ off silently. Exempt this app to keep it running."
                    )
                    OutlinedButton(onClick = {
                        context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                    }) { Text("Battery optimisation settings") }
                }
                if (!state.env.notificationsGranted) {
                    Hint("Notifications are off, so the EQ's status and off switch won't show in the shade.")
                    OutlinedButton(onClick = {
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                .setData(Uri.fromParts("package", context.packageName, null))
                        )
                    }) { Text("App settings") }
                }
            }
        }
    }
}

/**
 * Global mode can only follow the playing app with Notification access, and
 * Android 13+ blocks that switch for sideloaded apps until "Allow restricted
 * settings" is turned on in App info.
 */
@Composable
private fun NotificationAccessNote(state: EqUiState) {
    val context = LocalContext.current
    if (state.env.notificationAccess) {
        Text(
            "Global mode: the whole mix switches to the playing app's preset.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(vertical = 4.dp),
        )
        return
    }
    Column(Modifier.padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            "In Global mode these need Notification access, so Studio EQ can see which app is " +
                "playing. It only reads the media controls, never your notifications' contents, and " +
                "the app has no internet access.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Button(onClick = { openNotificationAccess(context) }) { Text("Allow notification access") }
        if (Build.VERSION.SDK_INT >= 33) {
            Hint(
                "If Android says \"Restricted setting\": open App info, tap ⋮ (top right) → " +
                    "Allow restricted settings, then try again. Android does this for apps installed " +
                    "from outside the Play Store."
            )
            OutlinedButton(onClick = {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        .setData(Uri.fromParts("package", context.packageName, null))
                )
            }) { Text("App info") }
        }
    }
}

private fun openNotificationAccess(context: Context) {
    val component = ComponentName(context, MediaNotificationListener::class.java)
    val detail = if (Build.VERSION.SDK_INT >= 30) {
        Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
            .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, component.flattenToString())
    } else {
        null
    }
    try {
        context.startActivity(detail ?: Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
    } catch (e: ActivityNotFoundException) {
        // Some phones lack the per-app detail screen; the list always exists.
        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
    }
}

@Composable
private fun AdvancedDetectionCard(state: EqUiState) {
    val clipboard = LocalClipboardManager.current
    val command = "adb shell pm grant ${LocalContext.current.packageName} android.permission.DUMP"
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Advanced detection", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            if (state.env.dumpGranted) {
                Text("On — every playing app is found automatically.", style = MaterialTheme.typography.bodyMedium)
            } else {
                Text("Off", style = MaterialTheme.typography.bodyMedium)
                Hint(
                    "For apps that don't announce their audio, grant this app read access to the " +
                        "system playback list. It's a one-time step from a computer with adb " +
                        "(USB debugging on), and survives reboots:"
                )
                SelectionContainer {
                    Text(command, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                }
                TextButton(onClick = { clipboard.setText(AnnotatedString(command)) }) { Text("Copy command") }
            }
        }
    }
}

@Composable
private fun PresetPicker(
    selectedName: String,
    options: List<Pair<String?, String>>,
    suggestedId: String?,
    onPick: (String?) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { open = true }) {
            Text(selectedName)
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { (id, name) ->
                DropdownMenuItem(
                    text = { Text(if (id != null && id == suggestedId) "$name (suggested)" else name) },
                    onClick = {
                        onPick(id)
                        open = false
                    },
                )
            }
        }
    }
}

@Composable
private fun ModeOption(title: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(vertical = 6.dp),
    ) {
        RadioButton(selected = selected, onClick = null)
        Column(Modifier.padding(start = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Hint(subtitle)
        }
    }
}

@Composable
internal fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
