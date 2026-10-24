package com.blindtechnexus.freeeloquencetts

import android.graphics.Typeface
import android.os.Build
import android.text.Layout
import android.text.Spanned
import android.text.style.AbsoluteSizeSpan
import android.text.style.ClickableSpan
import android.text.style.ImageSpan
import android.text.style.AlignmentSpan
import android.text.style.BackgroundColorSpan
import android.text.style.BulletSpan
import android.text.style.ForegroundColorSpan
import android.text.style.QuoteSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.SubscriptSpan
import android.text.style.SuperscriptSpan
import android.text.style.TextAppearanceSpan
import android.text.style.TypefaceSpan
import android.text.style.URLSpan
import android.text.style.UnderlineSpan

/**
 * The styles carried in styled text, read out in words.
 *
 * A caller does not always hand over plain text: an app reading its own
 * screen may pass bold, italic, coloured or linked stretches along with the
 * words, and the service used to drop all of it on the floor with a single
 * `toString()`. With [Settings.readTextStyles] on, every styled stretch is
 * announced instead: "bold hello end bold", "link App store end link", "red
 * warning end red".
 *
 * What is announced, and the whole of it:
 * - bold, italic and bold italic (StyleSpan)
 * - underline, strikethrough, superscript, subscript
 * - links (URLSpan, and any other ClickableSpan)
 * - images, with whatever the page said they show (ImageSpan)
 * - monospace and other named fonts (TypefaceSpan)
 * - larger and smaller text, and other resized text (RelativeSizeSpan,
 *   AbsoluteSizeSpan)
 * - the colour of coloured text, by name (ForegroundColorSpan)
 * - highlighted text, with the colour of the highlight
 *   (BackgroundColorSpan)
 * - bullets and quotes (BulletSpan, QuoteSpan)
 * - centred and right-aligned text (AlignmentSpan)
 *
 * The web is the reason most of the second half of that list exists. A page
 * read by a browser arrives here already flattened to text, and the flattening
 * keeps the meaning in spans: a link is a URLSpan, a heading is a StyleSpan
 * with a RelativeSizeSpan beside it, a list item is a BulletSpan, a picture is
 * an ImageSpan. Read without them, all of that is a wall of words in which a
 * link is indistinguishable from the sentence around it and a picture is
 * nothing at all -- which is what "the page read flat" sounds like. So every
 * one of them is announced as the thing it is, twice, around the words it
 * covers: "link Shop end link".
 *
 * A clickable span that is not a URL is still a link: on Android the
 * difference between the two is which method the app overrides, not what a
 * listener would hear, and a page whose links are handled in its own code
 * rather than by the browser carries the plain kind. An image has no words of
 * its own to wrap, so it is announced where it stands and the placeholder
 * character under it is not read -- "image picture of a red bicycle" -- which
 * is the one case where a span replaces text rather than describing it.
 *
 * What is deliberately not announced: black and white foreground colours,
 * which are the default ink on one screen or another and would be noise on
 * every stretch of every app that sets its colours explicitly; transparent
 * colours, which colour nothing; and layout spans that say nothing about a
 * word (margins, line heights, spell-check marks).
 *
 * Total: unknown spans are skipped, misbehaving spans are caught, and
 * anything that goes wrong anywhere in here answers the plain text, because
 * a style that cannot be read is worth the words without it and never worth
 * losing the utterance over.
 */
object TextStyles {

    /** The most spans one text is ever asked about. Past this the rest are
     *  skipped, because a hostile text is not owed an unbounded walk. */
    private const val MAX_SPANS = 500

