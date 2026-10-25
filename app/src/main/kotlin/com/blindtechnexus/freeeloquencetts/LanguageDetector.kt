package com.blindtechnexus.freeeloquencetts

/**
 * Finds, in one pass over the text, which of the ten engine modules each
 * part of a line belongs to, so the service can hand every part to the
 * engine that actually speaks it.
 *
 * Two scripts carry the decision by themselves:
 *
 *  - Devanagari is Hindi or Nepali, the two Indic modules this build
 *    carries. The script alone cannot say which, so each Devanagari run is
 *    scored against two lexicons of words and endings that occur in one
 *    language and not the other -- Nepali verb forms (`हुन्छ`, `गर्छु`,
 *    `भयो` ...), its own particles (`-लाई`, `-बाट`, `-सँग` ...) and its
 *    lexicon (`तपाईं`, `राम्रो`, `कान्छी` ...) against Hindi ones
 *    (`हैं`, `है`, `क्या`, `नहीं`, `वाला` ...). The short postpositions
 *    the two languages share (को, का, ले, मा ...) are deliberately left
 *    out, since substring matching would make every sentence ambiguous. A
 *    run with no marker follows the listener's default Indic language, and
 *    Hindi when that default is a Western one.
 *
 *  - Latin runs are English, Spanish, French, German or Italian. Diacritics
 *    that only one language owns (ß, ñ, ç, circumflexes ...) vote first,
 *    then a stopword lexicon per family, then a bigram scorer for words
 *    neither table knows. A small romanized-Indic lexicon catches words
 *    such as "Kanchi" or "namaste" written in Latin letters and sends them
 *    to the Hindi or Nepali engine rather than to an English one.
 *
 * Everything else -- ASCII and Devanagari digits, punctuation, symbols,
 * emoji, spaces -- is neutral and is spoken in the language of the word
 * beside it, so a number, a date or an emoji between two Hindi words stays
 * in the Hindi voice.
 *
 * Speed is a requirement rather than an aspiration: this runs on the way
 * to every utterance while the previous piece is still being spoken. There
 * are no regular expressions and no per-character allocations on the hot
 * path beyond the run buffers themselves -- one code-point loop, hash-set
 * lexicon lookups and buffers that start at the length they will need.
 *
 * The object is pure Kotlin with no Android dependency, so the same code
 * runs in the instrumented tests and in the service unchanged.
 */
object LanguageDetector {

	/** One run of text and the engine language ([Eci] code) it is spoken in. */
	class Segment(val text: String, val language: Int)

	// Script classes the walk distinguishes.
	private const val SCRIPT_NEUTRAL = 0
	private const val SCRIPT_DEVANAGARI = 1
	private const val SCRIPT_LATIN = 2

	// Latin-script families, as the two-letter name [Eci.Language] carries.
	private const val FAMILY_EN = "en"
	private const val FAMILY_ES = "es"
	private const val FAMILY_FR = "fr"
	private const val FAMILY_DE = "de"
	private const val FAMILY_IT = "it"

	private const val IDX_EN = 0
	private const val IDX_ES = 1
	private const val IDX_FR = 2
	private const val IDX_DE = 3
	private const val IDX_IT = 4

	private val FAMILIES = arrayOf(FAMILY_EN, FAMILY_ES, FAMILY_FR, FAMILY_DE, FAMILY_IT)

	/**
	 * The single language [text] is mostly in: the language of its first
	 * word run, or [baseLanguage] when it carries nothing that identifies a
	 * language (a number, an emoji, punctuation).
	 */
	fun detect(text: String, baseLanguage: Int): Int {
		if (text.isEmpty()) return baseLanguage
		val runs = toRuns(text, baseLanguage)
		for (run in runs) {
			if (run.script != SCRIPT_NEUTRAL) return run.language
		}
		return baseLanguage
	}

