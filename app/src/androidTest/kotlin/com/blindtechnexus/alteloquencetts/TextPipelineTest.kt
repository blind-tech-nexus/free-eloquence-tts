package com.blindtechnexus.alteloquencetts

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The one road all text takes, in the order the engine must see it.
 *
 * Each piece has a test of its own; what is checked here is the order, which
 * is the thing a test of each piece in isolation would miss. The fixes run
 * before the numbers, so a grouped thousand is one run again before it is
 * regrouped; the capital annotation is added after the fixes, so nothing
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
	fun theThousandsCommaGoesBeforeTheRegrouping() {
		assertEquals("1 0 0 0 0 0 0", apply("1,000,000", numbers = Numbers.DIGITS))
	}

	@Test
	fun theCapitalAnnotationSurvivesTheSymbols() {
		assertEquals(
			"`vbst+6 A `vbst-6",
			apply("A", capitals = CapitalLetters.PITCH, symbols = Symbols.ALL)
		)
	}

	@Test
	fun plainTextComesThroughTheWholeRoad() {
		assertEquals("Hello from Eloquence.", apply("Hello from Eloquence."))
	}

	private fun apply(
		text: String,
		ssml: Boolean = true,
		emoji: Boolean = true,
		numbers: Int = Numbers.AS_IS,
		capitals: Int = CapitalLetters.NONE,
		symbols: Int = Symbols.SOME,
		custom: String = ""
	): String {
		val context = InstrumentationRegistry.getInstrumentation().targetContext
		val settings = Settings(context)
		settings.stripSsml = ssml
		settings.readEmoji = emoji
		settings.numberMode = numbers
		settings.capitalMode = capitals
		settings.symbolMode = symbols
		settings.customSymbols = custom
		return TextPipeline.apply(text, settings)
	}
}
