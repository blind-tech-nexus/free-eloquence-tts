package com.blindtechnexus.alteloquencetts

import android.content.Context
import android.content.SharedPreferences

/**
 * What the settings screen wrote, read back for the engine.
 *
 * Voice settings are kept per voice. Reed at pitch 55 is still at pitch 55
 * after a trip through Glen and back, and stays that way until Reed is reset.
 * Speed is the exception and is kept once for everybody: six of the eight
 * presets ship at engine speed 50 and Glen and Sandy ship at 70, so a speed
 * that followed the voice would change the pace every time the voice changed.
 *
 * Nothing is stored until someone chooses it. That is the whole point rather
 * than a detail: every preset carries its own head size, inflection and volume,
 * so a default of our own written over the top would make every voice wrong in
 * the same way.
 *
 * A revision goes up whenever anything changes, so the service can tell whether
 * the instance it is holding is still configured the way the user left it
 * without comparing every value.
 */
class Settings(context: Context) {

	// The file androidx.preference used, kept under that name so a build with
	// the old screen and a build with this one read the same settings. It is
	// read through device-protected storage, because the speech service reads
	// it while the phone is still locked. DirectBoot has the rest of that.
	private val prefs: SharedPreferences = DirectBoot.storage(context)
		.getSharedPreferences("${context.packageName}_preferences", Context.MODE_PRIVATE)

	@Volatile
	var revision: Int = 0
		private set

