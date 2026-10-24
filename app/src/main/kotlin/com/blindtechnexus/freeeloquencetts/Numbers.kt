package com.blindtechnexus.freeeloquencetts

/**
 * How a run of digits is read, before the engine sees it.
 *
 * Modes:
 * - AS_IS: leave every number to the engine, which says "nine hundred
 *   eighty-five thousand two hundred fifty-six" for 985256.
 * - DIGITS: a long run one digit at a time -- 985256 is "9 8 5 2 5 6".
 * - PAIRS: a long run two at a time, aligned from the right -- 985256 is "98 52 56".
 * - TRIPLETS: a long run three at a time, aligned from the right -- 985256 is "985 256".
 *
 * Every engine that can be told how to read a number is told it one of these
 * three ways -- digits, pairs and triplets are the grouping every telephone
 * system, every account number and every read-out-loud code has ever used --
 * and this engine has no setting for it at all: the only lever is the text.
 * So the modes here are made out of spaces, which is how Speech Dispatcher's
 * own module for this engine does it too.
 *
 * The rules, which are the same in every mode but AS_IS and are meant to be
 * strong enough that nothing surprising ever happens to a number:
 *
 * 1. A run of fewer than [LONG_FROM] digits -- five or fewer -- is one
 *    number and is never broken up. Years, times, prices, PINs, page
 *    numbers, percentages and counts are all short, and "2026" read as
 *    "20 26" is a year nobody recognises. Only a run of six or more digits
 *    is ever regrouped, in any mode.
 *
 * 2. A number written with thousands separators is the number, whole.
 *    "1,150" and "10.000.000" (the continental spelling) are copied as
 *    written in every mode, because the separator is what says "a thousand"
 *    and breaking it at the separator would be reading the punctuation and
 *    losing the number. A separator has to be followed by exactly three
 *    digits to count as one; "5, 2026" and "1,0000" are two numbers.
 *
 *    The one exception is a shape this engine misreads, and it is not a
 *    preference: it is a fault. IBM's reader drops the magnitude of a
 *    comma-grouped number whose last group is zeros -- "1,000,000" comes out
 *    as "one million ... hundred", and "205,000,000" worse -- but reads the
 *    same digits without the separators correctly. The fault is measured on
 *    the comma and only the comma is repaired; "10.000.000" is left as it
 *    was written. The fault is measured on
 *    the comma and only the comma is repaired; "10.000.000" is left as it
 *    was written. So that shape alone has
 *    its separators taken out, in every mode, and the digits go on to be
 *    read as the mode says. Everything that already reads correctly is
 *    copied byte for byte: "1,150" and "205,558,107" are untouched, which is
 *    the whole of why this is a repair rather than a rule.
 *
 * 3. The digits after a decimal point are a fraction and are left alone.
 *    "3.1415926" is three point one four one five nine two six to the
 *    engine already, and regrouping the fraction would make it a telephone
 *    number. A point counts as decimal only when there are digits on both
 *    sides of it; "Section 7.123456" is still a fraction, which is the
 *    safer wrong answer.
 *
 * 4. Otherwise a long run is aligned from the right, like place values and
 *    standard spoken-number grouping. An odd length keeps its short group at
 *    the front: "1234567" in pairs is "1 23 45 67" and in triplets
 *    "1 234 567". A group that opens with a
 *    zero is said a digit at a time, so the zero is heard: "0412" in pairs
 *    is "0 4 12" rather than "four twelve".
 *
 * 5. A plus before digits is the dialling kind and is kept with a space
 *    after it ("+911" stays callable), punctuation of every other kind
 *    splits runs ("12:34" is two runs), and only the ten ASCII digits are
 *    touched. Everything that is not a run of digits comes through exactly
 *    as it was written.
 *
 * 6. A run that is part of a longer word or address is left alone. "v2.10"
 *    is a version, "192.168.0.1" is an address and "A1B2C3" is a code, and
 *    regrouping any of them would be making a quantity out of something
 *    that is not one. The test is a letter, a slash, an at-sign or another
 *    dot-group on either side.
 *
 * The whole thing is one pass over the text with no regular expressions, so
 * a page of text costs what copying it costs, and a text with nothing to do
 * -- which is most text -- comes back as the very string it was.
 */
