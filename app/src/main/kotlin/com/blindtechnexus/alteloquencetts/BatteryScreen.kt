package com.blindtechnexus.alteloquencetts

import android.content.res.Resources
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
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

/**
 * Everything this app can do to keep Android from stopping the voice.
 *
 * The screen is in three parts, because the three things they are for happen at
 * three different times.
 *
 * While the phone is idle: the exemption from battery optimization, which is the
 * single most important and cannot be granted from inside the app -- the button
 * hands over to the system's own screen, and the state shown here is read again
 * on every resume, because the answer can change while that screen is up; the
 * notification that holds the app in the foreground, which now stands whether or
 * not anything is bound to the engine, since a service that keeps it up of its
 * own has been added for the purpose; the same notification coming back
 * after a restart, which is the one thing a reboot takes away; and how often
 * the notification is looked for, where a phone that clears notifications
 * aggressively wants the short end.
 *
 * While speaking: speaking in the foreground, which holds the engine there
 * only while each utterance is being made, for whoever wants the protection
 * without a notification that never goes away; the wake lock, for the devices
 * whose processor sleeps mid sentence and drops the tail of the speech; how far the engine may run ahead of
 * what has been heard, which is the trade between losing words when a reader
 * interrupts and finishing a chapter the moment it was asked for; how long an
 * engine instance is kept after the last thing it said; and how long the engine
 * may go silent in the middle of an utterance before it is given up on.
 *
 * And for a fault: the switch that writes what is changed here to the system
 * log, so that a voice that stopped can be traced back to what was changed
 * before it did.
 *
 * Every one of the rows has its sentence above it, the way the rest of the
 * settings do: the names do not carry their own meaning, and a description
 * folded into a control would be read out again on every visit with no way past
 * it. What is in force at the moment is said once, at the bottom, in the terms
 * of what the phone is doing rather than in the names of the switches -- because
 * the question this screen is opened to answer is not what was set but whether
 * it is working.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatteryScreen(onBack: () -> Unit, onRequestExemption: () -> Unit) {
	val context = LocalContext.current
	val settings = remember { Settings(context.applicationContext) }
	var granted by remember { mutableStateOf(BatteryExemption.isGranted(context)) }
	var persistent by remember { mutableStateOf(settings.persistentNotification) }
	var startOnBoot by remember { mutableStateOf(settings.startOnBoot) }
	var stopButton by remember { mutableStateOf(settings.notificationStopButton) }
	var checkInterval by remember { mutableStateOf(settings.keepAliveCheckMs) }
	var foreground by remember { mutableStateOf(settings.foregroundWhileSpeaking) }
	var wakeLock by remember { mutableStateOf(settings.wakeLock) }
	var tail by remember { mutableStateOf(settings.wakeLockTailMs) }
	var release by remember { mutableStateOf(settings.releaseEngineAfterS) }
	var lead by remember { mutableStateOf(settings.speechLeadMs) }
	var runAhead by remember { mutableStateOf(settings.runAheadOnLongText) }
	var silence by remember { mutableStateOf(settings.engineSilenceMs) }
	var tracking by remember { mutableStateOf(settings.trackActions) }
	var notice by remember { mutableStateOf("") }
	// Resolved here rather than in the click: the click is not a composition,
	// and a sentence asked for there would not compile.
	val noBatteryScreen = stringResource(R.string.battery_no_screen)
	val bootNeedsNotification = stringResource(R.string.battery_boot_needs_notification)

	// The exemption is granted on a screen belonging to the system, and the
	// answer is read again when that screen is left. The keep-alive's own state
	// is read with it, for the same reason: the notification may have been
	// refused, swiped away or turned off in the app's own settings while this
	// one was in the background.
	val activity = context as? ComponentActivity
	DisposableEffect(settings) {
		onDispose { settings.close() }
	}
	DisposableEffect(activity) {
		val observer = LifecycleEventObserver { _, event ->
			if (event == Lifecycle.Event.ON_RESUME) {
				granted = BatteryExemption.isGranted(context)
				persistent = settings.persistentNotification
			}
		}
		activity?.lifecycle?.addObserver(observer)
		onDispose { activity?.lifecycle?.removeObserver(observer) }
	}

	Scaffold(
		topBar = {
			ScreenTopBar(title = stringResource(R.string.section_battery), onBack = onBack)
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
			LiveNotice(text = notice)

			SectionHeading(text = stringResource(R.string.battery_idle_heading))
			ExemptionRow(
				granted = granted,
				onClick = {
					Tracker.action(settings, "asked for the battery optimization exemption")
					onRequestExemption()
				}
			)
			SwitchRow(
				label = stringResource(R.string.persistent_notification_label),
				checked = persistent,
				description = stringResource(R.string.persistent_notification_description),
				onChange = { on ->
					persistent = on
					settings.persistentNotification = on
					// The setting and the service are one thing: a switch that
					// only wrote a number would leave the notification standing
					// until the next time the engine was spoken to, and a person
					// who has just turned it off would be looking at it.
					// The app's own service is what holds the notification;
					// KeepAlive is the one that knows what to do when the system
					// would not let that service start.
					KeepAlive.sync(context, on)
					Tracker.action(settings, "persistent notification ${if (on) "on" else "off"}")
				}
			)
			SwitchRow(
				label = stringResource(R.string.notification_stop_label),
				checked = stopButton,
				description = stringResource(R.string.notification_stop_description),
				onChange = { on ->
					stopButton = on
					settings.notificationStopButton = on
					// The notification is rebuilt by whoever is holding it up,
					// within the check that runs every few seconds.
					Tracker.action(settings, "notification stop button ${if (on) "on" else "off"}")
				}
			)
			SwitchRow(
				label = stringResource(R.string.start_on_boot_label),
				checked = startOnBoot,
				description = stringResource(R.string.start_on_boot_description),
				onChange = { on ->
					startOnBoot = on
					settings.startOnBoot = on
					if (on && !persistent) {
						// One is the other's means: what comes back after a
						// restart is the notification, and turning on the asking
						// without the thing asked for is a switch that promises a
						// thing it cannot do. So it is turned on too, and the
						// sentence above says as much rather than hiding it.
						persistent = true
						settings.persistentNotification = true
						KeepAlive.sync(context, true)
						notice = bootNeedsNotification
					}
					Tracker.action(settings, "start on boot ${if (on) "on" else "off"}")
				}
			)
			ChoiceButton(
				title = stringResource(R.string.notification_check_label),
				shown = stringResource(
					R.string.battery_value_is,
					stringResource(R.string.notification_check_label),
					durationLabel(context.resources, checkInterval)
				),
				options = Stability.NOTIFICATION_CHECKS.map { durationLabel(context.resources, it) },
				chosen = indexOf(checkInterval, Stability.NOTIFICATION_CHECKS),
				description = stringResource(R.string.notification_check_description),
				onChoose = { at ->
					val value = Stability.NOTIFICATION_CHECKS.getOrElse(at) { checkInterval }
					checkInterval = value
					settings.keepAliveCheckMs = value
					Tracker.action(settings, "notification check every ${value}ms")
				}
			)
			ActionRow(
				label = stringResource(
					if (ManufacturerScreens.hasOwnScreen()) R.string.battery_manufacturer_open
					else R.string.battery_android_open
				),
				description = stringResource(R.string.battery_manufacturer_description),
				onClick = {
					Tracker.action(settings, "asked for the phone's own background settings")
					if (!ManufacturerScreens.open(context) && !ManufacturerScreens.appDetails(context)) {
						notice = noBatteryScreen
					}
				}
			)

			SectionHeading(text = stringResource(R.string.battery_speaking_heading))
			SwitchRow(
				label = stringResource(R.string.foreground_while_speaking_label),
				checked = foreground,
				description = stringResource(R.string.foreground_while_speaking_description),
				onChange = { on ->
					foreground = on
					settings.foregroundWhileSpeaking = on
					Tracker.action(settings, "speak in the foreground ${if (on) "on" else "off"}")
				}
			)
			SwitchRow(
				label = stringResource(R.string.wake_lock_label),
				checked = wakeLock,
				description = stringResource(R.string.wake_lock_description),
				onChange = { on ->
					wakeLock = on
					settings.wakeLock = on
					Tracker.action(settings, "wake lock ${if (on) "on" else "off"}")
				}
			)
			ChoiceButton(
				title = stringResource(R.string.wake_lock_tail_label),
				shown = stringResource(
					R.string.battery_value_is,
					stringResource(R.string.wake_lock_tail_label),
					durationLabel(context.resources, tail)
				),
				options = Stability.WAKE_LOCK_TAILS.map { durationLabel(context.resources, it) },
				chosen = indexOf(tail, Stability.WAKE_LOCK_TAILS),
				description = stringResource(R.string.wake_lock_tail_description),
				onChoose = { at ->
					val value = Stability.WAKE_LOCK_TAILS.getOrElse(at) { tail }
					tail = value
					settings.wakeLockTailMs = value
					Tracker.action(settings, "wake lock held for ${value}ms after speaking")
				}
			)
			ChoiceButton(
				title = stringResource(R.string.speech_lead_label),
				shown = stringResource(
					R.string.battery_value_is,
					stringResource(R.string.speech_lead_label),
					durationLabel(context.resources, lead)
				),
				options = Pacing.LEADS.map { durationLabel(context.resources, it) },
				chosen = indexOf(lead, Pacing.LEADS),
				description = stringResource(R.string.speech_lead_description),
				onChoose = { at ->
					val value = Pacing.LEADS.getOrElse(at) { lead }
					lead = value
					settings.speechLeadMs = value
					Tracker.action(settings, "speech lead ${value}ms")
				}
			)
			SwitchRow(
				label = stringResource(R.string.run_ahead_label),
				checked = runAhead,
				description = stringResource(R.string.run_ahead_description),
				onChange = { on ->
					runAhead = on
					settings.runAheadOnLongText = on
					Tracker.action(settings, "run ahead on long text ${if (on) "on" else "off"}")
				}
			)
			ChoiceButton(
				title = stringResource(R.string.engine_release_label),
				shown = stringResource(
					R.string.battery_value_is,
					stringResource(R.string.engine_release_label),
					durationLabel(context.resources, release * MILLIS_PER_SECOND, forever = true)
				),
				options = Stability.ENGINE_RELEASES.map {
					durationLabel(context.resources, it * MILLIS_PER_SECOND, forever = true)
				},
				chosen = indexOf(release, Stability.ENGINE_RELEASES),
				description = stringResource(R.string.engine_release_description),
				onChoose = { at ->
					val value = Stability.ENGINE_RELEASES.getOrElse(at) { release }
					release = value
					settings.releaseEngineAfterS = value
					Tracker.action(settings, "engine given back after ${value}s idle")
				}
			)
			ChoiceButton(
				title = stringResource(R.string.engine_silence_label),
				shown = stringResource(
					R.string.battery_value_is,
					stringResource(R.string.engine_silence_label),
					durationLabel(context.resources, silence)
				),
				options = Stability.SILENCES.map { durationLabel(context.resources, it) },
				chosen = indexOf(silence, Stability.SILENCES),
				description = stringResource(R.string.engine_silence_description),
				onChoose = { at ->
					val value = Stability.SILENCES.getOrElse(at) { silence }
					silence = value
					settings.engineSilenceMs = value
					Tracker.action(settings, "utterance given up after ${value / MILLIS_PER_SECOND}s of silence")
				}
			)

			SectionHeading(text = stringResource(R.string.battery_fault_heading))
			SwitchRow(
				label = stringResource(R.string.track_actions_label),
				checked = tracking,
				description = stringResource(R.string.track_actions_description),
				onChange = { on ->
					tracking = on
					settings.trackActions = on
					Tracker.action(settings, "action tracking ${if (on) "on" else "off"}")
				}
			)
			Paragraph(
				text = stringResource(
					R.string.battery_now,
					if (KeepAliveService.isRunning) stringResource(R.string.battery_now_service)
					else stringResource(R.string.battery_now_none),
					if (KeepAliveNotification.notificationsAllowed(context))
						stringResource(R.string.battery_now_allowed)
					else stringResource(R.string.battery_now_refused)
				)
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
	val label = stringResource(R.string.battery_exemption_label)
	Described(description = stringResource(R.string.battery_exemption_description)) {
		OutlinedButton(
			onClick = onClick,
			modifier = Modifier
				.fillMaxWidth()
				.clearAndSetSemantics {
					contentDescription = label
					stateDescription = state
					role = Role.Button
				}
		) {
			Text(text = label)
			Text(text = " $state")
		}
	}
}

/**
 * A duration in words, for a row that names one.
 *
 * The units are spelled out rather than left as "ms" and "s", because a bare
 * abbreviation is read out as a letter and a word is read out as a word, and
 * every number on this screen is only ever heard. A multiple of a minute is said
 * as minutes for the same reason: ninety seconds is a thing a person has to work
 * out and a minute and a half is not.
 *
 * [forever] is for the settings where nought is not a short wait but no wait at
 * all -- the engine being kept for ever rather than for nought seconds -- and it
 * is a word the row's own meaning needs, so it is a parameter rather than a
 * convention.
 */
