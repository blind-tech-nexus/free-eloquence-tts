package com.blindtechnexus.freeeloquencetts

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
 * interval row is a button carrying its value, so its label is found as part
 * of what the button shows.
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
			R.string.notification_stop_label,
			R.string.start_on_boot_label,
			R.string.track_actions_label
		)) {
			compose.onNodeWithContentDescription(context.getString(label)).assertExists()
		}
		compose.onNodeWithText(context.getString(R.string.notification_check_label), substring = true)
			.assertExists()
		compose.onNodeWithText(context.getString(R.string.notification_check_description)).assertExists()
		// The "while speaking" section is gone: its answers are fixed in
		// Stability and Pacing rather than chosen here, so none of its rows
		// may be composed at all.
		for (label in listOf(
			R.string.foreground_while_speaking_label,
			R.string.wake_lock_label
		)) {
			compose.onNodeWithContentDescription(context.getString(label)).assertDoesNotExist()
		}
		for (label in listOf(
			R.string.wake_lock_tail_label,
			R.string.speech_lead_label,
			R.string.run_ahead_label,
			R.string.engine_release_label,
			R.string.engine_silence_label
		)) {
			compose.onNodeWithText(context.getString(label), substring = true).assertDoesNotExist()
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