    /** [text] with its styles announced, or the plain text when it carries
     *  none -- or when anything about it refuses to answer. */
    fun annotate(text: CharSequence?): String {
        if (text == null) return ""
        return try {
            if (text !is Spanned) return text.toString()
            if (text.isEmpty()) return ""
            val spans = try {
                text.getSpans(0, text.length, Any::class.java)
            } catch (broken: Exception) {
                return text.toString()
            }
            if (spans.isEmpty()) return text.toString()
            val marks = ArrayList<Mark>(spans.size * 2)
            // Only allocated when something in the text is a thing rather
            // than a description of the words: an image stands where a
            // character stands, and that character is not a word.
            var replaced: BooleanArray? = null
            var count = 0
            for (span in spans) {
                if (count >= MAX_SPANS) break
                val label = try {
                    labelOf(span)
                } catch (broken: Exception) {
                    continue
                } ?: continue
                // A try that answers with the bounds rather than assigning
                // them: a span whose ends cannot be read is skipped whole,
                // and nothing is left half-initialised for the rest of the
                // loop to trip over.
                val bounds = try {
                    text.getSpanStart(span) to text.getSpanEnd(span)
                } catch (broken: Exception) {
                    continue
                }
                val from = bounds.first
                val to = bounds.second
                if (from < 0 || to <= from || from >= text.length) continue
                val start = from.coerceIn(0, text.length)
                val end = to.coerceIn(0, text.length)
                if (end <= start) continue
                if (span is ImageSpan) {
                    // An image is not words with a style on them; it is a
                    // thing standing where a character stands. The character
                    // it stands on -- U+FFFC, an object replacement -- is not
                    // read, and what is read instead is the word image and
                    // whatever description the page gave it.
                    val described = imageOf(span)
                    if (replaced == null) replaced = BooleanArray(text.length)
                    for (at in start until end) replaced[at] = true
                    marks.add(Mark(start, false, true, described))
                    count++
                    continue
                }
                if (label.isEmpty()) {
                    // A marker that stands on its own, with no end to it.
                    val single = try {
                        singleOf(span)
                    } catch (broken: Exception) {
                        continue
                    } ?: continue
                    marks.add(Mark(start, false, true, single))
                } else {
                    marks.add(Mark(start, false, false, label))
                    marks.add(Mark(end, true, false, label))
                }
                count++
            }
            if (marks.isEmpty()) return text.toString()
            // Ends before starts at the same offset, so "end bold italic"
            // reads in the order the styles do.
            marks.sortWith(compareBy<Mark> { it.at }.thenBy { if (it.end) 0 else 1 })
            val out = StringBuilder(text.length + marks.size * 8)
            var i = 0
            var m = 0
            while (i <= text.length) {
                while (m < marks.size && marks[m].at == i) {
                    val mark = marks[m]
                    if (mark.end) out.append(" end ").append(mark.label).append(' ')
                    else out.append(' ').append(mark.label).append(' ')
                    m++
                }
                // A mark stranded behind (it cannot be, the list is sorted,
                // but a span that moved under us is not owed an infinite
                // loop) is dropped rather than chased.
                while (m < marks.size && marks[m].at < i) m++
                if (i < text.length) {
                    // The character under an image is a placeholder, and a
                    // placeholder read out loud is a wrong word between the
                    // image and what it shows.
                    val skip = replaced
                    if (skip == null || !skip[i]) out.append(text[i])
                }
                i++
            }
            collapse(out.toString())
        } catch (broken: Throwable) {
            try {
                text.toString()
            } catch (failed: Throwable) {
                ""
            }
        }
    }

    private class Mark(val at: Int, val end: Boolean, val single: Boolean, val label: String)

