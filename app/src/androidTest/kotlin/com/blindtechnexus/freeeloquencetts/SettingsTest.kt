package com.blindtechnexus.freeeloquencetts

import android.content.Context
import android.media.AudioAttributes
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
	fun aNamedCustomVoiceCanBeSavedSelectedAndDeleted() {
		val profile = CustomVoiceProfile(
			id = "test-profile",
			name = "My reader",
			baseVoice = GLEN,
			shape = mapOf(
				Eci.VOICE_GENDER to 1,
				Eci.VOICE_SPEED to 88,
				Eci.VOICE_PITCH_BASELINE to 72,
				Eci.VOICE_VOLUME to 91
			)
		)
		settings.saveCustomVoice(profile)
		settings.activeCustomVoiceId = profile.id
		assertEquals(listOf("My reader"), settings.customVoices().map { it.name })
		assertEquals(GLEN, settings.activeVoicePreset)
		assertEquals(72, settings.activeVoiceShape()[Eci.VOICE_PITCH_BASELINE])
		assertEquals(88, settings.activeVoiceShape()[Eci.VOICE_SPEED])
		val reread = Settings(InstrumentationRegistry.getInstrumentation().targetContext)
		try {
			assertEquals("My reader", reread.activeCustomVoice()?.name)
			assertEquals(72, reread.activeVoiceShape()[Eci.VOICE_PITCH_BASELINE])
		} finally {
			reread.close()
		}
		settings.deleteCustomVoice(profile.id)
		assertTrue(settings.customVoices().isEmpty())
		assertNull(settings.activeCustomVoiceId)
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

	/** The switches for the battery and stability screen are remembered.
	 *  Running in the foreground even while not in use starts off, because
	 *  the notification is what it costs and nobody has asked for it yet; a
	 *  voice that stops when the phone is locked is the price of that, and
	 *  the screen says so where the switch is. Every other switch here
	 *  starts off too, since a wake lock or a log nobody asked for would be
	 *  a surprise. There is no notification interval any more -- the
	 *  notification is posted once and left -- so what used to be read and
	 *  written here in milliseconds is simply not a setting at all. The
	 *  foreground and wake lock properties are retired from the screen and
	 *  fixed in Stability, but they still round-trip here so that
	 *  preferences written by an earlier build keep parsing. */
	@Test
	fun theBatteryAndStabilitySwitchesRememberTheirState() {
		// The wipe in setUp writes the switch off, so the key is taken away
		// first: what is asserted is the default, which is what a phone that
		// never had the switch hears.
		remove(Settings.KEY_PERSISTENT_NOTIFICATION)
		assertFalse("running in the foreground starts on", settings.persistentNotification)
		assertFalse("speaking in the foreground starts on", settings.foregroundWhileSpeaking)
		assertFalse("the wake lock starts on", settings.wakeLock)
		assertFalse("action tracking starts on", settings.trackActions)
		settings.persistentNotification = true
		settings.foregroundWhileSpeaking = true
		settings.wakeLock = true
		settings.trackActions = true
		assertTrue(settings.persistentNotification)
		assertTrue(settings.foregroundWhileSpeaking)
		assertTrue(settings.wakeLock)
		assertTrue(settings.trackActions)
	}

	/** The language this app speaks, and the fallback that makes it
	 *  impossible to speak none: American English until somebody chooses
	 *  otherwise, the British module if they choose it, and the American one
	 *  again for a number no build knows -- a preference written by another
	 *  version, or by hand, is a voice rather than a refusal. */
	@Test
	fun theLanguageIsOneOfTheTwoAndFallsBackToAmerican() {
		remove(Settings.KEY_LANGUAGE)
		assertEquals("the language starts as British", Eci.ENGLISH_US, settings.language)
		settings.language = Eci.ENGLISH_UK
		assertEquals("British English was not remembered", Eci.ENGLISH_UK, settings.language)
		settings.language = 0x1234
		assertEquals("a foreign language did not fall back", Eci.ENGLISH_US, settings.language)
		settings.language = Eci.GERMAN
		assertEquals("German was not remembered", Eci.GERMAN, settings.language)
		settings.language = Eci.ENGLISH_US
	}

	/** The sample rate is always one the engine has: a number from another
	 *  build or a hand-edited file lands on the nearest. */
	@Test
	fun theSampleRateIsAlwaysOneTheEngineHas() {
		remove(Settings.KEY_SAMPLE_RATE)
		assertEquals(Settings.DEFAULT_SAMPLE_RATE, settings.sampleRateHz)
		settings.sampleRateHz = 22050
		assertEquals(22050, settings.sampleRateHz)
		settings.sampleRateHz = 23000
		assertEquals("an off rate did not snap", 22050, settings.sampleRateHz)
		Settings.store(InstrumentationRegistry.getInstrumentation().targetContext)
			.edit().putInt(Settings.KEY_SAMPLE_RATE, 12345).commit()
		assertEquals("a hand-written rate did not snap", 11025, settings.sampleRateHz)
		remove(Settings.KEY_SAMPLE_RATE)
	}

	/** The new switches start where they should and are remembered. */
	@Test
	fun theCodesTheRoutingAndTheForcedLanguageAreRemembered() {
		remove(Settings.KEY_READ_OTP_DIGITS)
		remove(Settings.KEY_SYNTHESIS_ROUTING)
		remove(Settings.KEY_FORCE_LANGUAGE)
		assertTrue("codes start off", settings.readOtpDigits)
		assertEquals(SynthesisRouting.AUTOMATIC, settings.synthesisRouting)
		assertFalse("the language starts forced", settings.forceLanguage)
		settings.readOtpDigits = false
		assertFalse(settings.readOtpDigits)
		settings.synthesisRouting = SynthesisRouting.MEDIA
		assertEquals(SynthesisRouting.MEDIA, settings.synthesisRouting)
		settings.synthesisRouting = SynthesisRouting.ALARMS
		assertEquals(SynthesisRouting.ALARMS, settings.synthesisRouting)
		settings.synthesisRouting = 77
		assertEquals("a bad route did not fall back", SynthesisRouting.AUTOMATIC, settings.synthesisRouting)
		settings.forceLanguage = true
		assertTrue(settings.forceLanguage)
		remove(Settings.KEY_READ_OTP_DIGITS)
		remove(Settings.KEY_SYNTHESIS_ROUTING)
		remove(Settings.KEY_FORCE_LANGUAGE)
	}

	/** The routing menu: Default at the top, one channel at a time below it,
	 *  and each choice carrying the channel it names. */
	@Test
	fun theRoutingMenuStartsWithDefaultAndNamesItsChannels() {
		assertEquals("Default is not at the top", SynthesisRouting.AUTOMATIC, SynthesisRouting.CHOICES.first())
		assertTrue(SynthesisRouting.CHOICES.contains(SynthesisRouting.MEDIA))
		assertTrue(SynthesisRouting.CHOICES.contains(SynthesisRouting.ALARMS))
		assertTrue(SynthesisRouting.CHOICES.contains(SynthesisRouting.ACCESSIBILITY))
		// Default is the one that covers every route there is: every app on
		// the channel it asked for, and nothing moved.
		assertTrue(SynthesisRouting.handsToFramework(SynthesisRouting.AUTOMATIC))
		assertFalse(SynthesisRouting.playsItself(SynthesisRouting.AUTOMATIC))
		// And each of the others plays itself, on the channel it names.
		assertEquals(AudioAttributes.USAGE_MEDIA, SynthesisRouting.usageOf(SynthesisRouting.MEDIA))
		assertEquals(AudioAttributes.USAGE_ALARM, SynthesisRouting.usageOf(SynthesisRouting.ALARMS))
		assertEquals(
			AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY,
			SynthesisRouting.usageOf(SynthesisRouting.ACCESSIBILITY)
		)
		assertTrue(SynthesisRouting.playsItself(SynthesisRouting.ALARMS))
		// Direct routes used to reserve a third of a second (7,350 bytes at
		// 11,025 Hz). The low-latency queue is about fifty milliseconds.
		assertEquals(1102, SynthesisRouting.playerBufferBytes(11025, 1024))
		assertEquals(4096, SynthesisRouting.playerBufferBytes(11025, 4096))
	}

	/** Text styles are read until somebody says otherwise, and the answer is
	 *  remembered either way. */
	@Test
	fun textStylesAreReadUntilSomebodySaysOtherwise() {
		remove(Settings.KEY_READ_TEXT_STYLES)
		assertTrue("reading text styles starts off", settings.readTextStyles)
		settings.readTextStyles = false
		assertFalse(settings.readTextStyles)
		settings.readTextStyles = true
		assertTrue(settings.readTextStyles)
	}

	/** Takes one key away, so the default underneath it can be heard. */
	private fun remove(key: String) {
		val context = InstrumentationRegistry.getInstrumentation().targetContext
		DirectBoot.storage(context)
			.getSharedPreferences("${context.packageName}_preferences", Context.MODE_PRIVATE)
			.edit().remove(key).apply()
	}

	private fun wipe() {
		Settings.store(InstrumentationRegistry.getInstrumentation().targetContext).edit()
			.remove(Settings.KEY_CUSTOM_VOICES)
			.remove(Settings.KEY_ACTIVE_CUSTOM_VOICE)
			.apply()
		for (voice in Eci.PRESET_NAMES.indices) settings.clearShape(voice)
		settings.speed = Eci.DEFAULT_SPEED
		settings.voice = REED
		settings.persistentNotification = false
		settings.foregroundWhileSpeaking = false
		settings.wakeLock = false
		settings.trackActions = false
	}

	private companion object {
		const val REED = 0
		const val GLEN = 4
	}
}
