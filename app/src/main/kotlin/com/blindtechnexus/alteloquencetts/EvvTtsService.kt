package com.blindtechnexus.alteloquencetts

import android.media.AudioFormat
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.speech.tts.SynthesisCallback
import android.speech.tts.SynthesisRequest
import android.speech.tts.TextToSpeech
import android.speech.tts.TextToSpeechService
import android.speech.tts.Voice
import android.util.Log
import java.io.File
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

/**
 * The engine Android talks to.
 *
 * onSynthesizeText runs on the framework's own synthesis thread and is the only
 * place an utterance is driven; onStop arrives from another thread and does
 * nothing but set the engine's abort flag, which is what unblocks the read
 * below.
 *
 * Everything about the middle of it is about one thing: a person who has asked
 * for speech should not have to wait for it. The engine makes audio a hundred
 * times faster than it can be heard, so every millisecond between the request
 * and the first sample is this app's own doing -- text rewritten twice over,
 * memory copied from one buffer into the next, a whole file's worth of
 * dictionary taught while somebody waits to hear one word. What is done here is
 * done once per utterance at most, and once is the plan:
 *
 * The text goes through the pipeline once and through [TextPieces] once, which
 * is one pass over it, and the pieces name the text without cutting a string out
 * for every word in it.
 *
 * The samples travel from the engine to the framework in one buffer, as big as
 * the framework asks for and allocated once for the life of the service, and the
 * engine is not held back in slices any more than [Pacing] requires.
 *
 * A request for silence is not waited on anywhere. The abort reaches the engine
 * by its flag, the read that is sitting on samples by the wake-up under the
 * same lock, and the sleeps that pace the delivery and retry a full framework
 * buffer by an interrupt of the utterance's own thread, so the next thing asked
 * to be said starts behind nothing.
 *
 * The words a dictionary holds are taught when the service starts rather than in
 * front of the first thing said ([warmUp]), and the file they live in is asked
 * about once per utterance rather than read once per setting.
 *
 * A piece whose cut landed on a full stop ends where the engine would have
 * paused anyway. A piece whose cut had to be made in the middle of a sentence is
 * answered with the engine's own short-pause annotation, because four tenths of
 * a second of silence every five hundred characters is what reading a chapter
 * used to sound like, and none of it was in the text.
 */
class EvvTtsService : TextToSpeechService() {

	/** The engine instance, and the lock that says who may open, re-configure or
	 *  close it. The samples are read without the lock, which is what lets a
	 *  request for silence reach the thread waiting for them. */
	@Volatile
	private var engine: EvvEngine? = null

	private val engineLock = Any()

	private var settings: Settings? = null

	@Volatile
	private var stopped = false

	@Volatile
	private var appliedRevision = -1

	/** When the user's dictionary file last changed, which is what says the
	 *  words taught to the engine are still the words in the file. */
	@Volatile
	private var loadedWordsStamp = -1L

	/** Utterances being driven right now, so that the idle timer can tell a
	 *  quiet engine from a working one. */
	private val speaking = AtomicInteger()

	/** The thread driving the utterance in hand, or null.
	 *
	 *  It is kept so that a request for silence can wake the waits inside the
	 *  utterance rather than waiting for them to finish. The abort flag and
	 *  the engine's own wake-up reach a read that is sitting on the samples;
	 *  this reaches the two sleeps that pace the delivery and retry a full
	 *  framework buffer, which are the last things between an interruption
	 *  being asked for and the next utterance being allowed to start.
	 *
	 *  Interrupting a thread that is doing framework work is not safe, so
	 *  nothing on this path interrupts anything; it is the utterance's own
	 *  thread that interrupts its own sleeps, and the flag each catch clears
	 *  rather than re-lights. */
	@Volatile
	private var utteranceThread: Thread? = null

	/** One read buffer for the life of the service, rather than one per
	 *  utterance. The framework drives utterances one at a time and on one
	 *  thread, so nothing is ever reading the buffer while the next
	 *  utterance is filling it, and its size is its own business. */
	private var chunkBuffer: ByteArray? = null