private fun durationLabel(resources: Resources, millis: Int, forever: Boolean = false): String {
	if (millis == 0) {
		return resources.getString(if (forever) R.string.battery_forever else R.string.battery_never)
	}
	if (millis % (60 * 1000) == 0) {
		val minutes = millis / (60 * 1000)
		return resources.getQuantityString(R.plurals.battery_minutes, minutes, minutes)
	}
	if (millis % 1000 == 0) {
		val seconds = millis / 1000
		return resources.getQuantityString(R.plurals.battery_seconds, seconds, seconds)
	}
	return resources.getString(R.string.battery_milliseconds, millis)
}

/** Which of the offered values a setting stands nearest to. The values are the
 *  ones the screen offers, so the answer is exact for anything set here; a
 *  number that came from another version of the app, or from a preference
 *  written by hand, is rounded to the nearest one rather than shown as a choice
 *  that is not in the list. */
private fun indexOf(value: Int, options: List<Int>): Int {
	val exact = options.indexOf(value)
	if (exact >= 0) return exact
	var best = 0
	var bestBy = Int.MAX_VALUE
	for (at in options.indices) {
		val by = Math.abs(options[at] - value)
		if (by < bestBy) {
			bestBy = by
			best = at
		}
	}
	return best
}

private const val MILLIS_PER_SECOND = 1000
