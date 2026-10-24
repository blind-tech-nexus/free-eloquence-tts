package com.blindtechnexus.freeeloquencetts

import android.media.AudioAttributes
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Where the sound goes, as a table rather than as a sound.
 *
 * The routing screen offers one row per route and the service asks three
 * questions of the choice: does this app play the speech itself, is the asking
 * app handed the samples as well, and which Android usage does the app's own
 * player carry. Each of those is answered in one place -- [SynthesisRouting] --
 * so what is checked here is the table: that every choice is the choice it
 * says it is, that the default is the only one that plays nothing itself, that
 * only the default and *Accessibility and the app* hand the samples on, and
 * that every route has a usage and a sentence of its own.
 *
 * The values are compared with the platform's own constants rather than with
 * numbers, because the numbers are the platform's business and the constants
 * are the promise.
 */
@RunWith(AndroidJUnit4::class)
class SynthesisRoutingTest {

	@Test
	fun everyChoiceIsItself() {
		for (route in SynthesisRouting.CHOICES) {
			assertEquals("a route was not itself: $route", route, SynthesisRouting.clamp(route))
		}
		assertEquals(
			"a route was offered twice",
			SynthesisRouting.CHOICES.size,
			SynthesisRouting.CHOICES.toSet().size
		)
	}

	@Test
	fun aBadRouteFallsBackToTheDefault() {
		assertEquals(SynthesisRouting.AUTOMATIC, SynthesisRouting.clamp(-1))
		assertEquals(SynthesisRouting.AUTOMATIC, SynthesisRouting.clamp(999))
	}

	/** The default is the only route where this app does not play anything:
	 *  the samples are the framework's and the asking app's, as they are for
	 *  every other engine. */
	@Test
	fun onlyTheDefaultLeavesTheSoundToTheFramework() {
		assertFalse(SynthesisRouting.playsItself(SynthesisRouting.AUTOMATIC))
		assertTrue(SynthesisRouting.handsToFramework(SynthesisRouting.AUTOMATIC))
		for (route in SynthesisRouting.CHOICES) {
			if (route == SynthesisRouting.AUTOMATIC) continue
			assertTrue("did not play itself: $route", SynthesisRouting.playsItself(route))
		}
	}

	/** *Accessibility and the app* is the one route that does both. */
	@Test
	fun bothPlaysAndHandsOn() {
		assertTrue(SynthesisRouting.playsItself(SynthesisRouting.ALL))
		assertTrue(SynthesisRouting.handsToFramework(SynthesisRouting.ALL))
		for (route in SynthesisRouting.CHOICES) {
			if (route == SynthesisRouting.AUTOMATIC || route == SynthesisRouting.ALL) continue
			assertFalse("handed on as well: $route", SynthesisRouting.handsToFramework(route))
		}
	}

	@Test
	fun eachRouteCarriesItsOwnUsage() {
		assertEquals(
			AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY,
			SynthesisRouting.usageOf(SynthesisRouting.ACCESSIBILITY)
		)
		assertEquals(
			AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY,
			SynthesisRouting.usageOf(SynthesisRouting.ALL)
		)
		assertEquals(AudioAttributes.USAGE_MEDIA, SynthesisRouting.usageOf(SynthesisRouting.MEDIA))
		assertEquals(AudioAttributes.USAGE_ALARM, SynthesisRouting.usageOf(SynthesisRouting.ALARMS))
		assertEquals(
			AudioAttributes.USAGE_NOTIFICATION,
			SynthesisRouting.usageOf(SynthesisRouting.NOTIFICATION)
		)
		assertEquals(
			AudioAttributes.USAGE_NOTIFICATION_EVENT,
			SynthesisRouting.usageOf(SynthesisRouting.NOTIFICATION_EVENT)
		)
		assertEquals(
			AudioAttributes.USAGE_NOTIFICATION_RINGTONE,
			SynthesisRouting.usageOf(SynthesisRouting.RINGTONE)
		)
		assertEquals(
			AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE,
			SynthesisRouting.usageOf(SynthesisRouting.NAVIGATION)
		)
		assertEquals(
			AudioAttributes.USAGE_ASSISTANCE_SONIFICATION,
			SynthesisRouting.usageOf(SynthesisRouting.SONIFICATION)
		)
		assertEquals(
			AudioAttributes.USAGE_VOICE_COMMUNICATION,
			SynthesisRouting.usageOf(SynthesisRouting.VOICE_COMMUNICATION)
		)
		assertEquals(AudioAttributes.USAGE_GAME, SynthesisRouting.usageOf(SynthesisRouting.GAME))
	}

	@Test
	fun everyRouteHasASentence() {
		for (route in SynthesisRouting.CHOICES) {
			assertTrue("no description for $route", SynthesisRouting.describe(route).isNotBlank())
		}
	}
}