	/** Whether the engine has just done something no setting explains, which is
	 *  answered by giving the instance back rather than by guessing at a fix.
	 *  Written on the synthesis thread and read when the utterance ends, so
	 *  there is nothing to race about: see [afterAnUtterance]. */
	@Volatile
	private var engineIsSuspect = false

	@Volatile
	private var alive = false

	private val wakeLockManager: PowerManager? by lazy { getSystemService(PowerManager::class.java) }
	private var wakeLock: PowerManager.WakeLock? = null
	private var tailLock: PowerManager.WakeLock? = null

	private var keepAlive: KeepAlive? = null

	/** Every voice name this build offers, to the language and preset behind
	 *  it. A caller names a voice on every utterance, and rebuilding the
	 *  names to answer would be string building for a question whose answer
	 *  never changes. */
	private var voiceIndex: Map<String, Pair<Int, Int>> = emptyMap()

	private val matchLock = Any()
	private var matchKey: String? = null
	private var matchValue: Pair<Int, Int>? = null

	/** The dictionary file, resolved once. Resolving it walks the storage on
	 *  every call, which an utterance has no reason to pay for. */
	private var wordsFile: File? = null

	private val main = Handler(Looper.getMainLooper())

	/** Closes the engine after a stretch of nothing to say, which is the
	 *  language's tables and the engine's own arena given back to the phone. */
	private val closeIdle = Runnable {
		val s = settings ?: return@Runnable
		if (speaking.get() > 0) return@Runnable
		synchronized(engineLock) {
			if (speaking.get() > 0) return@synchronized
			val held = engine
			engine = null
			appliedRevision = -1
			if (held != null) {
				held.close()
				Log.i(TAG, "the engine gave back its memory after ${s.releaseEngineAfterS}s of nothing to say")
			}
		}
	}

	override fun onCreate() {
		super.onCreate()
		alive = true
		instance = this
		val made = Settings(this)
		settings = made
		voiceIndex = buildVoiceIndex()
		wordsFile = runCatching { UserDictionary.file(this) }.getOrNull()
		keepAlive = KeepAlive(this)
		keepAlive?.sync(made.persistentNotification)
		warmUp()
	}

	override fun onDestroy() {
		super.onDestroy()
		alive = false
		if (instance === this) instance = null
		main.removeCallbacks(closeIdle)
		keepAlive?.release()
		keepAlive = null
		val held = engine
		engine = null
		held?.close()
		releaseWakeLock(wakeLock)
		wakeLock = null
		releaseWakeLock(tailLock)
		tailLock = null
	}

	/**
	 * Opens the engine and teaches it the dictionary, before anything asks to
	 * be heard.
	 *
	 * Three thousand words take about half a second to put into the engine, and
	 * a service that starts when a screen reader starts is asked for its first
	 * sentence a second or two later. Paid at the start that is nothing anybody
	 * notices; paid in front of the first utterance it is half a second of
	 * silence before "charging" or "unread message", which is exactly the wait
	 * that reads as a slow engine.
	 *
	 * A language nobody has asked for is a wasted instance, so this opens the
	 * one the build was made with -- the same one a caller with no language of
	 * its own would be given -- and the service replaces it if a caller asks for
	 * another. It runs on a thread of its own for the same reason it is worth
	 * doing at all: the system is waiting to bind this service, and every tenth
	 * of a second spent here is a tenth of a second before the phone will speak
	 * at all.
	 */
	private fun warmUp() {
		val language = EvvEngine.available.firstOrNull() ?: return
		Thread({
			if (!alive) return@Thread
			synchronized(engineLock) {
				if (!alive || speaking.get() > 0) return@synchronized
				val made = ensureEngineLocked(language) ?: return@synchronized
				settings?.let { applySettings(made, it) }
			}
		}, "evv-warm").also { it.isDaemon = true }.start()
	}

	// ---- languages -------------------------------------------------------

