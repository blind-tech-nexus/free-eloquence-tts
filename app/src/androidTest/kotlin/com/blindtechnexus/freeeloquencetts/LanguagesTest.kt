package com.blindtechnexus.freeeloquencetts

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The two languages: English (United States) and English (United Kingdom).
 *
 * This build carries both modules -- `lang/enus` and `lang/engb`, named
 * together in the build script -- and both are offered: on the settings
 * screen, in the voices the service answers with, and in what a caller can
 * ask for by locale. So what is checked here is that the two numbers are the
 * engine's own, that each has a name Android understands and a voice name to
 * go with it, that an instance opens in either, and that the sample-rate
 * table is shared by both.
 */
@RunWith(AndroidJUnit4::class)
class LanguagesTest {

	@Test
	fun theWordsAreTheEnginesOwn() {
		// eciGeneralAmericanEnglish and eciBritishEnglish, which differ in
		// the one bit the dialect is written in.
		assertEquals(0x00010000, Eci.ENGLISH_US)
		assertEquals(0x00010001, Eci.ENGLISH_UK)
	}

	@Test
	fun bothLanguagesAreOfferedWithAFrameworkSpellingAndAVoiceName() {
		assertEquals(2, Eci.LANGUAGES.size)
		assertEquals("eng", Eci.ISO3_LANGUAGE)
		assertEquals("USA", Eci.ISO3_COUNTRY)
		assertEquals("en", Eci.ISO2_LANGUAGE)
		assertEquals(listOf("USA", "GBR"), Eci.LANGUAGES.map { it.iso3Country })
		assertEquals(listOf("US", "GB"), Eci.LANGUAGES.map { it.iso2Country })
		assertEquals("USA", Eci.countryOf(Eci.ENGLISH_US))
		assertEquals("GBR", Eci.countryOf(Eci.ENGLISH_UK))
		// A number no build knows answers with the American one rather than
		// with nothing, so a bad preference is a voice and not a refusal.
		assertEquals("USA", Eci.countryOf(0x0001FFFF))
		assertEquals("eng-USA-Reed", Eci.voiceName(0))
		assertEquals("eng-GBR-Reed", Eci.voiceName(0, Eci.ENGLISH_UK))
	}

	@Test
	fun anInstanceOpensInEither() {
		assertTrue("the native library did not load: ${EvvNative.loadError}", EvvNative.loaded)
		for (language in Eci.LANGUAGES) {
			val e = EvvEngine.open(language.code)
			assertNotNull("${language.label} would not open", e)
			if (e != null) {
				assertEquals("the instance speaks another language", language.code, e.language)
				e.close()
			}
		}
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