	/**
	 * [text] cut into language segments, in order, with every character in
	 * exactly one segment. Adjacent runs resolved to the same engine are
	 * merged, including the neutral material between them, so a sentence in
	 * one language stays one piece and only a real change of voice cuts it.
	 */
	fun split(text: String, baseLanguage: Int): List<Segment> {
		if (text.isEmpty()) return emptyList()
		val runs = toRuns(text, baseLanguage)
		if (runs.isEmpty()) return listOf(Segment(text, baseLanguage))

		// Neutral runs borrow the nearest word's language: first the word
		// before them, then -- for neutral material that leads the text --
		// the first word after them.
		var lastWord = baseLanguage
		for (run in runs) {
			if (run.script != SCRIPT_NEUTRAL) {
				lastWord = run.language
			} else {
				run.language = lastWord
			}
		}
		var nextWord = baseLanguage
		for (at in runs.indices.reversed()) {
			val run = runs[at]
			if (run.script != SCRIPT_NEUTRAL) {
				nextWord = run.language
			} else if (at == 0 || runs[at - 1].script == SCRIPT_NEUTRAL) {
				run.language = nextWord
			}
		}

		val out = ArrayList<Segment>(4)
		val buffer = StringBuilder(text.length)
		var current = runs[0].language
		for (run in runs) {
			if (run.language != current) {
				if (buffer.isNotEmpty()) {
					out.add(Segment(buffer.toString(), current))
					buffer.setLength(0)
				}
				current = run.language
			}
			buffer.append(run.text)
		}
		if (buffer.isNotEmpty()) out.add(Segment(buffer.toString(), current))
		return out
	}

	private class RawRun(val script: Int, val text: String) {
		var language = 0
	}

	/** One code-point pass into maximal word runs and neutral runs. */
	private fun toRuns(text: String, baseLanguage: Int): MutableList<RawRun> {
		val runs = ArrayList<RawRun>(8)
		val n = text.length
		var at = 0
		val neutral = StringBuilder()
		while (at < n) {
			val cp = text.codePointAt(at)
			val count = Character.charCount(cp)
			when (scriptOf(cp)) {
				SCRIPT_DEVANAGARI -> {
					flushNeutral(runs, neutral)
					val start = at
					at += count
					while (at < n && scriptOf(text.codePointAt(at)) == SCRIPT_DEVANAGARI) {
						at += Character.charCount(text.codePointAt(at))
					}
					val word = text.substring(start, at)
					val run = RawRun(SCRIPT_DEVANAGARI, word)
					run.language = classifyDevanagari(word, runs, baseLanguage)
					runs.add(run)
				}
				SCRIPT_LATIN -> {
					flushNeutral(runs, neutral)
					val start = at
					at += count
					// Apostrophes and hyphens inside a word belong to it
					// (don't, l'homme, mother-in-law); a dot does not.
					while (at < n) {
						val next = text.codePointAt(at)
						val sc = scriptOf(next)
						if (sc == SCRIPT_LATIN) {
							at += Character.charCount(next)
						} else if ((next == '\'' || next == '-' || next == 0x2019) &&
							at + Character.charCount(next) < n &&
							scriptOf(text.codePointAt(at + Character.charCount(next))) == SCRIPT_LATIN
						) {
							at += Character.charCount(next)
						} else {
							break
						}
					}
					val word = text.substring(start, at)
					val run = RawRun(SCRIPT_LATIN, word)
					run.language = classifyLatin(word, baseLanguage)
					runs.add(run)
				}
				else -> {
					neutral.appendCodePoint(cp)
					at += count
				}
			}
		}
		if (neutral.isNotEmpty()) runs.add(RawRun(SCRIPT_NEUTRAL, neutral.toString()))
		return runs
	}

	private fun flushNeutral(runs: MutableList<RawRun>, neutral: StringBuilder) {
		if (neutral.isNotEmpty()) {
			runs.add(RawRun(SCRIPT_NEUTRAL, neutral.toString()))
			neutral.setLength(0)
		}
	}

	/** Which script class [cp] belongs to. */
	private fun scriptOf(cp: Int): Int = when {
		isDevanagari(cp) -> SCRIPT_DEVANAGARI
		isLatinLetter(cp) -> SCRIPT_LATIN
		else -> SCRIPT_NEUTRAL
	}

	private fun isDevanagari(cp: Int): Boolean =
		(cp in 0x0900..0x097F) || (cp in 0xA8E0..0xA8FF)

