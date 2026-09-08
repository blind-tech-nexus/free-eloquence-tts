package com.blindtechnexus.alteloquencetts

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource

/** The engine's own settings, reached from the system's text-to-speech screen
 *  and from the launcher. */
class SettingsActivity : ComponentActivity() {

    private var model: SettingsModel? = null

    private val firstLaunchPreferences by lazy {
        getSharedPreferences(FIRST_LAUNCH_PREFERENCES, MODE_PRIVATE)
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        requestBatteryOptimizationExemption()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val firstLaunch = !firstLaunchPreferences.getBoolean(FIRST_LAUNCH_COMPLETE, false)
        val notDefaultTts = !isDefaultTts()
        setContent {
            var showWelcome by remember { mutableStateOf(firstLaunch) }
            var showDefaultTtsDialog by remember { mutableStateOf(notDefaultTts) }
            MaterialTheme {
                Surface {
                    if (showWelcome) {
                        WelcomeScreen {
                            firstLaunchPreferences.edit()
                                .putBoolean(FIRST_LAUNCH_COMPLETE, true)
                                .apply()
                            showWelcome = false
                            requestNotificationPermission()
                        }
                    } else {
                        val state = remember {
                            SettingsModel(this@SettingsActivity).also { model = it }
                        }
                        SettingsScreen(state)
                    }

                    if (showDefaultTtsDialog) {
                        AlertDialog(
                            onDismissRequest = { showDefaultTtsDialog = false },
                            title = {
                                Text(text = stringResource(R.string.default_tts_title))
                            },
                            text = {
                                Text(text = stringResource(R.string.default_tts_message))
                            },
                            confirmButton = {
                                TextButton(
                                    onClick = {
                                        showDefaultTtsDialog = false
                                        openTtsSettings()
                                    }
                                ) {
                                    Text(text = stringResource(R.string.set_as_default))
                                }
                            },
                            dismissButton = {
                                TextButton(
                                    onClick = { showDefaultTtsDialog = false }
                                ) {
                                    Text(text = stringResource(R.string.cancel))
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    /** Checks whether Alt Eloquence TTS is currently the default speech engine. */
    private fun isDefaultTts(): Boolean {
        return try {
            val defaultEngine = android.provider.Settings.Secure.getString(
                contentResolver,
                android.provider.Settings.Secure.TTS_DEFAULT_SYNTH
            )
            defaultEngine == packageName
        } catch (_: Exception) {
            false
        }
    }

    /** Opens the system Text-to-speech settings screen. */
    private fun openTtsSettings() {
        val intent = Intent("com.android.settings.TTS_SETTINGS").apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        try {
            startActivity(intent)
        } catch (_: Exception) {
            try {
                startActivity(
                    Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                )
            } catch (_: Exception) {
                try {
                    startActivity(
                        Intent(android.provider.Settings.ACTION_SETTINGS).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                    )
                } catch (_: Exception) {
                    // Ignore if settings cannot be opened
                }
            }
        }
    }

    /** Notification access is runtime-gated on Android 13 and later. */
    private fun requestNotificationPermission() {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            requestBatteryOptimizationExemption()
        }
    }

    /** Keep the speech engine available when a device's battery policy is
     *  aggressive. Android presents the system-owned confirmation screen. */
    private fun requestBatteryOptimizationExemption() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        val power = getSystemService(PowerManager::class.java) ?: return
        if (power.isIgnoringBatteryOptimizations(packageName)) return
        try {
            startActivity(
                Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                }
            )
        } catch (_: SecurityException) {
            // Some device manufacturers do not expose this confirmation
            // screen. The engine remains usable without the exemption.
        } catch (_: android.content.ActivityNotFoundException) {
            // Likewise, do not block the settings screen on a missing system UI.
        }
    }

    override fun onStop() {
        super.onStop()
        // Leaving the screen should not leave a sample talking over whatever
        // comes next.
        model?.stopSpeaking()
    }

    override fun onDestroy() {
        super.onDestroy()
        model?.close()
        model = null
    }

    private companion object {
        const val FIRST_LAUNCH_PREFERENCES = "welcome"
        const val FIRST_LAUNCH_COMPLETE = "completed"
    }
}
