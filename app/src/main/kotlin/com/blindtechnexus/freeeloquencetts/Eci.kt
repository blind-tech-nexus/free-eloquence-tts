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

	/** The bit that says the text handed over is UTF-16 rather than bytes. It
	 *  travels in the language word, not in a setting of its own. */
	const val UNICODE_CODE_SET = 0x800

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

	/** One language the settings screen offers.
	 *
	 *  [words] is every engine language word the language has here, the one to
	 *  use first of them, and empty for a language this build carries no
	 *  module for. [LOCALES] is what the engine answers with; this is what a
	 *  person is offered, which is a longer list on purpose -- see
	 *  [LANGUAGES]. */
	class Offered(val name: String, val words: List<Int>) {

		/** The engine word to ask for, or nought when this build cannot speak
		 *  it. Nought is also what "whichever app is asking decides" is stored
		 *  as, and the two are the same thing to the engine: there is no
		 *  language nought, so the word is only ever used after a check that
		 *  it is not. */
		val word: Int get() = words.firstOrNull() ?: 0

		val speaks: Boolean get() = words.isNotEmpty()
	}

	/**
	 * Every language the settings screen offers, in the order it offers them.
	 *
	 * Sixteen languages, which is the list this engine set out to cover, and
	 * nineteen entries because three of them have more than one dialect in the
	 * engine and each dialect is a choice of its own.
	 *
	 * Only some of them can be spoken. The engine is a rebuild of the languages
	 * IBM shipped and IBM shipped nine, so Mandarin, Portuguese, Finnish,
	 * Korean, Cantonese, Dutch, Norwegian, Swedish and Danish have no module to
	 * be spoken in at all. Japanese does have one and it is left out of this
	 * build deliberately: its text is Shift-JIS, EUC-JP or one of three
	 * seven-bit JIS sets, and [WesternText] can only hand the engine Latin-1,
	 * so offering it would be offering a language that gets mis-read rather
	 * than mis-spoken. The reason is written down at `evvdroid.langs` in the
	 * build script, which is where the module is dropped.
	 *
	 * They are all still listed. A language a person is looking for and cannot
	 * find is a language they cannot tell the difference between "not here" and
	 * "hidden", and the entry for one this build cannot speak says so in its
	 * own label rather than in a sentence nobody reads -- which matters most on
	 * a screen reader, where the label is the whole of what is heard.
	 */
	val LANGUAGES: List<Offered> = listOf(
		Offered("English (United States)", listOf(0x00010000)),
		Offered("English (United Kingdom)", listOf(0x00010001)),
		Offered("Spanish (Spain)", listOf(0x00020000)),
		Offered("Spanish (Latin America)", listOf(0x00020001)),
		Offered("French (France)", listOf(0x00030000)),
		Offered("French (Canada)", listOf(0x00030001)),
		Offered("German", listOf(0x00040000)),
		Offered("Italian", listOf(0x00050000)),
		Offered("Mandarin", emptyList()),
		Offered("Portuguese", emptyList()),
		Offered("Japanese", emptyList()),
		Offered("Finnish", emptyList()),
		Offered("Korean", emptyList()),
		Offered("Cantonese", emptyList()),
		Offered("Dutch", emptyList()),
		Offered("Norwegian", emptyList()),
		Offered("Swedish", emptyList()),
		Offered("Danish", emptyList()),
		Offered("Polish", listOf(0x00110000))
	)

	/** Whether the engine in this build can speak [offered].
	 *
	 *  Asked of the engine rather than of the list, because the list is what
	 *  the app offers and the modules linked into the binary are what the
	 *  engine has, and the two are allowed to differ: `evvdroid.langs` is a
	 *  build property, and a build made with a shorter one has fewer languages
	 *  in it and the same screen to offer them from. */
	fun speaks(offered: Offered): Boolean =
		offered.words.any { word -> EvvEngine.available.contains(word) }

	/** The entry [word] belongs to, or nought when it is not one of ours. */
	fun offeredFor(word: Int): Offered? {
		val base = baseLanguage(word)
		return LANGUAGES.firstOrNull { entry -> entry.words.any { it == base } }
	}

	/** A language word is the family in the top half, the code set in the third
	 *  byte and the dialect in the bottom one. */
	fun family(language: Int): Int = language and 0xFFFF0000.toInt()

	fun baseLanguage(language: Int): Int = language and UNICODE_CODE_SET.inv()

	private val LOCALES = mapOf(
		0x00010000 to Triple("eng", "USA", ""),
		0x00010001 to Triple("eng", "GBR", ""),
		0x00020000 to Triple("spa", "ESP", ""),
		0x00020001 to Triple("spa", "MEX", ""),
		0x00030000 to Triple("fra", "FRA", ""),
		0x00030001 to Triple("fra", "CAN", ""),
		0x00040000 to Triple("deu", "DEU", ""),
		0x00050000 to Triple("ita", "ITA", ""),
		0x00060000 to Triple("cmn", "CHN", ""),
		0x00060001 to Triple("cmn", "TWN", ""),
		0x00070000 to Triple("por", "BRA", ""),
		0x00080000 to Triple("jpn", "JPN", ""),
		0x00090000 to Triple("fin", "FIN", ""),
		0x000a0000 to Triple("kor", "KOR", ""),
		0x000b0000 to Triple("yue", "CHN", ""),
		0x000b0001 to Triple("yue", "HKG", ""),
		0x000c0000 to Triple("nld", "NLD", ""),
		0x000d0000 to Triple("nor", "NOR", ""),
		0x000e0000 to Triple("swe", "SWE", ""),
		0x000f0000 to Triple("dan", "DNK", ""),
		0x00110000 to Triple("pol", "POL", "")
	)

	/** The ISO-3 language and country an engine language answers to, which is
	 *  what Android asks about. */
	fun localeOf(language: Int): Triple<String, String, String>? =
		LOCALES[baseLanguage(language)]

	fun displayName(language: Int): String {
		val loc = localeOf(language) ?: return "0x%08x".format(language)
		return java.util.Locale(loc.first, loc.second).getDisplayName(java.util.Locale.getDefault())
	}
}
