package com.blindtechnexus.freeeloquencetts

import android.content.Intent
import android.provider.Settings as AndroidSettings
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
 * The small set of controls that can actually keep the voice available.
 * Internal timing, wake-lock and diagnostic switches do not belong here: the
 * engine uses safe low-latency values for those automatically.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatteryScreen(onBack: () -> Unit, onRequestExemption: () -> Unit) {
	val context = LocalContext.current
	val settings = remember { Settings(context.applicationContext) }
	val notificationOn = stringResource(R.string.battery_notification_on)
	val notificationOff = stringResource(R.string.battery_notification_off)
	val noBatteryScreen = stringResource(R.string.battery_no_screen)

	var granted by remember { mutableStateOf(BatteryExemption.isGranted(context)) }
	var notificationsAllowed by remember {
		mutableStateOf(KeepAliveNotification.notificationsAllowed(context))
	}
	var persistent by remember { mutableStateOf(settings.persistentNotification) }
	var stopButton by remember { mutableStateOf(settings.notificationStopButton) }
	var startOnBoot by remember {
		val enabled = settings.startOnBoot && settings.persistentNotification
		if (!enabled && settings.startOnBoot) settings.startOnBoot = false
		mutableStateOf(enabled)
	}
	var notice by remember { mutableStateOf("") }

	val activity = context as? ComponentActivity
	DisposableEffect(settings) {
		onDispose { settings.close() }
	}
	DisposableEffect(activity) {
		val observer = LifecycleEventObserver { _, event ->
			if (event == Lifecycle.Event.ON_RESUME) {
				granted = BatteryExemption.isGranted(context)
				notificationsAllowed = KeepAliveNotification.notificationsAllowed(context)
				persistent = settings.persistentNotification
				stopButton = settings.notificationStopButton
				startOnBoot = settings.startOnBoot && persistent
			}
		}
		activity?.lifecycle?.addObserver(observer)
		onDispose { activity?.lifecycle?.removeObserver(observer) }
	}

	Scaffold(
		topBar = { ScreenTopBar(title = stringResource(R.string.section_battery), onBack = onBack) }
	) { inset ->
		Column(
			modifier = Modifier
				.fillMaxSize()
				.padding(inset)
				.verticalScroll(rememberScrollState())
				.padding(bottom = 24.dp)
		) {
			SectionText(text = stringResource(R.string.battery_screen_description))
			LiveNotice(text = notice, horizontal = 16.dp)
			SectionHeading(text = stringResource(R.string.battery_protection_heading))
			ExemptionRow(
				granted = granted,
				onClick = onRequestExemption
			)
			SwitchRow(
				label = stringResource(R.string.persistent_notification_label),
				checked = persistent,
				description = stringResource(R.string.persistent_notification_description),
				onChange = { on ->
					persistent = on
					settings.persistentNotification = on
					if (!on && startOnBoot) {
						startOnBoot = false
						settings.startOnBoot = false
					}
					KeepAlive.sync(context, on)
					notice = if (on) notificationOn else notificationOff
				}
			)
			if (persistent && !notificationsAllowed) {
				SectionText(text = stringResource(R.string.battery_notifications_blocked))
				ActionRow(
					label = stringResource(R.string.battery_notification_settings),
					description = null,
					onClick = {
						val intent = Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS)
							.putExtra(AndroidSettings.EXTRA_APP_PACKAGE, context.packageName)
						if (runCatching { context.startActivity(intent); true }.getOrDefault(false).not()) {
							notice = noBatteryScreen
						}
					}
				)
			}
			if (persistent) {
				SwitchRow(
					label = stringResource(R.string.notification_stop_label),
					checked = stopButton,
					description = stringResource(R.string.notification_stop_description),
					onChange = { on ->
						stopButton = on
						settings.notificationStopButton = on
						KeepAlive.sync(context, true)
					}
				)
				SwitchRow(
					label = stringResource(R.string.start_on_boot_label),
					checked = startOnBoot,
					description = stringResource(R.string.start_on_boot_description),
					onChange = { on ->
						startOnBoot = on
						settings.startOnBoot = on
					}
				)
			}
			ActionRow(
				label = stringResource(
					if (ManufacturerScreens.hasOwnScreen()) R.string.battery_manufacturer_open
					else R.string.battery_android_open
				),
				description = stringResource(R.string.battery_manufacturer_description),
				onClick = {
					if (!ManufacturerScreens.open(context) && !ManufacturerScreens.appDetails(context)) {
						notice = noBatteryScreen
					}
				}
			)
		}
	}
}

@Composable
private fun ExemptionRow(granted: Boolean, onClick: () -> Unit) {
	val state = stringResource(
		if (granted) R.string.battery_exemption_granted else R.string.battery_exemption_not_granted
	)
	val label = stringResource(R.string.battery_exemption_label)
	Described(
		description = stringResource(
			if (granted) R.string.battery_exemption_allowed_description
			else R.string.battery_exemption_description
		)
	) {
		OutlinedButton(
			onClick = onClick,
			enabled = !granted,
			modifier = Modifier
				.fillMaxWidth()
				.clearAndSetSemantics {
					contentDescription = label
					stateDescription = state
					role = Role.Button
				}
		) {
			Text("$label: $state")
		}
	}
}