	private fun isLatinLetter(cp: Int): Boolean =
		(cp in 'a'.code..'z'.code) || (cp in 'A'.code..'Z'.code) || (cp in 0x00C0..0x024F)

	// ---- Devanagari: Hindi versus Nepali ---------------------------------

	/**
	 * Score one Devanagari run. Words and endings distinctive of Nepali are
	 * weighed against Hindi ones; the previous Devanagari decision breaks a
	 * tie so a paragraph does not flicker between the two voices word by
	 * word.
	 */
	private fun classifyDevanagari(word: String, earlier: List<RawRun>, baseLanguage: Int): Int {
		var nepali = 0
		var hindi = 0
		for (marker in NEPALI_MARKERS) {
			if (word.contains(marker)) nepali++
		}
		for (marker in HINDI_MARKERS) {
			if (word.contains(marker)) hindi++
		}
		if (nepali == hindi) {
			// Prefer the voice the previous Indic run used; else the
			// listener's default Indic language; else Hindi.
			for (at in earlier.indices.reversed()) {
				val run = earlier[at]
				if (run.script == SCRIPT_DEVANAGARI) return run.language
			}
			return if (baseLanguage == Eci.NEPALI) Eci.NEPALI else Eci.HINDI
		}
		return if (nepali > hindi) Eci.NEPALI else Eci.HINDI
	}

	// ---- Latin: the five Western families and romanized Indic ------------

	private fun classifyLatin(word: String, baseLanguage: Int): Int {
		val lower = word.lowercase()

		// Romanized Indic words keep their language even in Latin letters:
		// "Kanchi" is Nepali, "dhanyavad" Hindi, whatever the English words
		// around them do.
		if (lower in NEPALI_ROMAN) return Eci.NEPALI
		if (lower in HINDI_ROMAN) return Eci.HINDI

		val scores = IntArray(5)
		// Diacritics and punctuation only one family owns -- strong votes.
		for (ch in lower) {
			when (ch) {
				'ß', 'ä', 'ö', 'ü' -> scores[IDX_DE] += 3
				'ñ', '¿', '¡' -> scores[IDX_ES] += 3
				'ç', 'â', 'ê', 'î', 'ô', 'û', 'ë', 'ï', 'à', 'ù', 'œ', 'æ' ->
					scores[IDX_FR] += 3
				'ì', 'ò' -> scores[IDX_IT] += 2
				'é', 'è', 'á', 'í', 'ó', 'ú' -> scores[IDX_IT] += 1 // shared, weak
			}
		}
		if (LEX_EN.contains(lower)) scores[IDX_EN] += 4
		if (LEX_ES.contains(lower)) scores[IDX_ES] += 4
		if (LEX_FR.contains(lower)) scores[IDX_FR] += 4
		if (LEX_DE.contains(lower)) scores[IDX_DE] += 4
		if (LEX_IT.contains(lower)) scores[IDX_IT] += 4

		// Nothing the lexicons knew: score the letters themselves.
		if (scores.all { it == 0 }) {
			return familyToModule(bigramFamily(lower, baseLanguage), baseLanguage)
		}
		var best = 0
		for (i in 1 until scores.size) {
			if (scores[i] > scores[best]) best = i
		}
		return familyToModule(FAMILIES[best], baseLanguage)
	}

	/** Characteristic-bigram vote for a word no lexicon recognized.
	 *
	 *  A word with no characteristic bigram is a proper noun or a coinage, and
	 *  the least surprising voice is the one the listener is already in (when
	 *  it is a Western one); English is the standing default otherwise. A lone
	 *  weak bigram against a Western base language is treated the same way, so
	 *  one stray letter pair cannot bounce the whole sentence into another
	 *  voice. Ties keep the family with the lowest index, putting English first
	 *  on purpose. */
	private fun bigramFamily(word: String, baseLanguage: Int): String {
		val scores = IntArray(5)
		val max = word.length - 1
		var at = 0
		while (at < max) {
			val idx = BIGRAM_INDEX[word.substring(at, at + 2)]
			if (idx != null) scores[idx]++
			at++
		}
		var best = 0
		var bestScore = scores[0]
		for (i in 1 until scores.size) {
			if (scores[i] > bestScore) {
				best = i
				bestScore = scores[i]
			}
		}
		val baseFamily = Eci.languageOf(baseLanguage).iso2Language
		val atFamily = FAMILIES.indexOf(baseFamily)
		// No evidence, or only a single weak bigram against a Western base
		// language: stay in the voice the listener is already using.
		if (bestScore == 0 || (bestScore <= 1 && atFamily >= 0)) {
			if (atFamily >= 0) return FAMILIES[atFamily]
			return FAMILY_EN
		}
		return FAMILIES[best]
	}

