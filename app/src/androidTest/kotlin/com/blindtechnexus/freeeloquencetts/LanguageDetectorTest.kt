package com.blindtechnexus.freeeloquencetts

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The automatic language switcher's detector.
 *
 * Script decides the obvious cases outright -- Devanagari is Hindi or
 * Nepali, Latin is one of the five Western families -- and the markers,
 * lexicons and bigrams decide the cases a script cannot. Neutral material
 * (digits, dates, punctuation, emoji, spaces) follows the word beside it,
 * and every character of the input has to land in exactly one segment, so
 * a switch never loses or reorders a byte of the text.
 */
@RunWith(AndroidJUnit4::class)
class LanguageDetectorTest {

	private val base = Eci.ENGLISH_US

	@Test
	fun devanagariHindiIsRecognized() {
		assertEquals(
			Eci.HINDI,
			LanguageDetector.detect("नमस्ते, आप कैसे हैं? मैं हिंदुस्तान में रहता हूँ।", base)
		)
		assertEquals(Eci.HINDI, LanguageDetector.detect("यह एक हिंदी वाक्य है", Eci.NEPALI))
	}

	@Test
	fun sharedDevanagariWordsRespectTheConfiguredIndicVoice() {
		// Greetings and thanks are used in both languages; treating them as
		// Hindi-only markers needlessly switched Nepali listeners to Hindi.
		for (word in listOf("नमस्ते", "धन्यवाद")) {
			assertEquals(Eci.HINDI, LanguageDetector.detect(word, Eci.HINDI))
			assertEquals(Eci.NEPALI, LanguageDetector.detect(word, Eci.NEPALI))
		}
	}

	@Test
	fun devanagariNepaliIsRecognizedByItsMarkers() {
		assertEquals(
			Eci.NEPALI,
			LanguageDetector.detect("तपाईं कस्तो हुनुहुन्छ? मेरो नाम राम हो।", base)
		)
		assertEquals(
			Eci.NEPALI,
			LanguageDetector.detect("हामी काठमाडौँमा छौँ र त्यहाँ राम्रो छ।", base)
		)
		// A bare Devanagari word with no marker follows the listener's
		// default: Nepali when the phone speaks Nepali, Hindi otherwise.
		assertEquals(Eci.NEPALI, LanguageDetector.detect("फूल", Eci.NEPALI))
		assertEquals(Eci.HINDI, LanguageDetector.detect("फूल", Eci.ENGLISH_US))
	}

	@Test
	fun latinFamiliesAreRecognized() {
		assertEquals(Eci.ENGLISH_US, LanguageDetector.detect("Hello, how are you this morning?", base))
		assertEquals(Eci.SPANISH_SPAIN, LanguageDetector.detect("Hola, ¿cómo estás? Buenos días.", base))
		assertEquals(Eci.FRENCH_FRANCE, LanguageDetector.detect("Bonjour, comment allez-vous aujourd'hui ?", base))
		assertEquals(Eci.GERMAN, LanguageDetector.detect("Guten Morgen, wie geht es dir?", base))
		assertEquals(Eci.ITALIAN, LanguageDetector.detect("Ciao, come stai oggi? Buongiorno.", base))
	}

	@Test
	fun distinctiveDiacriticsWinOverShortContext() {
		assertEquals(Eci.GERMAN, LanguageDetector.detect("Straße und Größe", base))
		assertEquals(Eci.SPANISH_SPAIN, LanguageDetector.detect("niño y señor", base))
		assertEquals(Eci.FRENCH_FRANCE, LanguageDetector.detect("français et garçon", base))
	}

	@Test
	fun aWeakUnknownWordStaysInTheAmbientWesternVoice() {
		// "Welt" carries no German lexicon entry; its lone Spanish-looking
		// bigram must not override a listener who is already in German.
		assertEquals(Eci.GERMAN, LanguageDetector.detect("Welt", Eci.GERMAN))
	}

	@Test
	fun commonEnglishWordsTheLexiconDoesNotListStayEnglish() {
		for (word in listOf(
			"wavelength", "plywood", "friendship", "button", "letter", "summer",
			"chicken", "shell", "under", "water", "open", "sentence"
		)) {
			assertEquals(word, Eci.ENGLISH_US, LanguageDetector.detect(word, Eci.ENGLISH_US))
		}
	}

	@Test
	fun theListenersDialectChoiceIsKeptInsideItsFamily() {
		// A phone set to British English keeps British English for English runs.
		val gb = LanguageDetector.split("Hello there, friend", Eci.ENGLISH_UK)
		assertTrue(gb.all { it.language == Eci.ENGLISH_UK })
		// A Mexican-Spanish choice keeps the Mexican voice.
		val mx = LanguageDetector.split("Hola, buenos días", Eci.SPANISH_MEXICO)
		assertTrue(mx.all { it.language == Eci.SPANISH_MEXICO })
		// A family the listener did not pick falls back to its first module.
		val de = LanguageDetector.detect("Guten Tag", Eci.ENGLISH_US)
		assertEquals(Eci.GERMAN, de)
	}

	@Test
	fun mixedScriptIsSegmentedInOrder() {
		val segments = LanguageDetector.split("कान्छी Hello के छ? Good night", base)
		assertEquals(Eci.NEPALI, segments.first().language)
		// Every part of the text is preserved, in order.
		assertEquals("कान्छी Hello के छ? Good night", segments.joinToString("") { it.text })
		val languages = segments.map { it.language }.distinct()
		assertTrue(languages.contains(Eci.NEPALI))
		assertTrue(languages.contains(Eci.ENGLISH_US))
		// The Nepali run comes before the English greeting that follows it.
		val firstNepali = segments.indexOfFirst { it.language == Eci.NEPALI }
		val firstEnglish = segments.indexOfFirst { it.language == Eci.ENGLISH_US }
		assertTrue(firstNepali in 0..firstEnglish)
	}

