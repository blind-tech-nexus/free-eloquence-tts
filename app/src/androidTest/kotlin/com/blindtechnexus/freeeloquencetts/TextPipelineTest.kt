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
		assertEquals("a c", apply("a <b> c", ssml = true, styles = false))
		assertEquals("a <b> c", apply("a <b> c", ssml = false, styles = false))
	}

	@Test
	fun markupIsReadOutBeforeTheTagsAreStripped() {
		// With styles on, a tag is a word and not a bracket: stripping it
		// first is what used to lose the emphasis a page carried.
		assertEquals("hello bold sale end bold world", apply("hello <b>sale</b> world"))
		assertEquals("hello sale world", apply("hello <b>sale</b> world", styles = false))
	}

	@Test
	fun anEmojiIsNamedBeforeTheFixesSeeIt() {
		assertEquals("grinning face", apply("😀", emoji = true))
		assertEquals("", apply("😀", emoji = false))
	}

	@Test
	fun aGroupedThousandIsReadWhole() {
		// The shapes the engine reads correctly are left as written...
		assertEquals("10,000", apply("10,000", numbers = Numbers.PAIRS))
		assertEquals("1,150", apply("1,150", numbers = Numbers.DIGITS))
		// ... and the one it drops a million from is repaired, whatever the
		// mode, because the repair is a fault fix and not a preference.
		assertEquals("1 0 0 0 0 0 0", apply("1,000,000", numbers = Numbers.DIGITS))
		assertEquals("1000000", apply("1,000,000", numbers = Numbers.AS_IS))
	}

	@Test
	fun theCapitalAnnotationSurvivesTheSymbols() {
		assertEquals(
			"`vbst+6 A `vbst-6",
			apply("A", capitals = CapitalLetters.PITCH, symbols = Symbols.ALL)
		)
	}

	@Test
	fun aCommaStandingOnItsOwnIsNotSaid() {
		// "Some" punctuation is the engine's own reading with one repair: a
		// comma with no word against it would be named by the engine, and the
		// word "comma" is never what the writer meant.
		assertEquals("This is everything", apply("This is , everything"))
		assertEquals("Hello, world.", apply("Hello, world."))
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
		styles: Boolean = true,
		words: List<Pair<String, String>> = emptyList()
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
		return TextPipeline.apply(text, settings, words)
	}
}
