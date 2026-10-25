package com.blindtechnexus.freeeloquencetts

/**
 * Which of the symbols in the text are spoken by name.
 *
 * The engine has no setting for this. Its own behaviour, measured rather than
 * assumed by speaking one string at a time and comparing the audio, is three
 * things: a symbol standing on its own is always named, out of the language's
 * own character-name table -- "?" alone is "question mark", byte for byte the
 * same audio as the words; a symbol attached to a word is silent when it is
 * sentence punctuation or a bracket, and named when it carries meaning --
 * "$5" is "five dollars", "a=b" is "a equals b"; and the names it uses are its
 * own, which are "asterisk", "dot", "tilda" and "exclamation point" rather
 * than the words another synthesiser might choose.
 *
 * So the levels here are made out of text changes, on the way in, which is
 * also how Speech Dispatcher's own module for this engine does it: the only
 * lever anybody has is the text.
 *
 * [SOME] is the engine left as it stands, and is the default, so nobody's
 * speech changes under them when this arrives. The one exception is the
 * comma: the engine names a symbol that stands on its own, and a comma that
 * has no word against it -- ", ", ".,", a line that is nothing but commas --
 * is a pause the text asked for, not the word "comma". So a comma with no
 * letter or digit directly before or after it is taken out, and one written
 * against a word ("one, two") is left where it is, because it is the pause
 * and the engine does not name it.
 *
 * [MOST] and [ALL] add the names of symbols the engine leaves silent when
 * they are attached: brackets, quotes and the like at [MOST], periods and
 * commas at [ALL]. A symbol standing on its own is never touched, because the
 * engine already names it, with its own name and in its own prosody.
 *
 * The symbols the engine already reads in place -- the dollar in "$5", the
 * percent in "50%" -- are never named here even at [ALL], because naming them
 * ourselves would replace "five dollars" with "five dollar" and lose the
 * number the symbol was part of. They are only ever taken away, at [NONE].
 *
 * [NONE] names nothing: everything is replaced with silence except the
 * punctuation the engine needs for its pauses, and the apostrophe inside a
 * word, which is a contraction rather than a symbol. A decimal point, a
 * thousands separator and a colon between digits are the engine's number
 * reading rather than a symbol, and are left alone at every level.
 *
 * [CUSTOM] is [NONE] plus a list of the caller's own: the symbols in it are
 * named wherever they are found, and nothing else is.
 *
 * The backtick is never touched, at any level. It is the engine's annotation
 * escape and text that carries one is asking for a pause or a voice change,
 * not for the word "backquote". The hyphen is left alone as well: standing on
 * its own it is the one symbol the engine has no name for, and attached it is
 * how a minus sign or a range is written.
 */
object Symbols {

	/** No symbol is spoken by name. */
	const val NONE = 0

	/** The engine's own behaviour: what it names, it keeps naming. */
	const val SOME = 1

	/** Brackets, quotes, question marks and the like are spoken as well. */
	const val MOST = 2

	/** Every symbol is spoken, including periods and commas. */
	const val ALL = 3

	/** Only the symbols on the caller's list are spoken. */
	const val CUSTOM = 4

	/** The levels, in the order the settings screen offers them. */
	val MODES = listOf(ALL, MOST, SOME, CUSTOM, NONE)

	/** A symbol's spoken name, and the least level of verbosity that names it. */
	private class Entry(val name: String, val level: Int)

