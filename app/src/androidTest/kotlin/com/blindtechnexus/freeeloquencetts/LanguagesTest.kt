package com.blindtechnexus.freeeloquencetts

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Which languages can be chosen, and whether the choice means anything.
 *
 * The list is longer than the build, and that is deliberate rather than a
 * half-finished job: the engine's own catalogue has nineteen languages in it
 * and a build carries only the modules it was compiled with, so an entry is
 * offered by name and whether it can be spoken is asked of the engine at the
 * moment the question matters -- [Eci.speaks], called from the settings screen
 * and from the service, and not from a list that a shorter
 * `-Pevvdroid.langs` would make a lie.
 *
 * What is checked here is the part that has to hold whatever the build was
 * compiled with: nothing the engine can speak is missing from the list, no
 * language is claimed twice, and a choice survives being written down.
 */
@RunWith(AndroidJUnit4::class)
class LanguagesTest {

	/** Every language asked for, by the name the list gives it. */
	private val requested = listOf(
		"English (United States)", "English (United Kingdom)",
		"Spanish (Spain)", "Spanish (Latin America)",
		"French (France)", "French (Canada)",
		"German", "Italian", "Mandarin", "Portuguese", "Japanese",
		"Finnish", "Korean", "Cantonese", "Dutch", "Norwegian",
		"Swedish", "Danish", "Polish"
	)

	@Test
	fun everyLanguageAskedForIsInTheList() {
		val names = Eci.LANGUAGES.map { it.name }
		for (name in requested) {
			assertTrue("no entry called $name", names.contains(name))
		}
		assertEquals(requested.size, names.size)
		assertEquals(names.size, names.distinct().size)
	}

	@Test
	fun aLanguageIsOfferedUnderOneEngineWordAndNoMore() {
		val seen = HashSet<Int>()
		for (offered in Eci.LANGUAGES) {
			for (word in offered.words) {
				assertTrue("the word for ${offered.name} is claimed twice", seen.add(word))
				assertEquals(offered.name, Eci.offeredFor(word)?.name)
			}
		}
		assertNull(Eci.offeredFor(0))
	}

	@Test
	fun nothingTheBuildCanSpeakIsMissingFromTheList() {
		// Whatever modules went into this build, every one of them has to be
		// reachable from the settings screen. An engine language nobody offers
		// is a language the listener cannot ask for.
		val offered = HashSet<Int>()
		for (entry in Eci.LANGUAGES) offered.addAll(entry.words)
		for (word in EvvEngine.available) {
			val base = Eci.baseLanguage(word)
			assertTrue(
				"the build speaks language word $base and the list does not offer it",
				offered.contains(base) || offered.contains(word)
			)
		}
	}

	@Test
	fun aLanguageWithNoWordsIsNotSaidToBeSpeakable() {
		for (offered in Eci.LANGUAGES) {
			if (offered.words.isEmpty()) {
				assertFalse("an entry with no words cannot be spoken", Eci.speaks(offered))
			}
		}
	}

	@Test
	fun whatCanActuallyBeSpokenIsAskedOfTheEngine() {
		// The engine's answer, not the list's, is what decides. A default
		// build carries nine modules and speaks six languages; a build made
		// with fewer carries fewer, and the screen says so rather than taking
		// a tap and honouring nothing.
		val speakable = Eci.LANGUAGES.filter { Eci.speaks(it) }
		assertEquals(EvvEngine.available.isEmpty(), speakable.isEmpty())
	}

	@Test
	fun aChoiceIsWrittenDownAndReadBack() {
		val context = InstrumentationRegistry.getInstrumentation().targetContext
		val settings = Settings(context)
		val was = settings.language
		try {
			settings.language = 0
			assertEquals(0, settings.language)
			// Any language the catalogue has a word for can be written down,
			// whether or not this build carries the module for it: the choice
			// is a choice, and whether it can be honoured is asked of the
			// engine when it is asked to speak.
			val chosen = Eci.LANGUAGES.first { it.words.isNotEmpty() }.words.first()
			settings.language = chosen
			assertEquals(chosen, settings.language)
		} finally {
			settings.language = was
		}
	}

	@Test
	fun aLanguageThisBuildHasNoNameForIsNotAChoice() {
		val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
		val settings = Settings(context)
		val was = settings.language
		try {
			// 0x00FF0000 is a language word nothing in the catalogue uses.
			// Answering with it would be answering with a language the
			// settings screen cannot show, so it comes back as "let the app
			// asking decide".
			settings.language = 0x00FF0000
			assertEquals(0, settings.language)
		} finally {
			settings.language = was
		}
	}

	@Test
	fun theSettingsScreenKnowsWhichRowIsInForce() {
		assertEquals(-1, SettingsModel.indexOf(0))
		for (at in SettingsModel.LANGUAGES.indices) {
			val offered = SettingsModel.LANGUAGES[at]
			if (offered.words.isNotEmpty()) {
				assertEquals(at, SettingsModel.indexOf(offered.words.first()))
			}
		}
	}
}
