package com.blindtechnexus.freeeloquencetts

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * How the samples are finished before they are heard.
 *
 * The optimizer runs on every utterance in the service and on every sample in
 * the settings screen, so a mistake here is not one bad utterance but every
 * utterance. What is checked is the contract each mode makes: silence stays
 * silence, nothing ever clips past the mode's own ceiling, a steady offset is
 * lifted out, and Balanced -- the default -- leaves an ordinary tone very
 * nearly alone, which is the point of it.
 */
class AudioOptimizationTest {

	@Test
	fun theModesAreThreeAndOrdered() {
		assertEquals(0, AudioOptimization.BALANCED)
		assertEquals(1, AudioOptimization.VOLUME)
		assertEquals(2, AudioOptimization.QUALITY)
	}

	@Test
	fun aStoredModeIsClampedIntoRange() {
		assertEquals(AudioOptimization.BALANCED, AudioOptimization.clamp(-1))
		assertEquals(AudioOptimization.QUALITY, AudioOptimization.clamp(99))
		assertEquals(AudioOptimization.VOLUME, AudioOptimization.clamp(AudioOptimization.VOLUME))
	}

	@Test
	fun everyModeNamesAMethodTheEngineHas() {
		val methods = setOf(
			Eci.UPSAMPLE_HOLD, Eci.UPSAMPLE_LINEAR, Eci.UPSAMPLE_CUBIC, Eci.UPSAMPLE_SINC
		)
		for (mode in AudioOptimization.BALANCED..AudioOptimization.QUALITY) {
			assertTrue("mode $mode names no engine method", methods.contains(AudioOptimization.methodFor(mode)))
		}
	}

	@Test
	fun volumeIsTheLoudMethodAndQualityTheCleanOne() {
		assertEquals(Eci.UPSAMPLE_HOLD, AudioOptimization.methodFor(AudioOptimization.VOLUME))
		assertEquals(Eci.UPSAMPLE_SINC, AudioOptimization.methodFor(AudioOptimization.QUALITY))
	}

	@Test
	fun silencePassesThroughAsSilence() {
		for (mode in AudioOptimization.BALANCED..AudioOptimization.QUALITY) {
			val buffer = ByteArray(2048)
			val optimizer = AudioOptimizer(mode, 11025)
			assertEquals(2048, optimizer.process(buffer, 2048))
			assertTrue("mode $mode made sound out of silence", buffer.all { it == 0.toByte() })
		}
	}

	@Test
	fun anOddByteIsLeftWhereItLay() {
		val buffer = ByteArray(7)
		val optimizer = AudioOptimizer(AudioOptimization.BALANCED, 11025)
		assertEquals(6, optimizer.process(buffer, 7))
	}

	@Test
	fun noModeClipsPastItsOwnCeiling() {
		for (mode in AudioOptimization.BALANCED..AudioOptimization.QUALITY) {
			// A full-scale spike, then a loud tone: the attack has to catch
			// the first and the release must not pump the second past it.
			val buffer = constant(11025, 30000)
			putSample(buffer, 0, 32767)
			AudioOptimizer(mode, 11025).process(buffer, buffer.size)
			val peak = peak(buffer)
			val ceiling = (AudioOptimization.ceiling(mode) * 32768).toInt()
			assertTrue(
				"mode $mode peaked at $peak past its ceiling of $ceiling",
				peak <= ceiling
			)
		}
	}

	@Test
	fun aSteadyOffsetIsLiftedOut() {
		for (mode in AudioOptimization.BALANCED..AudioOptimization.QUALITY) {
			val buffer = constant(11025, 2000)
			AudioOptimizer(mode, 11025).process(buffer, buffer.size)
			val tail = peak(buffer, fromSample = 11025 - 100)
			assertTrue("mode $mode left an offset of $tail", tail < 1000)
		}
	}

	@Test
	fun balancedLeavesAnOrdinaryToneVeryNearlyAlone() {
		val buffer = sine(samples = 11025, hz = 440.0, amplitude = 20000)
		val before = peak(buffer)
		AudioOptimizer(AudioOptimization.BALANCED, 11025).process(buffer, buffer.size)
		val after = peak(buffer)
		assertTrue("balanced moved a tone from $before to $after", after > before * 0.8 && after < before * 1.2)
	}

	@Test
	fun theChoiceIsWrittenDownAndReadBack() {
		val context = InstrumentationRegistry.getInstrumentation().targetContext
		val settings = Settings(context)
		val was = settings.audioOptimization
		try {
			settings.audioOptimization = AudioOptimization.QUALITY
			assertEquals(AudioOptimization.QUALITY, settings.audioOptimization)
			settings.audioOptimization = 99
			assertEquals(AudioOptimization.QUALITY, settings.audioOptimization)
		} finally {
			settings.audioOptimization = was
		}
	}

	private fun sine(samples: Int, hz: Double, amplitude: Int): ByteArray {
		val out = ByteArray(samples * 2)
		for (i in 0 until samples) {
			putSample(out, i, (amplitude * sin(2.0 * PI * hz * i / 11025)).toInt())
		}
		return out
	}

	private fun constant(samples: Int, value: Int): ByteArray {
		val out = ByteArray(samples * 2)
		for (i in 0 until samples) putSample(out, i, value)
		return out
	}

	private fun putSample(buffer: ByteArray, at: Int, value: Int) {
		buffer[at * 2] = (value and 0xFF).toByte()
		buffer[at * 2 + 1] = ((value shr 8) and 0xFF).toByte()
	}

	private fun peak(buffer: ByteArray, fromSample: Int = 0): Int {
		var loudest = 0
		var i = fromSample * 2
		while (i + 1 < buffer.size) {
			val sample = (buffer[i].toInt() and 0xFF) or (buffer[i + 1].toInt() shl 8)
			loudest = maxOf(loudest, abs(sample))
			i += 2
		}
		return loudest
	}
}
