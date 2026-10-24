package com.blindtechnexus.freeeloquencetts

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.util.Log

/**
 * Where the sound goes.
 *
 * A speech engine on Android does not play anything. It hands the samples
 * to the framework, and the framework plays them on behalf of the app that
 * asked, on whatever audio channel that app chose: a screen reader on the
 * accessibility channel, a book reader on the media channel, a navigation
 * app on its own. That is [AUTOMATIC], the default, and it is what every
 * engine does and what this one does unless told otherwise.
 *
 * Every other choice has this app play the speech itself, on an audio
 * channel of the listener's choosing, because there are phones and setups on
 * which the app's choice is the wrong one: a car stereo that only carries
 * media, a hearing aid paired to the accessibility stream, a reader whose
 * own channel is ducked or muted by something else. The routes are the
 * Android audio usages, one per kind of sound the platform knows:
 *
 * - [MEDIA] -- music and audiobooks: the channel a car head unit plays.
 * - [ACCESSIBILITY] -- the screen-reader channel, ducked by other audio.
 * - [ALARMS] -- the alarm channel: full volume, and it plays through most
 *   silent modes.
 * - [NOTIFICATION] and [NOTIFICATION_EVENT] -- the notification channel.
 * - [RINGTONE] -- the incoming-call channel.
 * - [NAVIGATION] -- turn-by-turn guidance.
 * - [SONIFICATION] -- system sounds: the short-effect channel.
 * - [VOICE_COMMUNICATION] and [GAME] -- their own channels.
 * - [ASSISTANT] -- the assistant channel on Android 8 and later.
 * - [ALL] -- this app plays on the accessibility channel and the asking
 *   app is handed the samples as well, which is for the setup where one
 *   channel reaches one ear and the other the other.
 *
 * The one thing the routes cannot do is tell a request to play from a
 * request to write a file: both arrive here the same way. An app that asks
 * this engine to synthesise to a file while a route is chosen gets a file of
 * the right length with nothing in it, which is why the setting says so and
 * why [AUTOMATIC] is the default.
 */
object SynthesisRouting {

	/** The framework plays, on the channel the asking app chose. */
	const val AUTOMATIC = 0

	/** This app plays, on the accessibility channel. */
	const val ACCESSIBILITY = 1

	/** This app plays, on the media channel. */
	const val MEDIA = 2

	/** This app plays on the accessibility channel, and the asking app is
	 *  handed the samples as well. */
	const val ALL = 3

	const val ALARMS = 4
	const val NOTIFICATION = 5
	const val RINGTONE = 6
	const val NAVIGATION = 7
	const val NOTIFICATION_EVENT = 8
	const val SONIFICATION = 9
	const val VOICE_COMMUNICATION = 10
	const val GAME = 11
	const val ASSISTANT = 12

	/** Every route, in the order the screen offers them: the default first,
	 *  then the two a screen reader is most often asked to move to, then the
	 *  rest of the platform's channels. */
	val CHOICES = listOf(
		AUTOMATIC,
		ACCESSIBILITY,
		MEDIA,
		ALARMS,
		NOTIFICATION,
		NOTIFICATION_EVENT,
		RINGTONE,
		NAVIGATION,
		SONIFICATION,
		VOICE_COMMUNICATION,
		GAME,
		ASSISTANT,
		ALL
	)

	fun clamp(route: Int): Int = if (route in CHOICES) route else AUTOMATIC

	/** Whether this app plays the speech itself under [route]. */
	fun playsItself(route: Int): Boolean = clamp(route) != AUTOMATIC

	/** Whether the asking app is handed the samples under [route]. */
	fun handsToFramework(route: Int): Boolean = clamp(route) == AUTOMATIC || clamp(route) == ALL

