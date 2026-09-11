package com.blindtechnexus.alteloquencetts

/**
 * How a single capital letter is said, when it stands on its own.
 *
 * A letter alone in the text -- a grade, a menu key, a rating, a single
 * initial -- can be hard to tell from a word of one letter. The choice here
 * is what to do about it, and it is made out of text changes, which is the
 * only lever the engine has.
 *
 * Only a capital letter with no letter beside it is touched. A word, a line
 * of capitals, and a letter inside a word are none of them a single letter,
 * so "I am" keeps its "I" whatever the choice -- it is a word of one letter
 * and not an initial to call out.
 *
 * The three ways to say it:
 *
 * [NONE] leaves the engine alone. The letter is said the way it always was,
 * so nobody's speech changes when this arrives.
 *
 * [CAP] puts the word "cap" in front of it, so a capital is heard as "cap A"
 * rather than as a bare "A".
 *
 * [PITCH] says the letter at a higher pitch, the engine's own emphasis
 * annotation (`` `vbst+6 `` is six semitones up, and the matching `` `vbst-6 ``
 * puts the voice back). The letter is the same letter, only higher, which is
 * the one way of marking it that does not add a word.
 */
object CapitalLetters {

	/** The engine's own reading. */
	const val NONE = 0

	/** Say "cap" before the letter. */
	const val CAP = 1

	/** Say the letter at a higher pitch. */
	const val PITCH = 2

	/** The levels, in the order the settings screen offers them. */
	val MODES = listOf(NONE, CAP, PITCH)

	/** A capital letter with no letter on either side. The apostrophe is
	 *  treated as part of a word, so the "I" of "I'm" is not an initial. */
	private val SINGLE_CAPITAL = Regex("(?<![A-Za-z'])([A-Z])(?![A-Za-z'])")

	private const val UP = "`vbst+6 "
	private const val DOWN = "`vbst-6 "

	/** [text] with every single capital letter treated the way [mode] asks. */
	fun apply(text: String, mode: Int): String = when (mode) {
		CAP -> SINGLE_CAPITAL.replace(text) { " cap ${it.value} " }
		PITCH -> SINGLE_CAPITAL.replace(text) { " $UP${it.value} $DOWN" }
		else -> text
	}
}
