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
 * app on its own. That is [AUTOMATIC], and it is what every engine does and
 * what this one does unless told otherwise.
 *
 * That is also the choice called [AUTOMATIC] in the code and "Default" on the
 * screen, and the name is the point: it is the only setting that covers every
 * route there is, because every asking app gets the route it asked for. A
 * media player's book, a clock's alarm, a screen reader's label and a
 * navigation prompt all arrive on the channel their app chose, which is why it
 * is the default and why it is at the top of the list.
 *
 * The others have this app play the speech itself, on one channel of the
 * listener's choosing, because there are phones and setups on which the app's
 * choice is the wrong one -- a car stereo that only carries media, a hearing
 * aid paired to the accessibility stream, an alarm that must be heard at
 * alarm volume even with the media stream turned down, a reader whose own
 * channel is ducked or muted by something else. [MEDIA] plays every utterance
 * on the media channel, [ALARMS] on the alarm channel, [ACCESSIBILITY] on the
 * accessibility channel; in each the asking app is handed no samples, only the
 * start and the finish, so it still knows when each utterance began and ended.
 * [ALL] plays every utterance on the accessibility channel and hands the
 * samples to the asking app as well, which is for the setup where one channel
 * reaches one ear and the other the other.
 *
 * The one thing the three cannot do is tell a request to play from a
 * request to write a file: both arrive here the same way. An app that asks
 * this engine to synthesise to a file while one of the three is chosen gets
 * a file of the right length with nothing in it, which is why the setting
 * says so and why [AUTOMATIC] is the default.
 */
object SynthesisRouting {

	const val AUTOMATIC = 0
	const val ACCESSIBILITY = 1
	const val MEDIA = 2
	const val ALL = 3
	const val ALARMS = 4
	private const val BYTES_PER_FRAME = 2

	/**
	 * The list as it is shown, and the order is deliberate: Default first,
	 * because it is the one that covers every route and the one to leave
	 * alone, then the one channel at a time, then the doubling.
	 *
	 * The numbers are not the order and must not be renumbered: they are
	 * what earlier versions wrote into the settings, and a listener who chose
	 * Accessibility once must still have Accessibility after an update.
	 */
	val CHOICES = listOf(AUTOMATIC, MEDIA, ALARMS, ACCESSIBILITY, ALL)

	fun clamp(route: Int): Int = if (CHOICES.contains(route)) route else AUTOMATIC

	/** Whether this app plays the speech itself under [route]. */
	fun playsItself(route: Int): Boolean = clamp(route) != AUTOMATIC

	/** Whether the asking app is handed the samples under [route]. */
	fun handsToFramework(route: Int): Boolean = clamp(route) == AUTOMATIC || clamp(route) == ALL

	/** The usage the app's own player carries under [route]: what the
	 *  framework calls the channel. */
	fun usageOf(route: Int): Int = when (clamp(route)) {
		MEDIA -> AudioAttributes.USAGE_MEDIA
		// The alarm channel is the one a phone keeps audible when everything
		// else is turned down, and the one a clock's own alarm comes out of:
		// speech sent there follows the alarm volume and is not silenced by
		// "mute media".
		ALARMS -> AudioAttributes.USAGE_ALARM
		else -> AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY
	}

	/** The direct-routing queue: fifty milliseconds or the device's required
	 * floor, rounded to a complete 16-bit sample. Kept pure for measurement. */
	fun playerBufferBytes(rateHz: Int, minimum: Int): Int {
		val lowLatency = (rateHz.coerceAtLeast(1) * BYTES_PER_FRAME) / 20
		return maxOf(minimum.coerceAtLeast(2), lowLatency).let { it + (it and 1) }
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
	 * track uses the smallest practical low-latency buffer, so an utterance
	 * stays close to the ear and a stop lands promptly.
	 */
	class Player {
		@Volatile
		private var track: AudioTrack? = null
		private var rateHz = 0
		private var usage = -1
		private var framesWritten = 0L
		private var headAtBegin = 0L

		@Volatile
		private var cut = false

		/** Makes the track ready for an utterance at [rateHz] on [usage],
		 *  and answers whether there is one. */
		@Synchronized
		fun begin(rateHz: Int, usage: Int, volume: Float): Boolean {
			cut = false
			var held = track
			if (held == null || this.rateHz != rateHz || this.usage != usage || held.state != AudioTrack.STATE_INITIALIZED) {
				release()
				held = make(rateHz, usage) ?: return false
				track = held
				this.rateHz = rateHz
				this.usage = usage
			}
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
					if (at > 0) synchronized(this) { if (!cut) framesWritten += (at / BYTES_PER_FRAME).toLong() }
					return false
				}
				if (wrote < 0) {
					if (at > 0) synchronized(this) { if (!cut) framesWritten += (at / BYTES_PER_FRAME).toLong() }
					return false
				}
				if (wrote == 0) {
					// A full track that will not block -- it has been paused
					// by a cut on another thread -- is given a moment rather
					// than spun on.
					try { Thread.sleep(5) } catch (_: InterruptedException) {
						if (at > 0) synchronized(this) { if (!cut) framesWritten += (at / BYTES_PER_FRAME).toLong() }
						return false
					}
				}
				at += wrote
			}
			if (at > 0) {
				synchronized(this) {
					if (!cut) framesWritten += (at / BYTES_PER_FRAME).toLong()
				}
			}
			return !cut
		}

		/**
		 * Waits for what has been written to be heard, so the utterance's
		 * finish is reported when it is finished rather than when it was
		 * made. A cut returns at once; so does a track that stops moving
		 * for long enough that waiting is clearly wrong.
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
				val played = (head - headAtBegin) and 0xFFFFFFFFL
				if (played >= written) return
				if (head == last) {
					if (++still >= STALL_TICKS) return
				} else {
					still = 0
					last = head
				}
				try { Thread.sleep(TICK_MS) } catch (_: InterruptedException) { return }
			}
		}

		/** Stops the sound at once and forgets what has not been heard:
		 *  the half of a stop that [AudioTrack] can be told about. Called
		 *  from whichever thread the stop came on. */
		fun cut() {
			synchronized(this) {
				cut = true
				framesWritten = 0
			}
			val held = track ?: return
			try {
				held.pause()
				held.flush()
			} catch (_: Throwable) {
			}
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
			// Use the system floor instead of the old third-of-a-second queue.
			// A large queue made Media routing wait to collect audio before the
			// first syllable and made every interruption feel heavy. Fifty
			// milliseconds is enough for smooth streaming; devices that require
			// more already report that requirement in [minimum].
			val bytes = playerBufferBytes(rateHz, minimum)
			return try {
				val builder = AudioTrack.Builder()
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
				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
					builder.setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
				}
				val built = builder.build()
				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
					// Streaming tracks normally wait for a substantial part of
					// their buffer before starting. Speech should begin with the
					// first frame instead.
					runCatching { built.setStartThresholdInFrames(1) }
				}
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
			private const val TICK_MS = 10L
			private const val STALL_TICKS = 100
		}
	}
}
