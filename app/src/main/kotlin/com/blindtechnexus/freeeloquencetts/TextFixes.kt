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
 * - grouped thousands ",000": "1,000,000" -> "1000000" (removes commas when pattern is thousands)
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
		var needComma = false
		for (c in text) {
			if (!needLetterDigit && c.isLetter()) needLetterDigit = true
			if (!needOpener && c in OPENERS_SET) needOpener = true
			if (!needBracket && c == '(') needBracket = true
			if (!needMark && c in MARKS) needMark = true
			if (!needComma && c == ',') needComma = true
			if (needLetterDigit && needOpener && needBracket && needMark && needComma) break
		}
		if (!needLetterDigit && !needOpener && !needBracket && !needMark) return text

		val n = text.length
		val out = StringBuilder(n + 32)
		var i = 0
		while (i < n) {
			val c = text[i]

			// Handle grouped thousands: look ahead for digit(,ddd)+ pattern containing ,000
			if (needComma && c.isDigit()) {
				val tokenEnd = scanNumberWithCommas(text, i)
				if (tokenEnd > i) {
					val token = text.substring(i, tokenEnd)
					if (isGroupedThousands(token)) {
						// Remove commas
						for (ch in token) if (ch != ',') out.append(ch)
						i = tokenEnd
						continue
					}
				}
			}

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

	private fun scanNumberWithCommas(text: String, start: Int): Int {
		var j = start
		var hasComma = false
		while (j < text.length) {
			val ch = text[j]
			if (ch.isDigit()) j++
			else if (ch == ',') { hasComma = true; j++ }
			else break
		}
		// Must have at least one comma and not end with comma
		if (!hasComma) return start
		// Ensure token ends with digit, not comma
		if (j > start && text[j - 1] == ',') j--
		return j
	}

	private fun isGroupedThousands(token: String): Boolean {
		// Pattern: \b\d{1,3},000(?:,\d{3})+\b
		// Quick checks
		if (!token.contains(',')) return false
		if (!token.contains("000")) return false
		val parts = token.split(',')
		if (parts.size < 2) return false
		if (parts[0].length !in 1..3) return false
		if (!parts[0].all { it.isDigit() }) return false
		// First after first must be 000
		if (parts[1] != "000") return false
		// Rest must be exactly 3 digits
		for (k in 1 until parts.size) {
			if (parts[k].length != 3) return false
			if (!parts[k].all { it.isDigit() }) return false
		}
		return true
	}
}
