package com.blindtechnexus.freeeloquencetts

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Dates and times in a Hindi or Nepali segment, said as words.
 *
 * The native voices read bare numbers themselves; what they cannot know
 * is that `25/10/2026` is a date. [IndicText] settles that shape before
 * the text reaches them, and leaves everything else -- versions, ratios,
 * impossible dates -- exactly as written.
 */
@RunWith(AndroidJUnit4::class)
class IndicTextTest {

	@Test
	fun aDateIsSaidAsDayMonthYear() {
		assertEquals(
			"मीटिंग २५ अक्टूबर २०२६ को है",
			IndicText.apply("मीटिंग 25/10/2026 को है", Eci.HINDI)
		)
		assertEquals(
			"बैठक २५ अक्टोबर २०२६ मा छ",
			IndicText.apply("बैठक 25/10/2026 मा छ", Eci.NEPALI)
		)
		assertEquals(
			"२५ अक्टूबर २०२६",
			IndicText.apply("2026-10-25", Eci.HINDI)
		)
	}

	@Test
	fun aTimeIsSaidAsATimeOfDay() {
		assertEquals("१४ बजकर ३० मिनट", IndicText.apply("14:30", Eci.HINDI))
		assertEquals("१४ बजेर ३० मिनेट", IndicText.apply("14:30", Eci.NEPALI))
		assertEquals("९ बजे", IndicText.apply("9:00", Eci.HINDI))
		assertEquals("९ बजे", IndicText.apply("9:00", Eci.NEPALI))
	}

	@Test
	fun shapesThatAreNotDatesOrTimesAreLeftAlone() {
		// A version number, a ratio, an impossible date and a timestamp's
		// seconds are none of them a date or a time of day.
		for (text in listOf("2.5.1", "3:2", "40/13/2026", "14:30:56", "25-10", "no digits")) {
			assertEquals(text, IndicText.apply(text, Eci.HINDI))
			assertEquals(text, IndicText.apply(text, Eci.NEPALI))
		}
	}

	@Test
	fun otherLanguagesAreUntouched() {
		val text = "Meeting 25/10/2026 at 14:30"
		assertSame(text, IndicText.apply(text, Eci.ENGLISH_US))
		assertSame(text, IndicText.apply(text, Eci.GERMAN))
	}

	@Test
	fun plainIndicTextIsTheSameString() {
		val text = "नमस्ते, तपाईं कस्तो हुनुहुन्छ?"
		assertSame(text, IndicText.apply(text, Eci.NEPALI))
	}
}