	private val watcher = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> revision++ }

	init {
		carryOverTheOldKeys()
		prefs.registerOnSharedPreferenceChangeListener(watcher)
	}

	/** Takes the listener off the preferences.
	 *
	 *  Worth saying out loud because it is the one thing about this class that
	 *  is easy to get wrong: the preferences are cached by the system for the
	 *  life of the process, so an instance made for the length of one visit to a
	 *  screen and never unhooked is a listener the process holds for ever, and a
	 *  screen that is opened and left a hundred times a day leaks a hundred of
	 *  them. Whoever holds one for the life of the process -- the service, and
	 *  the settings model -- has nothing to give back. */
	fun close() {
		prefs.unregisterOnSharedPreferenceChangeListener(watcher)
	}

	var voice: Int
		get() = prefs.getInt(KEY_VOICE, 0).coerceIn(0, Eci.PRESET_NAMES.lastIndex)
		set(value) = prefs.edit().putInt(KEY_VOICE, value).apply()

	var sampleRateHz: Int
		get() = prefs.getInt(KEY_SAMPLE_RATE, DEFAULT_SAMPLE_RATE)
		set(value) = prefs.edit().putInt(KEY_SAMPLE_RATE, value).apply()

	/** The abbreviation dictionary. Off by default: it expands what it takes
	 *  for an abbreviation whether that was wanted or not. */
	var abbreviations: Boolean
		get() = prefs.getBoolean(KEY_ABBREVIATIONS, false)
		set(value) = prefs.edit().putBoolean(KEY_ABBREVIATIONS, value).apply()

	/** Whether emoji and emoticons are read by name. On by default: a reader
	 *  who cannot see a message still hears what it was about. */
	var readEmoji: Boolean
		get() = prefs.getBoolean(KEY_EMOJI, true)
		set(value) = prefs.edit().putBoolean(KEY_EMOJI, value).apply()

	/** How runs of digits are read. One of the [Numbers] levels. */
	var numberMode: Int
		get() = prefs.getInt(KEY_NUMBER_MODE, Numbers.AS_IS).coerceIn(Numbers.AS_IS, Numbers.TRIPLETS)
		set(value) = prefs.edit().putInt(KEY_NUMBER_MODE, value.coerceIn(Numbers.AS_IS, Numbers.TRIPLETS)).apply()

	/** Whether SSML and other XML-like tags are stripped before the text is
	 *  read. On by default, since the engine's own SSML reader is not turned
	 *  on and a tag would otherwise be spoken. */
	var stripSsml: Boolean
		get() = prefs.getBoolean(KEY_STRIP_SSML, true)
		set(value) = prefs.edit().putBoolean(KEY_STRIP_SSML, value).apply()

	/** How a single capital letter is said. One of the [CapitalLetters]
	 *  levels, and not a voice's business. */
	var capitalMode: Int
		get() = prefs.getInt(KEY_CAPITAL_MODE, CapitalLetters.NONE).coerceIn(CapitalLetters.NONE, CapitalLetters.PITCH)
		set(value) = prefs.edit().putInt(KEY_CAPITAL_MODE, value.coerceIn(CapitalLetters.NONE, CapitalLetters.PITCH)).apply()

	/** Whether the keep-alive notification holds the engine in the foreground,
	 *  so Android leaves it running in the background.
	 *
	 * The notification belongs to the app rather than to the speech service,
	 * which is what lets it stay up when nothing is bound to the engine: see
	 * [KeepAliveService]. */
	var persistentNotification: Boolean
		get() = prefs.getBoolean(KEY_PERSISTENT_NOTIFICATION, false)
		set(value) = prefs.edit().putBoolean(KEY_PERSISTENT_NOTIFICATION, value).apply()

	/** Whether the app and its notification come back by themselves after the
	 *  phone has been restarted. The engine itself is started by the system
	 *  when a screen reader asks for it, but the process the keep-alive lives
	 *  in does not come back on its own, and a voice that has to be waited for
	 *  after a reboot is a voice that is not there when it is needed. */
	var startOnBoot: Boolean
		get() = prefs.getBoolean(KEY_START_ON_BOOT, false)
		set(value) = prefs.edit().putBoolean(KEY_START_ON_BOOT, value).apply()

	/** Whether the notification carries a button that stops whatever is being
	 *  spoken. The engine has no other way of being silenced from outside the
	 *  app that is asking to be heard, and a reader that has started a
	 *  paragraph has to be reached through that app to stop it. */
	var notificationStopButton: Boolean
		get() = prefs.getBoolean(KEY_NOTIFICATION_STOP, true)
		set(value) = prefs.edit().putBoolean(KEY_NOTIFICATION_STOP, value).apply()

	/** Whether the CPU is kept awake while an utterance is being made. */
	var wakeLock: Boolean
		get() = prefs.getBoolean(KEY_WAKE_LOCK, false)
		set(value) = prefs.edit().putBoolean(KEY_WAKE_LOCK, value).apply()

	/** How long the CPU stays awake after the last thing said, in
	 *  milliseconds. A screen reader speaks in bursts -- a swipe, a line, a
	 *  swipe -- and on the devices that sleep between one and the next the
	 *  wake-up is charged to the second line. Holding the processor for a
	 *  moment after the first costs a little battery and takes the wait out
	 *  of the burst. Nought is not holding it at all, which is what the
	 *  switch above has always meant. */
	var wakeLockTailMs: Int
		get() = prefs.getInt(KEY_WAKE_LOCK_TAIL, 0).coerceAtLeast(0)
		set(value) = prefs.edit().putInt(KEY_WAKE_LOCK_TAIL, value.coerceAtLeast(0)).apply()

	/** How long an engine instance is kept after the last thing it said, in
	 *  seconds, before it is closed to give its memory back. The engine holds
	 *  the language's tables for as long as it lives, which is worth it while
	 *  a reader is being used and is not worth it an hour after anybody
	 *  stopped swiping. Nought is never, which is how it has always been. */
	var releaseEngineAfterS: Int
		get() = prefs.getInt(KEY_RELEASE_ENGINE, 0).coerceAtLeast(0)
		set(value) = prefs.edit().putInt(KEY_RELEASE_ENGINE, value.coerceAtLeast(0)).apply()

	/** How far the samples handed to the framework may run ahead of the
	 *  speech, in milliseconds. See [Pacing]: it is the one number that
	 *  decides both how much of a queue a reader has to lose when it asks for
	 *  silence and how much of a long message the engine is allowed to get on
	 *  with at once. */
	var speechLeadMs: Int
		get() = prefs.getInt(KEY_SPEECH_LEAD, Pacing.DEFAULT_MS).coerceIn(Pacing.MIN_MS, Pacing.MAX_MS)
		set(value) = prefs.edit().putInt(KEY_SPEECH_LEAD, value.coerceIn(Pacing.MIN_MS, Pacing.MAX_MS)).apply()

	/** Whether a message long enough to be a paragraph is synthesised as fast
	 *  as the engine can make it, rather than at the pace it is heard at. */
	var runAheadOnLongText: Boolean
		get() = prefs.getBoolean(KEY_RUN_AHEAD, true)
		set(value) = prefs.edit().putBoolean(KEY_RUN_AHEAD, value).apply()

	/** How long the engine may go on making no samples at all before the
	 *  utterance is given up on, in milliseconds. An engine that has hung is
	 *  worse than an engine that has finished the sentence early: the
	 *  framework's synthesis thread is the one thing waiting on it, and while
	 *  it waits every later request for speech is behind it, which is a phone
	 *  that has gone quiet for the rest of the day. */
	var engineSilenceMs: Int
		get() = prefs.getInt(KEY_ENGINE_SILENCE, DEFAULT_SILENCE_MS).coerceIn(MIN_SILENCE_MS, MAX_SILENCE_MS)
		set(value) = prefs.edit()
			.putInt(KEY_ENGINE_SILENCE, value.coerceIn(MIN_SILENCE_MS, MAX_SILENCE_MS)).apply()

	/** Whether what the user does on the battery screen is written to the
	 *  system log, so a stopped engine can be traced back to what changed. */
	var trackActions: Boolean
		get() = prefs.getBoolean(KEY_TRACK_ACTIONS, false)
		set(value) = prefs.edit().putBoolean(KEY_TRACK_ACTIONS, value).apply()

	/** How much of the engine's own pausing to keep. One of the Pauses
	 *  constants, and not a voice's business. */
	var pauses: Int
		get() = prefs.getInt(KEY_PAUSES, Pauses.ALL).coerceIn(Pauses.KEEP, Pauses.ALL)
		set(value) = prefs.edit().putInt(KEY_PAUSES, value).apply()

	/** Whether the engine guesses at phrase boundaries and shapes the
	 *  intonation to match. Off: a screen reader's line is usually a fragment
	 *  and the guess is wrong. */
	var phrasePrediction: Boolean
		get() = prefs.getBoolean(KEY_PHRASE_PREDICTION, false)
		set(value) = prefs.edit().putBoolean(KEY_PHRASE_PREDICTION, value).apply()

	/** Which of the symbols in the text are spoken by name. One of the
	 *  [Symbols] levels, and not a voice's business. */
	var symbolMode: Int
		get() = prefs.getInt(KEY_SYMBOL_MODE, Symbols.SOME).coerceIn(Symbols.NONE, Symbols.CUSTOM)
		set(value) = prefs.edit().putInt(KEY_SYMBOL_MODE, value.coerceIn(Symbols.NONE, Symbols.CUSTOM)).apply()

	/** The symbols a [Symbols.CUSTOM] level names. Stored as written, because
	 *  it is the caller's list and not ours to tidy. */
	var customSymbols: String
		get() = prefs.getString(KEY_CUSTOM_SYMBOLS, "") ?: ""
		set(value) = prefs.edit().putString(KEY_CUSTOM_SYMBOLS, value).apply()

	/** Whether the rate set here is spoken at even when the app asking for
	 *  speech asks for its own. A screen reader that sets a rate is not wrong
	 *  to, so this is off until somebody decides otherwise. */
	var forceRate: Boolean
		get() = prefs.getBoolean(KEY_FORCE_RATE, false)
		set(value) = prefs.edit().putBoolean(KEY_FORCE_RATE, value).apply()

	var forcePitch: Boolean
		get() = prefs.getBoolean(KEY_FORCE_PITCH, false)
		set(value) = prefs.edit().putBoolean(KEY_FORCE_PITCH, value).apply()

	/** The same for volume. Android hands no volume to the engine -- the
	 *  volume a client chooses is applied to the stream after the speech has
	 *  left here -- so this is the engine's own volume parameter being pinned
	 *  to what this app says rather than a client's number being ignored,
	 *  because there is no client's number to ignore. */
	var forceVolume: Boolean
		get() = prefs.getBoolean(KEY_FORCE_VOLUME, false)
		set(value) = prefs.edit().putBoolean(KEY_FORCE_VOLUME, value).apply()

	/** One speed for every voice. */
	var speed: Int
		get() = Eci.clampVoice(Eci.VOICE_SPEED, prefs.getInt(KEY_SPEED, Eci.DEFAULT_SPEED))
		set(value) = prefs.edit().putInt(KEY_SPEED, Eci.clampVoice(Eci.VOICE_SPEED, value)).apply()

	// ---- one voice's own settings ----------------------------------------

	fun shapeValue(voice: Int, param: Int): Int? {
		if (param == Eci.VOICE_SPEED) return speed
		val key = keyOf(voice, param)
		return if (prefs.contains(key)) Eci.clampVoice(param, prefs.getInt(key, 0)) else null
	}

	fun setShapeValue(voice: Int, param: Int, value: Int) {
		if (param == Eci.VOICE_SPEED) {
			speed = value
			return
		}
		prefs.edit().putInt(keyOf(voice, param), Eci.clampVoice(param, value)).apply()
	}

	/** What has been chosen for [voice], by the number eciSetVoiceParam takes.
	 *  What is not here is left as the preset has it. */
	fun shape(voice: Int): Map<Int, Int> {
		val out = LinkedHashMap<Int, Int>()
		for (param in PER_VOICE) {
			val key = keyOf(voice, param)
			if (prefs.contains(key)) out[param] = Eci.clampVoice(param, prefs.getInt(key, 0))
		}
		out[Eci.VOICE_SPEED] = speed
		return out
	}

	fun writeShape(voice: Int, values: Map<Int, Int>) {
		val edit = prefs.edit()
		for (param in PER_VOICE) values[param]?.let { edit.putInt(keyOf(voice, param), it) }
		edit.apply()
		values[Eci.VOICE_SPEED]?.let { speed = it }
	}

	/** Whether [voice] has been changed from what it came with. */
	fun shapeIsCustom(voice: Int): Boolean = PER_VOICE.any { prefs.contains(keyOf(voice, it)) }

	/** Forgets one voice's changes. The speed is everybody's and stays. */
	fun clearShape(voice: Int) {
		val edit = prefs.edit()
		for (param in PER_VOICE) edit.remove(keyOf(voice, param))
		edit.apply()
	}

	private fun keyOf(voice: Int, param: Int) = "voice${voice}_param$param"

	/** Settings written when there was one set of them for every voice. They go
	 *  to whichever voice was in force at the time, which is where they were
	 *  heard. */
	private fun carryOverTheOldKeys() {
		val old = Eci.NUM_VOICE_PARAMS.let { 0 until it }.map { "voice_param_$it" }
		if (old.none { prefs.contains(it) }) return
		val edit = prefs.edit()
		val was = prefs.getInt(KEY_VOICE, 0).coerceIn(0, Eci.PRESET_NAMES.lastIndex)
		for (param in 0 until Eci.NUM_VOICE_PARAMS) {
			val from = "voice_param_$param"
			if (!prefs.contains(from)) continue
			val value = prefs.getInt(from, 0)
			if (param == Eci.VOICE_SPEED) {
				edit.putInt(KEY_SPEED, Eci.clampVoice(param, value))
			} else {
				edit.putInt(keyOf(was, param), Eci.clampVoice(param, value))
			}
			edit.remove(from)
		}
		edit.apply()
	}

	companion object {
		const val KEY_VOICE = "voice_preset"
		const val KEY_SAMPLE_RATE = "sample_rate_hz"
		const val KEY_ABBREVIATIONS = "abbreviations"
		const val KEY_SPEED = "speed"
		const val KEY_PAUSES = "pauses"
		const val KEY_PHRASE_PREDICTION = "phrase_prediction"
		const val KEY_SYMBOL_MODE = "symbol_mode"
		const val KEY_CUSTOM_SYMBOLS = "custom_symbols"
		const val KEY_FORCE_RATE = "force_rate"
		const val KEY_FORCE_PITCH = "force_pitch"
		const val KEY_FORCE_VOLUME = "force_volume"
		const val KEY_EMOJI = "emoji"
		const val KEY_NUMBER_MODE = "number_mode"
		const val KEY_STRIP_SSML = "strip_ssml"
		const val KEY_CAPITAL_MODE = "capital_mode"
		const val KEY_PERSISTENT_NOTIFICATION = "persistent_notification"
		const val KEY_WAKE_LOCK = "wake_lock"
		const val KEY_TRACK_ACTIONS = "track_actions"
		const val KEY_START_ON_BOOT = "start_on_boot"
		const val KEY_NOTIFICATION_STOP = "notification_stop"
		const val KEY_WAKE_LOCK_TAIL = "wake_lock_tail_ms"
		const val KEY_RELEASE_ENGINE = "release_engine_after_s"
		const val KEY_SPEECH_LEAD = "speech_lead_ms"
		const val KEY_RUN_AHEAD = "run_ahead_on_long_text"
		const val KEY_ENGINE_SILENCE = "engine_silence_ms"
		const val DEFAULT_SAMPLE_RATE = 11025

		/** What the engine is allowed to go silent for before the utterance is
		 *  given up on. Fifteen seconds is already a hundred times what the
		 *  engine takes to make a piece of speech, so getting here means
		 *  something has gone wrong rather than that the text is long. */
		const val DEFAULT_SILENCE_MS = 15000
		const val MIN_SILENCE_MS = 1000
		const val MAX_SILENCE_MS = 300000

		/** The place every setting in this app is written down. Named once,
		 *  because the file is what it is and a second copy of its name would
		 *  be a second thing to get wrong. */
		fun store(context: Context): SharedPreferences = DirectBoot.storage(context)
			.getSharedPreferences("${context.packageName}_preferences", Context.MODE_PRIVATE)

		/** One value, read without an instance of this class.
		 *
		 *  A [Settings] registers a listener on the preferences and holds it
		 *  for as long as the process does, which suits a screen and is a leak
		 *  in a notification built every few seconds. Anything that asks once
		 *  in passing asks through here. */
		fun flag(context: Context, key: String, default: Boolean): Boolean =
			store(context).getBoolean(key, default)

		/** The same for a number. */
		fun number(context: Context, key: String, default: Int): Int =
			store(context).getInt(key, default)

		/** The eight. The order they have always been written and read in,
		 *  which is not the order the screen shows them in: [BEHAVIOURS] is
		 *  that one. */
		val SHAPE = listOf(
			Eci.VOICE_GENDER,
			Eci.VOICE_SPEED,
			Eci.VOICE_PITCH_BASELINE,
			Eci.VOICE_PITCH_FLUCTUATION,
			Eci.VOICE_HEAD_SIZE,
			Eci.VOICE_ROUGHNESS,
			Eci.VOICE_BREATHINESS,
			Eci.VOICE_VOLUME
		)

		/** The seven that belong to a voice. Speed belongs to the listener. */
		val PER_VOICE = SHAPE.filter { it != Eci.VOICE_SPEED }

		/** The seven that are a slider. Gender is a choice of two. */
		val SLIDERS = SHAPE.filter { it != Eci.VOICE_GENDER }

		/** The seven that are a slider again, in the order the screen shows
		 *  them: how fast, then how high, then the three that give a voice its
		 *  colour, then how loud. The order above puts head size between
		 *  inflection and roughness for a reason nobody can hear, and rate --
		 *  which is what everybody else calls the engine's speed, and what the
		 *  slider is labelled -- last but for volume. A test says the two
		 *  lists are the same seven, so a parameter added to one cannot go
		 *  missing from the screen. */
		val BEHAVIOURS = listOf(
			Eci.VOICE_SPEED,
			Eci.VOICE_PITCH_BASELINE,
			Eci.VOICE_PITCH_FLUCTUATION,
			Eci.VOICE_BREATHINESS,
			Eci.VOICE_HEAD_SIZE,
			Eci.VOICE_ROUGHNESS,
			Eci.VOICE_VOLUME
		)
	}
}
