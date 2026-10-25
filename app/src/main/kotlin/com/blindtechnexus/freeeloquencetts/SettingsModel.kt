package com.blindtechnexus.freeeloquencetts

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.UUID

/**
 * What the settings screen is showing, and the engine it can hear itself on.
 *
 * The screen edits engine numbers and shows percentages, and this is where the
 * two meet. Everything written here goes straight into [Settings], which is
 * what the speech service reads, so a change is in force before the sample
 * that demonstrates it has finished.
 */
class SettingsModel(context: Context) {

	private val app = context.applicationContext
	private val settings = Settings(app)

	/** The engine the sample is spoken on. One instance at a time, opened in
	 *  whichever English is chosen, and nothing about it can be changed
	 *  afterwards except the voice. */
	private val preview: EvvPreview = EvvPreview()

	/** Which English this app speaks. Two of them, and the choice is what
	 *  every utterance from anywhere without a locale of its own is spoken
	 *  in; see [Settings.language]. */
	var language by mutableStateOf(settings.language)
		private set

	/** Where [language] sits in the offered list, for a button that offers
	 *  them. */
	val languageIndex: Int
		get() = Eci.LANGUAGES.indexOfFirst { it.code == language }.coerceAtLeast(0)

	/** The built-in preset underneath the selected profile. */
	var voice by mutableStateOf(settings.activeVoicePreset)
		private set

	var customVoices by mutableStateOf(settings.customVoices())
		private set

	var activeCustomVoiceId by mutableStateOf(settings.activeCustomVoiceId)
		private set

	val voiceProfileNames: List<String>
		get() = Eci.PRESET_NAMES.toList() + customVoices.map { it.name }

	val selectedVoiceProfile: Int
		get() {
			val id = activeCustomVoiceId ?: return voice.coerceIn(Eci.PRESET_NAMES.indices)
			val at = customVoices.indexOfFirst { it.id == id }
			return if (at >= 0) Eci.PRESET_NAMES.size + at else voice.coerceIn(Eci.PRESET_NAMES.indices)
		}

	val voiceProfileName: String
		get() = voiceProfileNames.getOrElse(selectedVoiceProfile) {
			Eci.PRESET_NAMES.getOrElse(voice) { Eci.PRESET_NAMES[0] }
		}

	var expandAbbreviations by mutableStateOf(settings.expandAbbreviations)
		private set

	var codeMode by mutableStateOf(settings.codeMode)
		private set

	var codeCamelCase by mutableStateOf(settings.codeCamelCase)
		private set

	var codeSnakeCase by mutableStateOf(settings.codeSnakeCase)
		private set

	var codeOperators by mutableStateOf(settings.codeOperators)
		private set

	var codeBraces by mutableStateOf(settings.codeBraces)
		private set

	var codeIndentation by mutableStateOf(settings.codeIndentation)
		private set

	var codePaths by mutableStateOf(settings.codePaths)
		private set

	var codeUrls by mutableStateOf(settings.codeUrls)
		private set

	var spellingMode by mutableStateOf(settings.spellingMode)
		private set

	var abbreviations by mutableStateOf(settings.abbreviations)
		private set

	var sampleRateHz by mutableStateOf(settings.sampleRateHz)
		private set

	var audioOptimization by mutableStateOf(settings.audioOptimization)
		private set

	var pauses by mutableStateOf(settings.pauses)
		private set

	var phrasePrediction by mutableStateOf(settings.phrasePrediction)
		private set

	var symbolMode by mutableStateOf(settings.symbolMode)
		private set

	var customSymbols by mutableStateOf(settings.customSymbols)
		private set

	var readEmoji by mutableStateOf(settings.readEmoji)
		private set

	var numberMode by mutableStateOf(settings.numberMode)
		private set

	var readOtpDigits by mutableStateOf(settings.readOtpDigits)
		private set

	var forceLanguage by mutableStateOf(settings.forceLanguage)
		private set

	var autoLanguageSwitching by mutableStateOf(settings.autoLanguageSwitching)
		private set

	var synthesisRouting by mutableStateOf(settings.synthesisRouting)
		private set

	var stripSsml by mutableStateOf(settings.stripSsml)
		private set