	/** The usage the app's own player carries under [route]: what the
	 *  framework calls the channel. [ALL] plays on accessibility, which is
	 *  the half of it the listener chose; the other half is the asking app's
	 *  own route and this app does not have a say in it. */
	fun usageOf(route: Int): Int = when (clamp(route)) {
		MEDIA -> AudioAttributes.USAGE_MEDIA
		ALARMS -> AudioAttributes.USAGE_ALARM
		NOTIFICATION -> AudioAttributes.USAGE_NOTIFICATION
		NOTIFICATION_EVENT -> AudioAttributes.USAGE_NOTIFICATION_EVENT
		RINGTONE -> AudioAttributes.USAGE_NOTIFICATION_RINGTONE
		NAVIGATION -> AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE
		SONIFICATION -> AudioAttributes.USAGE_ASSISTANCE_SONIFICATION
		VOICE_COMMUNICATION -> AudioAttributes.USAGE_VOICE_COMMUNICATION
		GAME -> AudioAttributes.USAGE_GAME
		ASSISTANT -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			AudioAttributes.USAGE_ASSISTANT
		} else {
			AudioAttributes.USAGE_MEDIA
		}
		ALL -> AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY
		else -> AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY
	}

	/** The name of the Android stream a route lands on, for a log line and
	 *  for the screen's own explanation. Not used to open anything: the
	 *  attributes above are what chooses the channel. */
	fun describe(route: Int): String = when (clamp(route)) {
		MEDIA -> "media"
		ALARMS -> "alarms"
		NOTIFICATION -> "notification"
		NOTIFICATION_EVENT -> "notification event"
		RINGTONE -> "ringtone"
		NAVIGATION -> "navigation"
		SONIFICATION -> "system sound"
		VOICE_COMMUNICATION -> "voice communication"
		GAME -> "game"
		ASSISTANT -> "assistant"
		ALL -> "accessibility, and the asking app as well"
		ACCESSIBILITY -> "accessibility"
		else -> "the asking app's own channel"
	}

	/**
	 * The app's own player: one streaming [AudioTrack] on one channel, fed
	 * from the synthesis thread as the samples come off the engine.
	 *
	 * One track for the life of the service rather than one per utterance,
	 * because opening one costs tens of milliseconds and a screen reader
	 * says a hundred things a minute. It is remade only when the rate or
	 * the channel moves. Writes block when the track is full, which is the
	 * pacing: the engine makes speech much faster than it is heard and the
	 * track holds a fifth of a second of it, so an utterance runs no further
	 * ahead of the ear than that and a stop lands within a fifth of a second
	 * at the worst.
	 *
	 * Everything about the track is chosen for latency: the smallest buffer
	 * the device will take, the low-latency performance mode where the
	 * platform has one, and a read head that is compared with wrapping
	 * arithmetic so a long utterance is not mistaken for a finished one when
	 * the platform's 32-bit frame counter rolls over.
	 */
	class Player {
		@Volatile
		private var track: AudioTrack? = null
		private var rateHz = 0
		private var usage = -1
		private var framesWritten = 0L
		private var headAtBegin = 0L

		/** The utterance feeding the track, or null when nobody is. Two
		 *  callers can be mid-utterance at once -- a screen reader and an
		 *  app's own speech are two threads -- and one track cannot carry
		 *  two of them without mixing the sentences together. The second
		 *  falls back to the framework, which is a voice routed the way it
		 *  asked for rather than two voices over one another. */
		private var owner: Thread? = null

		@Volatile
		private var cut = false

		/** Makes the track ready for an utterance at [rateHz] on [usage],
		 *  and answers whether there is one. */
		@Synchronized
		fun begin(rateHz: Int, usage: Int, volume: Float): Boolean {
			val mine = owner
			if (mine != null && mine !== Thread.currentThread()) return false
			cut = false
			var ready = track
			if (ready == null || this.rateHz != rateHz || this.usage != usage ||
				ready.state != AudioTrack.STATE_INITIALIZED
			) {
				release()
				ready = null
			}
			val held = ready ?: make(rateHz, usage) ?: return false
			track = held
			this.rateHz = rateHz
			this.usage = usage
			framesWritten = 0
			// The head is counted from where it stands, because a pause and
			// a flush may or may not have put it back to nought.
			headAtBegin = try {
				held.playbackHeadPosition.toLong() and 0xFFFFFFFFL
			} catch (_: Throwable) {
				0L
			}
			try {
				held.setVolume(volume.coerceIn(0f, 1f))
			} catch (_: Throwable) {
			}
			try {
				if (held.playState != AudioTrack.PLAYSTATE_PLAYING) held.play()
			} catch (broken: Throwable) {
				Log.e(TAG, "the player would not start", broken)
				release()
				return false
			}
			// Ownership is taken only once the track is actually playing: a
			// caller that falls back to the framework must not leave the
			// player claimed by an utterance that never had it.
			owner = Thread.currentThread()
			return true
		}

		/** Hands [n] bytes of the buffer to the track, blocking while it is
		 *  full. Answers false when the utterance was cut or the track is
		 *  gone, which is when the caller should stop feeding it. */
		fun write(buffer: ByteArray, n: Int): Boolean {
			val held = track ?: return false
			var at = 0
			while (at < n && !cut) {
				val wrote = try {
					held.write(buffer, at, n - at)
				} catch (_: Throwable) {
					return false
				}
				if (wrote < 0) return false
				if (wrote == 0) {
					// A full track that will not block -- it has been paused
					// by a cut on another thread -- is given a moment rather
					// than spun on.
					try { Thread.sleep(2) } catch (_: InterruptedException) { return false }
					continue
				}
				at += wrote
				synchronized(this) { framesWritten += (wrote / BYTES_PER_FRAME).toLong() }
			}
			return !cut
		}

		/**
		 * Waits for what has been written to be heard, so the utterance's
		 * finish is reported when it is finished rather than when it was
		 * made. A cut returns at once; so does a track that stops moving
		 * for long enough that waiting is clearly wrong.
		 *
		 * The comparison is modular: the platform's playback head is a
		 * 32-bit count of frames and rolls over after about twenty-seven
		 * hours at 44.1 kHz, but also resets to nought when the track is
		 * flushed. What has been heard since this utterance began is the
		 * difference, wrapped, and it is that difference that is compared
		 * with what was written.
		 */
		fun drain() {
			val held = track ?: return
			val written = synchronized(this) { framesWritten }
			if (written <= 0) return
			var last = -1L
			var still = 0
			val deadline = System.nanoTime() + ((written * 1000L / maxOf(rateHz, 1)) + 2000L) * 1_000_000L
			while (!cut && System.nanoTime() < deadline) {
				val head = try {
					held.playbackHeadPosition.toLong() and 0xFFFFFFFFL
				} catch (_: Throwable) {
					return
				}
				val heard = (head - headAtBegin) and 0xFFFFFFFFL
				if (heard >= written) return
				if (head == last) {
					if (++still >= STALL_TICKS) return
				} else {
					still = 0
					last = head
				}
				try { Thread.sleep(TICK_MS) } catch (_: InterruptedException) { return }
			}
		}

		/** Lets the next utterance have the track, without closing it: the
		 *  player is kept open across utterances because opening one costs
		 *  tens of milliseconds and a screen reader says a hundred things a
		 *  minute. Called when the utterance that had it has finished. */
		@Synchronized
		fun end() {
			owner = null
			cut = false
		}

		/** Stops the sound at once and forgets what has not been heard:
		 *  the half of a stop that [AudioTrack] can be told about. Called
		 *  from whichever thread the stop came on. */
		fun cut() {
			cut = true
			// The utterance that owned the track is over, whether or not it
			// has noticed: the next one may have it.
			synchronized(this) { owner = null }
			val held = track ?: return
			try {
				held.pause()
				held.flush()
			} catch (_: Throwable) {
			}
			synchronized(this) { framesWritten = 0 }
		}

		@Synchronized
		fun release() {
			val held = track ?: return
			track = null
			try { held.pause() } catch (_: Throwable) {}
			try { held.flush() } catch (_: Throwable) {}
			try { held.release() } catch (_: Throwable) {}
		}

		private fun make(rateHz: Int, usage: Int): AudioTrack? {
			val minimum = try {
				AudioTrack.getMinBufferSize(rateHz, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
			} catch (_: Throwable) {
				-1
			}
			if (minimum <= 0) {
				Log.e(TAG, "no player at $rateHz Hz")
				return null
			}
			// A fifth of a second, never less than the system's floor and
			// never more than twice it: a small buffer is what a stop has
			// to drain, and a buffer at the floor is the lowest latency the
			// device offers.
			val fifth = (rateHz * BYTES_PER_FRAME) / 5
			val bytes = maxOf(minimum, minOf(fifth, minimum * 2)).let { it + (it and 1) }
			return try {
				val built = AudioTrack.Builder()
					.setAudioAttributes(
						AudioAttributes.Builder()
							.setUsage(usage)
							.setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
							.build()
					)
					.setAudioFormat(
						AudioFormat.Builder()
							.setEncoding(AudioFormat.ENCODING_PCM_16BIT)
							.setSampleRate(rateHz)
							.setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
							.build()
					)
					.setBufferSizeInBytes(bytes)
					.setTransferMode(AudioTrack.MODE_STREAM)
					.apply {
						// Fast mixer path where the device has one. A refusal
						// is the device saying it would rather use the deep
						// buffer, which is a slower answer and a correct one;
						// it is not a reason to have no player at all.
						if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
							try {
								setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
							} catch (_: Throwable) {
							}
						}
					}
					.build()
				if (built.state != AudioTrack.STATE_INITIALIZED) {
					Log.e(TAG, "the player would not initialise at $rateHz Hz")
					try { built.release() } catch (_: Throwable) {}
					null
				} else {
					built
				}
			} catch (broken: Throwable) {
				Log.e(TAG, "the player would not build", broken)
				null
			}
		}

		companion object {
			private const val TAG = "freeeloquencetts"
			private const val BYTES_PER_FRAME = 2
			private const val TICK_MS = 5L
			private const val STALL_TICKS = 200
		}
	}
}
