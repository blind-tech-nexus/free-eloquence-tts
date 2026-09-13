package com.blindtechnexus.freeeloquencetts

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
 * the road sees; the abbreviations run straight after it for the same reason,
 * and are a substitution rather than a pronunciation so they belong with it
 * rather than with the engine's own table. Then comes the code, which takes
 * the names apart and the operators and addresses into words -- after the
 * substitutions, because splitting `getElement` into two words would take an
 * entry away from the text it was written for, and before the symbols, because
 * a named operator is a word the symbol pass has nothing to say about. Then
 * the fixes for the words the engine spells out, and the thousands commas
 * those leave behind, so a number is a run of digits again before it is
 * regrouped. The digits are regrouped next. The single capital letters come
 * after the fixes, because they can add the engine's own backtick annotations,
 * which no rewrite may walk into. Spelling comes after the capitals and not
 * before them, because a word spelled out is nothing but single capitals
 * standing on their own and the capitals pass would annotate every letter of
 * it. Last are the symbols, which never touch a backtick, so an annotation
 * added earlier survives.
 */
object TextPipeline {

	/**
	 * [raw] all the way through the road, as one string.
	 *
	 * Fast and robust: each step is caught, early-exits when it has nothing to
	 * do, and avoids a second copy of the text wherever it can. The order is
	 * the thing the comment above is about and is not a detail.
	 */
	fun apply(
		raw: String,
		s: Settings,
		words: List<Pair<String, String>> = emptyList(),
		abbreviations: List<Abbreviations.Entry> = emptyList()
	): String {
		if (raw.isEmpty()) return raw
		var text = raw
		try { if (s.stripSsml) text = Ssml.strip(text) } catch (_: Exception) {}
		try { text = Emoji.apply(text, s.readEmoji) } catch (_: Exception) {}
		try { text = UserDictionary.apply(text, words) } catch (_: Exception) {}
		if (s.expandAbbreviations) {
			try { text = Abbreviations.apply(text, abbreviations) } catch (_: Exception) {}
		}
		try { text = CodeReading.apply(text, s.codeOptions()) } catch (_: Exception) {}
		try { text = TextFixes.apply(text) } catch (_: Exception) {}
		try { text = Numbers.apply(text, s.numberMode) } catch (_: Exception) {}
		try { text = CapitalLetters.apply(text, s.capitalMode) } catch (_: Exception) {}
		try { text = Spelling.apply(text, s.spellingMode) } catch (_: Exception) {}
		return try { Symbols.apply(text, s.symbolMode, s.customSymbols) } catch (_: Exception) { text }
	}

	/**
	 * [raw] cut into pieces and each piece through the road, one piece at a
	 * time and not before it is asked for.
	 *
	 * This is what a document goes through, and the whole of the reason it
	 * exists is that a document is long. Doing the road over all of it first
	 * is doing it over text that may never be spoken: a person who asks a
	 * reader to read a chapter hears nothing until the last of it has been
	 * rewritten, and a person who swipes away a second later paid for a
	 * rewrite of all of it and heard one sentence. So the pieces are a
	 * sequence rather than a list, and a piece is rewritten when the utterance
	 * reaches it, which puts the first syllable one piece of work behind the
	 * request rather than one document behind it.
	 *
	 * A piece is short by construction -- see [TextPieces.PIECE_CAP] -- so
	 * what is bought is a first syllable after a couple of hundred characters
	 * of work instead of after all of them, and what it costs is one extra
	 * call into the pipeline per piece, which is a function call.
	 *
	 * The pieces are the same bytes in the same order as the ones
	 * [TextPieces.pieces] would have given, and each is put through exactly
	 * the road [apply] puts a whole text through, so what is said is what the
	 * shorter road would have said.
	 */
	fun pieces(
		raw: String,
		s: Settings,
		words: List<Pair<String, String>>,
		abbreviations: List<Abbreviations.Entry>
	): Sequence<TextPieces.Piece> = TextPieces.pieceSequence(raw).map { piece ->
		val said = try {
			apply(piece.text, s, words, abbreviations)
		} catch (_: Exception) {
			piece.text
		}
		// A piece the settings reduced to nothing is not an utterance, and
		// handing one over would be a silence with a pause in it.
		if (said == piece.text) piece
		else if (said.isNotBlank()) TextPieces.Piece(said, piece.forced)
		else TextPieces.Piece("", piece.forced)
	}.filter { it.text.isNotEmpty() }
}