    /**
     * What [span] announces, or nothing when it is not a style with a name.
     * An empty string means a span whose marker stands alone (see
     * [singleOf]); null means no marker at all.
     */
    private fun labelOf(span: Any): String? = when (span) {
        is StyleSpan -> when (span.style and Typeface.BOLD_ITALIC) {
            Typeface.BOLD -> "bold"
            Typeface.ITALIC -> "italic"
            Typeface.BOLD_ITALIC -> "bold italic"
            else -> null
        }
        is UnderlineSpan -> "underline"
        is StrikethroughSpan -> "strikethrough"
        is SuperscriptSpan -> "superscript"
        is SubscriptSpan -> "subscript"
        is URLSpan -> "link"
        // Any other span that a finger can press is a link too, and a page
        // that handles its own links carries this kind rather than URLSpan.
        // The two are the same thing to a listener.
        is ClickableSpan -> "link"
        is BulletSpan -> ""
        is QuoteSpan -> "quote"
        is TypefaceSpan -> fontOf(span)
        is RelativeSizeSpan -> {
            val proportion = span.sizeChange
            when {
                proportion >= 1.05f -> "larger text"
                proportion <= 0.95f -> "smaller text"
                else -> null
            }
        }
        is AbsoluteSizeSpan -> "different size text"
        // A TextView's own appearance carries its weight, slant or family
        // with no StyleSpan beside it. This is the span a page uses when
        // the whole widget is bold, and leaving it out made that boldness
        // invisible to the reader.
        is TextAppearanceSpan -> {
            val style = try { span.textStyle } catch (_: Throwable) { 0 }
            when (style and Typeface.BOLD_ITALIC) {
                Typeface.BOLD -> "bold"
                Typeface.ITALIC -> "italic"
                Typeface.BOLD_ITALIC -> "bold italic"
                else -> fontOf(span)
            }
        }
        is ForegroundColorSpan -> foregroundOf(span.foregroundColor)
        is BackgroundColorSpan -> backgroundOf(span.backgroundColor)
        is AlignmentSpan -> when (span.alignment) {
            Layout.Alignment.ALIGN_CENTER -> "centered"
            Layout.Alignment.ALIGN_OPPOSITE -> "right aligned"
            else -> null
        }
        else -> null
    }

    private fun singleOf(span: Any): String? = when (span) {
        is BulletSpan -> "bullet"
        else -> null
    }

    /** What an image is: the word for it, and whatever the page called it. */
    private fun imageOf(span: ImageSpan): String {
        val described = try {
            // A description is Android 8's; before that an image has only the
            // name of the thing it was loaded from, which is what [source] is.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) span.contentDescription else null
        } catch (_: Throwable) {
            null
        }
        val said = try {
            span.source
        } catch (_: Throwable) {
            null
        }
        val label = (described?.takeIf { it.isNotBlank() } ?: said?.takeIf { it.isNotBlank() })
        if (label == null) return "image"
        val clean = label.filter { !it.isISOControl() }.trim().take(120)
        return if (clean.isEmpty()) "image" else "image $clean"
    }

    private fun fontOf(span: TextAppearanceSpan): String? = try {
        val family = span.family
        if (family.isNullOrBlank()) null else fontName(family)
    } catch (_: Throwable) {
        null
    }

    private fun fontOf(span: TypefaceSpan): String? {
        val family = try {
            span.family
        } catch (broken: Exception) {
            return null
        } ?: return "different font"
        return fontName(family)
    }

    private fun fontName(family: String): String? {
        if (family.isBlank()) return "different font"
        if (family.equals("monospace", ignoreCase = true)) return "monospace"
        val clean = family.filter { it.isLetterOrDigit() || it == ' ' }.trim().take(20)
        return if (clean.isEmpty()) "different font" else "font $clean"
    }

    // ---- styles carried in the text itself ------------------------------