	@Test
	fun unknownLatinWordsDoNotLeakIntoAnIndicVoice() {
		// Latin-script words have no Hindi/Nepali phonemes to route through the
		// Indic romanizer. Unknown English and product vocabulary must not be
		// guessed into another language merely because of a shared bigram.
		for (baseIndic in listOf(Eci.HINDI, Eci.NEPALI)) {
			for (word in listOf("message", "launcher", "dictation", "voice", "AI", "qzxv")) {
				assertEquals("$word from base $baseIndic", Eci.ENGLISH_US, LanguageDetector.detect(word, baseIndic))
			}
			val mixed = LanguageDetector.split("नमस्ते message launcher", baseIndic)
			assertEquals(baseIndic, mixed.first().language)
			assertEquals(Eci.ENGLISH_US, mixed.last().language)
			assertEquals("नमस्ते message launcher", mixed.joinToString("") { it.text })
		}
	}

	@Test
	fun romanizedIndicWordsKeepTheirLanguage() {
		// The example from the request: "Kanchi" written in Latin letters is
		// still Nepali, while the English word beside it stays English.
		val segments = LanguageDetector.split("Kanchi Hello", base)
		assertEquals(2, segments.size)
		assertEquals(Eci.NEPALI, segments[0].language)
		assertEquals(Eci.ENGLISH_US, segments[1].language)
		assertEquals(Eci.HINDI, LanguageDetector.detect("namaste and dhanyavad", base))
	}

	@Test
	fun neutralMaterialInheritsTheSurroundingLanguage() {
		// Digits, a date, punctuation and emoji between Hindi words stay Hindi.
		val hindi = LanguageDetector.split("मूल्य 1,250.50 है 😀 (25/10/2026)।", base)
		assertTrue(hindi.isNotEmpty())
		assertTrue(hindi.all { it.language == Eci.HINDI })
		// Leading neutral material takes the first word's language.
		val leading = LanguageDetector.split("123 नमस्ते", base)
		assertTrue(leading.all { it.language == Eci.HINDI })
	}

	@Test
	fun everyCharacterLandsInExactlyOneSegment() {
		val text = "Hello नमस्ते 123! ¿Qué tal? गाउँ 🙂 bye"
		val segments = LanguageDetector.split(text, base)
		assertEquals(text, segments.joinToString("") { it.text })
		assertTrue(segments.none { it.text.isEmpty() })
	}

	@Test
	fun neutralAndEmptyInputIsSafe() {
		assertEquals(base, LanguageDetector.detect("123 456 ... ?! 😀", base))
		assertTrue(LanguageDetector.split("", base).isEmpty())
		val only = LanguageDetector.split("...", base)
		assertEquals(1, only.size)
		assertEquals(base, only[0].language)
	}

	@Test
	fun anUnmarkedWordTakesTheLanguageOfItsSentence() {
		// घर and पानी are everyday words in both languages; the copula at
		// the end of each sentence is what says which voice they belong to,
		// and the whole sentence must follow it, word by word.
		val hindi = LanguageDetector.split("मेरे घर में पानी है", base)
		assertTrue(hindi.all { it.language == Eci.HINDI })
		val nepali = LanguageDetector.split("मेरो घरमा पानी छ", base)
		assertTrue(nepali.all { it.language == Eci.NEPALI })
	}

	@Test
	fun adjacentSentencesMayChangeLanguage() {
		val segments = LanguageDetector.split(
			"मुझे स्कूल जाना है। म बिस्तारै बोल्नुहोस् भन्छु।", base
		)
		assertEquals(Eci.HINDI, segments.first().language)
		assertEquals(Eci.NEPALI, segments.last().language)
		assertEquals(
			"मुझे स्कूल जाना है। म बिस्तारै बोल्नुहोस् भन्छु।",
			segments.joinToString("") { it.text }
		)
	}

	@Test
	fun nepaliCliticsAndPoliteEndingsMarkTheirSentence() {
		assertEquals(
			Eci.NEPALI,
			LanguageDetector.detect("मानिसहरूलाई कथा सुनाउनुहोस्", base)
		)
		assertEquals(
			Eci.NEPALI,
			LanguageDetector.detect("तपाईंको घर काठमाडौँमा छ", Eci.HINDI)
		)
		assertEquals(
			Eci.HINDI,
			LanguageDetector.detect("चायवाला आया और सब लोग हँसे", Eci.NEPALI)
		)
	}

	@Test
	fun anUnknownLatinWordBorrowsItsNeighboursLanguage() {
		// A product name between French words is read in the French voice
		// rather than bouncing the sentence into English for one word.
		val segments = LanguageDetector.split("Bonjour, ouvre Microsoft Launcher maintenant", base)
		assertTrue(segments.all { it.language == Eci.FRENCH_FRANCE })
		// And between Hindi words an English product name stays English,
		// because a Latin word has no Devanagari pronunciation.
		val mixed = LanguageDetector.split("मेरा Microsoft Launcher खोलो", Eci.HINDI)
		assertEquals(Eci.HINDI, mixed.first().language)
		assertTrue(mixed.any { it.language == Eci.ENGLISH_US })
	}

	@Test
	fun longMixedTextStaysFastAndComplete() {
		val line = buildString {
			repeat(200) {
				append("The quick brown fox ")
				append("तपाईं र हामी ")
				append("123 ")
			}
		}
		val segments = LanguageDetector.split(line, base)
		assertEquals(line, segments.joinToString("") { it.text })
		assertTrue(segments.size > 1)
	}
}
