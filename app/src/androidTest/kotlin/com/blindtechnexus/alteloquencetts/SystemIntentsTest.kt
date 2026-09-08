package com.blindtechnexus.alteloquencetts

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Every intent this app hands to the system, checked as data.
 *
 * These are the parts of the app that leave it, and a mistake in one is
 * invisible from inside: a button that opens something is a button that
 * works, whether it opened the team's WhatsApp or somebody else's, whether it
 * asked about this app's battery use or listed every app on the phone, and
 * whether it went to the speech settings or to the top of the settings app.
 *
 * None of them is started here. Starting one would put a system dialog or
 * somebody's messaging app on the screen of the device running the tests,
 * which is a side effect no assertion is worth.
 */
@RunWith(AndroidJUnit4::class)
class SystemIntentsTest {

	private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext

	@Test
	fun whatsAppIsTheNumberTheTeamGave() {
		val ways = ExternalLinks.whatsAppWays()
		assertEquals(Intent.ACTION_VIEW, ways[0].action)
		assertEquals("https://w.me/${ExternalLinks.WHATSAPP_NUMBER}", ways[0].data.toString())
		assertEquals(
			"whatsapp://send?phone=${ExternalLinks.WHATSAPP_NUMBER}",
			ways[1].data.toString()
		)
	}

	@Test
	fun telegramIsTheChannelTheTeamGave() {
		val ways = ExternalLinks.telegramWays()
		assertEquals(Intent.ACTION_VIEW, ways[0].action)
		assertEquals("https://t.me/${ExternalLinks.TELEGRAM_CHANNEL}", ways[0].data.toString())
		assertEquals(
			"tg://resolve?domain=${ExternalLinks.TELEGRAM_CHANNEL}",
			ways[1].data.toString()
		)
	}

	@Test
	fun emailIsAddressedAndAlreadyHasASubject() {
		val ways = ExternalLinks.emailWays(SUBJECT)
		// SENDTO rather than SEND: SEND offers a chooser of everything that
		// can take text, which is not an email and is a dead end for somebody
		// who cannot see what the choices are.
		assertEquals(Intent.ACTION_SENDTO, ways[0].action)
		assertEquals("mailto:${ExternalLinks.EMAIL_ADDRESS}", ways[0].data.toString())
		assertEquals(SUBJECT, ways[0].getStringExtra(Intent.EXTRA_SUBJECT))
		assertEquals(
			listOf(ExternalLinks.EMAIL_ADDRESS),
			ways[1].getStringArrayExtra(Intent.EXTRA_EMAIL)?.toList()
		)
	}

	@Test
	fun theExemptionIsAskedForByTheScreenThatNamesThisApp() {
		val ways = BatteryExemption.ways(context)
		assertEquals(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, ways[0].action)
		assertEquals("package:${context.packageName}", ways[0].data.toString())
		// The fallback, for a build whose manufacturer took the first one out.
		assertEquals(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS, ways[1].action)
	}

	@Test
	fun theSpeechSettingsAreAskedForBeforeAnythingElse() {
		val ways = SystemSpeechSettings.ways()
		assertEquals("com.android.settings.TTS_SETTINGS", ways[0].action)
		assertEquals(Settings.ACTION_ACCESSIBILITY_SETTINGS, ways[1].action)
		assertEquals(Settings.ACTION_SETTINGS, ways[2].action)
	}

	@Test
	fun askingTheDeviceWhetherItWillLeaveTheEngineAloneDoesNotThrow() {
		// The answer belongs to the device: an emulator that has never dozed is
		// exempt, and a phone whose manufacturer reimplemented this badly is
		// not. What is being checked is that asking does not take the app down,
		// because the answer is read on the way into the main screen and a
		// crash there would be a crash before anything was ever said.
		BatteryExemption.isGranted(context)
	}

	private companion object {
		const val SUBJECT = "Alt eloquence TTS"
	}
}
