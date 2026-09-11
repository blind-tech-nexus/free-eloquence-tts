package com.blindtechnexus.alteloquencetts

import android.media.AudioFormat
import android.os.PowerManager
import android.speech.tts.SynthesisCallback
import android.speech.tts.SynthesisRequest
import android.speech.tts.TextToSpeech
import android.speech.tts.TextToSpeechService
import android.speech.tts.Voice
import android.util.Log
import java.util.Locale

/**
 * The engine Android talks to.
 *
 * onSynthesizeText runs on the framework's own synthesis thread and is the
 * only place an utterance is driven; onStop arrives from another thread and
 * does nothing but set the engine's abort flag, which is what unblocks the
 * read below.
 */
class EvvTtsService : TextToSpeechService() {

	private var engine: EvvEngine? = null
	private var settings: Settings? = null

	@Volatile
	private var stopped = false

	private var appliedRevision = -1

	/** When the user's dictionary file last changed, which is what says the
	 *  words taught to the engine are still the words in the file. */
	private var loadedWordsStamp = -1L

	private val wakeLockManager: PowerManager? by lazy { getSystemService(PowerManager::class.java) }
	private var wakeLock: PowerManager.WakeLock? = null

	private var keepAlive: KeepAlive? = null

	override fun onCreate() {
		super.onCreate()
		settings = Settings(this)
		keepAlive = KeepAlive(this)
		settings?.let { keepAlive?.sync(it.persistentNotification) }
	}

	override fun onDestroy() {
		super.onDestroy()
		keepAlive?.sync(false)
		keepAlive = null
		engine?.close()
		engine = null
		wakeLock?.takeIf { it.isHeld }?.release()
		wakeLock = null
	}

	// ---- languages -------------------------------------------------------

	private fun matchLanguage(lang: String?, country: String?): Pair<Int, Int>? {
		if (lang.isNullOrEmpty()) return null
		val wantLang = normalise(lang)
		val wantCountry = country?.takeIf { it.isNotEmpty() }?.let { normaliseCountry(it) }
		var byLanguage: Int? = null
		for (candidate in EvvEngine.available) {
			val loc = Eci.localeOf(candidate) ?: continue
			if (!loc.first.equals(wantLang, ignoreCase = true)) continue
			if (wantCountry != null && loc.second.equals(wantCountry, ignoreCase = true)) {
				return candidate to TextToSpeech.LANG_COUNTRY_AVAILABLE
			}
			if (byLanguage == null) byLanguage = candidate
		}
		return byLanguage?.let { it to TextToSpeech.LANG_AVAILABLE }
	}

	private fun normalise(lang: String): String =
		runCatching { Locale(lang).isO3Language }.getOrNull()?.takeIf { it.isNotEmpty() } ?: lang

	private fun normaliseCountry(country: String): String =
		runCatching { Locale("", country).isO3Country }.getOrNull()?.takeIf { it.isNotEmpty() } ?: country

	override fun onIsLanguageAvailable(lang: String?, country: String?, variant: String?): Int {
		val found = matchLanguage(lang, country) ?: return TextToSpeech.LANG_NOT_SUPPORTED
		return found.second
	}

	override fun onLoadLanguage(lang: String?, country: String?, variant: String?): Int {
		val found = matchLanguage(lang, country) ?: return TextToSpeech.LANG_NOT_SUPPORTED
		ensureEngine(found.first) ?: return TextToSpeech.LANG_NOT_SUPPORTED
		return found.second
	}

	override fun onGetLanguage(): Array<String> {
		val language = engine?.language ?: EvvEngine.available.firstOrNull() ?: return arrayOf("eng", "USA", "")
		val loc = Eci.localeOf(language) ?: return arrayOf("eng", "USA", "")
		return arrayOf(loc.first, loc.second, loc.third)
	}

	// ---- voices ----------------------------------------------------------

	private fun voiceName(language: Int, preset: Int): String {
		val loc = Eci.localeOf(language) ?: return "evv-$preset"
		return "${loc.first}-${loc.second}-${Eci.PRESET_NAMES[preset]}"
	}