	/**
	 * The symbols that are named, by the engine's own names. The [SOME] level
	 * entries are the ones the engine already reads in place, and are never
	 * named by this filter: replacing them would trade "five dollars" for
	 * "five dollar". They are in the table so that [NONE] and [CUSTOM] can
	 * take them away, and so that a [CUSTOM] list can name one of them and
	 * have it mean "keep what the engine already says".
	 */
	private val TABLE = mapOf(
		'$' to Entry("dollar", SOME),
		'%' to Entry("percent", SOME),
		'+' to Entry("plus", SOME),
		'=' to Entry("equals", SOME),
		'@' to Entry("at", SOME),
		'#' to Entry("number", SOME),
		'*' to Entry("asterisk", SOME),
		'&' to Entry("and", SOME),
		'/' to Entry("slash", SOME),
		'<' to Entry("less than", SOME),
		'>' to Entry("greater than", SOME),
		'|' to Entry("bar", SOME),
		'~' to Entry("tilda", SOME),
		'^' to Entry("caret", SOME),
		'_' to Entry("underscore", SOME),
		'\\' to Entry("backslash", SOME),
		'(' to Entry("left parenthesis", MOST),
		')' to Entry("right parenthesis", MOST),
		'[' to Entry("left bracket", MOST),
		']' to Entry("right bracket", MOST),
		'{' to Entry("left brace", MOST),
		'}' to Entry("right brace", MOST),
		'"' to Entry("quote", MOST),
		'\'' to Entry("apostrophe", MOST),
		'?' to Entry("question mark", MOST),
		'!' to Entry("exclamation point", MOST),
		';' to Entry("semicolon", MOST),
		':' to Entry("colon", MOST),
		'.' to Entry("dot", ALL),
		',' to Entry("comma", ALL),
		'€' to Entry("euro", SOME),
		'£' to Entry("pounds", SOME),
		'¥' to Entry("yen", SOME),
		'™' to Entry("trademark", SOME),
		'°' to Entry("degrees", SOME),
		'§' to Entry("section", SOME),
		'¶' to Entry("paragraph", SOME),
		'©' to Entry("copyright", SOME),
		'®' to Entry("registered", SOME),
		'±' to Entry("plus or minus", SOME),
		'×' to Entry("times", SOME),
		'÷' to Entry("divided by", SOME),
		'≤' to Entry("less than or equal to", SOME),
		'≥' to Entry("greater than or equal to", SOME),
		'≠' to Entry("not equal to", SOME),
		'।' to Entry("full stop", ALL),
		'॥' to Entry("double full stop", ALL)
	)

	private val HINDI_NAMES = mapOf(
		'(' to "बायाँ कोष्ठक",
		')' to "दायाँ कोष्ठक",
		'[' to "बायाँ वर्ग कोष्ठक",
		']' to "दायाँ वर्ग कोष्ठक",
		'{' to "बायाँ मँझला कोष्ठक",
		'}' to "दायाँ मँझला कोष्ठक",
		'"' to "उद्धरण चिह्न",
		'\'' to "एकल उद्धरण",
		'?' to "प्रश्नवाचक चिह्न",
		'!' to "विस्मयादिबोधक चिह्न",
		';' to "अर्धविराम",
		':' to "अपूर्ण विराम",
		'.' to "बिंदु",
		',' to "अल्पविराम",
		'।' to "पूर्ण विराम",
		'॥' to "दोहरा पूर्ण विराम"
	)

	private val NEPALI_NAMES = mapOf(
		'(' to "बायाँ कोष्ठक",
		')' to "दायाँ कोष्ठक",
		'[' to "बायाँ वर्ग कोष्ठक",
		']' to "दायाँ वर्ग कोष्ठक",
		'{' to "बायाँ मझौला कोष्ठक",
		'}' to "दायाँ मझौला कोष्ठक",
		'"' to "उद्धरण चिन्ह",
		'\'' to "एकल उद्धरण",
		'?' to "प्रश्न चिन्ह",
		'!' to "विस्मयादिबोधक चिन्ह",
		';' to "अर्धविराम",
		':' to "विवरण चिन्ह",
		'.' to "थोप्लो",
		',' to "अल्पविराम",
		'।' to "पूर्णविराम",
		'॥' to "दोहोरो पूर्णविराम"
	)

	private fun spokenName(c: Char, entry: Entry, language: Int): String = when (language) {
		Eci.HINDI -> HINDI_NAMES[c] ?: entry.name
		Eci.NEPALI -> NEPALI_NAMES[c] ?: entry.name
		else -> entry.name
	}

	/**
	 * The typographic characters turned into the plain ones they stand for,
	 * which is what [WesternText] does to them later anyway. Doing it here
	 * means a curly quote is silenced at [NONE] just as a straight one is,
	 * rather than sailing through because it is not in the table.
	 */
	private val PLAIN = mapOf(
		'‘' to "'", '’' to "'", '‚' to "'", '‛' to "'",
		'“' to "\"", '”' to "\"", '„' to "\"", '‟' to "\"",
		'‹' to "<", '›' to ">", '«' to "\"", '»' to "\"",
		'′' to "'", '″' to "\"", '⁄' to "/", '∕' to "/",
		'…' to "...",
		'•' to ".", '·' to "."
	)