	private fun matchLanguage(lang: String?, country: String?): Pair<Int, Int>? {
		val key = "${lang.orEmpty()}|${country.orEmpty()}"
		synchronized(matchLock) {
			if (key == matchKey) return matchValue
			val found = matchLanguageNow(lang, country)
			matchKey = key
			matchValue = found
			return found
		}
	}

	private fun matchLanguageNow(lang: String?, country: String?): Pair<Int, Int>? {
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
		val made = synchronized(engineLock) { ensureEngineLocked(found.first) }
		if (made == null) return TextToSpeech.LANG_NOT_SUPPORTED
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
		voiceIndex[name]?.let { return it }
		// The index is built in onCreate; a service created before the native
		// library finished loading answers from the loop instead, rather than
		// refusing a voice it has.
		for (language in EvvEngine.available) {
			for (preset in Eci.PRESET_NAMES.indices) {
				if (voiceName(language, preset) == name) return language to preset
			}
		}
		return null
	}

	private fun buildVoiceIndex(): Map<String, Pair<Int, Int>> {
		val index = LinkedHashMap<String, Pair<Int, Int>>()
		for (language in EvvEngine.available) {
			for (preset in Eci.PRESET_NAMES.indices) {
				index[voiceName(language, preset)] = language to preset
			}
		}
		return index
	}

	override fun onIsValidVoiceName(name: String?): Int =
		if (findVoice(name) != null) TextToSpeech.SUCCESS else TextToSpeech.ERROR

	override fun onLoadVoice(name: String?): Int {
		val found = findVoice(name) ?: return TextToSpeech.ERROR
		val made = synchronized(engineLock) { ensureEngineLocked(found.first) }
		if (made == null) return TextToSpeech.ERROR
		return TextToSpeech.SUCCESS
	}

	override fun onGetDefaultVoiceNameFor(lang: String?, country: String?, variant: String?): String? {
		val found = matchLanguage(lang, country) ?: return null
		return voiceName(found.first, settings?.voice ?: 0)
	}

	// ---- speaking --------------------------------------------------------

