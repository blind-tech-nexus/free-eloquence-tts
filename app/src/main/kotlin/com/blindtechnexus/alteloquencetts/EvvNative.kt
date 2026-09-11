package com.blindtechnexus.alteloquencetts

/** The JNI surface. Every call here is thread safe except [speak] and [read],
 *  which belong to whichever thread is driving one utterance. */
internal object EvvNative {

	@Volatile
	var loadError: Throwable? = null
		private set

	val loaded: Boolean

	init {
		var ok = false
		try {
			System.loadLibrary("evvjni")
			ok = true
		} catch (e: UnsatisfiedLinkError) {
			loadError = e
		}
		loaded = ok
	}

	external fun create(language: Int): Long

	external fun destroy(handle: Long)

	external fun speak(handle: Long, text: ByteArray): Boolean

	/** Fills [dst] with PCM bytes. Answers 0 at the end of the utterance, -1
	 *  when it was stopped or the instance is closed, and -2 when [timeoutMs]
	 *  ran out -- see [EvvTtsService.TIMED_OUT], which is what the service
	 *  tells the two answers apart with.
	 *
	 *  [timeoutMs] is how long to wait for samples that are not coming. The
	 *  engine is asked for silence by a flag rather than by a call, and that
	 *  flag wakes a wait like this one the moment it goes up; the timeout is
	 *  what brings back an engine that is not even going to look at the
	 *  flag. Without it the framework's synthesis thread sits in here for
	 *  the rest of the day, and every later request for speech is behind it. */
	external fun read(handle: Long, dst: ByteArray, timeoutMs: Int): Int

	external fun stop(handle: Long)

	external fun setParam(handle: Long, param: Int, value: Int): Int

	external fun getParam(handle: Long, param: Int): Int

	external fun setVoiceParam(handle: Long, voice: Int, param: Int, value: Int): Int

	external fun getVoiceParam(handle: Long, voice: Int, param: Int): Int

	external fun copyVoice(handle: Long, from: Int, to: Int): Int

	/** Answers one of the eciDict codes, so nought is success. */
	external fun loadDictionary(handle: Long, volume: Int, path: String): Int

	external fun teachWord(handle: Long, volume: Int, key: ByteArray, say: ByteArray): Int

	external fun lookUpWord(handle: Long, volume: Int, key: ByteArray): String?

	external fun forgetDictionaries(handle: Long)

	external fun languages(): IntArray

	external fun version(): String
}
