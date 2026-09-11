package com.blindtechnexus.alteloquencetts

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource

/** Which of the screens is up. */
internal enum class Destination { WELCOME, MAIN, SETTINGS, ABOUT, DICTIONARY, BATTERY }

/**
 * The app's one activity, and the screens it shows.
 *
 * One activity rather than several, because there is nothing here that needs an
 * activity of its own and a lot that suffers from having one: a screen reader
 * announces the departure and the arrival of every activity, an engine preview
 * opened for the settings screen would be closed and reopened by a trip to the
 * about screen, and a back stack of four activities is four things to be
 * somewhere inside when the phone answers. So the screens are a state and the
 * back key moves it.
 *
 * Where it starts depends on who asked. From the launcher it is the main
 * screen, or the welcome screen the first time. From the gear beside the engine
 * in the system's own text to speech settings it is the settings screen,
 * because that is what the gear means and the main screen would be a detour
 * with no way back to it -- and there, back leaves the app rather than going to
 * a screen nobody asked for.
 *
 * Two permissions are asked for, and neither can be taken for granted:
 * notifications, which is a runtime permission on Android 13 and later, and an
 * exemption from battery optimization, which is required and is explained in
 * [BatteryExemption]. Both are asked for on every launch, in [onCreate] or on
 * the way past the welcome screen, and both are answered again every time the
 * activity is resumed, because the screens that grant them are not ours and
 * the answer can change while they are up.
 */
class SettingsActivity : ComponentActivity() {

	private var model: SettingsModel? = null

	private val firstLaunchPreferences by lazy {
		getSharedPreferences(FIRST_LAUNCH_PREFERENCES, MODE_PRIVATE)
	}

	/** Where the app is. Written by the screens and by the back key, read by
	 *  the composition, and a snapshot state so that writing it is enough to
	 *  redraw. */
	private var destination by mutableStateOf(Destination.MAIN)

	/** Where the activity came in, which is where the back key goes when there
	 *  is no main screen behind the one that is up. */
	private var enteredAt = Destination.MAIN

	/** Whether to say in so many words why the exemption is required. The
	 *  system's screen is a yes or no with nothing said about it, and a yes
	 *  asked for out of nowhere is refused. */
	private var asksForTheExemption by mutableStateOf(false)

	/** Whether this engine is the one the phone speaks in, which only the
	 *  system can make it and which is asked about once rather than nagged. */
	private var asksAboutTheDefaultEngine by mutableStateOf(false)