	/**
	 * The instance to speak in, made if it is not there.
	 *
	 * The caller holds [engineLock]. A caller that has been asked for a second
	 * language has the first one closed first, which is the one moment the
	 * engine's memory is worth more than the next few milliseconds of waiting
	 * for it.
	 */
	private fun ensureEngineLocked(language: Int): EvvEngine? {
		val held = engine
		if (held != null && held.language == language) return held
		held?.close()
		val made = EvvEngine.open(language)
		engine = made
		// A new instance knows nothing of the words taught to the one it
		// replaces, and the file they live in has not changed, so the stamp
		// that says "taught" is now a lie. Taking it back is what makes the
		// applySettings below teach them again; without it a dictionary
		// survived the file being edited and not the engine being remade.
		if (made != null && made !== held) loadedWordsStamp = -1L
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
	 *  when the file changes rather than whenever any setting does. The check is
	 *  the file's own stamp, which is two reads of a directory entry, so a word
	 *  added on the dictionary screen is heard the next time anything is spoken
	 *  without restarting the service.
	 *
	 *  The stamp is read outside [engineLock] and only a change takes the lock.
	 *  The lock is the thing that makes an utterance wait behind a warm-up or a
	 *  close, and the ordinary answer is that nothing has changed: paying the
	 *  wait for the two reads that say so is a toll on every word for a road
	 *  that is empty almost all of the time. When it is not empty the double
	 *  check inside the lock is what keeps two utterances from teaching the
	 *  same file twice. */
	private fun loadWordsIfChanged(target: EvvEngine) {
		if (wordsStamp() == loadedWordsStamp) return
		synchronized(engineLock) {
			if (wordsStamp() == loadedWordsStamp) return
			loadWords(target)
		}
	}

	/** The dictionary file's own stamp: when it last changed, or nought minus
	 *  one for a file that is not there. */
	private fun wordsStamp(): Long {
		val file = wordsFile ?: runCatching { UserDictionary.file(this) }.getOrNull()?.also { wordsFile = it }
		return if (file != null && file.exists()) file.lastModified() else -1L
	}

	/** Teach the file's words to [target] if they are not taught already.
	 *  The caller holds [engineLock]; the stamp check inside is what keeps
	 *  a configuration pass and a changed-file pass from both teaching. */
	private fun loadWords(target: EvvEngine) {
		val stamp = wordsStamp()
		if (stamp == loadedWordsStamp) return
		val file = wordsFile
		target.forgetDictionaries()
		if (file != null && file.exists()) {
			val taught = Dictionaries.load(target, Eci.DICT_MAIN, file, Charsets.UTF_8)
			Log.i(TAG, "user dictionary: $taught entries from ${file.name}")
		}
		loadedWordsStamp = stamp
	}

	override fun onStop() = stopSpeakingNow()

	/** Brings the utterance being spoken to an end.
	 *
	 * Two doors lead here: the framework's own stop, which arrives as
	 * [onStop], and the button on the keep-alive notification, which is the
	 * only way to be heard again quickly on a phone whose reader is halfway
	 * through a paragraph and whose app is on the other side of a lock screen.
	 * Nothing outside a speech service can reach the samples already handed to
	 * Android, so what is reachable is the wait: the flag takes the next turn
	 * of the pump away, which is the last thing that owes the framework a
	 * finish, and the abort is what makes a read that is sitting on the engine
	 * answer now rather than at the end of its own patience.
	 *
	 * It is deliberately not [stopSelf]: that would take the process's engine
	 * away with it, and the next request for speech would be answered by a
	 * phone that has to make the voice again from nothing.
	 *
	 * The interrupt of [utteranceThread] is what makes the stop instant
	 * rather than prompt: without it, a thread asleep in the pacing wait or
	 * in the retry on a full framework buffer notices the flag when its
	 * slice ends, and every later utterance is queued behind that slice.
	 */
	internal fun stopSpeakingNow() {
		stopped = true
		val held = engine
		if (held != null) {
			runCatching { held.stop() }
				.onFailure { Log.e(TAG, "the abort did not reach the engine", it) }
		}
		// The read waiting on samples is woken by the abort itself; this wakes
		// the thread that is not waiting on samples yet but is sleeping
		// between turns. Only while an utterance is actually being driven: a
		// flag set on an idle thread would sit there until somebody cleared
		// it, and the thread this one belongs to has framework work of its own
		// to do between utterances.
		if (speaking.get() > 0) {
			utteranceThread?.takeIf { it !== Thread.currentThread() }?.interrupt()
		}
	}

	override fun onSynthesizeText(request: SynthesisRequest?, callback: SynthesisCallback?) {
		if (request == null || callback == null) return
		// A stop that arrived while the last utterance was on its way out can
		// leave an interrupt standing on this one's thread, and a standing
		// flag makes every later sleep on this thread throw. The utterance
		// starts by taking the flag back, whatever was lit just before it.
		Thread.interrupted()
		stopped = false
		val s = settings
		if (s == null) {
			callback.error(TextToSpeech.ERROR_SERVICE)
			return
		}
		speaking.incrementAndGet()
		main.removeCallbacks(closeIdle)
		try {
			// The keep-alive follows the setting, so a switch moved on the
			// battery screen is in force the next time anything is spoken.
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
			val target = synchronized(engineLock) {
				val made = ensureEngineLocked(language)
				if (made != null && s.revision != appliedRevision) applySettings(made, s)
				made
			}
			if (target == null) {
				callback.error(TextToSpeech.ERROR_SERVICE)
				return
			}
			say(target, request, callback, s)
		} finally {
			speaking.decrementAndGet()
			afterAnUtterance(s)
		}
	}

	/** Everything that happens once the text is in hand: the settings that are
	 *  re-sent with every utterance, the road the text goes down, and the
	 *  samples on their way out. */
	private fun say(
		target: EvvEngine,
		request: SynthesisRequest,
		callback: SynthesisCallback,
		s: Settings
	) {
		// The thread a request for silence has to wake, from now until the
		// utterance is over.
		utteranceThread = Thread.currentThread()
		// Re-sent every time rather than once, because it is the one setting
		// with no way of telling whether something else has moved it.
		target.setAbbreviations(s.abbreviations)
		loadWordsIfChanged(target)
		// The rate and pitch a client asks for are scaled onto the voice,
		// unless the settings say this app's own are to be forced over them: a
		// screen reader that has set a rate is asking for that rate, and this is
		// the one place its asking is listened to or not.
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
		// Held in the foreground while this utterance is being made, when that
		// is asked for and the persistent notification is not already holding
		// it. Shown when speaking starts and taken away when it ends, with
		// the stop button the setting asks for: it stops the speech, which
		// is what there is to stop while this is up.
		val speakForeground = s.foregroundWhileSpeaking && !s.persistentNotification
		if (speakForeground) KeepAliveNotification.claim(this, s.notificationStopButton)
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
			val buffer = bufferFor(callback)
			// Piece by piece, so that asking for silence waits out a piece
			// rather than the whole message: the engine cannot abandon what it is
			// saying. Pauses are shortened after the split rather than before
			// it: the annotation sits in front of the full stop, and a piece
			// boundary is found by looking at the end of a word.
			val prosody = Prosody.prefix(s.phrasePrediction)
			val pieces = TextPieces.pieces(text)
			val lead = Pacing.leadFor(s.speechLeadMs, s.runAheadOnLongText, text.length, pieces.size)
			for ((at, piece) in pieces.withIndex()) {
				if (stopped) break
				val last = at == pieces.lastIndex
				var audio = prosody + Pauses.apply(piece.text, s.pauses, last)
				// A boundary the text did not ask for is no reason to stop for
				// four tenths of a second: the pieces of one utterance run on
				// into each other, so the gap the engine leaves at the end of
				// one is a pause nobody wrote.
				if (piece.forced && !last) audio += Pauses.AT_A_BOUNDARY
				if (!target.speak(audio)) {
					Log.e(TAG, "the engine refused ${audio.length} characters")
					// A refusal is the engine saying it will not take this
					// text, which the framework has to be told rather than
					// left to wait for.
					if (opening.began) callback.done() else callback.error(TextToSpeech.ERROR_SYNTHESIS)
					return
				}
				when (pump(target, callback, pace, opening, buffer, s.engineSilenceMs, lead)) {
					Pump.Next -> Unit
					Pump.Over -> {
						remember(pace)
						finish(callback, opening)
						return
					}
					Pump.Gone -> {
						remember(pace)
						return
					}
				}
			}
			remember(pace)
			finish(callback, opening)
		} finally {
			utteranceThread = null
			if (speakForeground) KeepAliveNotification.release(this, detach = false)
			releaseWakeLock(lock)
		}
	}