object Numbers {

	const val AS_IS = 0
	const val DIGITS = 1
	const val PAIRS = 2
	const val TRIPLETS = 3

	val MODES = listOf(AS_IS, DIGITS, PAIRS, TRIPLETS)

	/** A run this long or longer is regrouped; anything shorter is one
	 *  number, in every mode but AS_IS. */
	const val LONG_FROM = 6

	/** A mode outside the list is the default, so a preference written by
	 *  another build is a behaviour and not a crash. */
	fun clamp(mode: Int): Int = if (MODES.contains(mode)) mode else AS_IS

	/** How many digits make one group in [mode]: one, two or three. */
	fun groupSize(mode: Int): Int = when (mode) {
		PAIRS -> 2
		TRIPLETS -> 3
		else -> 1
	}

	/** The name of a mode, for a log or a test that has to say which one it
	 *  meant. */
	fun nameOf(mode: Int): String = when (clamp(mode)) {
		DIGITS -> "digits"
		PAIRS -> "pairs"
		TRIPLETS -> "triplets"
		else -> "as-is"
	}

	fun apply(text: String, mode: Int): String {
		val settled = clamp(mode)
		if (text.isEmpty()) return text

		// Nothing to do without a run of LONG_FROM digits, a separated
		// number worth repairing, or the one other thing this pass
		// rewrites. Most text has none of the three.
		if (!hasWork(text)) return text

		var changed = false
		val out = StringBuilder(text.length + text.length / 2 + 16)
		val n = text.length
		var i = 0
		while (i < n) {
			val c = text[i]
			if (c == '`') {
				// An engine annotation -- "`vbst+6", "`p1", "`[kaf1e]" -- is
				// carried through untouched: it is not text and its digits
				// are not a number.
				val k = EvvEngine.annotationEnd(text, i)
				out.append(text, i, k)
				i = k
				continue
			}
			if (c == '+' && i + 1 < n && text[i + 1].isAsciiDigit()) {
				// The dialling plus keeps a space after it, so it is said
				// as "plus" in front of the number rather than swallowed.
				out.append("+ ")
				changed = true
				i++
				continue
			}
			if (!c.isAsciiDigit()) {
				out.append(c)
				i++
				continue
			}
			var j = i
			while (j < n && text[j].isAsciiDigit()) j++
			// Rule 2, the grouping part: separators each followed by
			// exactly three digits, of one kind, make the number whole.
			val groups = scanSeparators(text, j)
			val intEnd = j + groups.length
			// Rule 3: a decimal number is a quantity, whole.
			if (isDecimalPoint(text, intEnd) || isFraction(text, i)) {
				if (groups.length > 0 && groups.corrupts) {
					// A decimal whose integer part is the shape the
					// engine misreads: the separators go, the value
					// stays.
					out.append(text, i, j)
					var k = j
					while (k < intEnd) {
						if (text[k].isAsciiDigit()) out.append(text[k])
						k++
					}
					out.append(text[intEnd])
					k = intEnd + 1
					while (k < n && text[k].isAsciiDigit()) {
						out.append(text[k])
						k++
					}
					i = k
					changed = true
					continue
				}
				out.append(text, i, intEnd)
				i = intEnd
				continue
			}
			if (groups.length > 0 && !groups.corrupts) {
				// Rule 2: a number whose separators the engine reads
				// correctly is the number, copied as written.
				out.append(text, i, intEnd)
				i = intEnd
				continue
			}
			if (groups.length > 0) {
				// Rule 2's exception: the separators are dropped and the
				// digits are grouped as the mode asks. It is the same
				// number said the only way this engine says it right.
				val digits = StringBuilder(j - i + groups.digits)
				digits.append(text, i, j)
				var k = j
				while (k < intEnd) {
					if (text[k].isAsciiDigit()) digits.append(text[k])
					k++
				}
				// Leaving the number to the engine still means leaving it
				// with separators it misreads, so AS_IS gets the digits
				// plain -- the same number, in the one spelling the engine
				// reads right -- and the other modes group them.
				if (settled == AS_IS) out.append(digits)
				else groupDigits(digits, settled, out)
				changed = true
				i = intEnd
				continue
			}
			// AS_IS is the engine's own reading and nothing is regrouped:
			// the pass only ever repairs a separated number there.
			if (settled != AS_IS && !partOfSomethingElse(text, i, j)) {
				changed = groupDigits(text, i, j, settled, out) or changed
			} else {
				out.append(text, i, j)
			}
			i = j
		}
		return if (changed) out.toString() else text
	}