    /**
     * The styles written into the text rather than attached to it: the
     * tags an article carries through a browser, and the small markdown
     * an app or a message may hand over.
     *
     * A text from a web page very often arrives as characters and nothing
     * else. The browser has flattened the page, a reader app has copied
     * the selection, and what reaches a speech engine is `<b>Bold</b>`
     * rather than a Spanned with a StyleSpan on it. [annotate] cannot see
     * a tag: there is no span to read. The user turned "read text styles"
     * on because the page had bold in it, and heard the tags stripped
     * silently by the SSML pass -- which is exactly the bug this fixes.
     *
     * What is recognised:
     * - the HTML tags a page uses for meaning: b, strong, i, em, u, ins,
     *   s, del, strike, sup, sub, code, kbd, samp, pre, a, h1 to h6,
     *   blockquote, q, li, mark and img with its alt text;
     * - `style` and `font` attributes that say the same thing, because a
     *   page built from divs says bold with CSS and never with `<b>`;
     * - the character entities a page encodes, so `&amp;` is "and" and
     *   `&#8217;` is an apostrophe rather than digits and a hash;
     * - the small markdown that reaches a speech engine in a message:
     *   `**bold**`, `*italic*`, `~~struck~~`, and `#`, `>` and `-` at
     *   the start of a line.
     *
     * What is deliberately not done: a script or a stylesheet is skipped
     * rather than read out, a tag nobody can name is dropped rather than
     * spelled, and an unbalanced tag announces nothing rather than
     * promising an end that never comes. When nothing in the text is a
     * style, the very string that came in goes back, which is what makes
     * this cost one scan of ordinary prose.
     *
     * Everything is scanned at indices with no regular expression: this
     * runs on the way to every utterance, and an article is long.
     */
    fun annotateMarkup(text: String): String {
        if (text.isEmpty()) return text
        if (!mayCarryMarkup(text)) return text
        var out: StringBuilder? = null
        val n = text.length
        var i = 0
        val open = ArrayList<OpenTag>(8)
        var changed = false
        while (i < n) {
            val c = text[i]
            if (c == '<') {
                val tag = tagEnd(text, i)
                if (tag > i) {
                    if (out == null) out = StringBuilder(n + 64).append(text, 0, i)
                    // A script or a stylesheet is code, not an article. Its
                    // contents are skipped to the closing tag rather than
                    // read as words.
                    val nameStart = if (i + 1 < n && text[i + 1] == '/') i + 2 else i + 1
                    var nameEnd = nameStart
                    while (nameEnd < tag && text[nameEnd].isLetterOrDigit()) nameEnd++
                    val name = lowered(text, nameStart, nameEnd)
                    if ((name == "script" || name == "style") && (i + 1 >= n || text[i + 1] != '/')) {
                        val close = indexOfIgnoreCase(text, "</$name", tag + 1)
                        if (close >= 0) {
                            val after = tagEnd(text, close)
                            i = if (after > close) after + 1 else close + 2 + name.length
                            changed = true
                            continue
                        }
                    }
                    changed = appendTag(text, i, tag, name, out, open) || changed
                    i = tag + 1
                    continue
                }
            } else if (c == '&') {
                val entity = decodeEntity(text, i, n)
                if (entity != null) {
                    if (out == null) out = StringBuilder(n + 64).append(text, 0, i)
                    out.append(entity.first)
                    i = entity.second
                    changed = true
                    continue
                }
            }
            if (out != null) out.append(c)
            i++
        }
        if (!changed) return markdown(text)
        return markdown(collapse((out ?: StringBuilder(text).append(text)).toString()))
    }

    /** One cheap scan that says whether [annotateMarkup] has anything to do.
     *  A '<' followed by a letter or a slash is a tag; a '&' followed by a
     *  letter or a hash is an entity; the markdown markers are their own
     *  characters. A page with none of them -- which is almost every
     *  utterance typed by a person -- is not copied at all. */
    private fun mayCarryMarkup(text: String): Boolean {
        val n = text.length
        var i = 0
        while (i < n) {
            when (text[i]) {
                '<' -> if (i + 1 < n && (text[i + 1].isLetter() || text[i + 1] == '/')) return true
                '&' -> if (i + 1 < n && (text[i + 1].isLetter() || text[i + 1] == '#')) return true
                '*' -> if (i + 1 < n && (text[i + 1] == '*' || !text[i + 1].isWhitespace())) return true
                '~' -> if (i + 1 < n && text[i + 1] == '~') return true
                '#' -> if (i == 0 || text[i - 1] == '\n' || text[i - 1] == '\r') return true
                '>' -> if (i == 0 || text[i - 1] == '\n' || text[i - 1] == '\r') return true
                '-' -> if (i == 0 || text[i - 1] == '\n' || text[i - 1] == '\r') return true
            }
            i++
        }
        return false
    }

    /** Just past the '>' of the tag starting at [start], respecting a quoted
     *  attribute, or -1 when this '<' is a less-than sign and not a tag. */
    private fun tagEnd(text: String, start: Int): Int {
        var i = start + 1
        val n = text.length
        if (i < n && (text[i] == '!' || text[i] == '?')) {
            // A declaration, a doctype or a processing instruction: it has
            // no words to say, so it is skipped whole.
            val close = text.indexOf('>', i)
            return if (close > 0) close else -1
        }
        val closing = i < n && text[i] == '/'
        if (closing) i++
        if (i >= n || !text[i].isLetter()) return -1
        var quote = '\u0000'
        while (i < n) {
            val c = text[i]
            if (quote != '\u0000') {
                if (c == quote) quote = '\u0000'
            } else if (c == '"' || c == '\'') {
                quote = c
            } else if (c == '>') {
                return i
            } else if (c == '<') {
                return -1
            } else if (i - start > 512) {
                return -1
            }
            i++
        }
        return -1
    }

