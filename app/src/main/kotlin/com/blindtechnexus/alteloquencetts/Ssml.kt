package com.blindtechnexus.alteloquencetts

/**
 * SSML and other XML-like tags, taken out of the text before it is read.
 *
 * The engine has an SSML reader of its own, but nothing in this app turns it
 * on, so a tag such as "&lt;break time=\"1s\"/&gt;" would be spoken -- or half
 * spoken -- rather than obeyed. A screen reader hands over plain text, but
 * other callers do not, and a tag in the middle of a sentence is a string of
 * words nobody asked to hear. Stripping the tags before anything else runs is
 * what "ignore SSML tags" means here: the words between the tags are kept,
 * the tags themselves are not.
 *
 * A tag is an opening angle bracket, anything but another angle bracket, and
 * the closing bracket, so a tag with a malformed inside is still taken out
 * whole. A stray &lt; with no closing bracket is left for the symbols to read,
 * which is the safer of the two wrong answers: "less than" is better than a
 * tag swallowed to the end of the line.
 */
object Ssml {

	private val TAG = Regex("<[^>]*>")
	private val SPACES = Regex(" {2,}")

	/** [text] with every tag taken out, the double spaces a removed tag leaves
	 *  behind run back into one, and the ends trimmed. */
	fun strip(text: String): String {
		if ('<' !in text) return text
		return SPACES.replace(TAG.replace(text, " "), " ").trim()
	}
}
