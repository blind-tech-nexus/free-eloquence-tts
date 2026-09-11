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

	/** The switches for the battery and stability screen are remembered, and
	 *  each is off until somebody turns it on: a notification or a wake lock
	 *  nobody asked for would be a surprise, and so would a log of what they
	 *  never did. The notification check starts at three seconds -- the
	 *  promise the screen makes -- and a value nobody offered falls back to
	 *  it rather than running wild. The foreground and wake lock properties
	 *  are retired from the screen and fixed in Stability, but they still
	 *  round-trip here so that preferences written by an earlier build keep
	 *  parsing. */
	@Test
	fun theBatteryAndStabilitySwitchesRememberTheirState() {
		assertFalse("the persistent notification starts on", settings.persistentNotification)
		assertFalse("speaking in the foreground starts on", settings.foregroundWhileSpeaking)
		assertFalse("the wake lock starts on", settings.wakeLock)
		assertFalse("action tracking starts on", settings.trackActions)
		assertEquals(KeepAlive.CHECK_MS.toInt(), settings.keepAliveCheckMs)
		assertEquals(
			"the offered intervals moved",
			listOf(2000, 3000, 5000, 10000),
			Stability.NOTIFICATION_CHECKS
		)
		settings.persistentNotification = true
		settings.foregroundWhileSpeaking = true
		settings.wakeLock = true
		settings.trackActions = true
		settings.keepAliveCheckMs = 5000
		assertTrue(settings.persistentNotification)
		assertTrue(settings.foregroundWhileSpeaking)
		assertTrue(settings.wakeLock)
		assertTrue(settings.trackActions)
		assertEquals(5000, settings.keepAliveCheckMs)
		settings.keepAliveCheckMs = 12345
		assertEquals(
			"a foreign interval did not fall back to three seconds",
			KeepAlive.CHECK_MS.toInt(),
			settings.keepAliveCheckMs
		)
	}

	private fun wipe() {
		for (voice in Eci.PRESET_NAMES.indices) settings.clearShape(voice)
		settings.speed = Eci.DEFAULT_SPEED
		settings.voice = REED
		settings.persistentNotification = false
		settings.foregroundWhileSpeaking = false
		settings.keepAliveCheckMs = KeepAlive.CHECK_MS.toInt()
		settings.wakeLock = false
		settings.trackActions = false
	}

	private companion object {
		const val REED = 0
		const val GLEN = 4
	}
}
