package com.blindtechnexus.alteloquencetts

import android.content.Context
import java.io.File
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

/**
 * The words the dictionary manager keeps, and the one file they live in.
 *
 * A dictionary here is a list of pairs: a word as the caller wrote it, and
 * what to say instead. They are kept in one UTF-8 file, one pair to a line, a
 * tab between the word and its replacement -- the same shape as the files
 * people share, so an import is the same reading and an export is the same
 * writing.
 *
 * They are not taught to the engine. IBM's main dictionary is case-sensitive
 * -- "WHO" is not "who" -- and a screen reader hands over whatever capital
 * letters the control had, so a word added as "doj" was never heard in "DOJ"
 * and the manager looked as if it did nothing at all. The replacements run
 * over the text instead, without regard to case, as whole words and as the
 * phrases a shared file carries, on the same road the rest of the utterance
 * takes. The engine then sees ordinary words, or its own pronunciation
 * alphabet, and speaks them.
 *
 * The file lives in device-protected storage, so the service can read it
 * before the phone has been unlocked. Nothing here is loaded at once: the
 * list is read when the screen opens and written whole whenever it changes,
 * which is fine for the few thousand words a dictionary holds.
 */
object UserDictionary {

	private const val NAME = "words.dic"

	/** Goes up on every successful write, so the speech service can tell the
	 *  words it last read are still the words in the file without asking the
	 *  filesystem on every utterance. */
	private val revisionCount = AtomicInteger(0)

	val revision: Int get() = revisionCount.get()

	private val matcherLock = Any()
	private var matcherEntries: List<Pair<String, String>>? = null
	private var matcher: WordReplacer? = null

	/** The file the words live in. Nothing is created here: a directory made
	 *  on every utterance is a system call for nothing, and the service asks
	 *  for the words by revision rather than by walking the path each time. */
	fun file(context: Context): File = File(DirectBoot.dictionaries(context), NAME)

	/** [file], with the directory that holds it made. What the dictionary
	 *  screen goes through when it has something to write. */
	fun fileForWriting(context: Context): File {
		val into = DirectBoot.dictionariesReady(context)
		return File(into, NAME)
	}

	/** The pairs in order, word first, then what to say. */
	fun words(context: Context): List<Pair<String, String>> {
		val text = runCatching { file(context).readText() }.getOrNull() ?: return emptyList()
		return parse(text)
	}

	/** Writes [entries] over the file. Returns whether it was written. */
	fun write(context: Context, entries: List<Pair<String, String>>): Boolean = try {
		val body = buildString {
			for ((word, say) in entries) {
				append(word)
				append('\t')
				append(say)
				append('\n')
			}
		}
		fileForWriting(context).writeText(body)
		revisionCount.incrementAndGet()
		true
	} catch (unwritable: Exception) {
		false
	}

	/** Adds [word] -> [say], replacing an older entry for the same word,
	 *  without regard to case. */
	fun add(context: Context, word: String, say: String): Boolean {
		val kept = words(context).filterNot { sameWord(it.first, word) }.toMutableList()
		kept.add(word to say)
		return write(context, kept)
	}

	/** Removes [word], without regard to case. */
	fun remove(context: Context, word: String): Boolean =
		write(context, words(context).filterNot { sameWord(it.first, word) })

	/** Replaces [oldWord] with [word] -> [say], without regard to case. */
	fun replace(context: Context, oldWord: String, word: String, say: String): Boolean {
		val kept = words(context).filterNot {
			sameWord(it.first, oldWord) || sameWord(it.first, word)
		}.toMutableList()
		kept.add(word to say)
		return write(context, kept)
	}

	/** Merges [entries] in, a newer pair winning over an older word, without
	 *  regard to case. */
	fun merge(context: Context, entries: List<Pair<String, String>>): Int {
		val kept = LinkedHashMap<String, Pair<String, String>>()
		for ((word, say) in words(context)) kept[foldKey(word)] = word to say
		var added = 0
		for ((word, say) in entries) {
			if (word.isEmpty() || say.isEmpty()) continue
			val key = foldKey(word)
			if (!kept.containsKey(key)) added++
			kept[key] = word to say
		}
		write(context, kept.values.toList())
		return added
	}