	private val notificationPermissionLauncher = registerForActivityResult(
		ActivityResultContracts.RequestPermission()
	) {
		// Whether the notification was given or refused, the asking goes on
		// to the exemption: the question is put whatever the answer was, and
		// a refusal here does not cancel the asking there.
		askAboutTheExemption()
	}

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		val firstLaunch = !firstLaunchPreferences.getBoolean(FIRST_LAUNCH_COMPLETE, false)
		val configured = intent?.action == ACTION_CONFIGURE_ENGINE
		enteredAt = if (configured) Destination.SETTINGS else Destination.MAIN
		destination = if (firstLaunch) Destination.WELCOME else enteredAt
		asksAboutTheDefaultEngine = !isDefaultTts()
		refresh()
		// Every launch, not only the first: whatever has not been given is
		// asked for now, so a refusal at the wrong moment does not become a
		// voice that stops working with nothing said about why. The welcome
		// screen does its own asking on the way past it.
		if (!firstLaunch) checkPermissions()
		setContent {
			MaterialTheme {
				Surface {
					BackHandler(
						enabled = destination != Destination.WELCOME &&
							destination != Destination.MAIN
					) {
						back()
					}
					when (destination) {
						Destination.WELCOME -> WelcomeScreen(onContinue = ::continueFromWelcome)
						Destination.MAIN -> MainScreen(
							onConfigure = { destination = Destination.SETTINGS },
							onOpenSystemSpeechSettings = {
								SystemSpeechSettings.open(this@SettingsActivity)
							},
							onAbout = { destination = Destination.ABOUT }
						)
						Destination.SETTINGS -> SettingsScreen(
							state = remember { settingsModel() },
							onBack = ::back,
							onOpenDictionary = { destination = Destination.DICTIONARY },
							onOpenBattery = { destination = Destination.BATTERY }
						)
						Destination.DICTIONARY -> DictionaryScreen(onBack = ::back)
						Destination.BATTERY -> BatteryScreen(
							onBack = ::back,
							onRequestExemption = ::requestTheExemption
						)
						Destination.ABOUT -> AboutScreen(onBack = ::back)
					}
					// Not over the welcome screen, which has a job of its own
					// to finish first, and one at a time: two dialogs up at
					// once are two things talking over each other, and
					// whichever is behind the other cannot be reached at all.
					if (destination != Destination.WELCOME) {
						when {
							asksForTheExemption -> ExemptionDialog(
								onAllow = {
									asksForTheExemption = false
									requestTheExemption()
								},
								onRefuse = { asksForTheExemption = false }
							)
							asksAboutTheDefaultEngine -> DefaultEngineDialog(
								onOpen = {
									asksAboutTheDefaultEngine = false
									SystemSpeechSettings.open(this@SettingsActivity)
								},
								onRefuse = { asksAboutTheDefaultEngine = false }
							)
						}
					}
				}
			}
		}
	}

	override fun onResume() {
		super.onResume()
		refresh()
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

	// ---- moving between the screens ---------------------------------------

	/** What the back key and the "Go back" button both do. */
	private fun back() {
		when (destination) {
			Destination.SETTINGS -> {
				model?.stopSpeaking()
				if (enteredAt == Destination.SETTINGS) finish() else destination = Destination.MAIN
			}
			Destination.DICTIONARY, Destination.BATTERY -> destination = Destination.SETTINGS
			Destination.ABOUT -> destination = Destination.MAIN
			Destination.MAIN, Destination.WELCOME -> finish()
		}
	}

	/** The welcome screen is read once, and the two permissions it warned
	 *  about are asked for on the way past it -- which is the same asking
	 *  every other launch does, so that the first launch and the hundredth
	 *  behave alike. */
	private fun continueFromWelcome() {
		firstLaunchPreferences.edit()
			.putBoolean(FIRST_LAUNCH_COMPLETE, true)
			.apply()
		destination = enteredAt
		checkPermissions()
	}

	/** The engine's own preview, made the first time the settings screen is
	 *  shown and kept until the activity goes. Making one opens an instance of
	 *  the engine, which is not free, and making one per visit to the screen
	 *  would be paying for the same thing several times. */
	private fun settingsModel(): SettingsModel =
		model ?: SettingsModel(this).also { model = it }

	// ---- what the device has to say ---------------------------------------

	/**
	 * Every permission the engine needs, asked for on every launch.
	 *
	 * Both are gone from the main screen: a row saying what has been given is
	 * a stop to swipe past on every visit for a question that is asked and
	 * answered once. Here instead, each launch checks what is missing and asks
	 * for it, so somebody who said no at the wrong moment is asked again the
	 * next time they open the app rather than never.
	 *
	 * The notification permission is asked for first and the exemption second:
	 * the notification is a system dialog that is answered and gone, while the
	 * exemption is this app's own explanation and then a system screen, and the
	 * longer asking belongs at the end.
	 */
	private fun checkPermissions() {
		if (
			Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
			checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
		) {
			notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
		} else {
			askAboutTheExemption()
		}
	}

	/** Puts the exemption question, in so many words, in front of the system's
	 *  own yes-or-no screen -- unless it has already been given. Nothing in
	 *  this app can grant it; the answer comes back through [refresh]. */
	private fun askAboutTheExemption() {
		if (BatteryExemption.isGranted(this)) return
		asksForTheExemption = true
	}

	/** Asks the device the two questions whose answers are not ours to keep:
	 *  whether the exemption has been given, and whether this is the engine the
	 *  phone speaks in. Both are read again on every resume, because both are
	 *  answered on screens belonging to somebody else. */
	private fun refresh() {
		if (BatteryExemption.isGranted(this)) asksForTheExemption = false
		if (isDefaultTts()) asksAboutTheDefaultEngine = false
	}

	/** Whether Alt eloquence TTS is the engine the phone speaks in. */
	private fun isDefaultTts(): Boolean = runCatching {
		android.provider.Settings.Secure.getString(
			contentResolver,
			android.provider.Settings.Secure.TTS_DEFAULT_SYNTH
		) == packageName
	}.getOrDefault(false)

	/** Hands the exemption over to the system to grant or refuse, and leaves
	 *  the answer to [refresh]. Nothing in this app can grant it. */
	internal fun requestTheExemption() {
		if (!BatteryExemption.request(this)) {
			// No screen at all would take the question, which is rare and is
			// worth a line in the log rather than a silence.
			Log.e(TAG, "nothing here asks about battery optimization")
		}
	}

	internal companion object {
		const val TAG = "alteloquencetts"

		/** What the system's own text to speech settings sends to open an
		 *  engine's settings activity, which is the gear beside the engine in
		 *  that list. A literal here because the framework keeps the constant
		 *  for it to itself, and the intent filter in the manifest names the
		 *  same string: the two have to agree, and a test says they do. */
		const val ACTION_CONFIGURE_ENGINE = "android.speech.tts.engine.CONFIGURE_ENGINE"

		/** Where it is remembered that the welcome screen has been read. A
		 *  test writes it, so that a launch lands on the screen being tested
		 *  rather than on whichever one a first launch would show. */
		const val FIRST_LAUNCH_PREFERENCES = "welcome"
		const val FIRST_LAUNCH_COMPLETE = "completed"
	}
}

