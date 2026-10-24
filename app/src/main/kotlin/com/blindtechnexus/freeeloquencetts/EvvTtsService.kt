package com.blindtechnexus.freeeloquencetts

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
import java.util.ArrayDeque
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
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
 * for every word in it. A text long enough to be a document is the exception in
 * the direction of less work rather than more: it is cut and rewritten one piece
 * at a time, as the utterance reaches each, so the first syllable is one piece
 * behind the request and not one document behind it.
 *
 * The samples travel from the engine to the framework in one buffer, as big as
 * the framework asks for and owned by the utterance rather than shared by the
 * service, and the engine is not held back in slices any more than [Pacing]
 * requires. A request
 * for silence does not wait out a slice: it wakes the wait, so the next
 * element can start as soon as the engine has dropped what it was making.
 *
 * The words a dictionary holds are rewritten in the pipeline rather than taught
 * to the engine. IBM's own dictionary is case-sensitive, which is why a word
 * added in the manager used to vanish in speech; the rewrite does not care
 * about case, and a revision on the file is what says the list in memory is
 * still the list on disk, so a word added on the dictionary screen is heard
 * the next time anything is spoken without restarting the service.
 *
 * A piece whose cut landed on a full stop ends where the engine would have
 * paused anyway. A piece whose cut had to be made in the middle of a sentence is
 * answered with the engine's own short-pause annotation, because four tenths of
 * a second of silence every five hundred characters is what reading a chapter
 * used to sound like, and none of it was in the text.
 */
class EvvTtsService : TextToSpeechService() {

	/** The engine instances, one per language, and the lock that says who may
	 *  open, re-configure or close one. The samples are read without the lock,
	 *  which is what lets a request for silence reach the thread waiting for
	 *  them.
	 *
	 *  A map rather than one instance because there are two languages now --
	 *  English as it is spoken in the United States and as it is spoken in the
	 *  United Kingdom -- and a screen reader that reads an American page and
	 *  then a British one must not pay a second of silence between them. Both
	 *  are opened on first use and neither is ever closed for the other,
	 *  which is the whole of what makes switching between them instant. */
	private val engines = HashMap<Int, EvvEngine>()

	private val engineLock = Any()

	/** The settings revision each instance was last configured at, so that a
	 *  switch moved on the settings screen is applied once on the next
	 *  utterance and never re-applied. */
	private val appliedRevisions = HashMap<Int, Int>()

	/** The instances an utterance went quiet in the middle of, which are given
	 *  back rather than guessed at. */
	private val suspect = HashSet<Int>()

	private var settings: Settings? = null

	@Volatile
	private var stopped = false

	/** The user's substitutions last handed to the pipeline, and the revision
	 *  of the abbreviation list they were read at. The file is not asked about
	 *  on every utterance: a write from the dictionary screen bumps the
	 *  revision, and that is the whole of the check. The user dictionary's own
	 *  version travels with its snapshot instead -- see [UserDictionary]. */
	@Volatile
	private var loadedAbbreviationsVersion = Int.MIN_VALUE

	@Volatile
	private var loadedAbbreviations: List<Abbreviations.Entry> = emptyList()

	/** The dictionary and the abbreviations a text is rewritten with, as one
	 *  thing to hand on. The dictionary is an index and not a list: it is
	 *  what the service holds, what the pipeline matches with, and -- see
	 *  [WordIndex] -- the only shape of a dictionary of millions of words
	 *  that a phone can hold. */
	private class Substitutions(
		val index: WordIndex,
		val abbreviations: List<Abbreviations.Entry>
	)

	/** Wakes a wait the moment silence is asked for, so a swipe does not sit
	 *  out the rest of a sleep before the next element can start. */
	private val gate = Object()

	/** The text being prepared for the utterance in flight, if there is one.
	 *  Volatile and read by [stopSpeakingNow], which arrives on whichever
	 *  thread asked for silence -- the framework's, the notification's, the
	 *  settings screen's -- and has to reach the work without waiting for
	 *  the thread that owns it. */
	@Volatile
	private var feeder: TextSource? = null

	/** The app's own player, for the routes that have this app play the
	 *  speech itself (see [SynthesisRouting]). Made on the first utterance
	 *  that wants it and kept for the life of the service. */
	private var player: SynthesisRouting.Player? = null

	/** Utterances being driven right now, so that the idle timer can tell a
	 *  quiet engine from a working one. */
	private val speaking = AtomicInteger()

	/** Whether the engine has just done something no setting explains, which is
	 *  answered by giving the instance back rather than by guessing at a fix.
	 *  Written on the synthesis thread and read when the utterance ends, so
	 *  there is nothing to race about: see [afterAnUtterance].
	 *
	 *  The language travels with it because there are two instances now and
	 *  only the one that misbehaved is given back: an American utterance that
	 *  went quiet must not cost the British voice a second of its own opening
	 *  the next time it is asked for. */
	@Volatile
	private var engineIsSuspect = false

	@Volatile
	private var suspectLanguage = Eci.ENGLISH_US

	/** Records that the instance speaking [language] misbehaved, without
	 *  giving it back yet: the hand that does that is [closeIdle], which runs
	 *  once the utterance that is still holding the instance is over. */
	private fun markSuspect(language: Int) {
		suspectLanguage = language
		engineIsSuspect = true
	}

	/** The thread the text of a long utterance is prepared on, so that the
	 *  work of the next piece happens while the piece before it is being
	 *  spoken rather than in front of it. One of them, daemon, named: the
	 *  work is a stream and only ever one utterance's worth of it. */
	private val textWork: ExecutorService = Executors.newSingleThreadExecutor(
		java.util.concurrent.ThreadFactory { work ->
			Thread(work, "evv-text").apply { isDaemon = true }
		}
	)

	@Volatile
	private var alive = false

	private val wakeLockManager: PowerManager? by lazy { getSystemService(PowerManager::class.java) }
	private var wakeLock: PowerManager.WakeLock? = null
	private var tailLock: PowerManager.WakeLock? = null

	private var keepAlive: KeepAlive? = null

	private val main = Handler(Looper.getMainLooper())

	/** Gives a suspect engine instance back, so that the next utterance opens a
	 *  fresh one. The engine is otherwise kept for ever -- making a new one
	 *  costs a second or so before the next first syllable -- so this only
	 *  ever runs from [afterAnUtterance] after an utterance the engine went
	 *  quiet in the middle of. */
	private val closeIdle = Runnable {
		if (speaking.get() > 0) return@Runnable
		val held = synchronized(engineLock) {
			if (speaking.get() > 0) return@synchronized emptyList<EvvEngine>()
			val given = ArrayList<EvvEngine>(suspect.size)
			for (language in suspect.toList()) {
				val instance = engines.remove(language) ?: continue
				appliedRevisions.remove(language)
				suspect.remove(language)
				given.add(instance)
			}
			given
		}
		// Off this thread, which is the main one. Closing an instance waits
		// for its reader to come out and for its worker to leave the engine,
		// which is milliseconds for a healthy one and the whole of the
		// bounded wait for a wedged one -- and a main thread held for
		// seconds is an app the system kills for not responding, which is
		// how giving back a quiet engine became a crash. The next utterance
		// opens a fresh one either way, since the instance is already
		// detached above.
		if (held.isNotEmpty()) {
			Thread({
				for (instance in held) {
					try {
						instance.close()
					} catch (_: Throwable) {
					}
				}
				Log.i(TAG, "the quiet engine was given back; the next utterance opens a fresh one")
			}, "evv-give-back").also { it.isDaemon = true }.start()
		}
	}