	/** Whether a character is one either table knows, which is the whole of
	 *  what decides whether there is anything here to do. A lookup of a
	 *  character against a hash map is a boxed character and a hash; this is a
	 *  read. Long text is mostly characters nobody can name, and it used to be
	 *  walked one map lookup at a time to find that out, then rebuilt, then
	 *  handed to a regular expression to collapse the spaces that were never
	 *  added. */
	private val RELEVANT = BooleanArray(0x10000).apply {
		for (c in TABLE.keys) this[c.code] = true
		for (c in PLAIN.keys) this[c.code] = true
	}

	/** The typographic characters on their own, which is what [normalize] is
	 *  looking for and nothing else. */
	private val PLAIN_CHARS = BooleanArray(0x10000).apply {
		for (c in PLAIN.keys) this[c.code] = true
	}

	/** The marks the engine needs where they are, to leave its pauses alone. */
	private const val PROSODY = ",.;:?!।॥"

	/**
	 * [text] with the symbols [mode] asks for named or silenced. [custom] is
	 * the caller's own list and is read only at [CUSTOM].
	 *
	 * [SOME] is the engine's own reading with one repair: a comma that no
	 * word stands against is taken out, because the engine would name it.
	 */
	fun apply(text: String, mode: Int, custom: String, language: Int = Eci.ENGLISH_US): String {
		if (text.isEmpty()) return text
		if (mode == SOME) return withoutLoneCommas(text)
		var relevant = false
		var scan = 0
		while (scan < text.length) {
			val ch = text[scan]
			if (ch == '`') {
				scan = EvvEngine.annotationEnd(text, scan)
				continue
			}
			if (ch.code < RELEVANT.size && RELEVANT[ch.code]) {
				relevant = true
				break
			}
			scan++
		}
		// Nothing in the text is a symbol or a typographic character, so there
		// is nothing to name, nothing to take away, and no spaces left behind
		// by either. What is said is trimmed, which is what the rebuild below
		// trims to as well.
		if (!relevant) return text.trim()
		val listed = if (mode == CUSTOM) custom.filter { TABLE.containsKey(it) }.toSet() else emptySet()
		val source = normalize(text)
		val out = StringBuilder(source.length + 32)
		var at = 0
		while (at < source.length) {
			val c = source[at]
			if (c == '`') {
				// An engine annotation -- "`vbst+6", "`[das]" -- is carried
				// through untouched: its '+', '-' or brackets belong to the
				// engine's escape, not to the sentence's punctuation.
				val end = EvvEngine.annotationEnd(source, at)
				out.append(source, at, end)
				at = end
				continue
			}
			when {
				// A contraction is a word, not a symbol, and the engine reads
				// it as one. "don apostrophe t" is nobody's reading of don't.
				c == '\'' && betweenWordCharacters(source, at) -> out.append(c)

				// A mark between two digits is inside a number, and the
				// engine's number reading is the right one: 3.14 is "three
				// point one four", not "three dot fourteen".
				c in ",.:" && betweenDigits(source, at) -> out.append(c)

				else -> {
					val entry = TABLE[c]
					when {
						entry == null -> out.append(c)

						// Silenced: at NONE, and at CUSTOM for everything not
						// on the list. A mark the engine pauses at is kept
						// where it is attached to something and not inside a
						// word, since a period in "example.com" would
						// otherwise still be named by the engine's own rules.
						mode == NONE || (mode == CUSTOM && c !in listed) ->
							if (c in PROSODY && attached(source, at) &&
								!betweenWordCharacters(source, at)
							) out.append(c) else out.append(' ')

						// Named, where the engine would have left the symbol
						// silent: attached, and one of the levels the mode
						// names. The engine's own reading of its own symbols
						// is better than ours -- "$5" is "five dollars" where
						// a replacement would say "five dollar" -- so the
						// [SOME]-level entries are never named here, at any
						// mode: a mode that does not silence them leaves them
						// to the engine.
						attached(source, at) && entry.level >= MOST && names(mode, entry, c, listed) ->
							out.append(' ').append(spokenName(c, entry, language)).append(' ')

						// Standing alone, or already read by the engine.
						else -> out.append(c)
					}
				}
			}
			at++
		}
		return COLLAPSE.replace(out.toString().trim(), " ")
	}