	override fun onGetVoices(): MutableList<Voice> {
		val out = ArrayList<Voice>()
		for (language in EvvEngine.available) {
			val loc = Eci.localeOf(language) ?: continue
			val locale = Locale(loc.first, loc.second)
			for (preset in Eci.PRESET_NAMES.indices) {
				out.add(
					Voice(
						voiceName(language, preset),
						locale,
						Voice.QUALITY_NORMAL,
						Voice.LATENCY_VERY_LOW,
						false,
						emptySet()
					)
				)
			}
		}
		return out
	}

	private fun findVoice(name: String?): Pair<Int, Int>? {
		if (name.isNullOrEmpty()) return null
		for (language in EvvEngine.available) {
			for (preset in Eci.PRESET_NAMES.indices) {
				if (voiceName(language, preset) == name) return language to preset
			}
		}
		return null
	}

	override fun onIsValidVoiceName(name: String?): Int =
		if (findVoice(name) != null) TextToSpeech.SUCCESS else TextToSpeech.ERROR

	override fun onLoadVoice(name: String?): Int {
		val found = findVoice(name) ?: return TextToSpeech.ERROR
		ensureEngine(found.first) ?: return TextToSpeech.ERROR
		return TextToSpeech.SUCCESS
	}

	override fun onGetDefaultVoiceNameFor(lang: String?, country: String?, variant: String?): String? {
		val found = matchLanguage(lang, country) ?: return null
		return voiceName(found.first, settings?.voice ?: 0)
	}

	// ---- speaking --------------------------------------------------------

	private fun ensureEngine(language: Int): EvvEngine? {
		val held = engine
		if (held != null && held.language == language) return held
		held?.close()
		val made = EvvEngine.open(language)
		engine = made
		if (made != null) settings?.let { applySettings(made, it) }
		return made
	}

	private fun applySettings(target: EvvEngine, s: Settings) {
		target.setSampleRate(s.sampleRateHz)
		target.setAbbreviations(s.abbreviations)
		loadWords(target)
		target.applyVoice(s.voice, s.shape(s.voice))
		appliedRevision = s.revision
	}

	/** Teaching a few thousand words costs about half a second, so it happens
	 *  when the file changes rather than whenever any setting does. The check
	 *  is the file's own stamp, which is cheap enough to make on every
	 *  utterance, so a word added on the dictionary screen is heard the next
	 *  time anything is spoken without restarting the service. */
	private fun loadWords(target: EvvEngine) {
		val file = UserDictionary.file(this)
		val stamp = if (file.exists()) file.lastModified() else -1L
		if (stamp == loadedWordsStamp) return
		target.forgetDictionaries()
		if (file.exists()) {
			val taught = Dictionaries.load(target, Eci.DICT_MAIN, file, Charsets.UTF_8)
			Log.i(TAG, "user dictionary: $taught entries from ${file.name}")
		}
		loadedWordsStamp = stamp
	}

	override fun onStop() {
		stopped = true
		engine?.stop()
	}