	override fun onCreate() {
		super.onCreate()
		alive = true
		instance = this
		val made = Settings(this)
		settings = made
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
		try { player?.release() } catch (_: Throwable) {}
		player = null
		val held = synchronized(engineLock) {
			val given = engines.values.toList()
			engines.clear()
			appliedRevisions.clear()
			suspect.clear()
			given
		}
		// On a thread of its own, for the same reason as above: this runs
		// on the main thread, and a wedged engine must not hold it. The
		// process is going away in any case, so a close that never finishes
		// costs nothing -- the system reclaims the native instance with it.
		if (held.isNotEmpty()) {
			Thread({
				for (instance in held) {
					try {
						instance.close()
					} catch (_: Throwable) {
					}
				}
			}, "evv-destroy").also { it.isDaemon = true }.start()
		}
		releaseWakeLock(wakeLock)
		wakeLock = null
		releaseWakeLock(tailLock)
		tailLock = null
		textWork.shutdownNow()
	}

	/**
	 * Opens the engine before anything asks to be heard.
	 *
	 * Opening the engine costs a second or so, and a service that starts when
	 * a screen reader starts is asked for its first sentence a second or two
	 * later. Paid at the start that is nothing anybody notices; paid in front
	 * of the first utterance it is a second of silence before "charging" or
	 * "unread message", which is exactly the wait that reads as a slow engine.
	 *
	 * The language the settings screen is set to is the one opened here,
	 * which is the one almost every utterance will be spoken in, and the
	 * other one opens the first time an utterance asks for it. Opening both
	 * would cost a second of startup for a voice that may never be used; the
	 * one that is used is warm before the first sentence.
	 *
	 * It runs on a thread of its own for the same reason it is worth doing at
	 * all: the system is waiting to bind this service, and every tenth of a
	 * second spent here is a tenth of a second before the phone will speak at
	 * all. The dictionary's index is asked for on the same thread, so the
	 * first utterance does not pay for a dictionary either.
	 *
	 * And the British instance is opened here as well, on a thread of its own
	 * rather than after the first: a second engine is a second of work, and a
	 * reader who has chosen English (United Kingdom) waits for it exactly once
	 * in the life of the process either way -- but a reader who has not chosen
	 * it waits for nothing, because this thread is not the one the first
	 * sentence runs on.
	 */
	private fun warmUp() {
		if (!EvvNative.loaded) return
		Thread({
			if (!alive) return@Thread
			substitutions()
			synchronized(engineLock) {
				if (!alive || speaking.get() > 0) return@synchronized
				val made = ensureEngineLocked(settings?.language ?: Eci.ENGLISH_US)
				if (made == null) return@synchronized
				settings?.let { applySettings(made, made.language, it) }
			}
		}, "evv-warm").also { it.isDaemon = true }.start()
	}

	// ---- languages -------------------------------------------------------

	/**
	 * What this engine answers when it is asked about a language.
	 *
	 * Eight languages, honestly advertised. Each of the eight answers for
	 * itself what it really is: country-available where its country was
	 * named, and available where only the language was, or a country the
	 * engine has no module for -- Australian English is the American voice
	 * under another flag, Swiss German the German one. And anything else --
	 * a language the engine has no module for, nothing at all -- is
	 * available too, because the engine speaks every utterance it is given
	 * in one of the eight rather than refusing it. That last answer is what
	 * keeps a TTS router such as Auto TTS routing: it probes with the
	 * device's locale, and an engine that answers "not supported" to the
	 * probe is an engine it never asks to speak, even in English. Answering
	 * available and then speaking is the behaviour to advertise, and it is
	 * this behaviour.
	 */
	private fun availabilityOf(lang: String?, country: String?): Int {
		val spoken = Eci.languageFor(lang, country) ?: return TextToSpeech.LANG_AVAILABLE
		return if (spoken.inCountry(country)) TextToSpeech.LANG_COUNTRY_AVAILABLE else TextToSpeech.LANG_AVAILABLE
	}

	/**
	 * Which of the eight a request is to be spoken in.
	 *
	 * The settings screen's answer when the language is forced there. Else
	 * the caller's own answer when it named one the engine has -- a screen
	 * reader that asked for de-DE is telling this service which language
	 * its page is in -- and the settings screen's answer when it did not, or
	 * named something no instance speaks. So a reader who has chosen
	 * English (United Kingdom) hears British English from everything that
	 * does not say otherwise, and hears German from the one app that asks
	 * for it, which is the behaviour with no surprises in it.
	 */
	private fun languageFor(request: SynthesisRequest, s: Settings): Int {
		if (s.forceLanguage) return s.language
		// The voice the caller chose by name says which language it is in,
		// and says it more exactly than the locale beside it: a caller that
		// picked deu-DEU-Reed out of the list wants German whatever its
		// page's locale is.
		val named = request.voiceName
		if (!named.isNullOrEmpty()) Eci.languageOfVoiceName(named)?.let { return it.code }
		return Eci.languageFor(request.language, request.country)?.code ?: s.language
	}

	override fun onIsLanguageAvailable(lang: String?, country: String?, variant: String?): Int =
		availabilityOf(lang, country)

	override fun onLoadLanguage(lang: String?, country: String?, variant: String?): Int {
		// Never throws: this runs on a binder thread, and an exception here
		// is a dead call rather than an answered one. Whatever went wrong,
		// the language question still has its honest answer below.
		val made = try {
			synchronized(engineLock) { ensureEngineLocked(languageFor(lang, country)) }
		} catch (_: Throwable) {
			null
		}
		if (made == null) return TextToSpeech.LANG_NOT_SUPPORTED
		return availabilityOf(lang, country)
	}

	/** The language a caller's own words name, for the calls that are asked
	 *  about a language without an utterance to go with it. */
	private fun languageFor(lang: String?, country: String?): Int {
		val s = settings
		if (s != null && s.forceLanguage) return s.language
		return Eci.languageFor(lang, country)?.code ?: s?.language ?: Eci.ENGLISH_US
	}

	override fun onGetLanguage(): Array<String> {
		val spoken = Eci.languageOf(settings?.language ?: Eci.ENGLISH_US)
		return arrayOf(spoken.iso3Language, spoken.iso3Country, "")
	}

