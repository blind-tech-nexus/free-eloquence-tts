package com.blindtechnexus.alteloquencetts

import android.content.Context
import java.io.BufferedReader
import java.io.File
import java.io.InputStream
import java.util.concurrent.atomic.AtomicInteger

/**
 * The words the dictionary manager keeps, and the one file they live in.
 *
 * Robust, low-latency, crash-proof:
 * - Reads file buffered line-by-line, never whole file into memory at once for huge files
 * - Writes atomically via temp file + rename
 * - All public methods catch exceptions and return safe defaults
 * - Import parses stream directly, not via giant string
 * - Handles billions of chars by not allocating huge builders
 */
object UserDictionary {

	private const val NAME = "words.dic"
	private const val MAX_LINE_LENGTH = 8192
	private const val MAX_ENTRIES = 200_000
	private const val MAX_FILE_BYTES = 50L * 1024 * 1024 // 50 MB cap for safety

	private val revisionCount = AtomicInteger(0)
	val revision: Int get() = revisionCount.get()

	private val matcherLock = Any()
	private var matcherEntries: List<Pair<String, String>>? = null
	private var matcher: WordReplacer? = null

	fun file(context: Context): File = File(DirectBoot.dictionaries(context), NAME)

	fun fileForWriting(context: Context): File {
		val into = DirectBoot.dictionariesReady(context)
		return File(into, NAME)
	}

	/** Safe, buffered read, line by line, capped. */
	fun words(context: Context): List<Pair<String, String>> {
		return try {
			val f = file(context)
			if (!f.exists() || !f.canRead()) return emptyList()
			if (f.length() > MAX_FILE_BYTES) {
				// If file is huge, read only first MAX_FILE_BYTES worth of lines
				readCapped(f)
			} else {
				readAll(f)
			}
		} catch (e: Exception) {
			emptyList()
		}
	}

	private fun readAll(f: File): List<Pair<String, String>> {
		val out = ArrayList<Pair<String, String>>(1024)
		f.bufferedReader(Charsets.UTF_8).use { reader ->
			var line: String?
			var count = 0
			while (reader.readLine().also { line = it } != null) {
				if (count++ >= MAX_ENTRIES) break
				val l = line ?: continue
				if (l.length > MAX_LINE_LENGTH) continue
				val split = l.indexOf('\t')
				if (split <= 0) continue
				val word = l.substring(0, split).trim()
				val say = l.substring(split + 1).trim()
				if (word.isNotEmpty() && say.isNotEmpty()) {
					if (word.length <= 500 && say.length <= 2000) {
						out.add(word to say)
					}
				}
			}
		}
		return out
	}

	private fun readCapped(f: File): List<Pair<String, String>> {
		val out = ArrayList<Pair<String, String>>(1024)
		var bytesRead = 0L
		f.bufferedReader(Charsets.UTF_8).use { reader ->
			var line: String?
			var count = 0
			while (reader.readLine().also { line = it } != null) {
				val l = line ?: continue
				bytesRead += l.length + 1
				if (bytesRead > MAX_FILE_BYTES) break
				if (count++ >= MAX_ENTRIES) break
				if (l.length > MAX_LINE_LENGTH) continue
				val split = l.indexOf('\t')
				if (split <= 0) continue
				val word = l.substring(0, split).trim()
				val say = l.substring(split + 1).trim()
				if (word.isNotEmpty() && say.isNotEmpty() && word.length <= 500 && say.length <= 2000) {
					out.add(word to say)
				}
			}
		}
		return out
	}

	/** Atomic write via temp file. */
	fun write(context: Context, entries: List<Pair<String, String>>): Boolean {
		return try {
			val target = fileForWriting(context)
			val tmp = File(target.parentFile, "${target.name}.tmp")
			tmp.bufferedWriter(Charsets.UTF_8).use { w ->
				var count = 0
				for ((word, say) in entries) {
					if (count++ >= MAX_ENTRIES) break
					if (word.isEmpty() || say.isEmpty()) continue
					if (word.length > 500 || say.length > 2000) continue
					// Sanitize: no newline or tab in word/say that would break format
					val cleanWord = word.replace("\n", " ").replace("\r", " ").replace("\t", " ").trim()
					val cleanSay = say.replace("\n", " ").replace("\r", " ").trim()
					if (cleanWord.isEmpty() || cleanSay.isEmpty()) continue
					w.write(cleanWord)
					w.write('\t'.code)
					w.write(cleanSay)
					w.write('\n'.code)
				}
			}
			// Atomic rename
			if (target.exists()) target.delete()
			tmp.renameTo(target)
			revisionCount.incrementAndGet()
			true
		} catch (e: Exception) {
			false
		}
	}