	/** Whether [text] holds anything this pass would change: a run of
	 *  [LONG_FROM] digits, a separated number, or a plus in front of a
	 *  digit. One scan, no allocation. */
	private fun hasWork(text: String): Boolean {
		var run = 0
		val n = text.length
		var i = 0
		while (i < n) {
			val c = text[i]
			if (c.isAsciiDigit()) {
				run++
				if (run >= LONG_FROM) return true
			} else {
				// A separator followed by three digits is a grouped number,
				// which is worth a second look even when each group is
				// short: the engine misreads some of them.
				if (run > 0 && (c == ',' || c == '.') && i + 3 < n &&
					text[i + 1].isAsciiDigit() && text[i + 2].isAsciiDigit() &&
					text[i + 3].isAsciiDigit() && (i + 4 >= n || !text[i + 4].isAsciiDigit())
				) {
					return true
				}
				run = 0
				if (c == '+' && i + 1 < n && text[i + 1].isAsciiDigit()) return true
			}
			i++
		}
		return false
	}

	/** What [scanSeparators] answers: how many characters the grouping adds
	 *  and whether it is the shape the engine misreads. */
	private class Grouping(val length: Int, val digits: Int, val corrupts: Boolean)

	private val NO_GROUPING = Grouping(0, 0, false)

	/**
	 * How many characters past a digit run belong to a thousands grouping:
	 * separators each followed by exactly three digits, with no fourth
	 * digit after the last group and no second kind of separator in the
	 * middle. The separator is a comma, or a full stop where the whole
	 * number is written the continental way ("10.000.000"); a single
	 * point followed by three digits is a decimal and not a grouping, which
	 * is why a point needs a second group before it counts. Anything else
	 * answers nought.
	 *
	 * [Grouping.corrupts] is the shape the engine drops the magnitude of: a
	 * comma-grouped number of two or more groups whose last one is "000".
	 * It is measured rather than guessed -- "1,000,000" is misread and
	 * "1,150" is not -- and it is the one reason a separated number is ever
	 * rewritten.
	 */
	private fun scanSeparators(text: String, from: Int): Grouping {
		val n = text.length
		if (from >= n) return NO_GROUPING
		val separator = text[from]
		if (separator != ',' && separator != '.') return NO_GROUPING
		var j = from
		var groups = 0
		var lastGroupZero = true
		while (j < n && text[j] == separator) {
			if (j + 3 >= n) break
			if (!text[j + 1].isAsciiDigit() || !text[j + 2].isAsciiDigit() || !text[j + 3].isAsciiDigit()) break
			// Exactly three: a fourth digit means the separator was inside
			// a longer run rather than between groups.
			if (j + 4 < n && text[j + 4].isAsciiDigit()) return NO_GROUPING
			if (text[j + 1] != '0' || text[j + 2] != '0' || text[j + 3] != '0') lastGroupZero = false
			groups++
			j += 4
		}
		if (groups == 0) return NO_GROUPING
		// A point needs a second group before it is a grouping rather than a
		// decimal: "1.5" is a half and "1.500" is one and a half, and saying
		// "1,500" as if it were one thousand five hundred is the wrong of
		// the two.
		if (separator == '.' && groups < 2) return NO_GROUPING
		// Two comma groups or more with a zero last group is the shape the
		// engine misreads; one group ("10,000") it reads correctly, and the
		// continental spelling is left alone because the fault is measured
		// on the comma and guessing that the point behaves the same way
		// would rewrite a number nobody complained about.
		val corrupts = separator == ',' && groups >= 2 && lastGroupZero
		return Grouping(j - from, groups * 3, corrupts)
	}

	/** Whether the character at [at] is a decimal point followed by a
	 *  digit: the start of a fraction. */
	private fun isDecimalPoint(text: String, at: Int): Boolean {
		if (at >= text.length) return false
		val point = text[at]
		if (point != '.' && point != ',') return false
		return at + 1 < text.length && text[at + 1].isAsciiDigit()
	}

