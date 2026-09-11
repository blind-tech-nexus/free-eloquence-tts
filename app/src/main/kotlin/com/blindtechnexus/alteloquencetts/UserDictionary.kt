package com.blindtechnexus.alteloquencetts

import android.content.Context
import java.io.File

/**
 * The words the dictionary manager keeps, and the one file they live in.
 *
 * A dictionary here is a list of pairs: a word as the caller wrote it, and
 * what to say instead. They are kept in one UTF-8 file, one pair to a line, a
 * tab between the word and its replacement -- the same shape as the files
 * people share, so an import is the same reading and an export is the same
 * writing, and the speech service teaches them to the engine a word at a
 * time through the one loader everything else uses.
 *
 * The file lives in device-protected storage, so the service can read it
 * before the phone has been unlocked. Nothing here is loaded at once: the
 * list is read when the screen opens and written whole whenever it changes,
 * which is fine for the few thousand words a dictionary holds.
 */
object UserDictionary {

	private const val NAME = "words.dic"

	/** The file the words live in. Nothing is created here: the service asks
	 *  for this on every utterance to decide whether the words it taught are
	 *  still the words in the file, and a directory made on every utterance is
	 *  a system call for nothing. */
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
		true
	} catch (unwritable: Exception) {
		false
	}

	/** Adds [word] -> [say], replacing an older entry for the same word. */
	fun add(context: Context, word: String, say: String): Boolean {
		val kept = words(context).filterNot { it.first.equals(word, ignoreCase = true) }.toMutableList()
		kept.add(word to say)
		return write(context, kept)
	}

	/** Removes [word]. */
	fun remove(context: Context, word: String): Boolean =
		write(context, words(context).filterNot { it.first == word })

	/** Replaces [oldWord] with [word] -> [say]. */
	fun replace(context: Context, oldWord: String, word: String, say: String): Boolean {
		val kept = words(context).filterNot { it.first == oldWord || it.first.equals(word, ignoreCase = true) }.toMutableList()
		kept.add(word to say)
		return write(context, kept)
	}

	/** Merges [entries] in, a newer pair winning over an older word. */
	fun merge(context: Context, entries: List<Pair<String, String>>): Int {
		val kept = LinkedHashMap<String, Pair<String, String>>()
		for ((word, say) in words(context)) kept[word.lowercase()] = word to say
		var added = 0
		for ((word, say) in entries) {
			if (word.isEmpty() || say.isEmpty()) continue
			if (!kept.containsKey(word.lowercase())) added++
			kept[word.lowercase()] = word to say
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
}