	var readTextStyles by mutableStateOf(settings.readTextStyles)
		private set

	var capitalMode by mutableStateOf(settings.capitalMode)
		private set

	var forceRate by mutableStateOf(settings.forceRate)
		private set

	var forcePitch by mutableStateOf(settings.forcePitch)
		private set

	var forceVolume by mutableStateOf(settings.forceVolume)
		private set

	private val shape = mutableStateMapOf<Int, Int>().apply { putAll(settings.activeVoiceShape()) }

	val gender: Int get() = shape[Eci.VOICE_GENDER] ?: Eci.defaultGender(voice)

	init {
		openPreview()
	}

	/** Opens the preview in the chosen English, and takes the voice's own
	 *  settings from the engine once it is there. The same work happens again
	 *  when the language changes, because the two voices are two engines and
	 *  a slider left at one English's preset would be a lie about the
	 *  other's. */
	private fun openPreview() {
		preview.open(language) {
			// A preset's own settings are what its sliders start at. A custom
			// profile already carries a complete shape and must never be
			// replaced by the preset underneath it.
			if (settings.activeCustomVoiceId == null && !settings.shapeIsCustom(settings.voice)) adoptVoice()
			shape.clear()
			shape.putAll(settings.activeVoiceShape())
		}
	}

	fun stopSpeaking() {
		preview.stopSpeaking()
	}

	fun close() {
		preview.close()
		settings.close()
	}

	fun percentOf(param: Int): Int = Eci.toPercent(param, shape[param] ?: 0)

	// ---- what the screen changes -----------------------------------------

	fun chooseVoice(which: Int) {
		val settled = which.coerceIn(Eci.PRESET_NAMES.indices)
		activeCustomVoiceId = null
		settings.activeCustomVoiceId = null
		voice = settled
		settings.voice = settled
		// A voice that has been changed keeps its changes. One that has not
		// takes what the engine says it is.
		if (!settings.shapeIsCustom(settled)) adoptVoice()
		shape.clear()
		shape.putAll(settings.shape(settled))
	}

	/** Selects either a built-in preset or one of the listener's profiles from
	 * the single Voice profile list shown on screen. */
	fun chooseVoiceProfile(which: Int) {
		if (which < Eci.PRESET_NAMES.size) {
			chooseVoice(which)
			return
		}
		val profile = customVoices.getOrNull(which - Eci.PRESET_NAMES.size) ?: return
		voice = profile.baseVoice
		settings.voice = profile.baseVoice
		settings.activeCustomVoiceId = profile.id
		activeCustomVoiceId = profile.id
		shape.clear()
		shape.putAll(profile.shape)
	}

	/** A complete starting shape for the custom-voice editor. */
	fun draftShape(baseVoice: Int): Map<Int, Int> {
		val settled = baseVoice.coerceIn(Eci.PRESET_NAMES.indices)
		val own = preview.presetShape(settled)
		val out = LinkedHashMap<Int, Int>()
		out.putAll(if (own.isNotEmpty()) own else settings.shape(settled))
		for (param in Settings.SHAPE) {
			if (!out.containsKey(param)) {
				out[param] = when (param) {
					Eci.VOICE_GENDER -> Eci.defaultGender(settled)
					Eci.VOICE_SPEED -> settings.speed
					Eci.VOICE_VOLUME -> 80
					else -> 50
				}
			}
		}
		out[Eci.VOICE_SPEED] = shape[Eci.VOICE_SPEED] ?: settings.speed
		return out
	}

	fun createCustomVoice(name: String, baseVoice: Int, values: Map<Int, Int>): Boolean {
		val clean = name.trim().replace(Regex("\\s+"), " ").take(CustomVoiceProfile.MAX_NAME_LENGTH)
		if (clean.isEmpty()) return false
		if (Eci.PRESET_NAMES.any { it.equals(clean, ignoreCase = true) } ||
			customVoices.any { it.name.equals(clean, ignoreCase = true) }
		) return false
		val profile = CustomVoiceProfile(
			id = UUID.randomUUID().toString(),
			name = clean,
			baseVoice = baseVoice.coerceIn(Eci.PRESET_NAMES.indices),
			shape = values
		).normalized()
		settings.saveCustomVoice(profile)
		customVoices = settings.customVoices()
		val at = customVoices.indexOfFirst { it.id == profile.id }
		if (at < 0) return false
		chooseVoiceProfile(Eci.PRESET_NAMES.size + at)
		return true
	}

