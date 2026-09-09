package com.blindtechnexus.alteloquencetts

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

/**
 * Activity for configuring voice guidance settings.
 */
class VoiceGuidanceSettingsActivity : ComponentActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            VoiceGuidanceSettingsScreen(
                onBack = { finish() }
            )
        }
    }
}

/**
 * Composable function for voice guidance settings screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceGuidanceSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val sharedPrefs = remember { 
        context.getSharedPreferences(CameraActivity.VOICE_GUIDANCE_PREFS, Context.MODE_PRIVATE) 
    }
    
    var voiceGuidanceEnabled by remember {
        mutableStateOf(sharedPrefs.getBoolean(CameraActivity.VOICE_GUIDANCE_ENABLED, true))
    }
    
    var vibrationEnabled by remember {
        mutableStateOf(sharedPrefs.getBoolean(CameraActivity.VIBRATION_ENABLED, true))
    }
    
    var vibrationIntensity by remember {
        mutableStateOf(sharedPrefs.getString(CameraActivity.VIBRATION_INTENSITY, CameraActivity.VIBRATION_MEDIUM) ?: CameraActivity.VIBRATION_MEDIUM)
    }
    
    Scaffold(
        topBar = {
            ScreenTopBar(
                title = stringResource(R.string.voice_guidance_settings),
                onBack = onBack
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Voice Guidance Toggle
            SettingRow(
                title = stringResource(R.string.voice_guidance_enabled),
                description = stringResource(R.string.voice_guidance_settings),
                control = {
                    Switch(
                        checked = voiceGuidanceEnabled,
                        onCheckedChange = { voiceGuidanceEnabled = it }
                    )
                }
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Vibration Toggle
            SettingRow(
                title = stringResource(R.string.vibration_enabled),
                description = stringResource(R.string.vibration_enabled),
                control = {
                    Switch(
                        checked = vibrationEnabled,
                        onCheckedChange = { vibrationEnabled = it }
                    )
                }
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Vibration Intensity
            Text(
                text = stringResource(R.string.vibration_intensity),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = 16.dp)
            )
            
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                VibrationIntensityOption(
                    text = stringResource(R.string.vibration_weak),
                    selected = vibrationIntensity == CameraActivity.VIBRATION_WEAK,
                    onSelected = { vibrationIntensity = CameraActivity.VIBRATION_WEAK }
                )
                
                VibrationIntensityOption(
                    text = stringResource(R.string.vibration_medium),
                    selected = vibrationIntensity == CameraActivity.VIBRATION_MEDIUM,
                    onSelected = { vibrationIntensity = CameraActivity.VIBRATION_MEDIUM }
                )
                
                VibrationIntensityOption(
                    text = stringResource(R.string.vibration_strong),
                    selected = vibrationIntensity == CameraActivity.VIBRATION_STRONG,
                    onSelected = { vibrationIntensity = CameraActivity.VIBRATION_STRONG }
                )
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Save and Close buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.End
            ) {
                Button(
                    onClick = onBack,
                    modifier = Modifier.padding(end = 8.dp)
                ) {
                    Text(text = stringResource(R.string.close))
                }
                
                Button(
                    onClick = {
                        // Save settings
                        with(sharedPrefs.edit()) {
                            putBoolean(CameraActivity.VOICE_GUIDANCE_ENABLED, voiceGuidanceEnabled)
                            putBoolean(CameraActivity.VIBRATION_ENABLED, vibrationEnabled)
                            putString(CameraActivity.VIBRATION_INTENSITY, vibrationIntensity)
                            apply()
                        }
                        onBack()
                    }
                ) {
                    Text(text = stringResource(R.string.save_settings))
                }
            }
        }
    }
}

/**
 * Setting row composable with a control.
 */
@Composable
fun SettingRow(
    title: String,
    description: String,
    control: @Composable () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall
            )
        }
        control()
    }
}

/**
 * Vibration intensity option radio button.
 */
@Composable
fun VibrationIntensityOption(
    text: String,
    selected: Boolean,
    onSelected: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {
        RadioButton(
            selected = selected,
            onClick = onSelected
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}