	/** The buffer the samples ride in, made once and kept. Its size is what
	 *  the framework says it will take, which is a property of its audio
	 *  player and not of the utterance, so in the ordinary run of a phone
	 *  this is one allocation for the life of the service rather than one
	 *  per thing said. */
	private fun bufferFor(callback: SynthesisCallback): ByteArray {
		val want = callback.maxBufferSize.coerceIn(MIN_CHUNK, MAX_CHUNK)
		chunkBuffer?.let { if (it.size == want) return it }
		return ByteArray(want).also { chunkBuffer = it }
	}

	/**
	 * Every utterance owes the framework a start and a finish, even one the
	 * engine answered with no samples at all -- text that the settings reduced
	 * to nothing. A callback left with neither is a client that waits for ever
	 * and goes quiet, and the quiet is what was reported when another engine
	 * such as AutoTTS asked here.
	 */
	private fun finish(callback: SynthesisCallback, opening: Opening) {
		if (opening.began) {
			callback.done()
		} else if (!stopped) {
			if (opening.open()) callback.done() else callback.error(TextToSpeech.ERROR_SYNTHESIS)
		}
	}

	/** What one piece ended with: the engine may go on, the utterance is over,
	 *  or the framework has stopped taking samples and owes nobody a finish. */
	private enum class Pump { Next, Over, Gone }

