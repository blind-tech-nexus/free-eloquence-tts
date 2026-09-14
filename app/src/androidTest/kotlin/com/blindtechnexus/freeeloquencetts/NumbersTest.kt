package com.blindtechnexus.freeeloquencetts

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * How a run of digits is regrouped, which is the whole of the number
 * processing: the engine has no setting for it, so the modes are made out of
 * spaces. What is checked is the strong rule -- a run of up to four digits
 * is one number, a comma inside a number means the number whole, and anything
 * longer is grouped from the left -- and that anything that is not a run of
 * the ten digits is left alone.
 */
@RunWith(AndroidJUnit4::class)
class NumbersTest {

	@Test
	fun asIsLeavesTheNumberAlone() {
		assertEquals("123456", Numbers.apply("123456", Numbers.AS_IS))
		assertEquals("3.14", Numbers.apply("3.14", Numbers.AS_IS))
	}

	@Test
	fun digitsKeepAShortRunWhole() {
		assertEquals("1234", Numbers.apply("1234", Numbers.DIGITS))
		assertEquals("hello 42", Numbers.apply("hello 42", Numbers.DIGITS))
		assertEquals("The year is 2026", Numbers.apply("The year is 2026", Numbers.DIGITS))
		assertEquals("3.14", Numbers.apply("3.14", Numbers.DIGITS))
	}

	@Test
	fun digitsSpaceALongRun() {
		assertEquals("1 2 3 4 5", Numbers.apply("12345", Numbers.DIGITS))
		assertEquals("call 1 2 3 4 5 6 now", Numbers.apply("call 123456 now", Numbers.DIGITS))
	}

	@Test
	fun aCommaMeansTheNumberWholeInEveryMode() {
		assertEquals("10,000", Numbers.apply("10,000", Numbers.DIGITS))
		assertEquals("1,000,000", Numbers.apply("1,000,000", Numbers.DIGITS))
		assertEquals("10,000", Numbers.apply("10,000", Numbers.PAIRS))
		assertEquals("1,000,000", Numbers.apply("1,000,000", Numbers.TRIPLETS))
		assertEquals("September 5, 2026", Numbers.apply("September 5, 2026", Numbers.DIGITS))
	}

	@Test
	fun pairsTakeTwoFromTheLeft() {
		assertEquals("12 34 56", Numbers.apply("123456", Numbers.PAIRS))
		// Odd length keeps short group at end (phone style left grouping)
		assertEquals("12 34 5", Numbers.apply("12345", Numbers.PAIRS))
		assertEquals("20 26", Numbers.apply("2026", Numbers.PAIRS))
		assertEquals("15 0 0", Numbers.apply("1500", Numbers.PAIRS))
	}

	@Test
	fun tripletsTakeThreeFromTheLeft() {
		assertEquals("123 456", Numbers.apply("123456", Numbers.TRIPLETS))
		assertEquals("123 4", Numbers.apply("1234", Numbers.TRIPLETS))
		assertEquals("123 456 789", Numbers.apply("123456789", Numbers.TRIPLETS))
	}

	/** A decimal point splits a number into runs, and each run is grouped on
	 *  its own. The punctuation itself is Symbols' business, not this one's. */
	@Test
	fun punctuationSplitsTheRuns() {
		assertEquals("3.14", Numbers.apply("3.14", Numbers.DIGITS))
		assertEquals("12:34", Numbers.apply("12:34", Numbers.PAIRS))
	}

	/** A plus before digits is the dialling kind and keeps a space after it,
	 *  with the run grouped like any other. */
	@Test
	fun theDiallingPlusIsKept() {
		assertEquals("+ 911", Numbers.apply("+911", Numbers.DIGITS))
		assertEquals("+ 1 2 3 4 5", Numbers.apply("+12345", Numbers.DIGITS))
	}

	@Test
	fun theWordsAroundARunAreLeftAlone() {
		// Only the run itself changes; the words either side of it come
		// through as they were written.
		assertEquals("The year is 2026", Numbers.apply("The year is 2026", Numbers.DIGITS))
		assertEquals("call 12", Numbers.apply("call 12", Numbers.TRIPLETS))
	}
}