	/**
	 * [text] with every comma that stands on its own taken out.
	 *
	 * The engine names a symbol that has nothing against it, so a comma of
	 * that kind is heard as the word "comma" in the middle of a sentence --
	 * which is never what the text meant. A comma written against a word or
	 * a digit is the pause the engine already makes, and is left exactly
	 * where it is; so is one inside a number, which is the engine's own
	 * number reading rather than punctuation.
	 *
	 * "Against" is a letter or a digit with no space in between: "(," and
	 * ",," are two symbols standing alone in the engine's eyes, and a space
	 * on either side is what makes a comma lonely in the first place. One
	 * scan, and a text with no comma at all -- which is most text -- is not
	 * even looked at.
	 */
	private fun withoutLoneCommas(text: String): String {
		if (text.indexOf(',') < 0) return text
		var out: StringBuilder? = null
		var at = 0
		val n = text.length
		while (at < n) {
			val c = text[at]
			if (c == '`') {
				val end = EvvEngine.annotationEnd(text, at)
				out?.append(text, at, end)
				at = end
				continue
			}
			if (c != ',') {
				out?.append(c)
				at++
				continue
			}
			// Inside a number: 1,024 and 3,14 are the engine's own reading.
			if (betweenDigits(text, at)) {
				out?.append(c)
				at++
				continue
			}
			// Against means directly against, with no space in between: a
			// comma with a space on either side is the one the engine names,
			// and a comma inside "one, two" is the pause it makes anyway.
			val before = at - 1
			val after = at + 1
			val worded = (before >= 0 && WordIndex.isWordCharacter(text[before])) ||
				(after < n && WordIndex.isWordCharacter(text[after]))
			if (worded) {
				out?.append(c)
				at++
				continue
			}
			// Nothing against it: the comma goes, and a space takes its
			// place only where two words would otherwise run together.
			if (out == null) {
				out = StringBuilder(n)
				out.append(text, 0, at)
			}
			if (at > 0 && !text[at - 1].isWhitespace() && after < n) out.append(' ')
			at++
		}
		val built = out ?: return text
		return spaced(built)
	}

	/** Runs of spaces down to one and the ends trimmed, in one pass and
	 *  without a regular expression: this runs on the way to every utterance.
	 *  A line break is left where it is -- a piece boundary is a line break --
	 *  and only the spaces and tabs around it go. */
	private fun spaced(text: CharSequence): String {
		val out = StringBuilder(text.length)
		var spaces = 0
		for (c in text) {
			if (c == ' ' || c == '\t') {
				spaces++
				continue
			}
			if (spaces > 0 && c != '\n' && out.isNotEmpty() && out[out.length - 1] != '\n') out.append(' ')
			spaces = 0
			out.append(c)
		}
		return out.toString()
	}

	/** Whether [mode] names [entry], once it is attached and at least [MOST]. */
	private fun names(mode: Int, entry: Entry, c: Char, listed: Set<Char>): Boolean = when (mode) {
		ALL -> true
		MOST -> entry.level == MOST
		CUSTOM -> c in listed
		else -> false
	}

	/** [text] with every typographic character turned into its plain one. */
	private fun normalize(text: String): String {
		var needed = false
		var scan = 0
		while (scan < text.length) {
			val ch = text[scan]
			if (ch == '`') {
				scan = EvvEngine.annotationEnd(text, scan)
				continue
			}
			val c = ch.code
			if (c < PLAIN_CHARS.size && PLAIN_CHARS[c]) {
				needed = true
				break
			}
			scan++
		}
		if (!needed) return text
		val out = StringBuilder(text.length)
		var at = 0
		while (at < text.length) {
			val c = text[at]
			if (c == '`') {
				val end = EvvEngine.annotationEnd(text, at)
				out.append(text, at, end)
				at = end
				continue
			}
			out.append(PLAIN[c] ?: c)
			at++
		}
		return out.toString()
	}

	/** Whether the character at [at] has something other than space beside it. */
	private fun attached(text: String, at: Int): Boolean {
		val before = at > 0 && !text[at - 1].isWhitespace()
		val after = at < text.length - 1 && !text[at + 1].isWhitespace()
		return before || after
	}

	/** Whether the character at [at] has a letter or a digit on both sides. */
	private fun betweenWordCharacters(text: String, at: Int): Boolean =
		at > 0 && at < text.length - 1 &&
			(WordIndex.isWordCharacter(text[at - 1]) && WordIndex.isWordCharacter(text[at + 1]))

	/** Whether the character at [at] has a digit on both sides. */
	private fun betweenDigits(text: String, at: Int): Boolean =
		at > 0 && at < text.length - 1 &&
			(text[at - 1].isDigit() && text[at + 1].isDigit())

	/** Runs of spaces, which the replacements above can leave behind. */
	private val COLLAPSE = Regex(" {2,}")
}