	/** Hands one piece's samples on as they arrive, and says what the caller
	 *  should do next. */
	private fun pump(
		target: EvvEngine,
		callback: SynthesisCallback,
		pace: Pace,
		opening: Opening,
		buffer: ByteArray,
		silenceMs: Int,
		leadMs: Long
	): Pump {
		while (!stopped) {
			val n = target.read(buffer, silenceMs)
			if (n == TIMED_OUT) {
				// The engine has gone quiet in the middle of a piece. Whatever
				// made it, the rest of this message is not coming either, and
				// every piece after it would pay the same wait -- so the
				// utterance ends here, out loud in the log, with what has been
				// heard already said and the framework told that it is over.
				Log.e(TAG, "the engine went silent after ${pace.handedMs()}ms of audio")
				// Stopping it is not politeness towards an engine that has
				// nothing to say. An utterance the engine is still working on
				// holds the thread that runs it, and the next piece waits for
				// that thread to be free -- so an engine left to finish what
				// nobody is waiting for any more is a phone whose next request
				// for speech arrives behind the one that was given up on.
				target.stop()
				// And whatever made it go silent is not mended by asking it
				// again: the instance is given back as soon as this utterance is
				// over, so the next one opens a fresh one. A second of the
				// reader's patience, against a voice that stays quiet for the
				// rest of the day.
				engineIsSuspect = true
				return Pump.Over
			}
			if (n <= 0) return Pump.Next
			if (!opening.open()) {
				target.stop()
				return Pump.Gone
			}
			if (!deliver(callback, buffer, n)) {
				target.stop()
				return Pump.Gone
			}
			pace.handed(n)
			if (!hold(pace, leadMs)) return Pump.Over
		}
		return Pump.Next
	}

	/**
	 * Hands one run of samples to the framework, and answers whether it took
	 * them.
	 *
	 * It answers no for two reasons that want different answers. A callback that
	 * has been torn down is an utterance that is over, and the speech stops
	 * here; a full buffer in a reader that is still playing is a momentary
	 * thing, and dropping the samples on the floor would be losing words for
	 * nothing. So the second is waited out, and the first is noticed at once
	 * because the flag that says so has been set before it matters.
	 */
	private fun deliver(callback: SynthesisCallback, buffer: ByteArray, n: Int): Boolean {
		var tries = 0
		while (!stopped) {
			if (callback.audioAvailable(buffer, 0, n) == TextToSpeech.SUCCESS) return true
			if (++tries >= DELIVER_TRIES) return false
			try {
				Thread.sleep(DELIVER_WAIT_MS)
			} catch (stopping: InterruptedException) {
				// The interrupt only ever comes from a request for silence,
				// which is what the stopped flag already says, and the flag is
				// swallowed on purpose: it was aimed at this sleep, and this
				// thread goes on from here to call back into the framework,
				// where a standing interrupt flag would trip whatever blocks
				// next and is nobody's business to be interrupted.
				return false
			}
		}
		return false
	}

	private fun remember(pace: Pace) {
		if (pace.handedMs() > mostAudioHandedMs) mostAudioHandedMs = pace.handedMs()
	}

	/** What happens in the pause after an utterance: the CPU held a little
	 *  longer when that was asked for, and the engine given back when it has
	 *  been quiet long enough. */
	private fun afterAnUtterance(s: Settings) {
		if (engineIsSuspect) {
			engineIsSuspect = false
			main.removeCallbacks(closeIdle)
			main.post(closeIdle)
		}
		if (s.wakeLock && s.wakeLockTailMs > 0) {
			val held = tailLock
			if (held == null) {
				val made = wakeLockManager?.newWakeLock(
					PowerManager.PARTIAL_WAKE_LOCK, "$TAG:wake:tail"
				)?.apply {
					setReferenceCounted(false)
					// The timeout is the release. A lock the system takes back
					// at the end of the wait is a lock that cannot be forgotten
					// because the next utterance never came.
					runCatching { acquire(s.wakeLockTailMs.toLong()) }
				}
				tailLock = made
			} else {
				runCatching { if (held.isHeld) held.release() }
				runCatching { held.acquire(s.wakeLockTailMs.toLong()) }
			}
		}
		val after = s.releaseEngineAfterS
		if (after > 0) {
			main.removeCallbacks(closeIdle)
			main.postDelayed(closeIdle, after * 1000L)
		}
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
		lock?.takeIf { it.isHeld }?.let { runCatching { it.release() } }
	}