	// ---- voices ----------------------------------------------------------

	/**
	 * The name of [preset] as callers know it. The form is the one this engine
	 * has always used -- eng-USA-Reed -- because callers keep the name they
	 * were given, and a renamed voice is a voice a screen reader that
	 * connected yesterday can no longer ask for by name.
	 */
	private fun voiceName(preset: Int, language: Int = Eci.ENGLISH_US): String =
		Eci.voiceName(preset.coerceIn(Eci.PRESET_NAMES.indices), language)

	override fun onGetVoices(): MutableList<Voice> {
		// The locale is a real one -- en-US, en-GB, not the ISO-3 pairs --
		// because that is what a Voice carries and what callers compare
		// against. A voice built on Locale("eng", "USA") never equals the
		// Locale.ENGLISH a router holds, which is one of the reasons a router
		// never routed here.
		//
		// Both languages are listed, and the nine voices of each: a screen
		// reader is shown two English voices to choose between and gets the
		// one it chose, which is the whole of what adding a second language
		// is for.
		val out = ArrayList<Voice>(Eci.PRESET_NAMES.size * Eci.LANGUAGES.size)
		for (language in Eci.LANGUAGES) {
			val locale = language.locale()
			for (preset in Eci.PRESET_NAMES.indices) {
				try {
					out.add(
						Voice(
							voiceName(preset, language.code),
							locale,
							Voice.QUALITY_NORMAL,
							Voice.LATENCY_VERY_LOW,
							false,
							emptySet()
						)
					)
				} catch (_: Throwable) {
					// One voice that would not build is one voice missing,
					// not a call that died. The engine still speaks in the
					// settings screen's voice whatever the list holds.
				}
			}
		}
		return out
	}

	/**
	 * Any name is a good name. The voice that speaks is the settings screen's,
	 * not the caller's -- a caller holds the Voice it was given however long
	 * the setting moves underneath it -- so refusing an unknown name refuses
	 * nothing but the utterance, and a TTS router forwarding one of its own
	 * names is exactly the caller that must not be refused.
	 */
	override fun onIsValidVoiceName(name: String?): Int =
		if (name.isNullOrEmpty()) TextToSpeech.ERROR else TextToSpeech.SUCCESS

	override fun onLoadVoice(name: String?): Int {
		if (name.isNullOrEmpty()) return TextToSpeech.ERROR
		val made = try {
			synchronized(engineLock) { ensureEngineLocked(languageFor(name)) }
		} catch (_: Throwable) {
			null
		}
		if (made == null) return TextToSpeech.ERROR
		return TextToSpeech.SUCCESS
	}

	/** The instance a name asks for, read out of the name itself: the voices
	 *  this engine offers are named eng-USA-Reed and eng-GBR-Reed, so which
	 *  of the two languages a name belongs to is in the name. Anything else
	 *  -- a router's own name, a name from another engine -- is answered with
	 *  the settings screen's language, which is the voice that speaks. */
	private fun languageFor(name: String): Int {
		val s = settings
		if (s != null && s.forceLanguage) return s.language
		return Eci.languageOfVoiceName(name)?.code ?: s?.language ?: Eci.ENGLISH_US
	}

	override fun onGetDefaultVoiceNameFor(lang: String?, country: String?, variant: String?): String = try {
		voiceName(
			(settings?.voice ?: 0).coerceIn(Eci.PRESET_NAMES.indices),
			languageFor(lang, country)
		)
	} catch (_: Throwable) {
		voiceName(0)
	}

	// ---- speaking --------------------------------------------------------

	/**
	 * The instance to speak [language] in, made if it is not there.
	 *
	 * The caller holds [engineLock]. Each language has an instance of its own,
	 * kept for ever: making one costs a second or so before the next first
	 * syllable, which is the wait a screen reader never forgives, so nothing
	 * here ever closes one to make room for the other.
	 *
	 * A language the engine was not built with -- the British module is
	 * compiled into this app, but a build of it that is not there should be a
	 * voice that falls back and not a service that answers silence -- is
	 * answered with the American instance, which speaks the text either way.
	 */
	private fun ensureEngineLocked(language: Int): EvvEngine? {
		engines[language]?.let { return it }
		val made = try {
			EvvEngine.open(language) ?: if (language != Eci.ENGLISH_US) EvvEngine.open(Eci.ENGLISH_US) else null
		} catch (_: Throwable) {
			null
		}
		if (made == null) return null
		engines[language] = made
		if (made.language != language) {
			// The fallback is kept under the language that was asked for as
			// well, so the next utterance in it does not try to open one
			// again: the second of work has already been paid.
			engines[made.language] = made
		}
		try {
			settings?.let { applySettings(made, language, it) }
		} catch (_: Throwable) {
			// The engine stands with whatever it had; the utterance still
			// speaks rather than being refused for a setting.
		}
		return made
	}

	/** The instance that speaks, and whether it was one there already. The
	 *  caller holds [engineLock]. */
	private fun engineFor(language: Int): EvvEngine? {
		val made = ensureEngineLocked(language)
		if (made == null) return null
		val s = settings ?: return made
		if (appliedRevisions[language] != s.revision) {
			applySettings(made, language, s)
		}
		return made
	}

	private fun applySettings(target: EvvEngine, language: Int, s: Settings) {
		try {
			target.setSampleRate(s.sampleRateHz)
		} catch (_: Throwable) {
		}
		try {
			target.setUpsampleMethod(AudioOptimization.methodFor(s.audioOptimization))
		} catch (_: Throwable) {
		}
		try {
			target.setAbbreviations(s.abbreviations)
		} catch (_: Throwable) {
		}
		try {
			target.applyVoice(s.voice, s.shape(s.voice))
		} catch (_: Throwable) {
		}
		appliedRevisions[language] = s.revision
	}

