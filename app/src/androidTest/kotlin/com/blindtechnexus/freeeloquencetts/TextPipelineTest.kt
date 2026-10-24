package com.blindtechnexus.freeeloquencetts

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The one road all text takes, in the order the engine must see it.
 *
 * Each piece has a test of its own; what is checked here is the order, which
 * is the thing a test of each piece in isolation would miss. The fixes keep a
 * grouped thousand grouped, so the regrouping sees the comma and reads the
 * number whole; the capital annotation is added after the fixes, so nothing
 * walks into it; and the symbols run last, which is why a backtick annotation
 * survives them.
 */
@RunWith(AndroidJUnit4::class)
class TextPipelineTest {

	@Test
	fun ssmlComesOffBeforeAnythingElse() {
		assertEquals("a c", apply("a <b> c", ssml = true))
		assertEquals("a <b> c", apply("a <b> c", ssml = false))
	}

	@Test
	fun anEmojiIsNamedBeforeTheFixesSeeIt() {
		assertEquals("grinning face", apply("😀", emoji = true))
		assertEquals("", apply("😀", emoji = false))
	}

	@Test
	fun aGroupedThousandIsReadWhole() {
		assertEquals("1,000,000", apply("1,000,000", numbers = Numbers.DIGITS))
		assertEquals("10,000", apply("10,000", numbers = Numbers.PAIRS))
	}

	@Test
	fun theCapitalAnnotationSurvivesTheSymbols() {
		assertEquals(
			"`vbst+6 A `vbst-6",
			apply("A", capitals = CapitalLetters.PITCH, symbols = Symbols.ALL)
		)
	}

	/** A page's markup is read before the SSML pass can take it away, which
	 *  is what makes "read text styles" work on an article a browser handed
	 *  over as characters rather than as spans. */
	@Test
	fun theMarkupAPageCarriesIsReadBeforeItIsStripped() {
		assertEquals("bold Bold end bold", apply("<b>Bold</b>", styles = true))
		assertEquals("Bold", apply("<b>Bold</b>", styles = false))
		assertEquals("heading Title end heading", apply("<h1>Title</h1>", styles = true))
		assertEquals("image a red bicycle here", apply("<img alt=\"a red bicycle\"> here", styles = true))
		assertEquals("AT&T", apply("AT&amp;T", styles = true))
		assertEquals("bold a word end bold", apply("a **word**", styles = true))
	}

	/** A comment is a comment and a pointer is a pointer: in code mode
	 *  nothing reads a leading hash as a heading or a pair of asterisks as
	 *  emphasis, however much they look like markdown. The code pass names
	 *  them, which is what a reader of code asked for. */
	@Test
	fun codeIsNotReadAsMarkdown() {
		assertEquals("hash not a heading", apply("# not a heading", styles = true, code = true))
		assertEquals("a star b star c", apply("a *b* c", styles = true, code = true))
	}

	@Test
	fun addressesAndPathsAreSaidAsThemselves() {
		assertEquals("See url example dot com.", apply("See https://example.com.", code = true))
		assertEquals(
			"email user at example dot com",
			apply("mailto:user@example.com", code = true)
		)
		assertEquals(
			"path home slash user slash notes dot txt",
			apply("/home/user/notes.txt", code = true)
		)
	}

	@Test
	fun plainTextComesThroughTheWholeRoad() {
		assertEquals("Hello from Eloquence.", apply("Hello from Eloquence."))
	}

	@Test
	fun aDictionaryWordIsReplacedBeforeTheFixes() {
		assertEquals(
			"dee oe jeigh",
			apply("doj", words = listOf("DOJ" to "dee oe jeigh"))
		)
	}

	private fun apply(
		text: String,
		ssml: Boolean = true,
		emoji: Boolean = true,
		numbers: Int = Numbers.AS_IS,
		capitals: Int = CapitalLetters.NONE,
		symbols: Int = Symbols.SOME,
		custom: String = "",
		words: List<Pair<String, String>> = emptyList(),
		styles: Boolean = true,
		code: Boolean = false
	): String {
		val context = InstrumentationRegistry.getInstrumentation().targetContext
		val settings = Settings(context)
		settings.stripSsml = ssml
		settings.readEmoji = emoji
		settings.numberMode = numbers
		settings.capitalMode = capitals
		settings.symbolMode = symbols
		settings.customSymbols = custom
		settings.readTextStyles = styles
		settings.codeMode = code
		return TextPipeline.apply(text, settings, words)
	}
}
