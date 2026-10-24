package com.blindtechnexus.freeeloquencetts

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith

/**
 * How a run of digits is regrouped, which is the whole of the number
 * processing: the engine has no setting for it, so the modes are made out of
 * spaces. What is checked is the strong rule -- a run of up to five digits
 * is one number, a decimal point or a correctly written separator means the
 * number whole, and a run of six or more is aligned from the right -- plus the
 * one repair, the comma-grouped number whose last group is zeros, which is
 * the shape this engine drops a million from.
 */
@RunWith(AndroidJUnit4::class)
class NumbersTest {

	@Test
	fun asIsLeavesTheNumberAlone() {
		assertEquals("123456", Numbers.apply("123456", Numbers.AS_IS))
		assertEquals("3.14", Numbers.apply("3.14", Numbers.AS_IS))
	}

	@Test
	fun theUsersExamples() {
		assertEquals("9 8 5 2 5 6", Numbers.apply("985256", Numbers.DIGITS))
		assertEquals("98 52 56", Numbers.apply("985256", Numbers.PAIRS))
		assertEquals("985 256", Numbers.apply("985256", Numbers.TRIPLETS))
	}

	@Test
	fun aShortRunIsOneNumberInEveryMode() {
		for (mode in listOf(Numbers.DIGITS, Numbers.PAIRS, Numbers.TRIPLETS)) {
			assertEquals("1234", Numbers.apply("1234", mode))
			assertEquals("12345", Numbers.apply("12345", mode))
			assertEquals("hello 42", Numbers.apply("hello 42", mode))
			assertEquals("The year is 2026", Numbers.apply("The year is 2026", mode))
			assertEquals("1500", Numbers.apply("1500", mode))
			assertEquals("3.14", Numbers.apply("3.14", mode))
		}
	}

	@Test
	fun aTextWithNothingToDoIsTheSameString() {
		val text = "Nothing here but 2026 and 12345 and 3.14"
		assertSame(text, Numbers.apply(text, Numbers.DIGITS))
	}

	@Test
	fun digitsSpaceALongRun() {
		assertEquals("1 2 3 4 5 6", Numbers.apply("123456", Numbers.DIGITS))
		assertEquals("call 1 2 3 4 5 6 7 now", Numbers.apply("call 1234567 now", Numbers.DIGITS))
	}

	@Test
	fun aSeparatorTheEngineReadsCorrectlyIsLeftAlone() {
		assertEquals("10,000", Numbers.apply("10,000", Numbers.DIGITS))
		// Two groups whose last one is not zeros read correctly too.
		assertEquals("1,150", Numbers.apply("1,150", Numbers.DIGITS))
		assertEquals("205,558,107", Numbers.apply("205,558,107", Numbers.DIGITS))
		assertEquals("10.000.000", Numbers.apply("10.000.000", Numbers.DIGITS))
		assertEquals("1,234.5", Numbers.apply("1,234.5", Numbers.DIGITS))
		assertEquals("September 5, 2026", Numbers.apply("September 5, 2026", Numbers.DIGITS))
	}

	@Test
	fun theShapeTheEngineMisreadsIsRepaired() {
		// "1,000,000" comes out of the engine with the million missing, and
		// the same digits without the separators do not. So the separators
		// go, whatever the mode -- the number is the same number either
		// way, and this is the only spelling of it the engine reads right.
		assertEquals("1000000", Numbers.apply("1,000,000", Numbers.AS_IS))
		assertEquals("1 0 0 0 0 0 0", Numbers.apply("1,000,000", Numbers.DIGITS))
		assertEquals("1 0 0 0 0 0 0", Numbers.apply("1,000,000", Numbers.PAIRS))
		assertEquals("1 0 0 0 0 0 0", Numbers.apply("1,000,000", Numbers.TRIPLETS))
		assertEquals("\$205000000", Numbers.apply("\$205,000,000", Numbers.AS_IS))
		assertEquals("1000000.5", Numbers.apply("1,000,000.5", Numbers.AS_IS))
		assertEquals("1000000", Numbers.apply("1,000,000", 42))
	}

	@Test
	fun aDecimalIsAQuantityWhole() {
		assertEquals("3.1415926", Numbers.apply("3.1415926", Numbers.DIGITS))
		assertEquals("985256.78", Numbers.apply("985256.78", Numbers.DIGITS))
		assertEquals("985256,78", Numbers.apply("985256,78", Numbers.PAIRS))
	}

	@Test
	fun pairsTakeTwoFromTheLeft() {
		assertEquals("12 34 56", Numbers.apply("123456", Numbers.PAIRS))
		// Odd length keeps the short group at the front, aligned like place values.
		assertEquals("1 23 45 67", Numbers.apply("1234567", Numbers.PAIRS))
		assertEquals("98 76 54 32 10", Numbers.apply("9876543210", Numbers.PAIRS))
	}

	@Test
	fun tripletsTakeThreeFromTheLeft() {
		assertEquals("123 456", Numbers.apply("123456", Numbers.TRIPLETS))
		assertEquals("1 234 567", Numbers.apply("1234567", Numbers.TRIPLETS))
		assertEquals("123 456 789", Numbers.apply("123456789", Numbers.TRIPLETS))
	}

	@Test
	fun aGroupThatOpensWithAZeroIsSaidDigitByDigit() {
		assertEquals("12 0 4 56", Numbers.apply("120456", Numbers.PAIRS))
		assertEquals("0 1 2 345", Numbers.apply("012345", Numbers.TRIPLETS))
	}

	@Test
	fun punctuationSplitsTheRuns() {
		assertEquals("12:34", Numbers.apply("12:34", Numbers.PAIRS))
		assertEquals("1 2 3 4 5 6:78", Numbers.apply("123456:78", Numbers.DIGITS))
	}

	@Test
	fun theDiallingPlusIsKept() {
		assertEquals("+ 911", Numbers.apply("+911", Numbers.DIGITS))
		assertEquals("+ 9 7 7 9 8 4 1", Numbers.apply("+9779841", Numbers.DIGITS))
	}

	@Test
	fun anAnnotationIsCarriedThrough() {
		assertEquals("`vbst+6 A `vbst-6 1 2 3 4 5 6", Numbers.apply("`vbst+6 A `vbst-6 123456", Numbers.DIGITS))
	}

	@Test
	fun anUnknownModeIsAsIs() {
		assertEquals("123456", Numbers.apply("123456", 42))
		assertEquals(Numbers.AS_IS, Numbers.clamp(-1))
	}

	@Test
	fun theWordsAroundARunAreLeftAlone() {
		assertEquals("The year is 2026", Numbers.apply("The year is 2026", Numbers.DIGITS))
		assertEquals("call 12", Numbers.apply("call 12", Numbers.TRIPLETS))
		assertEquals("id 1 2 3 4 5 6 done", Numbers.apply("id 123456 done", Numbers.DIGITS))
	}
}
