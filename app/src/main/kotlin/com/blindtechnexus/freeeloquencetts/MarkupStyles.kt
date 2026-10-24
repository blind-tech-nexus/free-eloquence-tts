package com.blindtechnexus.freeeloquencetts

/**
 * The emphasis carried in markup, read out in words.
 *
 * [TextStyles] reads the styles a *spanned* text carries, which is what a
 * browser hands a screen reader. Not every reader passes spans: a feed reader
 * that has the article as HTML, an e-mail client showing an HTML message, or a
 * browser that hands over flattened markup instead of rendered text all arrive
 * here as characters -- `<b>Sale</b>`, `**Sale**`, `<h2>Today</h2>` -- and the
 * pipeline used to strip the tags without a word about them. That is why a page
 * with bold headings read as flat text however loudly the "read text styles"
 * switch was on.
 *
 * So with that switch on, the markup is read the way a span is: "bold Sale end
 * bold", "heading 2 Today end heading", "link Shop end link". The tags
 * themselves still go -- only the word for them is left -- and a tag nothing
 * here knows is dropped, because a `<div>` is not something anybody hears.
 *
 * Announced: bold, italic, underline, strikethrough, code, subscript,
 * superscript, links, headings 1 to 6, list items, quotes, highlighted text,
 * and an image's alternative text. Not announced: layout tags, and anything
 * inside a tag's attributes.
 *
 * Cheap and total: a text with no `<` and no emphasis mark is returned as the
 * very string it was, which is the case for everything a person types.
 */
object MarkupStyles {

	/** How far a tag may run before the `>` that ends it. */
	private const val TAG_LIMIT = 400

	/** How much of an alternative text is read out for an image. */
	private const val ALT_LIMIT = 120

	/** [text] with its markup announced, or the text itself when it carries
	 *  none. */
	fun annotate(text: String): String {
		if (text.isEmpty()) return text
		var out = text
		if ('<' in out) out = tags(out)
		if ('*' in out || '_' in out || '#' in out) out = marks(out)
		return out
	}

	/** Runs of spaces down to one and the ends trimmed. A tag that became a
	 *  word leaves a space on either side of itself, and the words around it
	 *  already had one: two spaces between two words is what the engine
	 *  reads as a pause nobody asked for. */
	private fun tidy(text: String): String {
		val out = StringBuilder(text.length)
		var spaced = false
		for (c in text) {
			if (c.isWhitespace()) {
				spaced = true
				continue
			}
			if (spaced && out.isNotEmpty()) out.append(' ')
			spaced = false
			out.append(c)
		}
		return out.toString()
	}

	// ---- tags ------------------------------------------------------------

	/** The opening and closing words for one emphasis tag. */
	private class Tag(val start: String, val end: String)

	private val TAGS: Map<String, Tag> = buildMap(24) {
		for (name in listOf("b", "strong")) put(name, Tag("bold", "bold"))
		for (name in listOf("i", "em")) put(name, Tag("italic", "italic"))
		for (name in listOf("u", "ins")) put(name, Tag("underline", "underline"))
		for (name in listOf("s", "strike", "del")) put(name, Tag("strikethrough", "strikethrough"))
		for (name in listOf("code", "kbd", "samp", "tt")) put(name, Tag("code", "code"))
		put("sub", Tag("subscript", "subscript"))
		put("sup", Tag("superscript", "superscript"))
		put("a", Tag("link", "link"))
		put("q", Tag("quote", "quote"))
		put("blockquote", Tag("quote", "quote"))
		put("mark", Tag("highlighted", "highlighted"))
		for (level in 1..6) put("h$level", Tag("heading $level", "heading"))
	}

	/** The tags that are a break in the words and nothing more. */
	private val BREAKS = setOf(
		"p", "div", "br", "tr", "td", "th", "table", "ul", "ol", "dl", "dt", "dd",
		"section", "article", "header", "footer", "nav", "main", "aside", "figure",
		"figcaption", "hr", "pre", "body", "html", "span", "font", "center", "thead",
		"tbody", "tfoot", "caption", "details", "summary", "fieldset", "form", "label"
	)

	/** The tags with no closing one of their own. */
	private val VOID = setOf(
		"br", "hr", "img", "input", "meta", "link", "source", "wbr", "area", "col", "base", "embed"
	)

	private fun tags(text: String): String {
		val out = StringBuilder(text.length + 64)
		val open = ArrayList<String>(8)
		var at = 0
		var changed = false
		val n = text.length
		while (at < n) {
			val c = text[at]
			if (c != '<') {
				out.append(c)
				at++
				continue
			}
			val close = tagEnd(text, at)
			if (close < 0) {
				out.append(c)
				at++
				continue
			}
			val body = text.substring(at + 1, close)
			at = close + 1
			changed = true
			announce(body, out, open)
		}
		// Whatever was left open is closed in the words too, so a page that
		// ends inside a <b> does not leave the reader waiting for the end of
		// something that never comes.
		while (open.isNotEmpty()) {
			out.append(" end ").append(open.removeAt(open.size - 1)).append(' ')
		}
		return if (changed) tidy(out.toString()) else text
	}

