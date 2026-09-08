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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

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
        setContent {
            var showWelcome by remember { mutableStateOf(firstLaunch) }
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
