package com.blindtechnexus.freeeloquencetts

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith

/**
 * How a run of digits is regrouped, which is the whole of the number
 * processing: the engine has no setting for it, so the modes are made out of
 * spaces.
 *
 * The rules checked here are the ones [Numbers] states: a run of up to three
 * digits is one number and is never broken up; a number written with
 * thousands separators or with a decimal point is the number whole; a name
 * with digits in it -- mp3, sha256 -- is a word; and everything else long
 * enough to be regrouped is grouped from the **right**, so the leading group
 * is the short one: 1234567 in pairs is "1 23 45 67" and never "12 34 56 7".
 * That is the grouping a person reading a phone number or a year aloud uses,
 * and the one the old left-hand grouping got wrong.
 *
 * Nothing that is not a run of the ten ASCII digits is touched, and a text
 * with nothing to do is answered with the very string it was, which is what
 * keeps the pass out of the way of prose.
 */
@RunWith(AndroidJUnit4::class)
class NumbersTest {

	@Test
	fun asIsLeavesTheNumberAlone() {
		assertEquals("123456", Numbers.apply("123456", Numbers.AS_IS))
		assertEquals("3.14", Numbers.apply("3.14", Numbers.AS_IS))
		assertEquals("The year is 2026", Numbers.apply("The year is 2026", Numbers.AS_IS))
	}

	/** The three examples the modes are described by. */
	@Test
	fun theUsersExamples() {
		assertEquals("9 8 5 2 5 6", Numbers.apply("985256", Numbers.DIGITS))
		assertEquals("98 52 56", Numbers.apply("985256", Numbers.PAIRS))
		assertEquals("985 256", Numbers.apply("985256", Numbers.TRIPLETS))
	}

	@Test
	fun aShortRunIsOneNumberInEveryMode() {
		for (mode in listOf(Numbers.DIGITS, Numbers.PAIRS, Numbers.TRIPLETS)) {
			assertEquals("123", Numbers.apply("123", mode))
			assertEquals("hello 42", Numbers.apply("hello 42", mode))
			assertEquals("3.14", Numbers.apply("3.14", mode))
			assertEquals("times 12:34", Numbers.apply("times 12:34", mode))
		}
	}

	@Test
	fun aTextWithNothingToDoIsTheSameString() {
		val text = "Nothing here but 12 and 3.14 and a word"
		assertSame(text, Numbers.apply(text, Numbers.DIGITS))
		assertSame(text, Numbers.apply(text, Numbers.PAIRS))
		assertSame(text, Numbers.apply(text, Numbers.TRIPLETS))
	}

	@Test
	fun digitsSpaceALongRun() {
		assertEquals("1 2 3 4", Numbers.apply("1234", Numbers.DIGITS))
		assertEquals("1 2 3 4 5 6", Numbers.apply("123456", Numbers.DIGITS))
		assertEquals("call 1 2 3 4 5 6 7 now", Numbers.apply("call 1234567 now", Numbers.DIGITS))
	}

	@Test
	fun aSeparatorMeansTheNumberWholeInEveryMode() {
		for (mode in listOf(Numbers.DIGITS, Numbers.PAIRS, Numbers.TRIPLETS)) {
			assertEquals("10,000", Numbers.apply("10,000", mode))
			assertEquals("1,000,000", Numbers.apply("1,000,000", mode))
			assertEquals("1,234.5", Numbers.apply("1,234.5", mode))
			assertEquals("10.000.000", Numbers.apply("10.000.000", mode))
		}
	}

	/** A separator has to be followed by exactly three digits: a date is two
	 *  numbers and a comma, not a grouped thousand. */
	@Test
	fun aCommaThatIsNotAGroupingIsLeftAlone() {
		assertEquals("September 5, 20 26", Numbers.apply("September 5, 2026", Numbers.PAIRS))
		assertEquals("September 5, 2 0 2 6", Numbers.apply("September 5, 2026", Numbers.DIGITS))
	}

	@Test
	fun aDecimalIsAQuantityWhole() {
		assertEquals("3.1415926", Numbers.apply("3.1415926", Numbers.DIGITS))
		assertEquals("985256.78", Numbers.apply("985256.78", Numbers.DIGITS))
		assertEquals("985256,78", Numbers.apply("985256,78", Numbers.PAIRS))
	}

	/** Grouped from the right, so the short group comes first. */
	@Test
	fun pairsTakeTwoFromTheRight() {
		assertEquals("98 52 56", Numbers.apply("985256", Numbers.PAIRS))
		assertEquals("1 23 45 67", Numbers.apply("1234567", Numbers.PAIRS))
		assertEquals("98 76 54 32 10", Numbers.apply("9876543210", Numbers.PAIRS))
		assertEquals("The year is 20 26", Numbers.apply("The year is 2026", Numbers.PAIRS))
	}

	@Test
	fun tripletsTakeThreeFromTheRight() {
		assertEquals("123 456", Numbers.apply("123456", Numbers.TRIPLETS))
		assertEquals("1 234 567", Numbers.apply("1234567", Numbers.TRIPLETS))
		assertEquals("123 456 789", Numbers.apply("123456789", Numbers.TRIPLETS))
	}

	@Test
	fun aGroupThatOpensWithAZeroIsSaidDigitByDigit() {
		assertEquals("0 4 12", Numbers.apply("0412", Numbers.PAIRS))
		assertEquals("0 1 2 345", Numbers.apply("012345", Numbers.TRIPLETS))
	}

	/** A dash or a dot between letters and digits does not make a word, but a
	 *  letter directly touching the run does. */
	@Test
	fun aNameWithDigitsInItIsAWord() {
		for (mode in listOf(Numbers.DIGITS, Numbers.PAIRS, Numbers.TRIPLETS)) {
			assertEquals("mp3 and sha256", Numbers.apply("mp3 and sha256", mode))
			assertEquals("utf8", Numbers.apply("utf8", mode))
		}
	}

	@Test
	fun punctuationSplitsTheRuns() {
		assertEquals("12:34", Numbers.apply("12:34", Numbers.PAIRS))
		assertEquals("1 2 3 4 5 6:78", Numbers.apply("123456:78", Numbers.DIGITS))
		assertEquals("1 2 3 4-5 6 7 8", Numbers.apply("1234-5678", Numbers.DIGITS))
	}

	@Test
	fun theDiallingPlusIsKept() {
		assertEquals("+ 911", Numbers.apply("+911", Numbers.DIGITS))
		assertEquals("+ 9 77 98 41", Numbers.apply("+9779841", Numbers.PAIRS))
	}

	@Test
	fun anAnnotationIsCarriedThrough() {
		assertEquals(
			"`vbst+6 A `vbst-6 1 2 3 4 5 6",
			Numbers.apply("`vbst+6 A `vbst-6 123456", Numbers.DIGITS)
		)
	}

	@Test
	fun anUnknownModeIsAsIs() {
		assertEquals("123456", Numbers.apply("123456", 42))
		assertEquals(Numbers.AS_IS, Numbers.clamp(-1))
	}

	@Test
	fun theWordsAroundARunAreLeftAlone() {
		assertEquals("page 1 2 3 4 of 900", Numbers.apply("page 1234 of 900", Numbers.DIGITS))
		assertFalse("a short number was broken up", Numbers.apply("page 12 of 900", Numbers.DIGITS).contains("1 2"))
	}
}
