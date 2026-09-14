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
 * for every word in it. A text long enough to be a document is the exception in
 * the direction of less work rather than more: it is cut and rewritten one piece
 * at a time, as the utterance reaches each, so the first syllable is one piece
 * behind the request and not one document behind it.
 *
 * The samples travel from the engine to the framework in one buffer, as big as
 * the framework asks for and kept from one utterance to the next, and the
 * engine is not held back in slices any more than [Pacing] requires. A request
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

	/** The user's substitutions last handed to the pipeline, and the revision
	 *  of the abbreviation list they were read at. The file is not asked about
	 *  on every utterance: a write from the dictionary screen bumps the
	 *  revision, and that is the whole of the check. The user dictionary's own
	 *  version travels with its snapshot instead -- see [UserDictionary]. */
	@Volatile
	private var loadedAbbreviationsVersion = Int.MIN_VALUE

	@Volatile
	private var loadedAbbreviations: List<Abbreviations.Entry> = emptyList()

	/** The two lists a text is rewritten with, as one thing to hand on. */
	private class Substitutions(
		val words: List<Pair<String, String>>,
		val abbreviations: List<Abbreviations.Entry>
	)

	/** Wakes a wait the moment silence is asked for, so a swipe does not sit
	 *  out the rest of a sleep before the next element can start. */
	private val gate = Object()

	/** The buffer the framework is filled from, kept from one utterance to
	 *  the next when the size it asked for has not moved. */
	private var chunk: ByteArray? = null

	/** Utterances being driven right now, so that the idle timer can tell a
	 *  quiet engine from a working one. */
	private val speaking = AtomicInteger()

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

	private val main = Handler(Looper.getMainLooper())

	/** Gives a suspect engine instance back, so that the next utterance opens a
	 *  fresh one. The engine is otherwise kept for ever -- making a new one
	 *  costs a second or so before the next first syllable -- so this only
	 *  ever runs from [afterAnUtterance] after an utterance the engine went
	 *  quiet in the middle of. */
	private val closeIdle = Runnable {
		if (speaking.get() > 0) return@Runnable
		synchronized(engineLock) {
			if (speaking.get() > 0) return@synchronized
			val held = engine
			engine = null
			appliedRevision = -1
			if (held != null) {
				held.close()
				Log.i(TAG, "the quiet engine was given back; the next utterance opens a fresh one")
			}
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
		val held = engine
		engine = null
		held?.close()
		releaseWakeLock(wakeLock)
		wakeLock = null
		releaseWakeLock(tailLock)
		tailLock = null
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
	 * There is one language and so one instance, the one every utterance
	 * speaks in, so a warm one is never wasted. It runs on a thread of
	 * its own for the same reason it is worth doing at all: the system is
	 * waiting to bind this service, and every tenth of a second spent here is a
	 * tenth of a second before the phone will speak at all.
	 */
	private fun warmUp() {
		if (!EvvNative.loaded) return
		Thread({
			if (!alive) return@Thread
			substitutions()
			synchronized(engineLock) {
				if (!alive || speaking.get() > 0) return@synchronized
				val made = ensureEngineLocked() ?: return@synchronized
				settings?.let { applySettings(made, it) }
			}
		}, "evv-warm").also { it.isDaemon = true }.start()
	}

	// ---- languages -------------------------------------------------------

	/**
	 * What this engine answers when it is asked about a language.
	 *
	 * One voice, honestly advertised. English (United States) in any of its
	 * spellings is the voice itself, so it is country-available; any other
	 * English is the same voice under another flag, so it is available; and
	 * anything else -- another language, nothing at all -- is available too,
	 * because the engine speaks every utterance it is given in the voice
	 * rather than refusing it. That last answer is what keeps a TTS router
	 * such as Auto TTS routing: it probes with the device's locale, and an
	 * engine that answers "not supported" to the probe is an engine it never
	 * asks to speak, even in English. Answering available and then speaking
	 * is the behaviour to advertise, and it is this behaviour.
	 */
	private fun availabilityOf(lang: String?, country: String?): Int {
		if (isEnglish(lang)) {
			return if (isUnitedStates(country)) TextToSpeech.LANG_COUNTRY_AVAILABLE
			else TextToSpeech.LANG_AVAILABLE
		}
		return TextToSpeech.LANG_AVAILABLE
	}

	private fun isEnglish(lang: String?): Boolean =
		lang != null && (lang.equals("eng", ignoreCase = true) || lang.equals("en", ignoreCase = true))

	private fun isUnitedStates(country: String?): Boolean =
		country != null && (country.equals("USA", ignoreCase = true) || country.equals("US", ignoreCase = true))

	override fun onIsLanguageAvailable(lang: String?, country: String?, variant: String?): Int =
		availabilityOf(lang, country)

	override fun onLoadLanguage(lang: String?, country: String?, variant: String?): Int {
		val made = synchronized(engineLock) { ensureEngineLocked() }
		if (made == null) return TextToSpeech.LANG_NOT_SUPPORTED
		return availabilityOf(lang, country)
	}

	override fun onGetLanguage(): Array<String> = arrayOf(Eci.ISO3_LANGUAGE, Eci.ISO3_COUNTRY, "")

	// ---- voices ----------------------------------------------------------

	/**
	 * The name of [preset] as callers know it. The form is the one this engine
	 * has always used -- eng-USA-Reed -- because callers keep the name they
	 * were given, and a renamed voice is a voice a screen reader that
	 * connected yesterday can no longer ask for by name.
	 */
	private fun voiceName(preset: Int): String =
		"${Eci.ISO3_LANGUAGE}-${Eci.ISO3_COUNTRY}-${Eci.PRESET_NAMES[preset]}"

	override fun onGetVoices(): MutableList<Voice> {
		// The locale is a real one -- en-US, not the ISO-3 pair -- because
		// that is what a Voice carries and what callers compare against. A
		// voice built on Locale("eng", "USA") never equals the Locale.ENGLISH
		// a router holds, which is one of the reasons a router never routed
		// here.
		val locale = Locale("en", "US")
		val out = ArrayList<Voice>(Eci.PRESET_NAMES.size)
		for (preset in Eci.PRESET_NAMES.indices) {
			out.add(
				Voice(
					voiceName(preset),
					locale,
					Voice.QUALITY_NORMAL,
					Voice.LATENCY_VERY_LOW,
					false,
					emptySet()
				)
			)
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
		val made = synchronized(engineLock) { ensureEngineLocked() }
		if (made == null) return TextToSpeech.ERROR
		return TextToSpeech.SUCCESS
	}

	override fun onGetDefaultVoiceNameFor(lang: String?, country: String?, variant: String?): String =
		voiceName((settings?.voice ?: 0).coerceIn(Eci.PRESET_NAMES.indices))

	// ---- speaking --------------------------------------------------------

	/**
	 * The instance to speak in, made if it is not there.
	 *
	 * The caller holds [engineLock]. There is one language and so one
	 * instance, kept for ever: making a new one costs a second or so before
	 * the next first syllable, and nothing here ever asks for another.
	 */
	private fun ensureEngineLocked(): EvvEngine? {
		engine?.let { return it }
		val made = EvvEngine.open(Eci.ENGLISH_US)
		engine = made
		if (made != null) settings?.let { applySettings(made, it) }
		return made
	}

	private fun applySettings(target: EvvEngine, s: Settings) {
		target.setSampleRate(s.sampleRateHz)
		target.setUpsampleMethod(AudioOptimization.methodFor(s.audioOptimization))
		target.setAbbreviations(s.abbreviations)
		target.applyVoice(s.voice, s.shape(s.voice))
		appliedRevision = s.revision
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
		val words = dictionary?.entries ?: emptyList()
		val revision = Abbreviations.version
		if (revision != loadedAbbreviationsVersion) {
			loadedAbbreviations = runCatching { Abbreviations.entries(this) }
				.getOrNull() ?: loadedAbbreviations
			loadedAbbreviationsVersion = revision
		}
		return Substitutions(words, loadedAbbreviations)
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
		val held = engine
		if (held != null) {
			runCatching { held.stop() }
				.onFailure { Log.e(TAG, "the abort did not reach the engine", it) }
		}
	}

	override fun onSynthesizeText(request: SynthesisRequest?, callback: SynthesisCallback?) {
		if (request == null || callback == null) return
		synchronized(gate) { stopped = false }
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
			// caller named: a caller holds the Voice it was given however many
			// times the setting moves underneath it. So the name is not even
			// asked -- neither is the locale, since there is one language and
			// one instance that is never closed for another. Whatever the
			// caller asked for, in whatever language or under whatever name a
			// TTS router forwarded, is spoken in the voice, which is what keeps
			// a router's request from ever being answered with silence.
			if (!EvvNative.loaded) {
				callback.error(TextToSpeech.ERROR_NOT_INSTALLED_YET)
				return
			}
			val target = synchronized(engineLock) {
				val made = ensureEngineLocked()
				if (made != null && s.revision != appliedRevision) applySettings(made, s)
				made
			}
			if (target == null) {
				callback.error(TextToSpeech.ERROR_SERVICE)
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
		val speakForeground = Stability.FOREGROUND_WHILE_SPEAKING && !s.persistentNotification
		if (speakForeground) { try { KeepAliveNotification.claim(this, s.notificationStopButton) } catch (_: Exception) {} }
		val lock = acquireWakeLock()
		try {
			val rawText = styledText(request, s)
			if (rawText.isEmpty()) {
				callback.start(target.sampleRateHz, AudioFormat.ENCODING_PCM_16BIT, 1)
				callback.done()
				return
			}
			if (stopped) return
			val opening = Opening(callback, target.sampleRateHz)
			val pace = Pace(target.sampleRateHz * BYTES_PER_SAMPLE)
			val buffer = bufferFor(callback)
			val prosody = Prosody.prefix(s.phrasePrediction)
			val lead = Pacing.DEFAULT_MS.toLong()
			val replacements = substitutions()

			// Everything from here to the last piece is one utterance, and an
			// utterance that throws anywhere in the middle -- a sequence that
			// misbehaves, a callback that tears itself down, an engine that
			// wedges -- still owes the framework its finish and the phone its
			// next utterance. So the whole of it sits inside one hand that
			// catches everything: the utterance ends finished, the engine is
			// given back, and the thread that drove it goes back to the
			// framework unharmed. Whatever the mixed scripts of the world do
			// to a piece, they do it to that utterance and to nothing after
			// it.
			try {
				// The pieces, as a sequence rather than a list, and the whole of
				// the difference is what a document does with it. A list is every
				// piece cut and rewritten before the first one is spoken, so a
				// chapter of a book is silence for as long as it takes to rewrite a
				// chapter, and a person who swipes away a second in has paid for
				// all of it and heard one sentence. A sequence rewrites the piece
				// the utterance has reached, which puts the first syllable one
				// piece of work -- a couple of hundred characters -- behind the
				// request instead of one document behind it.
				//
				// Short text takes the road it always took: the whole of it through
				// the pipeline and then cut, because a sentence that has been
				// rewritten before it is cut is cut where the rewritten text says
				// and not where the caller's did, and for a message that is the
				// better answer.
				val stream: Sequence<TextPieces.Piece> =
					if (rawText.length > STREAM_FROM_CHARS) {
						TextPipeline.pieces(rawText, s, replacements.words, replacements.abbreviations)
					} else {
						val text = try {
							TextPipeline.apply(rawText, s, replacements.words, replacements.abbreviations)
						} catch (_: Exception) {
							rawText
						}
						if (text.isEmpty()) {
							callback.start(target.sampleRateHz, AudioFormat.ENCODING_PCM_16BIT, 1)
							callback.done()
							return
						}
						TextPieces.pieceSequence(text)
					}

				// One piece of lookahead, held in hand rather than read from a
				// list. The end of an utterance is where the engine's own pause
				// goes and where no boundary pause is added, and a sequence cannot
				// say which of its pieces is the last until it has asked for the
				// one after it -- so the one after it is asked for here, spoken
				// next, and held over. A reference, against a first syllable that
				// used to wait for a document.
				val iterator = stream.iterator()
				var held: TextPieces.Piece? = try {
					if (iterator.hasNext()) iterator.next() else null
				} catch (broken: Exception) {
					null
				}
				if (held == null) {
					callback.start(target.sampleRateHz, AudioFormat.ENCODING_PCM_16BIT, 1)
					callback.done()
					return
				}
				// The level of this utterance, made once for it. The optimizer
				// carries the utterance's own loudness from piece to piece, which
				// is what keeps the voice even from the first piece to the last
				// instead of swelling as it finds its feet on every one.
				val optimizer = AudioOptimizer(s.audioOptimization, target.sampleRateHz)
				// Pieces that threw, in a row. One bad piece is skipped and the
				// utterance goes on; a run of them is an engine that wants
				// giving back, and the utterance ends finished rather than
				// skipping its way through to nothing.
				var failures = 0
				while (!stopped) {
					val piece = held ?: break
					val next = try {
						if (iterator.hasNext()) iterator.next() else null
					} catch (broken: Exception) {
						null
					}
					val last = next == null
					var audio = try { prosody + Pauses.apply(piece.text, s.pauses, last) } catch (_: Exception) { prosody + piece.text }
					if (piece.forced && !last) audio += Pauses.AT_A_BOUNDARY
					// A piece that carries nothing to say -- whitespace, say,
					// or pauses the settings annotated onto nothing -- is not
					// handed over. An utterance of nothing is a wait for audio
					// that is never coming, and the engine refusing it is a
					// refusal the caller should never have invited.
					if (EvvEngine.isBlankSpeech(audio)) {
						held = next
						continue
					}
					// Asked again here, after the text work above, because a stop
					// lands in milliseconds and the work takes them: handing a
					// doomed piece over queues an utterance nobody reads while the
					// next one waits behind it. The fence inside speak() closes
					// what is left of the window.
					if (stopped) break
					try {
						if (!target.speak(audio, isStopped = { stopped })) {
							// A stopped utterance owes the framework nothing: done()
							// here would report a cut utterance as heard out. A refusal
							// anything else explains -- the engine wedged, the text
							// refused -- is answered by giving the instance back, so
							// the next utterance opens a fresh one instead of queuing
							// behind whatever went wrong.
							if (stopped) return
							Log.e(TAG, "engine refused ${audio.length} chars")
							engineIsSuspect = true
							if (opening.began) callback.done() else callback.error(TextToSpeech.ERROR_SYNTHESIS)
							return
						}
						when (pump(target, callback, pace, opening, buffer, optimizer, Stability.ENGINE_SILENCE_MS, lead)) {
							Pump.Next -> Unit
							Pump.Over -> { remember(pace); finish(callback, opening); return }
							Pump.Gone -> { remember(pace); return }
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
						engineIsSuspect = true
						if (failures >= MAX_PIECE_FAILURES) {
							remember(pace)
							finish(callback, opening)
							return
						}
					}
					held = next
				}
				remember(pace)
				finish(callback, opening)
			} catch (failed: Throwable) {
				Log.e(TAG, "the utterance failed and was finished anyway", failed)
				engineIsSuspect = true
				try {
					target.stop()
				} catch (broken: Throwable) {
					// The engine is being given back anyway.
				}
				remember(pace)
				finish(callback, opening)
			}
		} finally {
			if (speakForeground) { try { KeepAliveNotification.release(this, detach = false) } catch (_: Exception) {} }
			releaseWakeLock(lock)
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
		optimizer: AudioOptimizer,
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
			// Finished in place before anything hears it: the offset is lifted
			// out, the level is made even and the peaks that would clip are
			// rounded rather than cut. In Balanced it is nearly a no-op, which
			// is the point -- a clean recording should pass through untouched.
			optimizer.process(buffer, n)
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
			main.removeCallbacks(closeIdle)
			main.post(closeIdle)
		}
		if (Stability.WAKE_LOCK && Stability.WAKE_LOCK_TAIL_MS > 0) {
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
		val want = callback.maxBufferSize.coerceIn(MIN_CHUNK, MAX_CHUNK)
		val held = chunk
		if (held != null && held.size == want) return held
		return ByteArray(want).also { chunk = it }
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

		const val TAG = "freeeloquencetts"
		const val MIN_CHUNK = 1024
		const val MAX_CHUNK = 65536
		const val BYTES_PER_SAMPLE = 2

		/** How long a text has to be before it goes through the pipeline a
		 *  piece at a time rather than all at once. Ten thousand characters is
		 *  a couple of pages, which is where "the whole of it rewritten before
		 *  the first syllable" stops being a few milliseconds and starts being
		 *  a wait somebody can hear; below it the single pass is cheaper than
		 *  the per-piece calls and cuts the rewritten text rather than the
		 *  caller's, which is the better of the two. */
		const val STREAM_FROM_CHARS = 10000

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