	/** The pairs from the text of an imported file. */
	fun parse(text: String): List<Pair<String, String>> {
		val out = ArrayList<Pair<String, String>>()
		for (line in text.lineSequence()) {
			val split = line.indexOf('\t')
			if (split <= 0) continue
			val word = line.substring(0, split).trim()
			val say = line.substring(split + 1).trim()
			if (word.isNotEmpty() && say.isNotEmpty()) out.add(word to say)
		}
		return out
	}

	/**
	 * [text] with every dictionary word rewritten as what to say instead.
	 *
	 * Matching does not care about capital letters: "DOJ", "doj" and "Doj"
	 * are the same word. A match has to be a whole word -- or a whole phrase,
	 * where the key has a space in it -- so "cat" does not take the front off
	 * "category". The replacement is used as it was written.
	 *
	 * [entries] is the list [words] returned. The matcher built from it is
	 * kept for as long as the same list is handed over, which is what the
	 * speech service does between writes.
	 */
	fun apply(text: String, entries: List<Pair<String, String>>): String {
		if (text.isEmpty() || entries.isEmpty()) return text
		val replacer = synchronized(matcherLock) {
			if (matcherEntries === entries) {
				matcher ?: WordReplacer(entries).also { matcher = it }
			} else {
				WordReplacer(entries).also {
					matcher = it
					matcherEntries = entries
				}
			}
		}
		return replacer.apply(text)
	}

	private fun sameWord(a: String, b: String): Boolean = a.equals(b, ignoreCase = true)

	private fun foldKey(word: String): String = word.lowercase()
}

/**
 * One pass over the text, answering the original string when nothing in it
 * is a dictionary word.
 *
 * The keys live in a trie of folded characters, so a few thousand of them
 * cost one walk from each word that starts, not a scan of the list. A match
 * is kept only when the next character is not still a word character, which
 * is what makes "cat" leave "category" alone and what lets "new york" win
 * over "new" where both are present.
 */
private class WordReplacer(entries: List<Pair<String, String>>) {

	private class Node {
		val next = HashMap<Char, Node>()
		var say: String? = null
	}

	private val root = Node()
	private val empty: Boolean

	init {
		for ((word, say) in entries) {
			if (word.isEmpty() || say.isEmpty()) continue
			var node = root
			for (at in word.indices) {
				val k = fold(word[at])
				node = node.next.getOrPut(k) { Node() }
			}
			node.say = say
		}
		empty = root.next.isEmpty()
	}

	fun apply(text: String): String {
		if (empty) return text
		val n = text.length
		val out = StringBuilder(n)
		var i = 0
		var changed = false
		while (i < n) {
			val canStart = i == 0 || !isWordChar(text[i - 1]) || !isWordChar(text[i])
			if (canStart) {
				val hit = longest(text, i)
				if (hit != null) {
					out.append(hit.first)
					i += hit.second
					changed = true
					continue
				}
			}
			out.append(text[i])
			i++
		}
		return if (changed) out.toString() else text
	}

	private fun longest(text: String, from: Int): Pair<String, Int>? {
		var node = root
		var best: Pair<String, Int>? = null
		var j = from
		while (j < text.length) {
			node = node.next[fold(text[j])] ?: break
			j++
			val say = node.say
			if (say != null && !isWordChar(text, j)) {
				best = say to (j - from)
			}
		}
		return best
	}

	private fun fold(c: Char): Char = Character.toLowerCase(c)

	private fun isWordChar(c: Char): Boolean = c.isLetterOrDigit() || c == '\''

	private fun isWordChar(text: String, index: Int): Boolean =
		index >= 0 && index < text.length && isWordChar(text[index])
}