	fun deleteActiveCustomVoice() {
		val id = activeCustomVoiceId ?: return
		val base = voice
		settings.deleteCustomVoice(id)
		customVoices = settings.customVoices()
		activeCustomVoiceId = null
		chooseVoice(base)
	}

	/** Another of the eight. Everything else is left alone: the voice, the
	 *  shape and the speed are the listener's and not the language's, and a
	 *  screen that reset them for changing language would be a screen nobody
	 *  would use twice. */
	fun chooseLanguage(code: Int) {
		val settled = Eci.languageOrDefault(code)
		settings.language = settled
		language = settled
		openPreview()
	}

	fun setPercent(param: Int, percent: Int) = setShape(param, Eci.fromPercent(param, percent))

	fun setShape(param: Int, value: Int) {
		val settled = Eci.clampVoice(param, value)
		if (shape[param] == settled) return
		shape[param] = settled
		val customId = activeCustomVoiceId
		if (customId == null) {
			settings.setShapeValue(voice, param, settled)
		} else {
			val profile = customVoices.firstOrNull { it.id == customId } ?: return
			settings.saveCustomVoice(profile.copy(shape = LinkedHashMap(shape)))
			customVoices = settings.customVoices()
		}
	}

	/** Restores the selected preset, or the base preset underneath a custom
	 * profile. The custom profile keeps its name and selection while its tuning
	 * returns to a clean starting point. */
	fun resetVoice() {
		val customId = activeCustomVoiceId
		if (customId == null) {
			settings.clearShape(voice)
			adoptVoice()
			shape.clear()
			shape.putAll(settings.shape(voice))
			return
		}
		val profile = customVoices.firstOrNull { it.id == customId } ?: return
		val defaults = draftShape(profile.baseVoice)
		val reset = profile.copy(shape = defaults)
		settings.saveCustomVoice(reset)
		customVoices = settings.customVoices()
		shape.clear()
		shape.putAll(reset.shape)
	}

	fun chooseAbbreviations(on: Boolean) {
		abbreviations = on
		settings.abbreviations = on
	}

	fun chooseExpandAbbreviations(on: Boolean) {
		expandAbbreviations = on
		settings.expandAbbreviations = on
	}

	fun chooseCodeMode(on: Boolean) {
		codeMode = on
		settings.codeMode = on
	}

	fun chooseCodeCamelCase(on: Boolean) {
		codeCamelCase = on
		settings.codeCamelCase = on
	}

	fun chooseCodeSnakeCase(on: Boolean) {
		codeSnakeCase = on
		settings.codeSnakeCase = on
	}

	fun chooseCodeOperators(on: Boolean) {
		codeOperators = on
		settings.codeOperators = on
	}

	fun chooseCodeBraces(on: Boolean) {
		codeBraces = on
		settings.codeBraces = on
	}

	fun chooseCodeIndentation(on: Boolean) {
		codeIndentation = on
		settings.codeIndentation = on
	}

	fun chooseCodePaths(on: Boolean) {
		codePaths = on
		settings.codePaths = on
	}

	fun chooseCodeUrls(on: Boolean) {
		codeUrls = on
		settings.codeUrls = on
	}

	fun chooseSpellingMode(mode: Int) {
		spellingMode = mode
		settings.spellingMode = mode
	}

	fun chooseReadEmoji(on: Boolean) {
		readEmoji = on
		settings.readEmoji = on
	}

	fun chooseNumberMode(mode: Int) {
		numberMode = mode
		settings.numberMode = mode
	}

	fun chooseReadOtpDigits(on: Boolean) {
		readOtpDigits = on
		settings.readOtpDigits = on
	}

	fun chooseForceLanguage(on: Boolean) {
		forceLanguage = on
		settings.forceLanguage = on
	}

	fun chooseAutoLanguageSwitching(on: Boolean) {
		autoLanguageSwitching = on
		settings.autoLanguageSwitching = on
	}

