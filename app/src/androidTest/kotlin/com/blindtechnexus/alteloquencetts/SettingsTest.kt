package com.blindtechnexus.alteloquencetts

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * What is remembered, and for how long.
 *
 * A voice's settings belong to that voice. Setting Reed's pitch, going to Glen
 * and coming back has to find Reed's pitch where it was left, and only Reed's
 * reset may forget it. Speed is the one exception and is kept once for
 * everybody, because two of the eight presets ship faster than the rest and a
 * speed that followed the voice would change the pace on every change.
 */
@RunWith(AndroidJUnit4::class)
class SettingsTest {

	private lateinit var settings: Settings

	@Before
	fun setUp() {
		settings = Settings(InstrumentationRegistry.getInstrumentation().targetContext)
		wipe()
	}

	@After
	fun tearDown() {
		wipe()
	}

	@Test
	fun aVoiceKeepsWhatWasChosenForIt() {
		settings.setShapeValue(REED, Eci.VOICE_PITCH_BASELINE, 55)
		assertEquals(55, settings.shapeValue(REED, Eci.VOICE_PITCH_BASELINE))
		// Away to Glen and back.
		settings.voice = GLEN
		settings.setShapeValue(GLEN, Eci.VOICE_PITCH_BASELINE, 80)
		settings.voice = REED
		assertEquals("Reed did not keep his pitch", 55, settings.shapeValue(REED, Eci.VOICE_PITCH_BASELINE))
		assertEquals("Glen did not keep his", 80, settings.shapeValue(GLEN, Eci.VOICE_PITCH_BASELINE))
	}

	@Test
	fun oneVoicesResetLeavesTheOthersAlone() {
		settings.setShapeValue(REED, Eci.VOICE_PITCH_BASELINE, 55)
		settings.setShapeValue(GLEN, Eci.VOICE_PITCH_BASELINE, 80)
		settings.clearShape(REED)
		assertNull("Reed was not reset", settings.shapeValue(REED, Eci.VOICE_PITCH_BASELINE))
		assertEquals("Glen was reset too", 80, settings.shapeValue(GLEN, Eci.VOICE_PITCH_BASELINE))
	}

	@Test
	fun aVoiceNobodyHasTouchedSaysSo() {
		assertFalse(settings.shapeIsCustom(REED))
		settings.setShapeValue(REED, Eci.VOICE_VOLUME, 40)
		assertTrue(settings.shapeIsCustom(REED))
		assertFalse("a change to Reed marked Glen", settings.shapeIsCustom(GLEN))
	}

	@Test
	fun speedIsSharedByEveryVoice() {
		settings.setShapeValue(REED, Eci.VOICE_SPEED, 65)
		assertEquals("Glen did not get the speed", 65, settings.shapeValue(GLEN, Eci.VOICE_SPEED))
		assertEquals(65, settings.shape(GLEN)[Eci.VOICE_SPEED])
		// And a reset of one voice does not take it away, since it is not that
		// voice's to give up.
		settings.clearShape(REED)
		assertEquals(65, settings.shapeValue(REED, Eci.VOICE_SPEED))
	}

	@Test
	fun everyVoiceHasASpeedEvenUntouched() {
		assertEquals(Eci.DEFAULT_SPEED, settings.shape(REED)[Eci.VOICE_SPEED])
	}

	/** The two switches the battery screen still offers are remembered, and
	 *  each is off until somebody turns it on: a notification nobody asked for
	 *  would be a surprise, and so would a log of what they never did. */
	@Test
	fun theBatterySwitchesRememberTheirState() {
		assertFalse("the persistent notification starts on", settings.persistentNotification)
		assertFalse("action tracking starts on", settings.trackActions)
		settings.persistentNotification = true
		settings.trackActions = true
		assertTrue(settings.persistentNotification)
		assertTrue(settings.trackActions)
	}

	/** The numbers the "While speaking" section used to offer are pinned to
	 *  the best of them rather than asked for. A fresh install reads the pin
	 *  straight out of the defaults, and a value left behind by an older
	 *  build, or written by hand, is taken back by the next instance of the
	 *  settings rather than obeyed. The notification check starts at three
	 *  seconds -- the promise the app makes -- and a foreign interval falls
	 *  back to it, as it always did. */
	@Test
	fun theStabilityNumbersAnswerThePinnedBestOfThem() {
		assertTrue("the wake lock does not start held", settings.wakeLock)
		assertEquals(5000, settings.wakeLockTailMs)
		assertEquals(Pacing.DEFAULT_MS, settings.speechLeadMs)
		assertTrue("the engine does not run ahead on long text", settings.runAheadOnLongText)
		assertEquals("the engine is given back when it should be kept", 0, settings.releaseEngineAfterS)
		assertEquals(Settings.DEFAULT_SILENCE_MS, settings.engineSilenceMs)
		assertFalse("speaking in the foreground starts on", settings.foregroundWhileSpeaking)
		assertTrue("the notification does not carry its stop button", settings.notificationStopButton)
		assertEquals(KeepAlive.CHECK_MS.toInt(), settings.keepAliveCheckMs)
		assertEquals(
			"the offered intervals moved",
			listOf(2000, 3000, 5000, 10000),
			Stability.NOTIFICATION_CHECKS
		)
		// What an older build, or a hand in the preferences, leaves behind.
		settings.wakeLock = false
		settings.wakeLockTailMs = 60000
		settings.speechLeadMs = Pacing.MAX_MS
		settings.runAheadOnLongText = false
		settings.engineSilenceMs = 300000
		val again = Settings(InstrumentationRegistry.getInstrumentation().targetContext)
		assertTrue("the wake lock did not come back", again.wakeLock)
		assertEquals("the tail did not come back", Settings.PIN_WAKE_LOCK_TAIL_MS, again.wakeLockTailMs)
		assertEquals("the lead did not come back", Pacing.DEFAULT_MS, again.speechLeadMs)
		assertTrue("running ahead did not come back", again.runAheadOnLongText)
		assertEquals("silence did not come back", Settings.DEFAULT_SILENCE_MS, again.engineSilenceMs)
		again.keepAliveCheckMs = 12345
		assertEquals(
			"a foreign interval did not fall back to three seconds",
			KeepAlive.CHECK_MS.toInt(),
			again.keepAliveCheckMs
		)
		// The pin and the clock the notification readers check without a
		// settings instance are one number, or the screen's promise and the
		// service's wait disagree.
		assertEquals(KeepAlive.CHECK_MS.toInt(), Settings.PIN_KEEP_ALIVE_CHECK_MS)
	}

	private fun wipe() {
		for (voice in Eci.PRESET_NAMES.indices) settings.clearShape(voice)
		settings.speed = Eci.DEFAULT_SPEED
		settings.voice = REED
		settings.persistentNotification = false
		settings.trackActions = false
		// The pinned numbers go back to their pins, whatever a test moved,
		// by the same means the app uses: an instance sees them and pins
		// again. It is closed after, because a test that leaves a listener
		// registered on every wipe is a test that is proving the leak it
		// keeps talking about.
		Settings(InstrumentationRegistry.getInstrumentation().targetContext).close()
	}

	private companion object {
		const val REED = 0
		const val GLEN = 4
	}
}
