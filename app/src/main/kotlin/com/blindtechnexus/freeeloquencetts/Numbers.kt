package com.blindtechnexus.freeeloquencetts

/**
 * How a run of digits is read, before the engine sees it.
 *
 * Modes:
 * - AS_IS: leave numbers to the engine, which says "1 trillion 5 billion..."
 *   for large numbers
 * - DIGITS: a short run as one number, a long run one digit at a time --
 *   "2026" stays "2026" but "12345678" becomes "1 2 3 4 5 6 7 8"
 * - PAIRS: two at a time from the left, e.g. "1500" as "15 0 0"
 * - TRIPLETS: three at a time from the left
 *
 * The rules behind them, which hold in every mode but AS_IS:
 *
 * A short run is one number. In digits that is four or fewer -- years,
 * times, PINs, the "1500" of it -- said as a number and not spelled; only
 * a run of five or more is ever broken up. In pairs and triplets it is a
 * run no longer than the group, so "12" and "123" stand as they are and
 * "2026" in pairs is "20 26".
 *
 * A comma inside a number means the number, whole. "10,000" is ten thousand
 * in digits, pairs and triplets alike; breaking it at the comma would be
 * reading the punctuation and losing the number.
 *
 * Otherwise groups are taken from the left, phone-number style, so an odd
 * length keeps its short group at the end: "12345" in pairs is "12 34 5",
 * not "1 23 45". A group that opens with a zero is said a digit at a time,
 * so the zero is heard: "04" is "0 4".
 *
 * A plus before digits is the dialling kind and is kept with a space after
 * it ("+911" stays callable), punctuation splits runs ("3.14" is two runs),
 * and only the ten ASCII digits are touched. This is a single fast pass, no
 * regex, to handle huge text in realtime.
 */
object Numbers {

	const val AS_IS = 0
	const val DIGITS = 1
	const val PAIRS = 2
	const val TRIPLETS = 3

	val MODES = listOf(AS_IS, DIGITS, PAIRS, TRIPLETS)

	/** A run this long or shorter is one number, in every mode but AS_IS. */
	private const val WHOLE_UP_TO = 4

	fun apply(text: String, mode: Int): String {
		if (mode == AS_IS) return text
		if (text.isEmpty()) return text

		// Quick check: does the text hold any digit?
		var hasDigit = false
		for (c in text) {
			if (c in '0'..'9') { hasDigit = true; break }
		}
		if (!hasDigit) return text

		var changed = false
		val out = StringBuilder(text.length + text.length / 2 + 16)
		var i = 0
		val n = text.length
		while (i < n) {
			val c = text[i]
			// A plus before digits is the dialling kind: "+911" keeps its
			// plus with a space after it, and the run after is grouped like
			// any other.
			if (c == '+' && i + 1 < n && text[i + 1] in '0'..'9') {
				out.append('+').append(' ')
				changed = true
				i++
				continue
			}
			if (c in '0'..'9') {
				var j = i
				while (j < n && text[j] in '0'..'9') j++
				// A comma with groups of three after it is a grouped number,
				// and grouped numbers are copied whole: the comma is what
				// says "ten thousand" rather than "one zero zero zero zero".
				val grouped = scanGrouping(text, j)
				if (grouped > 0) {
					out.append(text, i, j + grouped)
					i = j + grouped
					continue
				}
				changed = changed or groupRun(text, i, j, mode, out)
				i = j
			} else {
				out.append(c)
				i++
			}
		}
		return if (changed) out.toString() else text
	}

	/**
	 * How many characters past a digit run belong to a comma grouping:
	 * commas each followed by exactly three digits, with no fourth digit
	 * after the last group. Anything else -- "5, 2026", "1,0000" -- is not
	 * a grouping and answers nought.
	 */
	private fun scanGrouping(text: String, from: Int): Int {
		val n = text.length
		var j = from
		var consumed = 0
		while (j < n && text[j] == ',') {
			if (j + 3 >= n) return 0
			if (!text[j + 1].isAsciiDigit() || !text[j + 2].isAsciiDigit() || !text[j + 3].isAsciiDigit()) return 0
			// Exactly three: a fourth digit means the comma was inside a
			// longer run, not between groups.
			if (j + 4 < n && text[j + 4].isAsciiDigit()) return 0
			consumed += 4
			j += 4
		}
		return consumed
	}

	private fun Char.isAsciiDigit(): Boolean = this in '0'..'9'

	/**
	 * One digit run grouped for [mode], appended to [out]. Answers whether
	 * anything but the run itself went in, so a text with nothing to do --
	 * which is most text -- comes back as the string it was.
	 */
	private fun groupRun(text: String, start: Int, end: Int, mode: Int, out: StringBuilder): Boolean {
		val length = end - start
		if (mode == DIGITS) {
			if (length <= WHOLE_UP_TO) {
				out.append(text, start, end)
				return false
			}
			for (k in start until end) {
				if (k > start) out.append(' ')
				out.append(text[k])
			}
			return true
		}
		// Pairs and triplets: a short run is one number, and a long one is
		// grouped from the left, phone-number style, so the short group --
		// when the length does not divide -- stands at the end.
		val group = if (mode == PAIRS) 2 else 3
		if (length <= WHOLE_UP_TO && length <= group) {
			out.append(text, start, end)
			return false
		}
		var p = start
		var first = true
		while (p < end) {
			val size = minOf(group, end - p)
			if (!first) out.append(' ')
			first = false
			if (size > 1 && text[p] == '0') {
				// A group that opens with a zero is said a digit at a
				// time, so the zero is heard rather than swallowed.
				out.append(text[p])
				for (k in p + 1 until p + size) {
					out.append(' ').append(text[k])
				}
			} else {
				out.append(text, p, p + size)
			}
			p += size
		}
		return true
	}
}
