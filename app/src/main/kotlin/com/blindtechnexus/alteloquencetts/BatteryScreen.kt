package com.blindtechnexus.alteloquencetts

import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

/**
 * Everything this app can do to keep Android from killing the engine in the
 * background.
 *
 * Four things are here, and each one is the one its row says it is:
 *
 * The exemption from battery optimization, which is the single most important
 * and cannot be granted from inside the app -- the button hands over to the
 * system's own screen, and the state shown here is read again on every resume,
 * because the answer can change while the system screen is up.
 *
 * A persistent notification, which holds the service in the foreground where
 * the system's rules do not kill it, and which the service itself checks for
 * every few seconds and restores if anything takes it away.
 *
 * A wake lock, which holds the CPU awake for the length of one utterance on
 * devices where the processor sleeps mid sentence and drops the tail of the
 * speech.
 *
 * A switch that writes what is changed here to the system log, so a stopped
 * voice can be traced back to what was changed.
 *
 * Every one of the four has its sentence above it, the way the rest of the
 * settings do: the names do not carry their own meaning, and a description
 * folded into the control would be read out again on every visit with no way
 * past it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatteryScreen(onBack: () -> Unit, onRequestExemption: () -> Unit) {
	val context = LocalContext.current
	val settings = remember { Settings(context.applicationContext) }
	var granted by remember { mutableStateOf(BatteryExemption.isGranted(context)) }
	var persistent by remember { mutableStateOf(settings.persistentNotification) }
	var wakeLock by remember { mutableStateOf(settings.wakeLock) }
	var tracking by remember { mutableStateOf(settings.trackActions) }

	// The exemption is granted on a screen belonging to the system, and the
	// answer is read again when that screen is left.
	val activity = context as? ComponentActivity
	DisposableEffect(activity) {
		val observer = LifecycleEventObserver { _, event ->
			if (event == Lifecycle.Event.ON_RESUME) granted = BatteryExemption.isGranted(context)
		}
		activity?.lifecycle?.addObserver(observer)
		onDispose { activity?.lifecycle?.removeObserver(observer) }
	}

	Scaffold(
		topBar = {
			ScreenTopBar(
				title = stringResource(R.string.section_battery),
				onBack = onBack
			)
		}
	) { inset ->
		Column(
			modifier = Modifier
				.fillMaxSize()
				.padding(inset)
				.verticalScroll(rememberScrollState())
				.padding(bottom = 24.dp)
		) {
			SectionText(text = stringResource(R.string.battery_screen_description))
			ExemptionRow(
				granted = granted,
				onClick = {
					Tracker.action(settings, "asked for the battery optimization exemption")
					onRequestExemption()
				}
			)
			BatterySwitch(
				label = stringResource(R.string.persistent_notification_label),
				description = stringResource(R.string.persistent_notification_description),
				checked = persistent,
				onChange = { on ->
					persistent = on
					settings.persistentNotification = on
					Tracker.action(settings, "persistent notification ${if (on) "on" else "off"}")
				}
			)
			BatterySwitch(
				label = stringResource(R.string.wake_lock_label),
				description = stringResource(R.string.wake_lock_description),
				checked = wakeLock,
				onChange = { on ->
					wakeLock = on
					settings.wakeLock = on
					Tracker.action(settings, "wake lock ${if (on) "on" else "off"}")
				}
			)
			BatterySwitch(
				label = stringResource(R.string.track_actions_label),
				description = stringResource(R.string.track_actions_description),
				checked = tracking,
				onChange = { on ->
					tracking = on
					settings.trackActions = on
					Tracker.action(settings, "action tracking ${if (on) "on" else "off"}")
				}
			)
		}
	}
}

/** The one setting this app cannot grant, with whether it has been given. */
@Composable
private fun ExemptionRow(granted: Boolean, onClick: () -> Unit) {
	val state = stringResource(
		if (granted) R.string.battery_exemption_granted else R.string.battery_exemption_not_granted
	)
	Described(text = stringResource(R.string.battery_exemption_description)) {
		OutlinedButton(
			onClick = onClick,
			modifier = Modifier
				.fillMaxWidth()
				.clearAndSetSemantics {
					contentDescription = stringResource(R.string.battery_exemption_label)
					stateDescription = state
					role = Role.Button
				}
		) {
			Text(
				text = stringResource(R.string.battery_exemption_label),
				style = MaterialTheme.typography.labelLarge
			)
			Text(
				text = " $state",
				style = MaterialTheme.typography.bodyMedium
			)
		}
	}
}

/** A label, a switch, and the state of it, as one control. */
@Composable
private fun BatterySwitch(
	label: String,
	description: String,
	checked: Boolean,
	onChange: (Boolean) -> Unit
) {
	val state = stringResource(if (checked) R.string.switch_on else R.string.switch_off)
	Described(text = description) {
		Row(
			modifier = Modifier
				.fillMaxWidth()
				.clickable { onChange(!checked) }
				.padding(vertical = 8.dp)
				.clearAndSetSemantics {
					contentDescription = label
					stateDescription = state
					role = Role.Switch
				},
			verticalAlignment = Alignment.CenterVertically
		) {
			Text(
				text = label,
				style = MaterialTheme.typography.bodyLarge,
				modifier = Modifier.weight(1f)
			)
			Switch(checked = checked, onCheckedChange = null)
		}
	}
}

/** A sentence above a control, the way the settings screen lays things out. */
@Composable
private fun Described(text: String, control: @Composable () -> Unit) {
	Column(
		modifier = Modifier
			.fillMaxWidth()
			.padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 10.dp)
	) {
		Text(
			text = text,
			style = MaterialTheme.typography.bodyMedium,
			modifier = Modifier
				.fillMaxWidth()
				.padding(bottom = 4.dp)
		)
		control()
	}
}
