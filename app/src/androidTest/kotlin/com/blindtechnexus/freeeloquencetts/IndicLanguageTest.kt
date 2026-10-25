package com.blindtechnexus.freeeloquencetts

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class IndicLanguageTest {

	@Test
	fun singleLettersStayEnglish() {
		val segsI = LanguageDetector.split("I", Eci.HINDI)
		assertEquals(1, segsI.size)
		assertEquals(Eci.ENGLISH_US, segsI[0].language)

		val segsE = LanguageDetector.split("e", Eci.NEPALI)
		assertEquals(1, segsE.size)
		assertEquals(Eci.ENGLISH_US, segsE[0].language)

		val segsO = LanguageDetector.split("o", Eci.ENGLISH_US)
		assertEquals(1, segsO.size)
		assertEquals(Eci.ENGLISH_US, segsO[0].language)
	}

	@Test
	fun commonEnglishUiWordsStayEnglish() {
		val words = listOf("message", "launcher", "dictation", "voice", "AI", "Microsoft Launcher", "Settings")
		for (w in words) {
			val segs = LanguageDetector.split(w, Eci.NEPALI)
			for (s in segs) {
				if (s.text.isNotBlank()) {
					assertEquals("Word '$w' should be English, got ${s.language}", Eci.ENGLISH_US, s.language)
				}
			}
		}
	}

	@Test
	fun devanagariDistinguishesNepaliAndHindi() {
		// Pure Nepali text with strong Nepali lexical markers (छ, हो, राम्रो, सन्देश)
		val nepaliText = "यो राम्रो सन्देश हो"
		val nepaliSegs = LanguageDetector.split(nepaliText, Eci.HINDI)
		assertTrue(nepaliSegs.any { it.language == Eci.NEPALI })

		// Pure Hindi text with strong Hindi lexical markers (है, अच्छा, संदेश)
		val hindiText = "यह अच्छा संदेश है"
		val hindiSegs = LanguageDetector.split(hindiText, Eci.NEPALI)
		assertTrue(hindiSegs.any { it.language == Eci.HINDI })
	}

	@Test
	fun mixedScriptSplitsCorrectly() {
		// Mixed Nepali and English sentence:
		// "यो Microsoft Launcher को voice AI message हो"
		val mixed = "यो Microsoft Launcher को voice AI message हो"
		val segs = LanguageDetector.split(mixed, Eci.NEPALI)
		assertTrue(segs.size >= 3)
		val englishParts = segs.filter { it.language == Eci.ENGLISH_US }.map { it.text.trim() }
		assertTrue(englishParts.any { it.contains("Launcher") || it.contains("Microsoft") })
		assertTrue(englishParts.any { it.contains("message") || it.contains("voice") })
		val nepaliParts = segs.filter { it.language == Eci.NEPALI }.map { it.text.trim() }
		assertTrue(nepaliParts.any { it.contains("यो") })
	}

	@Test
	fun indicDatesAndTimesFormatProperly() {
		assertEquals("मीटिंग २५ अक्टूबर २०२६ को है", IndicText.apply("मीटिंग 25/10/2026 को है", Eci.HINDI))
		assertEquals("बैठक २५ अक्टोबर २०२६ मा छ", IndicText.apply("बैठक 25/10/2026 मा छ", Eci.NEPALI))
		assertEquals("१४ बजकर ३० मिनट", IndicText.apply("14:30", Eci.HINDI))
		assertEquals("१४ बजेर ३० मिनेट", IndicText.apply("14:30", Eci.NEPALI))
		assertEquals("९ बजे", IndicText.apply("9:00", Eci.HINDI))
		assertEquals("९ बजे", IndicText.apply("9:00", Eci.NEPALI))
	}

	@Test
	fun indicAbbreviationsExpandProperly() {
		val abbrevNe = IndicText.apply("वि.सं. २०८१", Eci.NEPALI)
		assertTrue(abbrevNe.contains("विक्रम संवत्"))

		val docNe = IndicText.apply("डा. श्रेष्ठ", Eci.NEPALI)
		assertTrue(docNe.contains("डाक्टर"))
	}

	@Test
	fun indicNumberToWords() {
		assertEquals("शून्य", IndicText.numberToWords(0, Eci.NEPALI))
		assertEquals("एक", IndicText.numberToWords(1, Eci.NEPALI))
		assertEquals("दश", IndicText.numberToWords(10, Eci.NEPALI))
		assertEquals("एघार", IndicText.numberToWords(11, Eci.NEPALI))
		assertEquals("उनान्सय", IndicText.numberToWords(99, Eci.NEPALI))
		assertEquals("एक सय", IndicText.numberToWords(100, Eci.NEPALI))

		assertEquals("शून्य", IndicText.numberToWords(0, Eci.HINDI))
		assertEquals("एक", IndicText.numberToWords(1, Eci.HINDI))
		assertEquals("दस", IndicText.numberToWords(10, Eci.HINDI))
		assertEquals("ग्यारह", IndicText.numberToWords(11, Eci.HINDI))
		assertEquals("निन्यानवे", IndicText.numberToWords(99, Eci.HINDI))
		assertEquals("एक सौ", IndicText.numberToWords(100, Eci.HINDI))
	}
}
