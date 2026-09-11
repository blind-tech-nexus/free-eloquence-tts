package com.blindtechnexus.alteloquencetts

/**
 * How far the engine is allowed to get ahead of the speech it has made.
 *
 * This engine is a hundred times faster than the voice it produces: a second
 * and a third of audio comes out in twelve milliseconds. Android takes every
 * byte it is offered and queues it, so with nothing to hold it back a reader
 * swiping through a list has five or six items synthesised and waiting while
 * the first is still being spoken, and every one of them is lost when it asks
 * for silence -- items that were never heard at all, which is what this file
 * exists to prevent. [EvvTtsService] therefore stops feeding the framework
 * whenever the samples it has handed over are more than a lead's worth of
 * audio ahead of the wall clock.
 *
 * The size of that lead is a trade of one thing against the other, and it is
 * the only one: a short lead means less to lose when speech is cut off, and a
 * long one means the engine is left alone to get on with its work. The trade
 * is not the same at both ends of the range of things a caller asks to be
 * read, though. A line of a list is spoken once and interrupted a moment
 * later, which is the case a short lead is for. A paragraph is one message,
 * takes as long to hear as it takes to say whoever is listening, and gains
 * nothing from being fed at the pace it is heard at -- only the framework's
 * synthesis thread is held, and every later request for speech is behind that
 * thread. So a message long enough to be a paragraph is given [LONG_LEAD_MS]
 * instead, which is far more than any caller's buffer wants but is a rounding
 * error next to the minutes a long one runs to.
 *
 * Both numbers were once the user's to move, on the battery and stability
 * screen. They are not any more: the screen keeps no row a person cannot
 * answer a question about, and the app answers them itself, at the values the
 * measurements say a screen reader wants. The range stays here, and so does
 * [Settings]' pin, because the settings still travel through the same doors
 * and a number an older build left behind is met by the same bounds.
 */
object Pacing {

	/** The shortest lead worth offering. Below this a reader's queue is empty
	 *  more often than it is full, and the framework is left asking for
	 *  samples that the engine has not been allowed to make yet. */
	const val MIN_MS = 150

	/** The longest. Above it the framework's own buffer is what decides how
	 *  far ahead the engine gets, so the number stops meaning anything. */
	const val MAX_MS = 4000

	/** What it has always been, what a screen reader wants, and what the app
	 *  now holds it at: [Settings.PIN_SPEECH_LEAD_MS] is this number. */
	const val DEFAULT_MS = 300

	/** The lead a message long enough to be a paragraph is given. */
	const val LONG_LEAD_MS = 4000L

	/** How much text makes a message a paragraph rather than a line: this many
	 *  characters, which is about [TextPieces.PIECE_CAP] cut into more than
	 *  one piece and so more than one pause the framework would otherwise sit
	 *  through with the engine idle. */
	const val LONG_UTTERANCE_CHARS = 600

	/** The leads the screen offers, smallest first. */
	val LEADS = listOf(MIN_MS, DEFAULT_MS, 700, 2000, MAX_MS)

	/** Whether [chars] of text in [pieces] pieces is long enough to be worth
	 *  running ahead on. Two pieces is the least that can pay: with one there
	 *  is nothing after it to get ahead of. */
	fun isLong(chars: Int, pieces: Int): Boolean =
		pieces > 1 && chars >= LONG_UTTERANCE_CHARS

	/** How far ahead of the speech the samples may run, for one message. */
	fun leadFor(leadMs: Int, runAhead: Boolean, chars: Int, pieces: Int): Long {
		val set = leadMs.coerceIn(MIN_MS, MAX_MS).toLong()
		return if (runAhead && isLong(chars, pieces)) maxOf(set, LONG_LEAD_MS) else set
	}
}

/**
 * The numbers that keep the engine alive, and the shape the screen used to
 * offer them in, so that the settings and the service all read the same list
 * rather than three copies of it.
 *
 * The rows have come off the screen and [Settings] pins each number to the
 * best of these; the lists are what "best" is measured against, what an old
 * stored value is snapped back to, and what the bounds in [Pacing] and
 * [Settings] mean.
 */
object Stability {

	/** How long the CPU is held after the last thing said, in milliseconds.
	 *  Nought is the switch above it on its own. */
	val WAKE_LOCK_TAILS = listOf(0, 1000, 5000, 15000, 60000)

	/** How long an engine instance is kept after the last thing it said, in
	 *  seconds. Nought is for ever, which is what it has always been. */
	val ENGINE_RELEASES = listOf(0, 60, 300, 900, 3600)

	/** How long the engine may go silent before an utterance is given up on, in
	 *  milliseconds, because that is the unit the setting is kept in and the two
	 *  must be the same number rather than the same number times a thousand. The
	 *  last is a ceiling nobody is meant to reach, for a device where waiting for
	 *  the speech is the lesser of two troubles. */
	val SILENCES = listOf(5000, 15000, 60000, 300000)

	/** How often the notification is looked for and put back, in
	 *  milliseconds. Two seconds is the short end for a phone that clears
	 *  notifications aggressively, ten seconds the long end for one that
	 *  leaves them alone, and three is what the screen has always promised. */
	val NOTIFICATION_CHECKS = listOf(2000, 3000, 5000, 10000)
}