	/** Whether the run starting at [start] sits directly after a decimal
	 *  point that itself sits directly after a digit: the fraction of a
	 *  decimal number. */
	private fun isFraction(text: String, start: Int): Boolean {
		if (start < 2) return false
		val point = text[start - 1]
		if (point != '.' && point != ',') return false
		return text[start - 2].isAsciiDigit()
	}

	/**
	 * Whether the run [start, end) is part of something that is not a
	 * number: a longer word ("A1B2C3"), a version ("v2.10"), an address
	 * ("192.168.0.1"), a path or an address of a web page. Regrouping any
	 * of those would turn a name into a quantity.
	 *
	 * The test is deliberately narrow. A letter directly against the digits
	 * is a code or a name -- [TextFixes] has already put a space between a
	 * word and the number that follows it, so "teamtalk5" arrives here as
	 * two things -- and a dot, slash or at-sign against them is part of an
	 * address.
	 */
	private fun partOfSomethingElse(text: String, start: Int, end: Int): Boolean {
		val before = if (start > 0) text[start - 1] else ' '
		if (before.isLetter() || before == '/' || before == '\\' || before == '@' ||
			before == '_'
		) return true
		val after = if (end < text.length) text[end] else ' '
		if (after.isLetter() || after == '/' || after == '\\' || after == '@' ||
			after == '_'
		) return true
		// A dot with digits on both sides is a dotted quad, a version or a
		// file name, not a number with a decimal point -- a decimal point
		// is handled before this and never reaches here.
		if (after == '.' && end + 1 < text.length && text[end + 1].isAsciiDigit()) return true
		if (before == '.' && start >= 2 && text[start - 2].isAsciiDigit()) return true
		return false
	}

	private fun Char.isAsciiDigit(): Boolean = this in '0'..'9'

	/**
	 * One digit run grouped for [mode], appended to [out]. Answers whether
	 * anything but the run itself went in, so a text with nothing to do --
	 * which is most text -- comes back as the string it was.
	 */
	private fun groupDigits(text: String, start: Int, end: Int, mode: Int, out: StringBuilder): Boolean {
		val length = end - start
		// Rule 1: a short run is one number.
		if (length < LONG_FROM) {
			out.append(text, start, end)
			return false
		}
		if (mode == DIGITS) {
			var k = start
			while (k < end) {
				if (k > start) out.append(' ')
				out.append(text[k])
				k++
			}
			return true
		}
		// Rule 4: align groups from the right, leaving a short group at
		// the front. This is how pairs and triplets are normally read and
		// keeps the final groups stable as a number grows.
		val group = groupSize(mode)
		val remainder = length % group
		var p = start
		var first = true
		while (p < end) {
			val size = if (first && remainder != 0) remainder else minOf(group, end - p)
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

	/** The same, for digits that had separators between them and are held in
	 *  a builder of their own. */
	private fun groupDigits(digits: CharSequence, mode: Int, out: StringBuilder): Boolean {
		val length = digits.length
		if (length < LONG_FROM) {
			out.append(digits)
			return false
		}
		if (mode == DIGITS) {
			for (k in 0 until length) {
				if (k > 0) out.append(' ')
				out.append(digits[k])
			}
			return true
		}
		val group = groupSize(mode)
		val remainder = length % group
		var p = 0
		var first = true
		while (p < length) {
			val size = if (first && remainder != 0) remainder else minOf(group, length - p)
			if (!first) out.append(' ')
			first = false
			if (size > 1 && digits[p] == '0') {
				out.append(digits[p])
				for (k in p + 1 until p + size) {
					out.append(' ').append(digits[k])
				}
			} else {
				out.append(digits, p, p + size)
			}
			p += size
		}
		return true
	}

	/**
	 * [digits] said one at a time, with a space between each. The shape
	 * the OTP reader and the DIGITS mode both produce, written once so the
	 * two can never disagree about it.
	 */
	fun spaced(digits: CharSequence): String {
		val out = StringBuilder(digits.length * 2)
		for (k in digits.indices) {
			if (k > 0) out.append(' ')
			out.append(digits[k])
		}
		return out.toString()
	}
}
