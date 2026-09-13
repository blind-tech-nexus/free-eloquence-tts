package com.blindtechnexus.freeeloquencetts

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The one language: English (United States).
 *
 * This build carries exactly one language module and the app offers no choice
 * anywhere -- not on the settings screen, not in the stored preferences, and
 * not in what the service asks the engine for. So what is checked here is
 * small on purpose: the word is the engine's own, the spelling is the one
 * Android asks about, an instance opens in it, and the sample-rate table it
 * is synthesised at round-trips.
 */
@RunWith(AndroidJUnit4::class)
class LanguagesTest {

	@Test
	fun theWordIsTheEnginesOwn() {
		assertEquals(0x00010000, Eci.ENGLISH_US)
	}

	@Test
	fun theFrameworkSpellingIsEngUsa() {
		assertEquals("eng", Eci.ISO3_LANGUAGE)
		assertEquals("USA", Eci.ISO3_COUNTRY)
	}

	@Test
	fun anInstanceOpensInIt() {
		assertTrue("the native library did not load: ${EvvNative.loadError}", EvvNative.loaded)
		val e = EvvEngine.open(Eci.ENGLISH_US)
		assertNotNull("US English would not open", e)
		e?.close()
	}

	@Test
	fun everySampleRateRoundTrips() {
		for (hz in Eci.SAMPLE_RATES) {
			assertEquals(hz, Eci.sampleRateHz(Eci.sampleRateIndex(hz)))
		}
	}

	@Test
	fun anUnknownRateFallsBackTo11025() {
		assertEquals(11025, Eci.sampleRateHz(99))
		assertEquals(1, Eci.sampleRateIndex(12345))
	}
}
