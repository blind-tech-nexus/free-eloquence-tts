package com.blindtechnexus.alteloquencetts

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * That the battery and about screens build themselves, and offer what they
 * promise.
 *
 * The same reasoning as in [ScreensTest]: a screen here is composed rather
 * than inflated, so nothing about it is checked until it is drawn. These two
 * are not reachable from a launch -- the battery screen hangs off the
 * settings screen and the about screen off the main one -- so they are
 * composed directly instead.
 *
 * The switches are found by what they say rather than by their text: a
 * switch here is one control whose label is its content description and whose
 * state is its state description, and that is what a screen reader meets. The
 * rows the engine now answers for itself are checked the other way round --
 * they have to answer that they are not there -- because a screen that lost a
 * row by accident and a screen that was never given one look the same to
 * nobody.
 */
@RunWith(AndroidJUnit4::class)
class ScreensComposeTest {

	@get:Rule
	val compose = createComposeRule()

	private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext

	@Test
	fun theBatteryScreenBuildsItselfAndOffersEveryRow() {
		compose.setContent {
			MaterialTheme {
				BatteryScreen(onBack = {}, onRequestExemption = {})
			}
		}
		for (label in listOf(
			R.string.persistent_notification_label,
			R.string.start_on_boot_label,
			R.string.track_actions_label
		)) {
			compose.onNodeWithContentDescription(context.getString(label)).assertExists()
		}
		// The engine's own numbers are pinned in the settings rather than
		// offered here, so the section they lived in and every row under it
		// have to be absent, not merely empty. The literals are written out
		// because the strings they used to name are gone from the build,
		// which is half of the point of saying them here.
		for (gone in listOf(
			"While speaking",
			"Speak in the foreground",
			"Keep the CPU awake while speaking",
			"Hold the CPU a little longer",
			"How far ahead of the speech",
			"Run ahead on long text",
			"Give the engine back when idle",
			"Give up after the engine goes silent",
			"How often the notification is looked for",
			"Stop button in the notification"
		)) {
			compose.onNodeWithText(gone, substring = true).assertDoesNotExist()
		}
	}

	@Test
	fun theAboutScreenBuildsItselfAndOffersAnUpdateCheck() {
		compose.setContent {
			MaterialTheme {
				AboutScreen(onBack = {})
			}
		}
		compose.onNodeWithText(context.getString(R.string.about_update_check)).assertExists()
		compose.onNodeWithText(context.getString(R.string.about_whatsapp_action)).assertExists()
	}
}