	/**
	 * The module that speaks [family]. The listener's own choice wins when
	 * it is of the same family, so a phone set to British English keeps
	 * British English and a Mexican-Spanish choice keeps the Mexican voice;
	 * otherwise the first module of the family (the mother-country one) is
	 * used.
	 */
	private fun familyToModule(family: String, baseLanguage: Int): Int {
		val baseLang = Eci.languageOf(baseLanguage)
		if (baseLang.iso2Language == family) return baseLanguage
		for (language in Eci.LANGUAGES) {
			if (language.iso2Language == family) return language.code
		}
		return baseLanguage
	}

	// ---- lexicons ---------------------------------------------------------

	/** Nepali-only words, clitics, endings and proper nouns.
	 *
	 *  Deliberately no bare postpositions such as को/का/की/ले/मा: Hindi uses
	 *  the same particles and [contains] matching would misread every Hindi
	 *  sentence. Everything here is a longer string that occurs in Nepali and
	 *  not in Hindi, or an ending distinctively Nepali (the oblique -ओ/-ओइ). */
	private val NEPALI_MARKERS = setOf(
		"हुन्छ", "हुँदैन", "हुने", "हुनु", "भएको", "भएका", "भएन", "गएन", "भयो", "थियो", "थिई",
		"गर्छु", "गर्छौं", "गर्छ", "गर्छन्", "पर्छ", "सक्छ", "आउँछ", "जान्छ", "छैन", "हुनसक्छ",
		"तपाईं", "तपाई", "उहाँ", "हामी", "उनी", "उनीहरू", "हाम्रो", "तिम्रो", "मेरो",
		"केटा", "केटी", "मान्छे", "मानिस", "राम्रो", "नराम्रो", "ठूलो", "सानो", "नयाँ",
		"पछि", "अगाडि", "भित्र", "बाहिर", "माथि", "मुनि", "वरिपरि", "देखि", "सम्म",
		"सँग", "बाट", "लाई", "हरू", "हरु", "रुपैयाँ", "रुपैयां", "काठमाडौं", "काठमाडौँ",
		"नेपाल", "पोखरा", "लुम्बिनी", "गोरखा", "विक्रम", "संवत्", "तरकारी", "गाउँ",
		"पहाड", "हिमाल", "चोक", "बजार", "दाजु", "भाइ", "बहिनी", "छोरा", "छोरी", "बुबा",
		"कान्छी", "कान्छा", "नमस्कार", "अहिले", "किनभने", "विद्यालय", "नुह", "छौं", "हुनुहुन्छ"
	)

	/** Hindi-only words, endings and proper nouns. */
	private val HINDI_MARKERS = setOf(
		"हैं", "है", "हूँ", "हूं", "होगा", "होगी", "होता", "होती", "हुआ", "हुए", "हुई",
		"रहा", "रही", "रहे", "था", "थी", "थे", "नहीं", "क्या", "क्यों", "कैसे", "कैसा",
		"कौन", "मुझे", "मुझको", "हमें", "तुम्हें", "अपने", "वाला", "वाली", "वाले",
		"लिए", "द्वारा", "चाहिए", "बहुत", "अभी", "फिर", "लोग", "नमस्ते", "धन्यवाद",
		"शुक्रिया", "अलविदा", "कृपया", "भारत", "हिंदुस्तान", "हिन्दुस्तान", "दिल्ली",
		"मुंबई", "बंबई", "लड़का", "लड़की", "सुबह", "दोपहर", "महीना", "हफ्ता", "हफ़्ता",
		"सप्ताह", "रुपये", "रुपया", "पैसा", "पैसे", "वक़्त", "वक्त", "ज़िंदगी", "दुनिया",
		"इधर", "उधर", "ऊपर", "नीचे", "सामने", "पुराना", "अंदर", "कहाँ", "कहां", "और",
		// Short, unmistakably Hindi function and content words. Nepali uses
		// हो/छ rather than है and यो/त्यो rather than यह/वह, so these do not
		// collide with the Nepali list.
		"एक", "यह", "वह", "हिंदी", "हिन्दी", "भाषा", "वाक्य", "करता", "करती", "करते"
	)

