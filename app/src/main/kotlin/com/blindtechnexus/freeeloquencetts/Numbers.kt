package com.blindtechnexus.freeeloquencetts

/**
 * How a run of digits is read, before the engine sees it.
 *
 * Modes:
 * - AS_IS: leave numbers to engine, which says "1 trillion 5 billion..." for large numbers
 * - DIGITS: one digit at a time, e.g. + 9 7 7 8 5 3 6 2 8 2 7
 * - PAIRS: two at a time, e.g. + 97 79 82 38 04 (zero kept as "0 4")
 * - TRIPLETS: three at a time
 *
 * Groups are taken from the left (phone-number style) so odd length keeps short group at end:
 * "12345" in pairs is "12 34 5", not "1 23 45". Leading zero groups are expanded to keep zero:
 * "04" -> "0 4" so zero is spoken.
 *
 * Only ASCII digits 0-9 are touched. Punctuation splits runs, so "3.14" becomes "3 . 1 4" in digits.
 * This is a single fast pass, no regex, to handle huge text in realtime.
 */
object Numbers {

	const val AS_IS = 0
	const val DIGITS = 1
	const val PAIRS = 2
	const val TRIPLETS = 3

	val MODES = listOf(AS_IS, DIGITS, PAIRS, TRIPLETS)

	fun apply(text: String, mode: Int): String {
		if (mode == AS_IS) return text
		if (text.isEmpty()) return text

		// Quick check: does text contain any digit?
		var hasDigit = false
		for (c in text) {
			if (c in '0'..'9') { hasDigit = true; break }
		}
		if (!hasDigit) return text

		// Estimate capacity: digits mode expands roughly 2x
		val sb = StringBuilder(text.length + (text.length / 2) + 16)
		var i = 0
		val n = text.length
		while (i < n) {
			val c = text[i]
			// Handle plus sign followed by digit: "+977" -> "+ 97..."
			if (c == '+' && i + 1 < n && text[i + 1] in '0'..'9') {
				sb.append('+')
				sb.append(' ')
				i++
				continue
			}
			if (c in '0'..'9') {
				var j = i
				while (j < n && text[j] in '0'..'9') j++
				// run is text[i..j)
				when (mode) {
					DIGITS -> {
						for (k in i until j) {
							if (k > i) sb.append(' ')
							sb.append(text[k])
						}
					}
					PAIRS -> {
						appendGrouped(text, i, j, 2, sb)
					}
					TRIPLETS -> {
						appendGrouped(text, i, j, 3, sb)
					}
				}
				i = j
			} else {
				sb.append(c)
				i++
			}
		}
		return sb.toString()
	}

	private fun appendGrouped(text: String, start: Int, end: Int, groupSize: Int, out: StringBuilder) {
		var pos = start
		var firstGroup = true
		while (pos < end) {
			val remaining = end - pos
			val curSize = if (remaining >= groupSize) groupSize else remaining
			// Check leading zero: if group starts with '0' and size>1, expand to single digits to preserve zero
			if (curSize > 1 && text[pos] == '0') {
				// Expand this group into spaced single digits
				for (k in 0 until curSize) {
					if (!firstGroup || k > 0) out.append(' ')
					out.append(text[pos + k])
					firstGroup = false
				}
			} else {
				if (!firstGroup) out.append(' ')
				for (k in 0 until curSize) {
					out.append(text[pos + k])
				}
				firstGroup = false
			}
			pos += curSize
		}
	}
}