    /** Appends what one tag means, and answers whether it was understood. */
    private fun appendTag(
        text: String,
        start: Int,
        end: Int,
        name: String,
        out: StringBuilder,
        open: ArrayList<OpenTag>
    ): Boolean {
        val closing = start + 1 < text.length && text[start + 1] == '/'
        if (name.isEmpty()) return false
        if (!closing && name == "img") {
            val alt = attribute(text, start, end, "alt")
            out.append(' ').append("image")
            if (!alt.isNullOrBlank()) {
                val clean = alt.filter { !it.isISOControl() }.trim().take(120)
                if (clean.isNotEmpty()) out.append(' ').append(clean)
            }
            out.append(' ')
            return true
        }
        if (!closing && name == "li") {
            // A list item has no end worth announcing: the bullet is the
            // whole of what it says, and an "end bullet" after every item
            // is noise in a list of twelve.
            out.append(' ').append("bullet").append(' ')
            return true
        }
        if (!closing && (name == "br" || name == "hr")) {
            out.append(' ')
            return true
        }
        if (closing) {
            // An end says what the start it closes said, and it is found by
            // the tag's name rather than by what it might mean: the words of
            // a closing tag are not in the tag, so `<span style="color:
            // red">` has to be closed by what the opening `<span>` said.
            // Only an end that some start promised is announced.
            val at = open.indexOfLast { it.name == name }
            if (at < 0) return false
            out.append(" end ").append(open.removeAt(at).label).append(' ')
            return true
        }
        val label = labelOfTag(text, start, end, name) ?: return false
        out.append(' ').append(label).append(' ')
        // A tag whose closing form is not worth announcing -- a paragraph,
        // a div with a colour -- is not pushed, so its end says nothing.
        if (endsInLabel(name, label)) open.add(OpenTag(name, label))
        return true
    }

    /** A tag that is open, and the words it was announced with. */
    private class OpenTag(val name: String, val label: String)

    /** The words a tag is announced with, or null when it says nothing. */
    private fun labelOfTag(text: String, start: Int, end: Int, name: String): String? = when (name) {
        "b", "strong" -> "bold"
        "i", "em", "cite", "var", "dfn" -> "italic"
        "u", "ins" -> "underline"
        "s", "strike", "del" -> "strikethrough"
        "sup" -> "superscript"
        "sub" -> "subscript"
        "code", "kbd", "samp", "tt", "pre" -> "code"
        "a" -> "link"
        "h1", "h2", "h3", "h4", "h5", "h6" -> "heading"
        "blockquote", "q" -> "quote"
        "mark" -> "highlighted"
        "span", "font", "div", "p", "section", "article", "td", "th" -> styledByAttribute(text, start, end)
        else -> null
    }

    /** A tag that carries no name of its own but may carry a style. More
     *  than one style can be set on one tag, and all of them are said:
     *  `style="font-weight: bold; color: red"` is "bold red". */
    private fun styledByAttribute(text: String, start: Int, end: Int): String? {
        val style = attribute(text, start, end, "style")?.lowercase() ?: ""
        var label: String? = null
        fun add(words: String) {
            label = if (label == null) words else label + " " + words
        }
        if (style.isNotEmpty()) {
            if (style.contains("font-weight") && (style.contains("bold") || style.contains("700") || style.contains("800") || style.contains("900"))) add("bold")
            if (style.contains("font-style") && style.contains("italic")) add("italic")
            if (style.contains("text-decoration") && style.contains("underline")) add("underline")
            if (style.contains("text-decoration") && style.contains("line-through")) add("strikethrough")
            // A colour written the modern way, in the style attribute,
            // is as much a colour as the legacy attribute beside it -- and
            // it is the one most pages carry.
            cssColorOf(style)?.let { add(it) }
        }
        val color = attribute(text, start, end, "color")
        if (!color.isNullOrBlank()) {
            val named = color.trim().lowercase()
            if (named in NAMED_CSS) add(named)
        }
        return label
    }