	/**
	 * Waits until the audio already handed over has nearly caught up, and
	 * answers whether the piece after it is still wanted.
	 *
	 * This engine is far faster than the speech it makes, and Android queues
	 * every byte of it, so a reader swiping quickly has five or six items
	 * synthesised and waiting while the first is still being spoken -- and when
	 * it then asks for silence, the whole of that goes, which is items that were
	 * never heard at all. Running no further ahead than [leadMs] keeps at most
	 * one utterance in flight, so silence arrives where the reader actually is.
	 *
	 * How far ahead that is belongs to the message rather than to the engine,
	 * which is what [Pacing] works out: tight for the line of a list somebody is
	 * swiping through, wide for the paragraph nobody is going to interrupt
	 * between two of its own sentences.
	 *
	 * The wait is in slices so that a stop that arrives some other way is
	 * noticed within a slice, and a slice is slept only for what is left of
	 * the lead -- and it is interrupted outright by [stopSpeakingNow], which
	 * is what makes the notice of a real interruption a thing of the moment
	 * rather than a thing of the next slice.
	 */
	private fun hold(pace: Pace, leadMs: Long): Boolean {
		while (!stopped) {
			val ahead = pace.ahead() - leadMs
			if (ahead <= 0) return true
			try {
				Thread.sleep(minOf(ahead, SLICE_MS))
			} catch (stopping: InterruptedException) {
				// Swallowed for the same reason as in [deliver]: the stop
				// flag is the message, and the flag on the thread was aimed
				// at the sleep it woke.
				return false
			}
		}
		return false
	}

	/** Tells the framework an utterance has begun, once, and not before there is
	 *  something to hear. [began] answers whether it was ever told, which is
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

	/** How far the audio handed over has run ahead of the time it takes to play
	 *  it. */
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
		/** The service to speak to, while one is running. Nothing else in the
		 *  app can ask a bound service to be quiet, which is why the reference
		 *  is here and why it is cleared the moment the service is not. */
		@Volatile
		var instance: EvvTtsService? = null

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
		const val MAX_CHUNK = 65536
		const val BYTES_PER_SAMPLE = 2

		/** What [EvvNative.read] answers when the engine has gone quiet rather
		 *  than finished, which is the difference between a piece that ended and
		 *  a message that has to be given up on. */
		const val TIMED_OUT = -2

		/** How much audio may sit ahead of the speech. This is the answer the
		 *  battery screen used to offer a choice around and no longer does:
		 *  the setting, pinned, and this is its shape as a duration, which is
		 *  what the tests hold the two against. See [Pacing]. */
		const val LEAD_MS = Pacing.DEFAULT_MS.toLong()

		/** How long a wait for the speech to catch up is slept in one go. It is
		 *  what bounds how long a request for silence that arrived some other
		 *  way than by the interrupt can take, so it is kept well under the
		 *  lead and small enough that the waking of it is noise against the
		 *  ten milliseconds of audio it is worth. */
		const val SLICE_MS = 10L

		/** How often, and how many times, the framework is asked again for room.
		 *  A buffer that will not take another run of samples is nearly always
		 *  one that is full, and the next moment it is not is the next one to
		 *  ask in. The wait is the same slice as the pacing one: a full track
		 *  empties at the rate it plays at, and one wake in ten milliseconds
		 *  keeps a swallowed chunk from costing more than that. */
		const val DELIVER_TRIES = 500
		const val DELIVER_WAIT_MS = 10L

		/** The longest an utterance may hold the wake lock before the system
		 *  takes it back, as a ceiling rather than a course. */
		const val WAKELOCK_TIMEOUT_MS = 10 * 60 * 1000L
	}
}