	override fun onSynthesizeText(request: SynthesisRequest?, callback: SynthesisCallback?) {
		if (request == null || callback == null) return
		stopped = false
		val s = settings
		if (s == null) {
			callback.error(TextToSpeech.ERROR_SERVICE)
			return
		}
		// The keep-alive follows the setting, so a switch moved on the battery
		// screen is in force the next time anything is spoken.
		keepAlive?.sync(s.persistentNotification)
		// The voice is whichever the settings screen says, not whichever the
		// caller named, so the name only decides the language. It is asked
		// first: a caller that set a voice and then speaks with an empty or
		// foreign locale -- automation apps and TTS routers do -- has still
		// named the language. Failing that, the locale decides it; failing
		// both, the first language the engine has is better than silence,
		// which is what refusing a router's nameless request used to give.
		val wanted = findVoice(request.voiceName)
		val found = matchLanguage(request.language, request.country)
		val language = wanted?.first ?: found?.first ?: EvvEngine.available.firstOrNull()
		if (language == null) {
			callback.error(TextToSpeech.ERROR_NOT_INSTALLED_YET)
			return
		}
		val target = ensureEngine(language)
		if (target == null) {
			callback.error(TextToSpeech.ERROR_SERVICE)
			return
		}
		if (s.revision != appliedRevision) applySettings(target, s)
		// Re-sent every time rather than once, because it is the one setting
		// with no way of telling whether something else has moved it.
		target.setAbbreviations(s.abbreviations)
		loadWords(target)
		// The rate and pitch a client asks for are scaled onto the voice,
		// unless the settings say this app's own are to be forced over them:
		// a screen reader that has set a rate is asking for that rate, and
		// this is the one place its asking is listened to or not.
		if (!s.forceRate) target.setRatePercent(request.speechRate)
		if (!s.forcePitch) target.setPitchPercent(request.pitch)
		// Volume is the engine's own parameter and no client's number ever
		// reaches it -- Android applies a client's volume to the stream, after
		// the speech has left here -- so forcing it is re-asserting this app's
		// own number on every utterance, the way the abbreviation switch is
		// re-sent above.
		if (s.forceVolume) {
			s.shape(s.voice)[Eci.VOICE_VOLUME]?.let { volume -> target.setVolume(volume) }
		}
		val lock = acquireWakeLock(s)
		try {
			val text = TextPipeline.apply(request.charSequenceText?.toString().orEmpty(), s)
			if (text.isEmpty()) {
				callback.start(target.sampleRateHz, AudioFormat.ENCODING_PCM_16BIT, 1)
				callback.done()
				return
			}
			// The framework is not told an utterance has begun until there are
			// samples for it. Saying so up front and then being silenced before
			// the engine has made anything hands the reader a whole start and
			// finish with no speech in it, and TalkBack reads that as its queue
			// running dry. So the opening is made lazily, below.
			val opening = Opening(callback, target.sampleRateHz)
			val pace = Pace(target.sampleRateHz * BYTES_PER_SAMPLE)
			// Piece by piece, so that asking for silence waits out a piece
			// rather than the whole message: the engine cannot abandon what it
			// is saying. Pauses are shortened after the split rather than
			// before it: the annotation sits in front of the full stop, and a
			// piece boundary is found by looking at the end of a word.
			val prosody = Prosody.prefix(s.phrasePrediction)
			val pieces = TextPieces.split(text)
			for ((at, raw) in pieces.withIndex()) {
				if (stopped) break
				val piece = prosody + Pauses.apply(raw, s.pauses, at == pieces.lastIndex)
				if (!target.speak(piece)) {
					Log.e(TAG, "the engine refused ${piece.length} characters")
					callback.error(TextToSpeech.ERROR_SYNTHESIS)
					return
				}
				if (!pump(target, callback, pace, opening)) {
					remember(pace)
					return
				}
			}
			remember(pace)
			// Every utterance owes the framework a start and a finish, even one
			// the engine answered with no samples at all -- text that the
			// settings reduced to nothing. A callback left with neither is a
			// client that waits for ever and goes quiet, and the quiet is what
			// was reported when another engine such as AutoTTS asked here.
			if (opening.began) {
				callback.done()
			} else if (!stopped) {
				if (opening.open()) callback.done() else callback.error(TextToSpeech.ERROR_SYNTHESIS)
			}
		} finally {
			releaseWakeLock(lock)
		}
	}

	/** Hands one piece's samples on as they arrive, and answers whether the
	 *  caller may go on to the next. */
	private fun pump(target: EvvEngine, callback: SynthesisCallback, pace: Pace, opening: Opening): Boolean {
		val size = callback.maxBufferSize.coerceIn(MIN_CHUNK, MAX_CHUNK)
		val buffer = ByteArray(size)
		while (!stopped) {
			val n = target.read(buffer)
			if (n <= 0) break
			if (!opening.open()) {
				target.stop()
				return false
			}
			if (callback.audioAvailable(buffer, 0, n) != TextToSpeech.SUCCESS) {
				// The framework has torn the callback down already, so done()
				// is neither wanted nor listened to.
				target.stop()
				return false
			}
			pace.handed(n)
			hold(pace)
		}
		return true
	}