    /** The colour a `style` attribute sets, by name, or null when it sets
     *  none this says in words. Declarations are split on their semicolons
     *  and named by their property, so `background-color` is not taken for
     *  `color`. */
    private fun cssColorOf(style: String): String? {
        var from = 0
        while (from < style.length) {
            val end = style.indexOf(';', from).let { if (it < 0) style.length else it }
            val colon = style.indexOf(':', from)
            if (colon in from until end) {
                val property = style.substring(from, colon).trim()
                if (property == "color") {
                    val value = style.substring(colon + 1, end).trim()
                    if (value in NAMED_CSS) return value
                }
            }
            from = end + 1
        }
        return null
    }

    /** Whether the tag's closing form is announced too. */
    private fun endsInLabel(name: String, label: String): Boolean =
        name != "div" && name != "p" && name != "section" && name != "article" &&
            name != "td" && name != "th" && name != "font"

    /** The value of [wanted] in the tag at [start, end), decoded and
     *  unquoted, or null when the tag does not carry it. */
    private fun attribute(text: String, start: Int, end: Int, wanted: String): String? {
        var i = start + 1
        while (i < end) {
            while (i < end && text[i].isWhitespace()) i++
            val nameStart = i
            while (i < end && (text[i].isLetterOrDigit() || text[i] == '-' || text[i] == '_')) i++
            if (i == nameStart) { i++; continue }
            val name = lowered(text, nameStart, i)
            while (i < end && text[i].isWhitespace()) i++
            if (i >= end || text[i] != '=') continue
            i++
            while (i < end && text[i].isWhitespace()) i++
            if (i >= end) return null
            val quote = if (text[i] == '"' || text[i] == '\'') text[i++] else '\u0000'
            val valueStart = i
            if (quote != '\u0000') {
                while (i < end && text[i] != quote) i++
            } else {
                while (i < end && !text[i].isWhitespace()) i++
            }
            if (name == wanted) return decodeEntities(text, valueStart, i)
            if (i < end) i++
        }
        return null
    }

    /** The markdown a message carries, read out as the styles it means. */
    private fun markdown(text: String): String {
        var working = text
        // Line prefixes first: they are unambiguous at the start of a line,
        // and a bullet's asterisk must not be taken for an italic marker.
        if (text.indexOf('#') >= 0 || text.indexOf('>') >= 0 ||
            text.indexOf("- ") >= 0 || text.indexOf("* ") >= 0
        ) {
            val built = lineMarkdown(text)
            if (built != null) working = built
        }
        if (working.indexOf('*') >= 0) {
            working = inlineMarkdown(working, "**", "bold") ?: working
            working = inlineMarkdown(working, "*", "italic") ?: working
        }
        if (working.indexOf('~') >= 0) {
            working = inlineMarkdown(working, "~~", "strikethrough") ?: working
        }
        return if (working === text) text else collapse(working)
    }

    /** The line prefixes of markdown: a heading, a quote, a bullet. Null
     *  when no line carries one. */
    private fun lineMarkdown(text: String): String? {
        val n = text.length
        val sb = StringBuilder(n + 32)
        var changed = false
        var lineStart = 0
        while (lineStart <= n) {
            var lineEnd = text.indexOf('\n', lineStart)
            if (lineEnd < 0) lineEnd = n
            var i = lineStart
            while (i < lineEnd && (text[i] == ' ' || text[i] == '\t')) i++
            var label: String? = null
            var skip = i
            if (i < lineEnd) {
                val c = text[i]
                if (c == '#') {
                    var hashes = 0
                    while (i + hashes < lineEnd && text[i + hashes] == '#') hashes++
                    if (hashes in 1..6 && i + hashes < lineEnd && text[i + hashes] == ' ') {
                        label = "heading"
                        skip = i + hashes + 1
                    }
                } else if (c == '>' && i + 1 < lineEnd && text[i + 1] == ' ') {
                    label = "quote"
                    skip = i + 2
                } else if ((c == '-' || c == '*' || c == '+') && i + 1 < lineEnd && text[i + 1] == ' ') {
                    label = "bullet"
                    skip = i + 2
                }
            }
            if (label != null) {
                sb.append(text, lineStart, i).append(label).append(' ')
                sb.append(text, skip, lineEnd)
                changed = true
            } else {
                sb.append(text, lineStart, lineEnd)
            }
            if (lineEnd < n) sb.append('\n')
            lineStart = lineEnd + 1
        }
        return if (changed) sb.toString() else null
    }