	/** The user's substitutions to rewrite this utterance with.
	 *
	 *  Neither list is read off the disk on every utterance. The user
	 *  dictionary carries its own version, and the trie the matching runs on is
	 *  built on a thread of its own by the write that moved it -- see
	 *  [UserDictionary.snapshot] -- so what is waited for here is a rebuild
	 *  that is usually finished already, and what is paid on the ordinary
	 *  utterance is one integer comparison. The abbreviations are the same
	 *  shape with a version of their own and no rebuild to wait for, being a
	 *  list rather than a structure. */
	@Synchronized
	private fun substitutions(): Substitutions {
		val dictionary = runCatching { UserDictionary.snapshot(this) }.getOrNull()
		val index = dictionary?.index ?: WordIndex.EMPTY
		val revision = Abbreviations.version
		if (revision != loadedAbbreviationsVersion) {
			loadedAbbreviations = runCatching { Abbreviations.entries(this) }
				.getOrNull() ?: loadedAbbreviations
			loadedAbbreviationsVersion = revision
		}
		return Substitutions(index, loadedAbbreviations)
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
	 */
	internal fun stopSpeakingNow() {
		synchronized(gate) {
			stopped = true
			gate.notifyAll()
		}
		// The text being prepared for a piece nobody will hear is dropped
		// where it stands. This is the half of interrupting that the engine
		// cannot be told about: a swipe that arrives while a thousand
		// characters are being rewritten used to leave that work running to
		// the end of the text behind the next utterance, and the next
		// utterance is what the person is waiting for.
		feeder?.cancel()
		// The sound this app is playing itself, if it is, stops where it
		// stands: this is the one half of a stop that reaches samples
		// already made, and only on the routes where they are ours.
		try { player?.cut() } catch (_: Throwable) {}
		// Every instance, because there may be several and only one of them
		// is speaking. An abort costs a lock and a flag when nothing is being
		// said in it, which is nothing against getting the wrong one back
		// and leaving a voice running that nobody asked for.
		for (held in synchronized(engineLock) { engines.values.toList() }) {
			runCatching { held.stop() }
				.onFailure { Log.e(TAG, "the abort did not reach the engine", it) }
		}
	}

	override fun onSynthesizeText(request: SynthesisRequest?, callback: SynthesisCallback?) {
		if (request == null || callback == null) return
		synchronized(gate) { stopped = false }
		val s = settings
		if (s == null) {
			try {
				callback.error(TextToSpeech.ERROR_SERVICE)
			} catch (_: Throwable) {
			}
			return
		}
		speaking.incrementAndGet()
		main.removeCallbacks(closeIdle)
		try {
			// The keep-alive follows the setting, so a switch moved on the
			// battery screen is in force the next time anything is spoken.
			keepAlive?.sync(s.persistentNotification)
			if (!EvvNative.loaded) {
				try {
					callback.error(TextToSpeech.ERROR_NOT_INSTALLED_YET)
				} catch (_: Throwable) {
				}
				return
			}
			// The language is the caller's when it named one of the eight,
			// and the settings screen's when it did not or when the screen
			// forces its own. Everything else
			// about the voice is the settings screen's and not the
			// caller's: a caller holds the Voice it was given however many
			// times the setting moves underneath it, so a router forwarding
			// its own name is answered in this app's voice rather than
			// refused -- which is what keeps a router's request from ever
			// being answered with silence.
			val language = try {
				languageFor(request, s)
			} catch (_: Throwable) {
				s.language
			}
			val target = try {
				synchronized(engineLock) { engineFor(language) }
			} catch (_: Throwable) {
				null
			}
			if (target == null) {
				try {
					callback.error(TextToSpeech.ERROR_SERVICE)
				} catch (_: Throwable) {
				}
				return
			}
			try {
				say(target, request, callback, s)
			} catch (failed: Throwable) {
				// The last resort, which say() itself should never reach:
				// an utterance that throws its way out still owes the
				// framework an answer, and the phone its next utterance.
				Log.e(TAG, "the utterance was lost", failed)
				try {
					callback.error(TextToSpeech.ERROR_SYNTHESIS)
				} catch (broken: Throwable) {
					// Nothing left to tell.
				}
			}
		} finally {
			speaking.decrementAndGet()
			afterAnUtterance()
		}
	}

	/**
	 * One utterance, from the text to the last sample.
	 *
	 * The shape of it is the answer to the two things a reader notices: how
	 * long the silence is before the first syllable, and how quickly a swipe
	 * stops it.
	 *
	 * The text is prepared on a thread of its own -- see [Prefetcher] -- so
	 * the pipeline work for the piece after this one happens while this one
	 * is being spoken. On a long text that is the difference between a
	 * tenth of a second of silence between pieces and none, and it is what
	 * lets a stop land in the middle of the work without waiting for it:
	 * [stopSpeakingNow] drops the prepared text where it stands.
	 *
	 * The samples are made by the engine on the synthesis thread as they are
	 * read, and the read is woken by a stop the moment it happens rather than
	 * at the end of a sleep. So an interruption and a synthesis are two
	 * threads doing their own work at the same time, and the only thing that
	 * orders them is the flag they both watch.
	 */
	private fun say(
		target: EvvEngine,
		request: SynthesisRequest,
		callback: SynthesisCallback,
		s: Settings
	) {
		try { target.setAbbreviations(s.abbreviations) } catch (_: Exception) {}
		try {
			if (!s.forceRate) target.setRatePercent(request.speechRate)
			if (!s.forcePitch) target.setPitchPercent(request.pitch)
		} catch (_: Exception) {}
		if (s.forceVolume) {
			try { s.shape(s.voice)[Eci.VOICE_VOLUME]?.let { v -> target.setVolume(v) } } catch (_: Exception) {}
		}
		// No notification is posted here, whatever the battery screen says.
		// The notification is the persistent one and only the persistent one:
		// a person who has turned it off gets no notification at all, and one
		// who has turned it on has it already, standing whether anything is
		// being spoken or not. See [KeepAlive].
		val lock = acquireWakeLock()
		try {
			val rawText = styledText(request, s)
			if (rawText.isEmpty()) {
				try {
					callback.start(target.sampleRateHz, AudioFormat.ENCODING_PCM_16BIT, 1)
					callback.done()
				} catch (_: Throwable) {
					// The framework is gone; there is nobody left to answer.
				}
				return
			}
			if (stopped) return
			val opening = Opening(callback, target.sampleRateHz)
			val pace = Pace(target.sampleRateHz * BYTES_PER_SAMPLE)
			val buffer = bufferFor(callback)
			// Where the sound goes. Under Automatic the samples are the
			// framework's and this app plays nothing; under every other
			// route this app plays them itself, and hands them on as well
			// only under Accessibility and the app. A player that would
			// not open, or that is busy with another utterance, falls back
			// to the framework, so a route that cannot be taken is still a
			// voice and not silence.
			val route = s.synthesisRouting
			var toFramework = SynthesisRouting.handsToFramework(route)
			var own: SynthesisRouting.Player? = null
			if (SynthesisRouting.playsItself(route)) {
				own = playerFor(route, target.sampleRateHz, request)
				if (own == null) toFramework = true
			}
			val prosody = Prosody.prefix(s.phrasePrediction)
			val lead = Pacing.DEFAULT_MS.toLong()
			val replacements = substitutions()
			// The level of this utterance, made once for it. The optimizer
			// carries the utterance's own loudness from piece to piece, which
			// is what keeps the voice even from the first piece to the last
			// instead of swelling as it finds its feet on every one.
			val optimizer = AudioOptimizer(s.audioOptimization, target.sampleRateHz)

			// Everything from here to the last piece is one utterance, and an
			// utterance that throws anywhere in the middle -- a sequence that
			// misbehaves, a callback that tears itself down, an engine that
			// wedges -- still owes the framework its finish and the phone its
			// next utterance. So the whole of it sits inside one hand that
			// catches everything: the utterance ends finished, the engine is
			// given back, and the thread that drove it goes back to the
			// framework unharmed.
			// A line is prepared here, where it stands; a document is
			// prepared on a thread of its own, a piece or two ahead. The
			// difference is a context switch in front of the first syllable
			// of everything a screen reader says.
			val work: TextSource = if (rawText.length <= INLINE_MAX_CHARS) {
				InlineText(rawText, s, replacements, prosody)
			} else {
				Prefetcher(rawText, s, replacements, prosody)
			}
			feeder = work
			// Every utterance starts and ends the framework's callback, and
			// an utterance this app plays itself still starts and ends it:
			// that is what keeps a client -- and a request to write a file,
			// which cannot be told apart from a request to speak -- with an
			// answer instead of a hang. What the framework is handed under
			// those routes is silence of the same length as what is heard,
			// so the file is the right length and the right thing to find
			// in it is nothing. One buffer of zeroes for the utterance, not
			// one per piece.
			val silent = if (!toFramework) ByteArray(buffer.size) else null
			try {
				// Pieces that threw, in a row. One bad piece is skipped and
				// the utterance goes on; a run of them is an engine that
				// wants giving back, and the utterance ends finished rather
				// than skipping its way through to nothing.
				var failures = 0
				while (!stopped) {
					// The next piece, ready to be said. This is the wait that
					// used to be the pipeline running on this thread, and it
					// is now somebody else's thread working while the piece
					// before it is still being spoken.
					val audio = work.take() ?: break
					if (stopped) break
					// A piece that carries nothing to say -- whitespace, say,
					// or pauses the settings annotated onto nothing -- is not
					// handed over. An utterance of nothing is a wait for
					// audio that is never coming, and the engine refusing it
					// is a refusal the caller should never have invited.
					if (EvvEngine.isBlankSpeech(audio)) continue
					try {
						if (!target.speak(audio, isStopped = { stopped })) {
							// A stopped utterance owes the framework nothing:
							// done() here would report a cut utterance as
							// heard out. A refusal anything else explains --
							// the engine wedged, the text refused -- is
							// answered by giving the instance back, so the
							// next utterance opens a fresh one instead of
							// queuing behind whatever went wrong.
							if (stopped) return
							Log.e(TAG, "engine refused ${audio.length} chars")
							markSuspect(target.language)
							try {
								if (opening.began) callback.done() else callback.error(TextToSpeech.ERROR_SYNTHESIS)
							} catch (_: Throwable) {
							}
							return
						}
						when (pump(target, callback, pace, opening, buffer, optimizer, Stability.ENGINE_SILENCE_MS, lead, own, toFramework, silent)) {
							Pump.Next -> Unit
							Pump.Over -> { remember(pace); finish(callback, opening, own != null); return }
							// Gone while still wanted is a framework that
							// stopped taking samples without asking for
							// silence -- a dead binder, a torn-down callback.
							// Returning with no finish would leave the caller
							// waiting for ever on an utterance that is never
							// coming, which is what reads as "it just stopped
							// synthesizing"; the utterance is finished
							// instead, and the call below swallows the
							// framework being past answering.
							Pump.Gone -> {
								remember(pace)
								if (!stopped) finish(callback, opening, own != null)
								return
							}
						}
						failures = 0
					} catch (failed: Throwable) {
						// One piece lost, out loud in the log, and the
						// utterance goes on with the next: a bad piece must
						// cost its own words and never the message around
						// them, and never the service either.
						Log.e(TAG, "a piece failed (${audio.length} chars) and was skipped", failed)
						failures++
						try {
							target.stop()
						} catch (broken: Throwable) {
							// The engine is being given back anyway.
						}
						markSuspect(target.language)
						if (failures >= MAX_PIECE_FAILURES) {
							remember(pace)
							finish(callback, opening, own != null)
							return
						}
					}
				}
				remember(pace)
				finish(callback, opening, own != null)
			} catch (failed: Throwable) {
				Log.e(TAG, "the utterance failed and was finished anyway", failed)
				markSuspect(target.language)
				try {
					target.stop()
				} catch (broken: Throwable) {
					// The engine is being given back anyway.
				}
				remember(pace)
				finish(callback, opening, own != null)
			} finally {
				// Nothing of this utterance may outlive it: the prepared
				// text goes, and the reference to it goes with it, so a stop
				// that arrives a moment later cannot cancel the next
				// utterance's work by mistake.
				work.cancel()
				if (feeder === work) feeder = null
				// An utterance that ended before its finish -- a stop that
				// beat the engine to it, an engine that refused the first
				// piece -- still gives the app's own player back, so the next
				// utterance on another thread can have it rather than being
				// played by the framework on a channel nobody chose.
				if (own != null) try { player?.end() } catch (_: Throwable) {}
			}
		} finally {
			releaseWakeLock(lock)
		}
	}

	/** Where the prepared pieces of an utterance come from: a precomputed
	 *  list for short text, a queue fed by another thread for long text. */
	private interface TextSource {
		/** The next piece ready to be spoken, or null when the utterance has
		 *  no more. Never blocks for longer than the piece being prepared. */
		fun take(): String?

		/** Drops whatever is left, now. Answers at once. */
		fun cancel()
	}

	/** The words of a piece as the engine is handed them: the prosody
	 *  prefix, the shortened pauses the settings ask for, and the short
	 *  boundary pause a cut in the middle of a sentence earns. */
	private fun audioFor(piece: TextPieces.Piece, last: Boolean, s: Settings, prosody: String): String {
		var audio = try {
			prosody + Pauses.apply(piece.text, s.pauses, last)
		} catch (_: Throwable) {
			prosody + piece.text
		}
		if (piece.forced && !last) audio += Pauses.AT_A_BOUNDARY
		return audio
	}

	/**
	 * The text of a short utterance, prepared where it stands.
	 *
	 * Almost everything a screen reader says is a line: "screen locked",
	 * "charging, 42 percent", "message from Sam". Handing one of those to a
	 * thread and waking back for it costs two context switches and a
	 * condition variable, which is small in absolute terms and is paid in
	 * front of the first syllable of every single thing said. Below
	 * [INLINE_MAX_CHARS] the whole of the text is rewritten and cut right
	 * here, on the synthesis thread, and the engine starts with no hand-off
	 * at all. The work is a pass over a few dozen characters; it is cheaper
	 * than the wake-up it replaces.
	 *
	 * Cancelling is a flag and a clear. Nothing was ever handed to another
	 * thread, so there is nothing to wait for.
	 */
	private inner class InlineText(
		raw: String,
		s: Settings,
		replacements: Substitutions,
		prosody: String
	) : TextSource {

		private val pieces = ArrayDeque<String>(4)

		@Volatile
		private var cancelled = false

		init {
			try {
				// The whole road first and the cut after, because a sentence
				// the rewritten text says ends in a better place than the
				// one the caller's text does.
				val text = try {
					TextPipeline.apply(raw, s, replacements.index, replacements.abbreviations)
				} catch (_: Throwable) {
					raw
				}
				val list = TextPieces.pieces(text)
				for (index in list.indices) {
					val piece = list[index]
					val audio = audioFor(piece, index == list.lastIndex, s, prosody)
					if (!EvvEngine.isBlankSpeech(audio)) pieces.addLast(audio)
				}
			} catch (broken: Throwable) {
				Log.e(TAG, "the text of a short utterance could not be prepared", broken)
			}
		}

		@Synchronized
		override fun take(): String? = if (cancelled) null else pieces.pollFirst()

		@Synchronized
		override fun cancel() {
			cancelled = true
			pieces.clear()
		}
	}

	/**
	 * The text of a long utterance, made a piece or two ahead of the speech.
	 *
	 * This is where the pipeline runs for a document, and running it here
	 * rather than on the synthesis thread is the whole of what makes a long
	 * text sound continuous and a swipe land at once. Three properties, and
	 * each of them is a delay that is not paid any more:
	 *
	 * - The next pieces are prepared while the one before them is being
	 *   spoken, so the engine is never idle waiting for text that is ready a
	 *   millisecond later than it is needed. Two pieces are kept ready,
	 *   which is more than enough: preparing one costs a fraction of the
	 *   time the engine takes to say one, and a queue that never fills is a
	 *   producer that never blocks.
	 * - A bounded number of pieces is prepared and no more, so a document is
	 *   not cut and rewritten in full behind a person who is about to swipe
	 *   away the first sentence of it.
	 * - [cancel] stops the work where it stands. A stopped utterance leaves
	 *   no thread grinding through the rest of a chapter, which is what the
	 *   next utterance would otherwise have been waiting behind.
	 *
	 * The pieces themselves are the same pieces in the same order, prepared
	 * by the same code, so what is said is what the single-threaded version
	 * said; only the thread it was said from has moved.
	 */
	private inner class Prefetcher(
		raw: String,
		s: Settings,
		replacements: Substitutions,
		prosody: String
	) : TextSource {

		/** Prepared pieces waiting to be spoken. Small on purpose: it is a
		 *  lead of text, not a queue of a document, and a stop throws it
		 *  away. */
		private val queue = ArrayBlockingQueue<String>(PREFETCH_DEPTH)

		@Volatile
		private var cancelled = false

		private var future: Future<*>? = null

		init {
			val job = try {
				textWork.submit(Runnable { produce(raw, s, replacements, prosody) })
			} catch (_: Throwable) {
				null
			}
			future = job
			if (job == null) {
				// Nowhere to prepare the text: the consumer is told there is
				// none rather than being left waiting for a thread the
				// executor would not give.
				queue.offer(TEXT_END)
			}
		}

		private fun produce(raw: String, s: Settings, replacements: Substitutions, prosody: String) {
			try {
				// A document is cut first and rewritten one piece at a time,
				// so the first syllable is one piece of work behind the
				// request; a shorter text goes through the road whole and is
				// cut after, because a sentence cut where the rewritten text
				// says is cut in the better place. See [TextPieces].
				val pieces: Iterator<TextPieces.Piece> = if (raw.length > STREAM_FROM_CHARS) {
					TextPipeline.pieces(raw, s, replacements.index, replacements.abbreviations).iterator()
				} else {
					val text = try {
						TextPipeline.apply(raw, s, replacements.index, replacements.abbreviations)
					} catch (_: Throwable) {
						raw
					}
					TextPieces.pieceSequence(text).iterator()
				}
				var held = next(pieces) ?: return
				while (!isCancelled()) {
					val following = next(pieces)
					val last = following == null
					val audio = audioFor(held, last, s, prosody)
					if (!EvvEngine.isBlankSpeech(audio) && !publish(audio)) return
					held = following ?: break
				}
			} catch (broken: Throwable) {
				// A piece that could not be prepared is a piece that is not
				// said, and the utterance around it is finished normally.
				if (!isCancelled()) Log.e(TAG, "the text of an utterance could not be prepared", broken)
			} finally {
				publish(TEXT_END)
			}
		}

		private fun next(pieces: Iterator<TextPieces.Piece>): TextPieces.Piece? = try {
			if (pieces.hasNext()) pieces.next() else null
		} catch (_: Throwable) {
			null
		}

		/** Hands one piece over, waiting only while the queue is full --
		 *  which is the engine telling the text thread to wait, and never the
		 *  other way around. */
		private fun publish(audio: String): Boolean {
			while (!cancelled) {
				try {
					if (queue.offer(audio, PUBLISH_WAIT_MS, TimeUnit.MILLISECONDS)) return true
				} catch (_: InterruptedException) {
					Thread.currentThread().interrupt()
					return false
				}
			}
			return false
		}

		/** The next prepared piece, or nothing when the utterance is over.
		 *  Blocks only while the piece is being prepared, which is the work
		 *  the engine is not waiting on because it is speaking the one
		 *  before it. The wait is bounded: a text thread that has died without
		 *  saying so is a finished utterance, not a service that hangs. */
		override fun take(): String? {
			val deadline = System.nanoTime() + MAX_TEXT_WAIT_MS * 1_000_000L
			while (!cancelled) {
				val left = (deadline - System.nanoTime()) / 1_000_000L
				if (left <= 0L) {
					Log.e(TAG, "the text of an utterance stopped arriving")
					cancel()
					return null
				}
				val item = try {
					queue.poll(minOf(left, WAIT_MS), TimeUnit.MILLISECONDS)
				} catch (stopping: InterruptedException) {
					Thread.currentThread().interrupt()
					return null
				} ?: continue
				if (item == TEXT_END) return null
				return item
			}
			return null
		}

		private fun isCancelled(): Boolean = cancelled || Thread.currentThread().isInterrupted

		/** Drops whatever is being prepared, now. Answers at once: the work
		 *  is on another thread and stopping it is a flag, an interrupt and a
		 *  cleared queue, never a wait for it to notice. */
		override fun cancel() {
			cancelled = true
			queue.clear()
			queue.offer(TEXT_END)
			try {
				future?.cancel(true)
			} catch (_: Throwable) {
			}
		}

	}

	/**
	 * What the caller asked to be heard.
	 *
	 * The request carries a character sequence rather than a string, and a
	 * sequence may carry styles along with the words -- bold, italic,
	 * colours, links. With [Settings.readTextStyles] on those are read out
	 * in words (see [TextStyles]); otherwise they are dropped the way they
	 * always were. Total: a sequence that misbehaves answers the words
	 * without it, and one that will not even do that answers nothing, which
	 * the caller above finishes as an empty utterance rather than as a
	 * service that died.
	 */
	private fun styledText(request: SynthesisRequest, s: Settings): String {
		return try {
			val sequence = request.charSequenceText ?: return ""
			if (s.readTextStyles) TextStyles.annotate(sequence) else sequence.toString()
		} catch (broken: Throwable) {
			try {
				request.charSequenceText?.toString().orEmpty()
			} catch (failed: Throwable) {
				""
			}
		}
	}

	/**
	 * Every utterance owes the framework a start and a finish, even one the
	 * engine answered with no samples at all -- text that the settings reduced
	 * to nothing. A callback left with neither is a client that waits for ever
	 * and goes quiet, and the quiet is what was reported when another engine
	 * such as AutoTTS asked here.
	 */
	private fun finish(callback: SynthesisCallback, opening: Opening, playedItself: Boolean) {
		// An utterance this app played itself is finished when it has been
		// heard, not when it was made: the finish waits for the last of it,
		// and a stop ends the wait at once. Whether it did is the
		// utterance's own answer and not a field of the service: two
		// callers can be mid-utterance at once, and one of them finishing
		// must not drain the other's sound or clear its flag.
		if (playedItself) {
			try { if (!stopped) player?.drain() } catch (_: Throwable) {}
			// The player stays open for the next utterance -- opening one
			// costs tens of milliseconds -- but it stops being this
			// utterance's, so the next one on another thread can have it.
			try { player?.end() } catch (_: Throwable) {}
		}
		// Every call guarded: the framework end of the callback can die at
		// any moment -- a caller gone, a binder torn down -- and a dead
		// framework throwing back is not an engine fault and must not read
		// as one. Nothing here throws, whatever the callback does.
		if (opening.began) {
			try {
				callback.done()
			} catch (_: Throwable) {
			}
		} else if (!stopped) {
			try {
				if (opening.open()) callback.done() else callback.error(TextToSpeech.ERROR_SYNTHESIS)
			} catch (_: Throwable) {
			}
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
		optimizer: AudioOptimizer,
		silenceMs: Int,
		leadMs: Long,
		own: SynthesisRouting.Player? = null,
		toFramework: Boolean = true,
		/** What the framework is handed when [toFramework] is off: a
		 *  buffer of zeroes the same size as [buffer], so the callback is
		 *  answered with silence rather than with nothing. */
		silent: ByteArray? = null
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
				markSuspect(target.language)
				return Pump.Over
			}
			if (n <= 0) return Pump.Next
			// Finished in place before anything hears it: the offset is lifted
			// out, the level is made even and the peaks that would clip are
			// rounded rather than cut. In Balanced it is nearly a no-op, which
			// is the point -- a clean recording should pass through untouched.
			optimizer.process(buffer, n)
			if (!opening.open()) {
				target.stop()
				return Pump.Gone
			}
			if (own != null && !own.write(buffer, n)) {
				// Cut from under it, or a player that died: the utterance
				// is over either way, and the framework still gets its
				// finish from the caller.
				target.stop()
				return Pump.Over
			}
			// The real samples under Automatic and under the route that
			// hands them on as well, silence under the rest: the callback is
			// answered either way.
			if (!deliver(callback, if (toFramework) buffer else (silent ?: ByteArray(n)), n)) {
				target.stop()
				return Pump.Gone
			}
			pace.handed(n)
			// The track paces an utterance this app plays itself -- a write
			// into a full track waits -- so the hold is for the framework's
			// samples alone.
			if (toFramework && !hold(pace, leadMs)) return Pump.Over
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
			val took = try {
				callback.audioAvailable(buffer, 0, n)
			} catch (_: Throwable) {
				// A dead framework answers by throwing rather than by
				// refusing. Counted as a refusal, like any other: the wait
				// below notices a stop at once, and past the last try the
				// utterance is finished as gone rather than retried for ever.
				TextToSpeech.ERROR
			}
			if (took == TextToSpeech.SUCCESS) return true
			if (++tries >= DELIVER_TRIES) return false
			if (!waitUpTo(DELIVER_WAIT_MS)) return false
		}
		return false
	}