/**
 * Why the exemption from battery optimization is required.
 *
 * The system's own screen that grants it is a yes or no with nothing said about
 * why it is being asked, and this is the one permission the engine cannot work
 * without: refused, the voice stops whenever the phone decides the app has used
 * too much power in the background. So the reason is given first, and the
 * system's screen comes after it.
 */
@Composable
private fun ExemptionDialog(onAllow: () -> Unit, onRefuse: () -> Unit) {
	AlertDialog(
		onDismissRequest = onRefuse,
		title = { Text(text = stringResource(R.string.battery_title)) },
		text = { Text(text = stringResource(R.string.battery_message)) },
		confirmButton = {
			TextButton(onClick = onAllow) {
				Text(text = stringResource(R.string.battery_allow))
			}
		},
		dismissButton = {
			TextButton(onClick = onRefuse) {
				Text(text = stringResource(R.string.battery_later))
			}
		}
	)
}

/**
 * Whether this engine is the one the phone speaks in.
 *
 * An engine nobody has chosen speaks to nobody, and choosing one is the
 * system's rather than this app's: the preferred engine lives in the secure
 * settings, which nothing but the system writes. So all this can do is say
 * that it has not been chosen and open the screen where it is chosen.
 */
@Composable
private fun DefaultEngineDialog(onOpen: () -> Unit, onRefuse: () -> Unit) {
	AlertDialog(
		onDismissRequest = onRefuse,
		title = { Text(text = stringResource(R.string.default_tts_title)) },
		text = { Text(text = stringResource(R.string.default_tts_message)) },
		confirmButton = {
			TextButton(onClick = onOpen) {
				Text(text = stringResource(R.string.set_as_default))
			}
		},
		dismissButton = {
			TextButton(onClick = onRefuse) {
				Text(text = stringResource(R.string.cancel))
			}
		}
	)
}