	/** Announces the tag whose body is [body], or says nothing about it. */
	private fun announce(body: String, out: StringBuilder, open: MutableList<String>) {
		var at = 0
		val n = body.length
		if (n == 0) return
		// A comment, a doctype, a script: nothing to say.
		if (body[0] == '!' || body[0] == '?') return
		val closing = body[0] == '/'
		if (closing) at = 1
		while (at < n && body[at].isWhitespace()) at++
		var end = at
		while (end < n && body[end].isLetterOrDigit()) end++
		if (end == at) return
		val name = body.substring(at, end).lowercase()

		if (name == "img") {
			val alt = attribute(body, "alt")
			out.append(" image ")
			if (alt != null) out.append(alt).append(' ')
			return
		}
		if (name == "li") {
			out.append(" bullet ")
			return
		}
		if (name in VOID || name in BREAKS) {
			out.append(' ')
			return
		}
		val tag = TAGS[name] ?: return
		if (closing) {
			val found = open.lastIndexOf(tag.end)
			if (found >= 0) {
				open.removeAt(found)
				out.append(" end ").append(tag.end).append(' ')
			}
			return
		}
		out.append(' ').append(tag.start).append(' ')
		open.add(tag.end)
	}

	/** The index of the `>` that closes the tag starting at [at], or -1. */
	private fun tagEnd(text: String, at: Int): Int {
		if (at + 1 >= text.length) return -1
		val first = text[at + 1]
		// "a < b" is prose; "<b", "</b" and "<h1" are tags.
		if (!(first.isLetter() || first == '/' || first == '!')) return -1
		val limit = minOf(text.length, at + TAG_LIMIT)
		var j = at + 1
		while (j < limit) {
			val c = text[j]
			if (c == '>') return j
			if (c == '<') return -1
			j++
		}
		return -1
	}

	/** The value of `name="..."` in a tag body, or null. */
	private fun attribute(body: String, name: String): String? {
		var at = body.indexOf(name, ignoreCase = true)
		while (at > 0 && (body[at - 1].isLetterOrDigit() || body[at - 1] == '-')) {
			at = body.indexOf(name, at + 1, ignoreCase = true)
		}
		if (at < 0) return null
		var i = at + name.length
		while (i < body.length && (body[i] == ' ' || body[i] == '=')) i++
		if (i >= body.length) return null
		val quote = body[i]
		if (quote != '"' && quote != '\'') return null
		val close = body.indexOf(quote, i + 1)
		if (close < 0) return null
		val clean = StringBuilder(64)
		for (k in i + 1 until close) {
			val c = body[k]
			if (c.isLetterOrDigit() || c == '.' || c == '\'' || c == ',') clean.append(c)
			else if (c.isWhitespace() || c == '-' || c == '_' || c == '/') clean.append(' ')
			if (clean.length >= ALT_LIMIT) break
		}
		val answer = clean.toString().trim()
		return if (answer.isEmpty()) null else answer
	}

	// ---- marks -----------------------------------------------------------

	/**
	 * `**bold**`, `__bold__`, `*italic*`, `_italic_` and `# heading`, said the
	 * way their HTML equivalents are.
	 *
	 * A mark only counts as one when the text inside it starts and ends with
	 * something that is not a space, so "2 * 3" and "a * b" keep their
	 * asterisks and a paragraph is not made italic by one of them.
	 */
	private fun marks(text: String): String {
		val out = StringBuilder(text.length + 32)
		var changed = false
		var at = 0
		val n = text.length
		while (at < n) {
			val c = text[at]
			if (c != '\n' && c != '*' && c != '_' && c != '#') {
				out.append(c)
				at++
				continue
			}
			val lineStart = at == 0 || text[at - 1] == '\n'
			if (c == '#' && lineStart) {
				var j = at
				var hashes = 0
				while (j < n && text[j] == '#') {
					hashes++
					j++
				}
				if (hashes in 1..6 && j < n && text[j] == ' ') {
					val close = text.indexOf('\n', j)
					val stop = if (close < 0) n else close
					val line = text.substring(j + 1, stop).trim()
					out.append(" heading ").append(hashes).append(' ').append(line)
					out.append(" end heading ")
					at = stop
					changed = true
					continue
				}
				out.append(c)
				at++
				continue
			}
			if (c == '*' || c == '_') {
				val double = at + 1 < n && text[at + 1] == c
				val width = if (double) 2 else 1
				val from = at + width
				val closer = closer(text, from, c, double)
				if (closer > from && !text[from].isWhitespace() && !text[closer - 1].isWhitespace()) {
					val label = if (double) "bold" else "italic"
					out.append(' ').append(label).append(' ')
					out.append(text, from, closer)
					out.append(" end ").append(label).append(' ')
					at = closer + width
					changed = true
					continue
				}
			}
			out.append(c)
			at++
		}
		return if (changed) tidy(out.toString()) else text
	}

	/** The index of the mark that closes one opened just before [from]. */
	private fun closer(text: String, from: Int, mark: Char, double: Boolean): Int {
		var at = from
		val n = text.length
		while (at < n) {
			val c = text[at]
			if (c == '\n' && !double) return -1
			if (c == mark) {
				val pair = at + 1 < n && text[at + 1] == mark
				if (double && pair) return at
				if (!double && !pair && (at == 0 || text[at - 1] != mark)) return at
			}
			at++
		}
		return -1
	}
}