    /** Every [marker]-wrapped run as "marker words end marker", or null when
     *  there is no pair. A marker has to open at a word boundary and close
     *  at one, so multiplication and a lone asterisk are left alone. */
    private fun inlineMarkdown(text: String, marker: String, label: String): String? {
        var search = 0
        var out: StringBuilder? = null
        while (true) {
            val open = text.indexOf(marker, search)
            if (open < 0) break
            // Not inside a word, and not part of a longer run of the same
            // marker (which is the two-character form's own business).
            val before = if (open > 0) text[open - 1] else ' '
            if (before.isLetterOrDigit() || (marker.length == 1 && open > 0 && text[open - 1] == marker[0])) {
                search = open + marker.length
                continue
            }
            val contentStart = open + marker.length
            val close = text.indexOf(marker, contentStart)
            if (close < 0) break
            if (close == contentStart || text[close - 1].isWhitespace() || text[contentStart].isWhitespace()) {
                search = open + marker.length
                continue
            }
            val after = if (close + marker.length < text.length) text[close + marker.length] else ' '
            if (after.isLetterOrDigit()) {
                search = close + marker.length
                continue
            }
            val sb = out ?: StringBuilder(text.length + 32).also { out = it }
            sb.append(text, search, open)
            sb.append(label).append(' ').append(text, contentStart, close).append(" end ").append(label).append(' ')
            search = close + marker.length
        }
        val sb = out ?: return null
        sb.append(text, search, text.length)
        return sb.toString()
    }

    /** Character entity at [at], if there is one: what it stands for, and
     *  just past the semicolon. Deliberately small -- the ones a page
     *  actually writes, and any numeric one. */
    private fun decodeEntity(text: String, at: Int, n: Int): Pair<CharSequence, Int>? {
        if (text[at] != '&') return null
        val semi = text.indexOf(';', at + 1)
        if (semi < 0 || semi - at > 10) return null
        val name = text.substring(at + 1, semi)
        if (name.isEmpty()) return null
        if (name[0] == '#') {
            val code = try {
                if (name.length > 1 && (name[1] == 'x' || name[1] == 'X')) {
                    name.substring(2).toInt(16)
                } else {
                    name.substring(1).toInt()
                }
            } catch (_: Throwable) {
                return null
            }
            if (code <= 0 || code > 0x10FFFF) return null
            val chars = Character.toChars(code)
            return String(chars) to semi + 1
        }
        val plain = when (name) {
            "amp" -> "&"
            "lt" -> "<"
            "gt" -> ">"
            "quot" -> "\""
            "apos" -> "'"
            "nbsp" -> " "
            "ndash" -> "-"
            "mdash" -> "-"
            "hellip" -> "..."
            "rsquo", "lsquo" -> "'"
            "rdquo", "ldquo" -> "\""
            "copy" -> "copyright"
            "reg" -> "registered"
            "trade" -> "trademark"
            "deg" -> "degrees"
            "times" -> "times"
            "divide" -> "divided by"
            else -> return null
        }
        return plain to semi + 1
    }

    /** [text] with every entity decoded, for an attribute value. */
    private fun decodeEntities(text: String, from: Int, to: Int): String {
        if (text.indexOf('&', from) < 0 || text.indexOf('&', from) >= to) return text.substring(from, to)
        val sb = StringBuilder(to - from)
        var i = from
        while (i < to) {
            if (text[i] == '&') {
                val semi = text.indexOf(';', i + 1)
                if (semi in (i + 1) until to) {
                    val decoded = decodeEntity(text, i, to)
                    if (decoded != null && decoded.second <= to) {
                        sb.append(decoded.first)
                        i = decoded.second
                        continue
                    }
                }
            }
            sb.append(text[i])
            i++
        }
        return sb.toString()
    }

