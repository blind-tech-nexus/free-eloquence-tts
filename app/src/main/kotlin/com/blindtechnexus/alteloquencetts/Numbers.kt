package com.blindtechnexus.alteloquencetts

/**
 * How a run of digits is read, before the engine sees it.
 *
 * The engine has no setting for this, so the choice is made out of text
 * changes: a run of digits is rewritten with spaces in it, and the engine
 * then reads each group as a number of its own. "123456" read as pairs is
 * "12 34 56", which the engine says as "twelve thirty-four fifty-six".
 *
 * The groups are taken from the right, which is how a number is usually
 * broken down: an odd-length run keeps a short group at the front rather
 * than at the end, so "12345" in pairs is "1 23 45" and not "12 34 5".
 *
 * Only runs of the ten ASCII digits are touched. A decimal point, a colon
 * in a time and a thousands separator all split a number into separate
 * runs, and each is then regrouped on its own, so "3.14" in digits is
 * "3 . 1 4" -- still "three point one four" -- and nothing here rewrites
 * the punctuation itself. That is [Symbols]' business.
 */
object Numbers {

	/** The engine reads numbers its own way. */
	const val AS_IS = 0

	/** One digit at a time: "1234" is "one two three four". */
	const val DIGITS = 1

	/** Two digits at a time: "1234" is "twelve thirty-four". */
	const val PAIRS = 2

	/** Three digits at a time: "123456" is read as "123 456". */
	const val TRIPLETS = 3

	/** The levels, in the order the settings screen offers them. */
	val MODES = listOf(AS_IS, DIGITS, PAIRS, TRIPLETS)

	private val DIGIT_RUN = Regex("\\d+")

	/** [text] with every run of digits regrouped the way [mode] asks for. */
	fun apply(text: String, mode: Int): String = when (mode) {
		DIGITS -> DIGIT_RUN.replace(text) { it.value.asIterable().joinToString(" ") }
		PAIRS -> DIGIT_RUN.replace(text) { group(it.value, 2) }
		TRIPLETS -> DIGIT_RUN.replace(text) { group(it.value, 3) }
		else -> text
	}

	/** [digits] cut into groups of [size], from the right, space separated. */
	private fun group(digits: String, size: Int): String {
		val groups = ArrayList<String>(digits.length / size + 1)
		var end = digits.length
		while (end > 0) {
			val start = (end - size).coerceAtLeast(0)
			groups.add(digits.substring(start, end))
			end = start
		}
		return groups.asReversed().joinToString(" ")
	}
}