	/** Romanized words that are Nepali even though they are Latin letters. */
	private val NEPALI_ROMAN = setOf(
		"kanchi", "kanchhi", "kanchha", "namaskar", "dhanyabad",
		"tapai", "tapain", "hunchha", "huncha", "chha", "chaina", "chhaina", "hoina",
		"kathmandu", "pokhara", "lumbini", "gorkha", "sherpa", "thapa", "gurung",
		"basnet", "basnyat", "nepal", "nepali", "ramro", "ramri", "sanchai",
		"dhanyabaad", "hajur", "haina"
	)

	/** Romanized words that are Hindi even though they are Latin letters. */
	private val HINDI_ROMAN = setOf(
		"dhanyavad", "dhanyawad", "shukriya", "accha", "acha", "theek", "thik", "kaisa",
		"kaise", "kahan", "kya", "kyon", "nahin", "nahi", "aap", "hum", "tum", "bhai",
		"behan", "mata", "pita", "rupaya", "rupaye", "paise", "paisa", "namaste",
		"hindi", "hindustan", "bharat", "delhi", "mumbai", "bambai", "kripya", "alvida",
		"bahut", "abhi", "phir", "log", "chahiye"
	)

	private val LEX_EN = setOf(
		"the", "and", "is", "are", "was", "were", "be", "been", "being", "of", "to", "in",
		"on", "at", "that", "this", "it", "for", "with", "as", "by", "from", "or", "but",
		"not", "no", "yes", "you", "he", "she", "we", "they", "his", "her", "their", "our",
		"your", "my", "me", "him", "them", "us", "have", "has", "had", "do", "does", "did",
		"will", "would", "can", "could", "shall", "should", "may", "might", "must", "what",
		"when", "where", "who", "how", "why", "which", "all", "each", "every", "some",
		"any", "if", "then", "than", "so", "just", "about", "into", "over", "under",
		"hello", "good", "morning", "evening", "night", "please", "thanks", "thank", "sorry",
		"welcome", "name", "fine", "very", "much", "more", "most", "now", "here", "there"
	)

	private val LEX_ES = setOf(
		"el", "la", "los", "las", "un", "una", "unos", "unas", "y", "o", "de", "del", "al",
		"en", "con", "por", "para", "que", "qué", "como", "cómo", "donde", "dónde",
		"cuando", "cuándo", "quien", "quién", "cual", "cuál", "cuanto", "cuánto", "es",
		"son", "soy", "eres", "está", "están", "estoy", "tengo", "tienes", "tiene", "hay",
		"muy", "pero", "más", "mas", "esto", "ese", "esa", "ella", "él", "nosotros",
		"vosotros", "hola", "gracias", "adiós", "buenos", "días", "noches", "tarde",
		"favor", "bien", "mal", "aquí", "ahí", "allí", "ahora", "también",
		"todo", "nada", "algo", "uno", "dos", "tres", "cuatro", "cinco", "señor", "señora"
	)

	private val LEX_FR = setOf(
		"le", "la", "les", "un", "une", "des", "du", "de", "et", "ou", "à", "a", "au",
		"aux", "en", "dans", "pour", "par", "que", "quel", "quelle", "quels", "quelles",
		"quoi", "qui", "comment", "où", "quand", "pourquoi", "est", "sont", "suis", "es",
		"avoir", "être", "mais", "plus", "très", "tres", "bien", "bonjour", "bonsoir",
		"merci", "salut", "vous", "nous", "ils", "elles", "il", "elle", "ce",
		"cette", "ces", "se", "ne", "pas", "avec", "sans", "sur", "sous", "tout", "tous",
		"toute", "toutes", "moi", "toi", "lui", "leur", "oui", "non",
		"maintenant", "ici", "là", "déjà", "encore", "aussi", "alors", "donc"
	)

