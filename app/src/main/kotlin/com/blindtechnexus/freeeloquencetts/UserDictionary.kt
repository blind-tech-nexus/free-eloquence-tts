package com.blindtechnexus.freeeloquencetts

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
 *
 * Two more things, both about a profile that has grown.
 *
 * The list carries a version, and the version is written down beside the file
 * rather than kept in memory. A number in memory starts again at nought every
 * time the process does, so a service restarted between two visits to the
 * dictionary screen would find the counter it was comparing against moved and
 * rebuild for no reason -- and, worse, a service that had not restarted would
 * be the only thing that could tell the two lists apart. On disk it is one
 * small file that says what the last write was, and any reader in any process
 * agrees about it.
 *
 * The trie the matching runs on is built on a thread of its own, and the
 * building is started by the write rather than waited for by the reader. That
 * is the whole of the change and it is worth saying why it matters: a profile
 * of twenty thousand words is a hundred thousand small objects to make, which
 * on a phone is tens of milliseconds, and the thread that used to pay for it
 * is the framework's synthesis thread, in front of the first syllable of
 * whatever was asked for. Now the screen that saved the word asks for the
 * rebuild the moment it has written the file, and the service picks up the
 * answer by version -- which costs it one integer comparison, and costs it a
 * wait only in the case where somebody spoke inside the few tens of
 * milliseconds the rebuild takes.
 */
object UserDictionary {

	private const val NAME = "words.dic"
	private const val VERSION_NAME = "words.dic.ver"
	private const val MAX_LINE_LENGTH = 8192
	private const val MAX_ENTRIES = 200_000
	private const val MAX_FILE_BYTES = 50L * 1024 * 1024 // 50 MB cap for safety

	/** The version of the list on disk. Every write moves it on, and the
	 *  number itself is meaningless: what matters is that two readers who see
	 *  the same number are looking at the same list, and a reader who sees a
	 *  different one knows the trie it holds is stale. */
	private val versionCount = AtomicInteger(0)

	@Volatile
	private var versionLoaded = false

	val version: Int get() = versionCount.get()

	/** What a reader takes away: the list, and the version it was read at. */
	class Snapshot(val version: Int, val entries: List<Pair<String, String>>)

	/** The trie already built, and the version of the list it was built from.
	 *  Both volatile and both written under [buildLock], so a reader either
	 *  sees the pair that goes together or sees an older pair, never a trie
	 *  from one list against the version of another. */
	@Volatile
	private var builtVersion = Int.MIN_VALUE

	@Volatile
	private var builtEntries: List<Pair<String, String>> = emptyList()

	@Volatile
	private var builtMatcher: WordReplacer? = null

	private val buildLock = Any()

	/** The thread the rebuild runs on. One of them, daemon, named, and shared:
	 *  a rebuild is a burst of allocation and not a stream of work, so a pool
	 *  would be a pool of one and an executor service would be a thing to shut
	 *  down that nothing here is in a position to shut down. */
	private val builder: java.util.concurrent.ExecutorService =
		java.util.concurrent.Executors.newSingleThreadExecutor(
			java.util.concurrent.ThreadFactory { work ->
				Thread(work, "evv-dictionary").apply { isDaemon = true }
			}
		)

	/** The matcher the pipeline reaches, cached against the list it was built
	 *  from. [snapshot] primes it; this is the fallback for a caller that has
	 *  a list of its own, which is what the tests hand over. */
	private val matcherLock = Any()
	private var matcherEntries: List<Pair<String, String>>? = null
	private var matcher: WordReplacer? = null

	fun file(context: Context): File = File(DirectBoot.dictionaries(context), NAME)

	fun fileForWriting(context: Context): File {
		val into = DirectBoot.dictionariesReady(context)
		return File(into, NAME)
	}

	/** The file the version is kept in, beside the list it describes. */
	private fun versionFile(context: Context): File =
		File(DirectBoot.dictionaries(context), VERSION_NAME)

