package com.blindtechnexus.freeeloquencetts

/**
 * Text the engine reads wrong, put right before it sees it.
 * Fast single-pass, no regex, handles billion chars in realtime.
 *
 * Fixes:
 * - letter+digit: "teamtalk5" -> "teamtalk 5"
 * - letter+opener: "abc~" -> "abc ~" etc
 * - "books (s)" -> "books(s)"
 * - space before punctuation: "hello ." -> "hello."
 *
 * Commas inside numbers are deliberately left alone: "10,000" stays "10,000",
 * because a comma between digits says the number is read whole (see
 * [Numbers], whose first rule is exactly that). Stripping them here would
 * turn a formatted number into a long plain run and have it read digit by
 * digit, which is the opposite of what the comma means.
 */
object TextFixes {

	private const val OPENERS = "~#\$%^*({|[<\"•"
	private val OPENERS_SET = OPENERS.toSet()
	private val MARKS = setOf(':', '.', '!', ';', ',', '?')

	fun apply(text: String): String {
		if (text.isEmpty()) return text

		// Quick scan if any fix needed
		var needLetterDigit = false
		var needOpener = false
		var needBracket = false
		var needMark = false
		for (c in text) {
			if (!needLetterDigit && c.isLetter()) needLetterDigit = true
			if (!needOpener && c in OPENERS_SET) needOpener = true
			if (!needBracket && c == '(') needBracket = true
			if (!needMark && c in MARKS) needMark = true
			if (needLetterDigit && needOpener && needBracket && needMark) break
		}
		if (!needLetterDigit && !needOpener && !needBracket && !needMark) return text

		val n = text.length
		val out = StringBuilder(n + 32)
		var i = 0
		while (i < n) {
			val c = text[i]

			// Handle spaces before marks and "(s)" - look ahead when we see whitespace
			if (c.isWhitespace()) {
				var j = i
				while (j < n && text[j].isWhitespace()) j++
				if (j < n) {
					val next = text[j]
					// Check "(s)" case: spaces + "(s)" after a letter
					if (needBracket && next == '(' && j + 2 < n && text[j + 1] == 's' && text[j + 2] == ')') {
						// If previous output ends with letter, skip spaces
						if (out.isNotEmpty() && out.last().isLetter()) {
							// Skip spaces, will handle '(' in next iteration
							i = j
							continue
						}
					}
					// Space before punctuation mark: if next is mark and not followed by letter/digit
					if (needMark && next in MARKS) {
						val afterNext = if (j + 1 < n) text[j + 1] else '\u0000'
						if (afterNext == '\u0000' || (!afterNext.isLetter() && !afterNext.isDigit())) {
							// Skip spaces
							i = j
							continue
						}
					}
				}
				// Otherwise keep single space
				out.append(' ')
				i = j
				continue
			}

			// Letter + digit: insert space
			if (c.isDigit() && out.isNotEmpty()) {
				val prev = out.last()
				if (prev.isLetter()) {
					out.append(' ')
				}
			}

			// Letter + opener: insert space
			if (c in OPENERS_SET && out.isNotEmpty()) {
				val prev = out.last()
				if (prev.isLetter()) {
					// Check if previous char in original was not already space
					if (prev != ' ') out.append(' ')
				}
			}

			out.append(c)
			i++
		}
		return out.toString()
	}

}