    private fun lowered(text: String, from: Int, to: Int): String {
        val sb = StringBuilder(to - from)
        for (i in from until to) {
            val c = text[i]
            sb.append(if (c in 'A'..'Z') (c + 32) else c)
        }
        return sb.toString()
    }

    private fun indexOfIgnoreCase(text: String, needle: String, from: Int): Int {
        val n = text.length - needle.length
        var i = from
        while (i <= n) {
            var k = 0
            while (k < needle.length) {
                if (lowerAscii(text[i + k]) != lowerAscii(needle[k])) break
                k++
            }
            if (k == needle.length) return i
            i++
        }
        return -1
    }

    private fun lowerAscii(c: Char): Char = if (c in 'A'..'Z') c + 32 else c

    /** The colour words a page writes in a font tag, which are already the
     *  words a listener wants. Anything else is left to the style pass. */
    private val NAMED_CSS = setOf("red", "green", "blue", "yellow", "orange", "purple", "pink", "brown", "gray", "grey", "black", "white", "cyan", "magenta")

    private fun foregroundOf(color: Int): String? {
        if (alphaOf(color) < 24) return null
        val name = colorName(color) ?: return null
        // The default ink, on one screen or another, and noise everywhere
        // an app sets its colours explicitly.
        if (name == "black" || name == "white") return null
        return name
    }

    private fun backgroundOf(color: Int): String? {
        if (alphaOf(color) < 24) return null
        val name = colorName(color) ?: return null
        if (name == "white") return null
        return "highlighted $name"
    }

    private fun alphaOf(color: Int): Int = (color ushr 24) and 0xFF

    private class NamedColor(val name: String, val red: Int, val green: Int, val blue: Int)

    private val COLORS = listOf(
        NamedColor("black", 0, 0, 0),
        NamedColor("white", 255, 255, 255),
        NamedColor("red", 255, 0, 0),
        NamedColor("green", 0, 128, 0),
        NamedColor("blue", 0, 0, 255),
        NamedColor("yellow", 255, 255, 0),
        NamedColor("cyan", 0, 255, 255),
        NamedColor("magenta", 255, 0, 255),
        NamedColor("gray", 128, 128, 128),
        NamedColor("orange", 255, 165, 0),
        NamedColor("purple", 128, 0, 128),
        NamedColor("brown", 165, 42, 42),
        NamedColor("pink", 255, 192, 203),
        NamedColor("lime", 0, 255, 0),
        NamedColor("navy", 0, 0, 128),
        NamedColor("teal", 0, 128, 128),
        NamedColor("maroon", 128, 0, 0),
        NamedColor("olive", 128, 128, 0)
    )

    /** The nearest named colour to [color], by squared distance in RGB. */
    private fun colorName(color: Int): String? {
        val red = (color shr 16) and 0xFF
        val green = (color shr 8) and 0xFF
        val blue = color and 0xFF
        var best: NamedColor? = null
        var bestBy = Int.MAX_VALUE
        for (named in COLORS) {
            val dr = red - named.red
            val dg = green - named.green
            val db = blue - named.blue
            val by = dr * dr + dg * dg + db * db
            if (by < bestBy) {
                bestBy = by
                best = named
            }
        }
        return best?.name
    }

    /** Runs of whitespace down to one space, and the ends trimmed. */
    private fun collapse(text: String): String {
        val out = StringBuilder(text.length)
        var spaced = true
        for (c in text) {
            if (c.isWhitespace()) {
                if (!spaced) {
                    out.append(' ')
                    spaced = true
                }
            } else {
                out.append(c)
                spaced = false
            }
        }
        var end = out.length
        while (end > 0 && out[end - 1] == ' ') end--
        return if (end == out.length) out.toString() else out.substring(0, end)
    }
}
