package com.blindtechnexus.freeeloquencetts

/**
 * A voice made by the listener.
 *
 * [baseVoice] is one of the built-in presets. [shape] is the complete set of
 * parameters laid over that preset, including speed and gender, so choosing a
 * custom profile restores exactly the voice that was saved. The profile has a
 * stable [id] because names are for people and may be changed or reused.
 */
data class CustomVoiceProfile(
	val id: String,
	val name: String,
	val baseVoice: Int,
	val shape: Map<Int, Int>
) {
	fun normalized(): CustomVoiceProfile {
		val cleanName = name.trim().replace(Regex("\\s+"), " ").take(MAX_NAME_LENGTH)
		val cleanBase = baseVoice.coerceIn(Eci.PRESET_NAMES.indices)
		val cleanShape = LinkedHashMap<Int, Int>()
		for (param in Settings.SHAPE) {
			shape[param]?.let { cleanShape[param] = Eci.clampVoice(param, it) }
		}
		if (!cleanShape.containsKey(Eci.VOICE_SPEED)) {
			cleanShape[Eci.VOICE_SPEED] = Eci.DEFAULT_SPEED
		}
		return copy(name = cleanName, baseVoice = cleanBase, shape = cleanShape)
	}

	companion object {
		const val MAX_NAME_LENGTH = 40
	}
}
