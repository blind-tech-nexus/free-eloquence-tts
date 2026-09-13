package com.blindtechnexus.freeeloquencetts

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * How a run of digits is regrouped, which is the whole of the number
 * processing: the engine has no setting for it, so the modes are made out of
 * spaces. What is checked is that the right groups come out, taken from the
 * right, and that anything that is not a run of the ten digits is left alone.
 */
@RunWith(AndroidJUnit4::class)
class NumbersTest {

	@Test
	fun asIsLeavesTheNumberAlone() {
		assertEquals("123456", Numbers.apply("123456", Numbers.AS_IS))
		assertEquals("3.14", Numbers.apply("3.14", Numbers.AS_IS))
	}

	@Test
	fun digitsSpacesEveryDigit() {
		assertEquals("1 2 3 4", Numbers.apply("1234", Numbers.DIGITS))
		assertEquals("hello 4 2", Numbers.apply("hello 42", Numbers.DIGITS))
	}

	@Test
	fun pairsTakeTwoFromTheRight() {
		assertEquals("12 34 56", Numbers.apply("123456", Numbers.PAIRS))
		// Odd length keeps short group at end (phone style left grouping)
		assertEquals("12 34 5", Numbers.apply("12345", Numbers.PAIRS))
	}

	@Test
	fun tripletsTakeThreeFromTheRight() {
		assertEquals("123 456", Numbers.apply("123456", Numbers.TRIPLETS))
		assertEquals("123 4", Numbers.apply("1234", Numbers.TRIPLETS))
		assertEquals("123 456 789", Numbers.apply("123456789", Numbers.TRIPLETS))
	}

	/** A decimal point splits a number into runs, and each run is regrouped on
	 *  its own. The punctuation itself is Symbols' business, not this one's. */
	@Test
	fun punctuationSplitsTheRuns() {
		assertEquals("3.1 4", Numbers.apply("3.14", Numbers.DIGITS))
		assertEquals("12:34", Numbers.apply("12:34", Numbers.PAIRS))
	}

	@Test
	fun theWordsAroundARunAreLeftAlone() {
		// Only the run itself changes; the words either side of it come
		// through as they were written.
		assertEquals("The year is 2 0 2 6", Numbers.apply("The year is 2026", Numbers.DIGITS))
		assertEquals("call 12", Numbers.apply("call 12", Numbers.TRIPLETS))
	}
}