	private fun remember(pace: Pace) {
		if (pace.handedMs() > mostAudioHandedMs) mostAudioHandedMs = pace.handedMs()
	}

	/** What happens in the pause after an utterance: a suspect engine given
	 *  back at once so that the next one opens fresh, the CPU held a little
	 *  longer so a second utterance onto a locked phone finds it awake, and
	 *  the engine given back after long enough idle. The last is fixed off
	 *  -- see [Stability] -- and its machinery stays for what it says rather
	 *  than for what it does. This runs before the synthesis thread is free
	 *  for the next request, so whatever it does delays the next first
	 *  syllable by that much. */
	private fun afterAnUtterance() {
		if (engineIsSuspect) {
			engineIsSuspect = false
			// Which instance it was is what the give-back needs, and it is
			// recorded here rather than there because the flag is the only
			// thing the utterance's own thread was in a position to set.
			synchronized(engineLock) { suspect.add(suspectLanguage) }
			main.removeCallbacks(closeIdle)
			main.post(closeIdle)
		}
		if (Stability.WAKE_LOCK && Stability.WAKE_LOCK_TAIL_MS > 0) {
			try {
				val held = tailLock
				if (held == null) {
					val made = wakeLockManager?.newWakeLock(
						PowerManager.PARTIAL_WAKE_LOCK, "$TAG:wake:tail"
					)?.apply {
						setReferenceCounted(false)
						// The timeout is the release. A lock the system takes back
						// at the end of the wait is a lock that cannot be forgotten
						// because the next utterance never came.
						runCatching { acquire(Stability.WAKE_LOCK_TAIL_MS.toLong()) }
					}
					tailLock = made
				} else {
					runCatching { if (held.isHeld) held.release() }
					runCatching { held.acquire(Stability.WAKE_LOCK_TAIL_MS.toLong()) }
				}
			} catch (_: Throwable) {
				// The CPU stays as it was; the utterance is over either way.
			}
		}
		val after = Stability.RELEASE_ENGINE_AFTER_S
		if (after > 0) {
			main.removeCallbacks(closeIdle)
			main.postDelayed(closeIdle, after * 1000L)
		}
	}

