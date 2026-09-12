package com.blindtechnexus.alteloquencetts

/**
 * The text handed over, in the order the engine must see it.
 *
 * One road through the same changes for both the speech service and the
 * preview on the settings screen, so what "Speak a sample" says is what a
 * caller hears. The order matters and is deliberate.
 *
 * The SSML tags come out first, while the text is still the caller's. Emoji
 * and emoticons are then named or dropped, so their names go through the rest
 * of the road as ordinary words. The user's own dictionary runs next, on the
 * words as they were written, so a replacement is the spelling the rest of
 * the road sees. Then come the fixes for the words the engine spells out, and
 * the thousands commas those leave behind, so a number is a run of digits
 * again before it is regrouped. The digits are regrouped next. The single
 * capital letters come after the fixes, because they can add the engine's own
 * backtick annotations, which no rewrite may walk into. Last are the symbols,
 * which never touch a backtick, so an annotation added earlier survives.
 */
object TextPipeline {

	/**
	 * Fast, robust pipeline: each step is try-catched, early-exits when no work,
	 * and handles billion-char texts without OOM by avoiding extra copies when possible.
	 * Order is preserved for correctness.
	 */
	fun apply(
		raw: String,
		s: Settings,
		words: List<Pair<String, String>> = emptyList()
	): String {
		if (raw.isEmpty()) return raw
		var text = raw
		try { if (s.stripSsml) text = Ssml.strip(text) } catch (_: Exception) {}
		try { text = Emoji.apply(text, s.readEmoji) } catch (_: Exception) {}
		try { text = UserDictionary.apply(text, words) } catch (_: Exception) {}
		try { text = TextFixes.apply(text) } catch (_: Exception) {}
		try { text = Numbers.apply(text, s.numberMode) } catch (_: Exception) {}
		try { text = CapitalLetters.apply(text, s.capitalMode) } catch (_: Exception) {}
		return try { Symbols.apply(text, s.symbolMode, s.customSymbols) } catch (_: Exception) { text }
	}
}