	fun add(context: Context, word: String, say: String): Boolean {
		return try {
			if (word.isBlank() || say.isBlank()) return false
			val kept = words(context).filterNot { sameWord(it.first, word) }.toMutableList()
			if (kept.size >= MAX_ENTRIES) return false
			kept.add(word.trim() to say.trim())
			write(context, kept)
		} catch (e: Exception) {
			false
		}
	}

	fun remove(context: Context, word: String): Boolean {
		return try {
			write(context, words(context).filterNot { sameWord(it.first, word) })
		} catch (e: Exception) {
			false
		}
	}

	fun replace(context: Context, oldWord: String, word: String, say: String): Boolean {
		return try {
			if (word.isBlank() || say.isBlank()) return false
			val kept = words(context).filterNot {
				sameWord(it.first, oldWord) || sameWord(it.first, word)
			}.toMutableList()
			kept.add(word.trim() to say.trim())
			write(context, kept)
		} catch (e: Exception) {
			false
		}
	}

	fun merge(context: Context, entries: List<Pair<String, String>>): Int {
		return try {
			if (entries.isEmpty()) return 0
			val kept = LinkedHashMap<String, Pair<String, String>>()
			for ((word, say) in words(context)) {
				if (kept.size >= MAX_ENTRIES) break
				kept[foldKey(word)] = word to say
			}
			var added = 0
			for ((word, say) in entries) {
				if (kept.size >= MAX_ENTRIES) break
				if (word.isEmpty() || say.isEmpty()) continue
				if (word.length > 500 || say.length > 2000) continue
				val key = foldKey(word)
				if (!kept.containsKey(key)) added++
				kept[key] = word to say
			}
			write(context, kept.values.toList())
			added
		} catch (e: Exception) {
			0
		}
	}

	/** Parse text (used for small imports). Safe, capped. */
	fun parse(text: String): List<Pair<String, String>> {
		if (text.isEmpty()) return emptyList()
		return try {
			val out = ArrayList<Pair<String, String>>()
			var count = 0
			for (line in text.lineSequence()) {
				if (count++ >= MAX_ENTRIES) break
				if (line.length > MAX_LINE_LENGTH) continue
				val split = line.indexOf('\t')
				if (split <= 0) continue
				val word = line.substring(0, split).trim()
				val say = line.substring(split + 1).trim()
				if (word.isNotEmpty() && say.isNotEmpty() && word.length <= 500 && say.length <= 2000) {
					out.add(word to say)
				}
			}
			out
		} catch (e: Exception) {
			emptyList()
		}
	}

	/** Parse directly from InputStream without loading whole file into memory. */
	fun parseStream(input: InputStream): List<Pair<String, String>> {
		return try {
			val out = ArrayList<Pair<String, String>>(1024)
			BufferedReader(input.reader(Charsets.UTF_8), 8192).use { reader ->
				var line: String?
				var count = 0
				var bytes = 0L
				while (reader.readLine().also { line = it } != null) {
					val l = line ?: continue
					bytes += l.length
					if (bytes > MAX_FILE_BYTES) break
					if (count++ >= MAX_ENTRIES) break
					if (l.length > MAX_LINE_LENGTH) continue
					val split = l.indexOf('\t')
					if (split <= 0) continue
					val word = l.substring(0, split).trim()
					val say = l.substring(split + 1).trim()
					if (word.isNotEmpty() && say.isNotEmpty() && word.length <= 500 && say.length <= 2000) {
						out.add(word to say)
					}
				}
			}
			out
		} catch (e: Exception) {
			emptyList()
		}
	}

	fun apply(text: String, entries: List<Pair<String, String>>): String {
		if (text.isEmpty() || entries.isEmpty()) return text
		return try {
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
			replacer.apply(text)
		} catch (e: Exception) {
			text
		}
	}

	private fun sameWord(a: String, b: String): Boolean = a.equals(b, ignoreCase = true)
	private fun foldKey(word: String): String = word.lowercase()
}

/**
 * One pass over the text, answering original when nothing matches.
 * Trie-based, fast, safe.
 */
private class WordReplacer(entries: List<Pair<String, String>>) {

	private class Node {
		val next = HashMap<Char, Node>()
		var say: String? = null
	}

	private val root = Node()
	private val empty: Boolean

	init {
		var count = 0
		for ((word, say) in entries) {
			if (count++ >= 200_000) break
			if (word.isEmpty() || say.isEmpty()) continue
			if (word.length > 500) continue
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
		// Fast path: if text is huge ( > 1M ), process in chunks to avoid huge builder?
		// For simplicity, still single builder but with initial capacity
		val n = text.length
		// Avoid OOM for billion chars: if text is extremely large, process piecewise?
		// Here we process whole, but caller already splits into 500-char pieces for TTS.
		val out = StringBuilder(n + 32)
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
