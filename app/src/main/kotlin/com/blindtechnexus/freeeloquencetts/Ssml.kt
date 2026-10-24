package com.blindtechnexus.freeeloquencetts

/**
 * The tags and the entities a web page carries, taken out of the text before
 * anything reads it.
 *
 * A caller that hands over a page as HTML -- a feed reader, an e-mail client, a
 * browser that passes the markup rather than the rendered text -- is handing
 * over something the engine cannot say: `<p>` would be read as a word, `&amp;`
 * as "ampersand a m p semicolon", and `&#8212;` as a string of digits. So the
 * tags go and what they were wrapping stays, which is what this has always
 * done and what [Settings.stripSsml] asks for.
 *
 * What it does not do is take a comparison for a tag. A `<` is only a tag when
 * a letter, a slash or a `!` follows it and a `>` closes it soon after, so the
 * text of a message about code -- `if (a < b)` -- survives, which it did not
 * while every `<` started a tag.
 *
 * The entities are decoded rather than dropped, because `&amp;` says something
 * and "ampersand" does not: the five XML ones, the space, and the numeric ones
 * in either notation. An entity this does not know is left as it was written,
 * which is the one answer that loses nothing.
 *
 * Fast, one pass, no regular expressions: a text with neither a `<` nor an `&`
 * is returned as the very string it was.
 */
object Ssml {

	/** How far a tag may run before the `>` that ends it. Long enough for
	 *  the attributes a page really writes, short enough that a stray `<` in
	 *  prose cannot swallow a paragraph looking for a closing bracket. */
	private const val TAG_LIMIT = 200

	fun strip(text: String): String {
		if (text.isEmpty()) return text
		if ('<' !in text && '&' !in text) return text

		val sb = StringBuilder(text.length)
		var lastWasSpace = false
		var at = 0
		val n = text.length
		while (at < n) {
			val c = text[at]
			when {
				c == '<' -> {
					val close = tagEnd(text, at)
					if (close < 0) {
						// Not a tag: a comparison, or a stray bracket. It is
						// text, and it is said.
						sb.append(c)
						lastWasSpace = false
						at++
						continue
					}
					if (!lastWasSpace && sb.isNotEmpty()) {
						sb.append(' ')
						lastWasSpace = true
					}
					at = close + 1
				}

				c == '&' -> {
					val decoded = entity(text, at)
					if (decoded == null) {
						sb.append(c)
						lastWasSpace = false
						at++
					} else {
						for (d in decoded) {
							if (d.isWhitespace()) {
								if (!lastWasSpace) {
									sb.append(' ')
									lastWasSpace = true
								}
							} else {
								sb.append(d)
								lastWasSpace = false
							}
						}
						at += entityLength(text, at)
					}
				}

				c.isWhitespace() -> {
					if (!lastWasSpace) {
						sb.append(' ')
						lastWasSpace = true
					}
					at++
				}

				else -> {
					sb.append(c)
					lastWasSpace = false
					at++
				}
			}
		}
		return sb.toString().trim()
	}

	/**
	 * The index of the `>` that closes the tag starting at [at], or -1 when
	 * what is there is not a tag at all.
	 *
	 * A tag begins with a name, a closing slash, a declaration bang or a
	 * processing instruction; "a < b" begins with a space and is prose. And a
	 * tag with a space after its name has an `=` in it before the bracket, so
	 * "a<b and c>d" -- arithmetic, or a line of code -- is not a tag with two
	 * attributes and nobody's words are swallowed looking for one.
	 */
	private fun tagEnd(text: String, at: Int): Int {
		if (at + 1 >= text.length) return -1
		val limit = minOf(text.length, at + TAG_LIMIT)
		val first = text[at + 1]
		if (!(first.isLetter() || first == '/' || first == '!' || first == '?')) return -1
		var nameStart = at + 1
		if (text[nameStart] == '/') nameStart++
		var nameEnd = nameStart
		while (nameEnd < text.length && nameEnd < limit &&
			(text[nameEnd].isLetterOrDigit() || text[nameEnd] == '-')
		) nameEnd++
		var hasEquals = false
		var j = nameEnd
		while (j < limit) {
			val c = text[j]
			if (c == '>') {
				if (j > nameEnd && !hasEquals && text[nameEnd].isWhitespace()) return -1
				return j
			}
			if (c == '=') hasEquals = true
			if (c == '<') return -1
			j++
		}
		return -1
	}

	private fun entity(text: String, at: Int): String? {
		val semi = text.indexOf(';', at + 1)
		if (semi < 0 || semi - at > 10) return null
		val body = text.substring(at + 1, semi)
		when (body) {
			"amp" -> return "&"
			"lt" -> return "<"
			"gt" -> return ">"
			"quot" -> return "\""
			"apos" -> return "'"
			"nbsp" -> return " "
			"mdash" -> return " - "
			"ndash" -> return " - "
			"hellip" -> return "..."
			"copy" -> return " copyright "
			"reg" -> return " registered "
			"trade" -> return " trademark "
			"deg" -> return " degrees "
		}
		// &#8212; and &#x2014; are the same character written two ways.
		if (body.startsWith("#")) {
			val code = if (body.length > 1 && (body[1] == 'x' || body[1] == 'X')) {
				body.substring(2).toIntOrNull(16)
			} else {
				body.substring(1).toIntOrNull()
			} ?: return null
			if (code <= 0 || code > 0x10FFFF) return null
			return try {
				String(Character.toChars(code))
			} catch (_: Throwable) {
				null
			}
		}
		return null
	}

	/** How many characters the entity at [at] takes, semicolon and all. */
	private fun entityLength(text: String, at: Int): Int {
		val semi = text.indexOf(';', at + 1)
		return if (semi < 0) 1 else semi - at + 1
	}
}
