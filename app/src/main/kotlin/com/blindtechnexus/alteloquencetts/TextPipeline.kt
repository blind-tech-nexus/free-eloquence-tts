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

	fun apply(
		raw: String,
		s: Settings,
		words: List<Pair<String, String>> = emptyList()
	): String {
		var text = raw
		if (s.stripSsml) text = Ssml.strip(text)
		text = Emoji.apply(text, s.readEmoji)
		text = UserDictionary.apply(text, words)
		text = TextFixes.apply(text)
		text = Numbers.apply(text, s.numberMode)
		text = CapitalLetters.apply(text, s.capitalMode)
		return Symbols.apply(text, s.symbolMode, s.customSymbols)
	}
}
