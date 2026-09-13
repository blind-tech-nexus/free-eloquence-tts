package com.blindtechnexus.freeeloquencetts

/** The numbers the published ECI interface takes, and the few tables that turn
 *  them into something Android understands. */
object Eci {

	const val PARAM_SYNTH_MODE = 0
	const val PARAM_INPUT_TYPE = 1
	const val PARAM_TEXT_MODE = 2
	const val PARAM_DICTIONARY = 3
	const val PARAM_SAMPLE_RATE = 5
	const val PARAM_REAL_WORLD_UNITS = 8
	const val PARAM_LANGUAGE_DIALECT = 9
	const val PARAM_NUMBER_MODE = 10

	const val VOICE_GENDER = 0
	const val VOICE_HEAD_SIZE = 1
	const val VOICE_PITCH_BASELINE = 2
	const val VOICE_PITCH_FLUCTUATION = 3
	const val VOICE_ROUGHNESS = 4
	const val VOICE_BREATHINESS = 5
	const val VOICE_SPEED = 6
	const val VOICE_VOLUME = 7

	/** Voice 0 is the one being spoken in; 1 to 8 are the language's presets
	 *  and are read only; 9 to 16 are the caller's to edit. */
	const val VOICE_CURRENT = 0
	const val FIRST_PRESET = 1
	const val LAST_PRESET = 8
	const val SCRATCH_VOICE = 9

	const val NUM_VOICE_PARAMS = 8

	/** The volumes a dictionary set holds. The fourth, the extended main
	 *  dictionary, is for a language written in another script and every call
	 *  refuses it here. */
	const val DICT_MAIN = 0
	const val DICT_ROOT = 1
	const val DICT_ABBREVIATION = 2

	val DICT_VOLUMES = listOf(DICT_MAIN, DICT_ROOT, DICT_ABBREVIATION)

	/**
	 * The speed every voice is spoken at unless someone has chosen otherwise.
	 *
	 * Six of the eight presets ship at 50 and Glen and Sandy ship at 70, which
	 * is half again as fast. That is IBM's data rather than a fault, but it
	 * makes speed a property of the voice, so picking Glen sped everything up
	 * without anyone asking for it. Speed belongs to whoever is listening.
	 */
	const val DEFAULT_SPEED = 50

	const val SPEED_MAX = 250
	const val PERCENT_MAX = 100

	/** What each of the eight will take. Gender is a choice of two, speed runs
	 *  to 250, and the rest are hundredths. */
	fun voiceRange(param: Int): IntRange = when (param) {
		VOICE_GENDER -> 0..1
		VOICE_SPEED -> 0..SPEED_MAX
		else -> 0..PERCENT_MAX
	}

	fun clampVoice(param: Int, value: Int): Int {
		val range = voiceRange(param)
		return value.coerceIn(range.first, range.last)
	}

	/** The eight are on three different scales, and a screen reader saying a
	 *  bare number leaves it to the listener to remember which. So the settings
	 *  screen works in hundredths of whatever the setting's own range is, which
	 *  for six of the seven sliders is the number the engine already uses and
	 *  for speed is a fifth of it. */
	fun toPercent(param: Int, value: Int): Int {
		val top = voiceRange(param).last
		if (top <= 0) return 0
		return Math.round(value * 100.0 / top).toInt().coerceIn(0, PERCENT_MAX)
	}

	fun fromPercent(param: Int, percent: Int): Int {
		val top = voiceRange(param).last
		return Math.round(percent.coerceIn(0, PERCENT_MAX) * top / 100.0).toInt()
	}

	/** What eciSampleRate's index means in hertz, in the order the engine
	 *  numbers them. The first four are IBM's numbering and the rest openevv's. */
	val SAMPLE_RATES = intArrayOf(8000, 11025, 22050, 16000, 32000, 44100, 48000)

	fun sampleRateHz(index: Int): Int =
		SAMPLE_RATES.getOrElse(index) { SAMPLE_RATES[1] }

	fun sampleRateIndex(hz: Int): Int {
		val at = SAMPLE_RATES.indexOf(hz)
		return if (at >= 0) at else 1
	}

	/** The names the eight presets have gone by since Eloquence shipped. The
	 *  engine numbers them 1 to 8 in this order. */
	val PRESET_NAMES = arrayOf(
		"Reed", "Shelley", "Bobby", "Rocko", "Glen", "Sandy", "Grandma", "Grandpa"
	)

	/**
	 * The one language this app speaks: English (United States).
	 *
	 * The engine this is built on can be compiled with several languages, but
	 * this build carries exactly one module -- `LANGS` in the build script
	 * names only lang/enus, and not overridably -- and the app offers exactly
	 * one voice in it. There is no
	 * language setting anywhere: not on the settings screen, not in the stored
	 * preferences, and not in what the service asks the engine for. Every
	 * utterance is synthesised in this language, whatever locale the caller
	 * named, which is also what keeps the engine instance open for ever
	 * instead of being closed and remade -- at about a second a time --
	 * whenever a screen reader alternates between two languages.
	 */
	const val ENGLISH_US = 0x00010000

	/** The ISO-3 language and country English (United States) answers to,
	 *  which is what Android asks about. */
	const val ISO3_LANGUAGE = "eng"
	const val ISO3_COUNTRY = "USA"

	/**
	 * How the engine raises its own eleven thousand and twenty five hertz to a
	 * higher rate. These are the CVT_* values from the engine's
	 * src/eci/sound/eci_pcm.h, and [EvvNative.setUpsampleMethod] carries one
	 * of them down. See [AudioOptimization] for which mode uses which, and why
	 * the choice is audible: the engine's true bandwidth never changes, so the
	 * method is what decides what the new band carries -- a mirror of the
	 * speech, a curve through it, or nothing at all.
	 */
	const val UPSAMPLE_HOLD = 0
	const val UPSAMPLE_LINEAR = 2
	const val UPSAMPLE_CUBIC = 3
	const val UPSAMPLE_SINC = 4
}