	/**
	 * Reads the version off the disk, once per process.
	 *
	 * Read rather than assumed, and read before the first comparison rather
	 * than at some convenient moment: a service that started at nought would
	 * see a file whose version is forty and rebuild, which is correct, while a
	 * service that started at forty from a stale guess would see a file whose
	 * version is forty-one and rebuild, which is also correct -- so the only
	 * thing the read buys is not rebuilding on a process start where nothing
	 * changed. That is worth a small file read once and is not worth more.
	 */
	private fun ensureVersion(context: Context) {
		if (versionLoaded) return
		synchronized(versionCount) {
			if (versionLoaded) return
			versionCount.set(readVersion(context))
			versionLoaded = true
		}
	}

	private fun readVersion(context: Context): Int = try {
		val f = versionFile(context)
		if (f.exists() && f.canRead()) {
			f.readText(Charsets.UTF_8).trim().toIntOrNull() ?: 0
		} else {
			0
		}
	} catch (unreadable: Exception) {
		0
	}

	/** Writes the version down and moves the counter on. Answering the new
	 *  number even when the file could not be written, because a counter that
	 *  stops moving is a reader that stops noticing, and the next successful
	 *  write puts the number back on the disk. */
	private fun bumpVersion(context: Context): Int {
		ensureVersion(context)
		val next = versionCount.incrementAndGet()
		try {
			versionFile(context).writeText(next.toString(), Charsets.UTF_8)
		} catch (unwritable: Exception) {
			// Left unlogged on purpose: this runs on the thread that saved a
			// word, and the word is saved whatever happened here.
		}
		return next
	}

	/**
	 * The list and its version, with the trie for it ready.
	 *
	 * Answers at once when the version has not moved, which is the case on
	 * every utterance but the first after a change. When it has moved the
	 * rebuild happens here, under [buildLock], so two threads asking at once
	 * build it once between them; [rebuildAsync] is what keeps that from
	 * usually being the synthesis thread.
	 */
	fun snapshot(context: Context): Snapshot {
		ensureVersion(context)
		val want = version
		if (builtVersion == want) return Snapshot(want, builtEntries)
		synchronized(buildLock) {
			if (builtVersion == want) return Snapshot(want, builtEntries)
			val entries = words(context)
			val made = WordReplacer(entries)
			// The pipeline's own cache is primed from here rather than left to
			// build it on the synthesis thread, and the list handed on is the
			// one the trie was built from, so the identity check there hits.
			synchronized(matcherLock) {
				matcher = made
				matcherEntries = entries
			}
			builtMatcher = made
			builtEntries = entries
			builtVersion = want
			return Snapshot(want, entries)
		}
	}

	/**
	 * Asks for the trie to be rebuilt, on the builder's thread, and answers at
	 * once.
	 *
	 * Called by every write, so that the rebuild is under way before whoever
	 * saved the word has finished reading the confirmation and is long since
	 * finished by the time anything is spoken. It is idempotent and cheap to
	 * ask twice: the second ask finds the version already built and does
	 * nothing.
	 */
	fun rebuildAsync(context: Context) {
		val app = context.applicationContext
		try {
			builder.submit(Runnable {
				try {
					snapshot(app)
				} catch (broken: Exception) {
					// A rebuild that failed leaves the last good trie in
					// place, and the next utterance asks again.
				}
			})
		} catch (refused: Exception) {
			// Nothing to do but let the reader build it when it asks.
		}
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
			bumpVersion(context)
			// The trie for the list just written is built now, off this
			// thread, so that the utterance which follows finds it ready
			// rather than paying for it. See [snapshot].
			rebuildAsync(context)
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
			// The fast road, and the one every utterance takes: [snapshot]
			// hands on the very list it built the trie from, so an unchanged
			// dictionary is one reference comparison against two volatiles and
			// no lock at all.
			val ready = builtMatcher
			if (ready != null && builtEntries === entries) return ready.apply(text)
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