	// ---- keeping the CPU awake ------------------------------------------

	/** A partial wake lock, held across the utterance. Without it a locked
	 *  phone sleeps the CPU in the middle of synthesis, the waits time out
	 *  one after another, and the utterance is given up on -- which reads as
	 *  "locking the phone stops the speech". See [Stability]. */
	private fun acquireWakeLock(): PowerManager.WakeLock? {
		if (!Stability.WAKE_LOCK) return null
		val held = wakeLock
		if (held != null) {
			try {
				if (held.isHeld) return held
			} catch (_: Throwable) {
			}
		}
		val made = try {
			wakeLockManager?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$TAG:wake")?.apply {
				setReferenceCounted(false)
				runCatching { acquire(WAKELOCK_TIMEOUT_MS) }
			}
		} catch (_: Throwable) {
			null
		}
		wakeLock = made
		return made
	}

	private fun releaseWakeLock(lock: PowerManager.WakeLock?) {
		try {
			if (lock != null && lock.isHeld) lock.release()
		} catch (_: Throwable) {
		}
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
	 * The wait is woken the moment silence is asked for, so a swipe does not
	 * sit out the rest of a sleep before the next element can start.
	 */
	private fun hold(pace: Pace, leadMs: Long): Boolean {
		while (!stopped) {
			val ahead = pace.ahead() - leadMs
			if (ahead <= 0) return true
			if (!waitUpTo(ahead)) return false
		}
		return false
	}

	/** Sleeps up to [ms] or until silence is asked for, and answers whether
	 *  the utterance is still wanted. */
	private fun waitUpTo(ms: Long): Boolean {
		if (stopped) return false
		if (ms <= 0L) return true
		val deadline = System.nanoTime() + ms * 1_000_000L
		synchronized(gate) {
			while (!stopped) {
				val left = (deadline - System.nanoTime()) / 1_000_000L
				if (left <= 0L) return true
				try {
					gate.wait(left)
				} catch (stopping: InterruptedException) {
					Thread.currentThread().interrupt()
					return false
				}
			}
			return false
		}
	}

	/** The buffer the framework asked for, reused when the size has not
	 *  moved, so a swipe through a list does not allocate one per item. */
	private fun bufferFor(callback: SynthesisCallback): ByteArray {
		val want = try {
			callback.maxBufferSize
		} catch (_: Throwable) {
			DEFAULT_CHUNK
		}.coerceIn(MIN_CHUNK, MAX_CHUNK).let { it - (it and 1) }
		// Always a whole number of samples: a buffer of an odd number of
		// bytes would split the last sample between two reads, and every
		// sample after it in the second read would be the two bytes of two
		// different samples -- a click on every chunk boundary.
		//
		// Owned by the utterance rather than shared. The framework speaks
		// one utterance at a time from one client, but two clients -- a
		// screen reader and an app's own speech -- are two threads, and a
		// buffer kept on the service is two threads writing each other's
		// audio. Four kilobytes is nothing against that.
		return ByteArray(want)
	}

	/** The app's own player, ready for an utterance at [rateHz] on the
	 *  channel [route] names, or null when there is no player to be had. */
	private fun playerFor(route: Int, rateHz: Int, request: SynthesisRequest): SynthesisRouting.Player? {
		val held = player ?: SynthesisRouting.Player().also { player = it }
		// The volume the caller asked for is the one thing of the caller's
		// that still applies to a sound this app plays itself: the framework
		// would have applied it, so this app does.
		val volume = try {
			request.params?.getFloat(android.speech.tts.TextToSpeech.Engine.KEY_PARAM_VOLUME, 1f) ?: 1f
		} catch (_: Throwable) {
			1f
		}
		val ready = try {
			held.begin(rateHz, SynthesisRouting.usageOf(route), volume)
		} catch (_: Throwable) {
			false
		}
		return if (ready) held else null
	}

	/** Tells the framework an utterance has begun, once, and not before there is
	 *  something to hear. [began] answers whether it was ever told, which is
	 *  what decides whether it is owed a finish. */
	private class Opening(private val callback: SynthesisCallback, private val rateHz: Int) {
		var began = false
			private set

		fun open(): Boolean {
			if (began) return true
			began = try {
				callback.start(rateHz, AudioFormat.ENCODING_PCM_16BIT, 1) == TextToSpeech.SUCCESS
			} catch (_: Throwable) {
				false
			}
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

		const val TAG = "freeeloquencetts"
		const val MIN_CHUNK = 1024
		const val MAX_CHUNK = 65536
		const val DEFAULT_CHUNK = 4096
		const val BYTES_PER_SAMPLE = 2

		/** How long a wait on the prepared text is, between one glance at the
		 *  flags and the next. Long enough that a stall costs nothing, short
		 *  enough that a cancelled utterance's thread leaves promptly even
		 *  where the wait is not woken. */
		const val WAIT_MS = 50L

		/** How long a text has to be before it goes through the pipeline a
		 *  piece at a time rather than all at once. Two thousand characters is
		 *  a screen or two, which is where "the whole of it rewritten before
		 *  the first syllable" stops being a few milliseconds and starts being
		 *  a wait somebody can hear; below it the single pass is cheaper than
		 *  the per-piece calls and cuts the rewritten text rather than the
		 *  caller's, which is the better of the two. */
		const val STREAM_FROM_CHARS = 2000

		/** Below this many characters the whole utterance is prepared on the
		 *  synthesis thread. A line of a screen reader is a few dozen; a
		 *  paragraph is not, and a paragraph wants the other thread. */
		const val INLINE_MAX_CHARS = 320

		/** How many prepared pieces wait for the engine. Two is a lead of a
		 *  piece or two of text, which is far more than the text thread needs
		 *  and far less than a stop would have to throw away. */
		const val PREFETCH_DEPTH = 2

		/** How long the producer waits for room in the queue before looking
		 *  at the cancel flag again. */
		const val PUBLISH_WAIT_MS = 50L

		/** The longest a prepared piece may take to arrive before the
		 *  utterance is finished rather than waiting for ever. Ten seconds is
		 *  thousands of times the cost of preparing a piece; reaching it means
		 *  the text thread has died. */
		const val MAX_TEXT_WAIT_MS = 10_000L

		/** What the text thread puts in the queue to say there is no more.
		 *  An utterance is never this string. */
		const val TEXT_END = "\u0000end\u0000"

		/** What [EvvNative.read] answers when the engine has gone quiet rather
		 *  than finished, which is the difference between a piece that ended and
		 *  a message that has to be given up on. */
		const val TIMED_OUT = -2

		/** How much audio may sit ahead of the speech. Fixed: see [Pacing]. */
		const val LEAD_MS = Pacing.DEFAULT_MS.toLong()

		/** Low latency slice: 1ms for faster interruption. */
		const val SLICE_MS = 1L

		/** Low latency deliver: 1ms wait, 2000 tries = 2 sec patience, but interruption
		 *  is noticed within 1ms instead of 5ms. Faster stop response. */
		const val DELIVER_TRIES = 2000
		const val DELIVER_WAIT_MS = 1L

	/** The longest an utterance may hold the wake lock before the system
	 *  takes it back, as a ceiling rather than a course. */
	const val WAKELOCK_TIMEOUT_MS = 10 * 60 * 1000L

	/** How many pieces in a row may throw before the utterance ends
	 *  finished instead of skipping its way through to nothing. Three: one
	 *  bad piece is an accident, two a coincidence, and three an engine that
	 *  wants giving back. */
	const val MAX_PIECE_FAILURES = 3
	}
}