	fun chooseSynthesisRouting(route: Int) {
		val settled = SynthesisRouting.clamp(route)
		synthesisRouting = settled
		settings.synthesisRouting = settled
	}

	fun chooseStripSsml(on: Boolean) {
		stripSsml = on
		settings.stripSsml = on
	}

	fun chooseReadTextStyles(on: Boolean) {
		readTextStyles = on
		settings.readTextStyles = on
	}

	fun chooseCapitalMode(mode: Int) {
		capitalMode = mode
		settings.capitalMode = mode
	}

	fun choosePhrasePrediction(on: Boolean) {
		phrasePrediction = on
		settings.phrasePrediction = on
	}

	fun chooseSymbolMode(mode: Int) {
		symbolMode = mode
		settings.symbolMode = mode
	}

	fun chooseCustomSymbols(list: String) {
		customSymbols = list
		settings.customSymbols = list
	}

	fun chooseForceRate(on: Boolean) {
		forceRate = on
		settings.forceRate = on
	}

	fun chooseForcePitch(on: Boolean) {
		forcePitch = on
		settings.forcePitch = on
	}

	fun chooseForceVolume(on: Boolean) {
		forceVolume = on
		settings.forceVolume = on
	}

	fun choosePauses(mode: Int) {
		pauses = mode
		settings.pauses = mode
	}

	fun chooseSampleRate(hz: Int) {
		val settled = Eci.nearestSampleRate(hz)
		sampleRateHz = settled
		settings.sampleRateHz = settled
	}

	fun chooseAudioOptimization(mode: Int) {
		audioOptimization = mode
		settings.audioOptimization = mode
	}

	// ---- hearing it ------------------------------------------------------

	/** The sample in the language that will speak it: a German voice
	 *  reading an English sentence is a sample of nothing. */
	private fun previewTextOf(code: Int): Int = when (code) {
		Eci.GERMAN -> R.string.preview_text_de
		Eci.SPANISH_SPAIN, Eci.SPANISH_MEXICO -> R.string.preview_text_es
		Eci.FRENCH_FRANCE, Eci.FRENCH_CANADA -> R.string.preview_text_fr
		Eci.ITALIAN -> R.string.preview_text_it
		Eci.HINDI -> R.string.preview_text_hi
		Eci.NEPALI -> R.string.preview_text_ne
		else -> R.string.preview_text
	}

	/** Nothing here speaks by itself. Every setting is in force the moment it
	 *  is written down, and this is how it gets heard. The sample goes through
	 *  the same text road as a caller's speech, so what is heard is what the
	 *  setting really does. */
	fun say() {
		val prepared = preparedPreviewText()
		preview.say(
			prepared,
			settings.activeVoicePreset,
			settings.activeVoiceShape(),
			sampleRateHz,
			settings.abbreviations,
			settings.audioOptimization
		)
	}

	/** Hears a custom profile before saving it. */
	fun sayDraft(baseVoice: Int, values: Map<Int, Int>) {
		preview.say(
			preparedPreviewText(),
			baseVoice.coerceIn(Eci.PRESET_NAMES.indices),
			values,
			sampleRateHz,
			settings.abbreviations,
			settings.audioOptimization
		)
	}

	private fun preparedPreviewText(): String {
		// The same snapshot the service takes, so the sample hears the list
		// that is in force rather than a fresh read of the file behind it.
		val dictionary = runCatching { UserDictionary.snapshot(app) }.getOrNull()
		val text = TextPipeline.apply(
			app.getString(previewTextOf(language)),
			settings,
			dictionary?.index ?: WordIndex.EMPTY,
			Abbreviations.entries(app)
		)
		return Prosody.prefix(settings.phrasePrediction) +
			Pauses.apply(text, settings.pauses, true)
	}

	private fun adoptVoice() {
		val own = preview.presetShape(settings.voice)
		if (own.isEmpty()) return
		// Everything but the speed, which stays where the listener put it.
		// Glen and Sandy ship at 70 where the rest are 50, so taking the
		// voice's own would move the slider and the pace on every change.
		settings.writeShape(settings.voice, own - Eci.VOICE_SPEED)
	}

	companion object {
		val RATES: List<Int> = Eci.SAMPLE_RATES.sorted()

	}
}