	private fun remember(pace: Pace) {
		if (pace.handedMs() > mostAudioHandedMs) mostAudioHandedMs = pace.handedMs()
	}

	// ---- keeping the CPU awake ------------------------------------------

	/** A partial wake lock, held across the utterance when the user asked for
	 *  it. On some devices the CPU sleeps mid-sentence and the tail of the
	 *  speech is lost, which is what this is for. */
	private fun acquireWakeLock(s: Settings): PowerManager.WakeLock? {
		if (!s.wakeLock) return null
		val held = wakeLock
		if (held != null && held.isHeld) return held
		val made = wakeLockManager?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$TAG:wake")?.apply {
			setReferenceCounted(false)
			acquire(WAKELOCK_TIMEOUT_MS)
		}
		wakeLock = made
		return made
	}

	private fun releaseWakeLock(lock: PowerManager.WakeLock?) {
		lock?.takeIf { it.isHeld }?.release()
	}

	/**
	 * Waits until the audio already handed over has nearly caught up.
	 *
	 * This engine is far faster than the speech it makes -- a second and a
	 * third of audio comes out in twelve milliseconds -- and Android takes
	 * every byte of it without complaint and queues it. So a reader swiping
	 * quickly has five or six items synthesised and queued while the first is
	 * still being spoken, and when it then asks for silence the whole queue
	 * goes, which is items that were never heard at all. Running no further
	 * ahead than [LEAD_MS] keeps at most one utterance in flight, so silence
	 * arrives where the reader actually is.
	 *
	 * The wait is in slices so that a stop is noticed at once rather than at
	 * the end of it.
	 */
	private fun hold(pace: Pace) {
		while (!stopped) {
			val ahead = pace.ahead() - LEAD_MS
			if (ahead <= 0) return
			try {
				Thread.sleep(minOf(ahead, SLICE_MS))
			} catch (stopping: InterruptedException) {
				Thread.currentThread().interrupt()
				return
			}
		}
	}

	/** Tells the framework an utterance has begun, once, and not before there
	 *  is something to hear. [began] answers whether it was ever told, which is
	 *  what decides whether it is owed a finish. */
	private class Opening(private val callback: SynthesisCallback, private val rateHz: Int) {
		var began = false
			private set

		fun open(): Boolean {
			if (began) return true
			began = callback.start(rateHz, AudioFormat.ENCODING_PCM_16BIT, 1) == TextToSpeech.SUCCESS
			return began
		}
	}

	/** How far the audio handed over has run ahead of the time it takes to
	 *  play it. */
	private class Pace(private val bytesPerSecond: Int) {
		private val from = System.nanoTime()
		private var bytes = 0L

		fun handed(more: Int) {
			bytes += more
		}

		fun handedMs(): Long = if (bytesPerSecond <= 0) 0 else bytes * 1000L / bytesPerSecond

		fun ahead(): Long = handedMs() - (System.nanoTime() - from) / 1_000_000L
	}

	internal companion object {
		/** The most audio, in milliseconds, that any one utterance has handed
		 *  over since this was last cleared. Nothing in the app reads it. It is
		 *  what lets a test say the engine is not running ahead of the speech,
		 *  which is invisible from outside: an engine that queues six items in
		 *  a tenth of a second and one that paces itself look the same to the
		 *  framework and sound completely different to a person. */
		@Volatile
		var mostAudioHandedMs: Long = 0

		const val TAG = "alteloquencetts"
		const val MIN_CHUNK = 1024
		const val MAX_CHUNK = 8192
		const val BYTES_PER_SAMPLE = 2
		/** How much audio may sit ahead of the speech, so that the track never
		 *  runs dry while the engine is held back. */
		const val LEAD_MS = 300L
		const val SLICE_MS = 50L
		/** The longest an utterance may hold the wake lock before the system
		 *  takes it back, as a ceiling rather than a course. */
		const val WAKELOCK_TIMEOUT_MS = 10 * 60 * 1000L
	}
}