	private val LEX_DE = setOf(
		"der", "die", "das", "und", "oder", "aber", "ein", "eine", "einen", "einer",
		"einem", "eines", "nicht", "ist", "sind", "war", "waren", "ich", "du", "er", "sie",
		"es", "wir", "ihr", "mit", "von", "zu", "zur", "zum", "auf", "für", "auch", "noch",
		"nur", "schon", "hallo", "guten", "morgen", "abend", "nacht", "danke", "bitte",
		"sehr", "kein", "keine", "mein", "dein", "sein", "unser", "euer",
		"wenn", "weil", "dass", "daß", "also", "hier", "dort", "jetzt", "immer", "wieder",
		"über", "unter", "vor", "nach", "aus", "bei", "seit", "durch", "ohne", "um",
		"tschüss", "ja", "nein", "vielleicht", "etwas"
	)

	private val LEX_IT = setOf(
		"il", "lo", "la", "i", "gli", "le", "un", "una", "e", "o", "di", "da", "del",
		"della", "dei", "in", "con", "per", "tra", "fra", "che", "chi", "come", "dove",
		"quando", "perché", "perche", "quale", "quanto", "è", "sono", "sei", "ho", "hai",
		"ha", "hanno", "ciao", "grazie", "prego", "buongiorno", "buonasera", "arrivederci",
		"molto", "più", "anche", "sempre", "questa", "questo", "quello", "quella", "noi",
		"voi", "lui", "lei", "loro", "mio", "tuo", "suo", "nostro", "vostro", "si", "no",
		"bene", "male", "qui", "qua", "lì", "ora", "adesso", "oggi", "ieri",
		"domani", "tutto", "niente", "qualcosa", "signore", "signora", "favore"
	)

	/** Bigram → family index, for words the lexicons do not know. */
	private val BIGRAM_INDEX: Map<String, Int> = run {
		val map = HashMap<String, Int>(160)
		fun put(family: Int, vararg bigrams: String) {
			// First writer wins. English is put first on purpose: a bigram
			// several families share -- "en", "er", "ll", "ch" -- is at least
			// as common in English and English is the standing default, so a
			// contested pair votes English rather than bouncing an English
			// word into a voice it never belonged to. The bigrams truly owned
			// by another family ("sch", "que", "gli", "rr" ...) are absent
			// from the English table and register normally.
			for (bg in bigrams) map.putIfAbsent(bg, family)
		}
		put(IDX_EN, "th", "he", "er", "an", "re", "ou", "gh", "ht", "sh", "ch", "wh", "ph", "ng", "ck", "ll", "ve", "ow", "ea", "oo",
			"en", "nd", "in", "is", "it", "at", "to", "on", "nt", "ha", "hi", "wa", "fi", "ie", "ee", "ld", "se", "st", "ai", "fr", "tt")
		put(IDX_ES, "de", "es", "en", "el", "la", "os", "ar", "ue", "ie", "rr", "ll", "ñe", "aba", "ida", "llo", "lla", "nta", "cio")
		put(IDX_FR, "le", "et", "en", "es", "un", "les", "des", "que", "ion", "ent", "ait", "ons", "eur", "eux", "oi", "ch", "nn", "mm", "tt", "ll", "qu", "ou")
		put(IDX_DE, "en", "er", "ch", "sch", "ein", "der", "die", "und", "ich", "den", "ten", "nen", "lich", "heit", "ung", "sein", "ster", "äu", "eu")
		put(IDX_IT, "il", "lo", "la", "che", "per", "sono", "gli", "zz", "tt", "ll", "ss", "nn", "pp", "cc", "bb", "mm", "rg", "nz", "ci", "gi", "vo", "zione", "amento")
		map
	}
}
