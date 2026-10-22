package com.tenan.equalizer

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.media.audiofx.AudioEffect
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tenan.equalizer.ui.AppsScreen
import com.tenan.equalizer.ui.DeviceScreen
import com.tenan.equalizer.ui.EqViewModel
import com.tenan.equalizer.ui.EqualizerScreen
import com.tenan.equalizer.ui.theme.StudioEqTheme

class MainActivity : ComponentActivity() {
    private val viewModel: EqViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            StudioEqTheme {
                StudioEqApp(viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /**
     * A player's "Equalizer" button (Spotify: Settings → Playback → Equalizer)
     * launches the system EQ panel action with its audio session attached.
     */
    private fun handleIntent(intent: Intent?) {
        if (intent?.action != AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL) return
        viewModel.onOpenedFromPlayer(
            intent.getStringExtra(AudioEffect.EXTRA_PACKAGE_NAME) ?: callingPackage,
            intent.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION, AudioEffect.ERROR_BAD_VALUE),
        )
        setResult(Activity.RESULT_OK)
    }
}

private data class Tab(val label: String, val icon: ImageVector)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StudioEqApp(viewModel: EqViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    // Permissions change in Settings and over adb; re-check whenever we return.
    LifecycleResumeEffect(Unit) {
        viewModel.refreshEnvironment()
        onPauseOrDispose { }
    }

    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { viewModel.refreshEnvironment() }
    val bluetoothPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { viewModel.refreshEnvironment() }

    val onToggle: (Boolean) -> Unit = { on ->
        // Bluetooth access is asked for on the Device tab, where the Beats
        // battery reading explains why; two prompts at once would drop one.
        if (on && Build.VERSION.SDK_INT >= 33 && !state.env.notificationsGranted) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        viewModel.setEnabled(on)
    }
    val requestBluetooth = {
        if (Build.VERSION.SDK_INT >= 31) bluetoothPermission.launch(Manifest.permission.BLUETOOTH_CONNECT)
    }

    val tabs = listOf(
        Tab("Equalizer", Icons.Filled.Equalizer),
        Tab("Device", Icons.Filled.Headphones),
        Tab("Apps", Icons.AutoMirrored.Filled.QueueMusic),
    )

    Scaffold(
        topBar = { TopAppBar(title = { Text("Studio EQ") }) },
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
            0 -> EqualizerScreen(state, viewModel, onToggle, contentModifier)
            1 -> DeviceScreen(state, viewModel, requestBluetooth, contentModifier)
            else -> AppsScreen(state, viewModel, contentModifier)
        }
    }
}
