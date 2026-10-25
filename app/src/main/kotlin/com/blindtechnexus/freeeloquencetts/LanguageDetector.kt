package com.blindtechnexus.freeeloquencetts

/**
 * Finds, in one pass over the text, which of the ten engine modules each
 * part of a line belongs to, so the service can hand every part to the
 * engine that actually speaks it.
 *
 * Two scripts carry the decision by themselves:
 *
 *  - Devanagari is Hindi or Nepali, the two Indic modules this build
 *    carries. One word alone rarely says which -- the script is shared and
 *    so is most of the everyday vocabulary -- so the decision is made by
 *    the sentence: the marked words of a stretch (Nepali `हुन्छ`, `गर्छु`,
 *    `तपाईं`, `-लाई`, `-बाट`, `-सँग`, `-नुहोस्` ... against Hindi `है`,
 *    `नहीं`, `मुझे`, `-वाला` ...) vote, and every unmarked word in the
 *    stretch is spoken in the language they chose. A stretch with no marked
 *    word follows the previous Devanagari decision, then the listener's
 *    default Indic language, and Hindi when that default is a Western one.
 *    Shared greetings and thanks (`नमस्ते`, `धन्यवाद`) are in both lists on
 *    purpose nowhere: they belong to whichever Indic voice is speaking.
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

		/** How much evidence a Latin word carries of its own: 0 is none, so
		 *  [resolveRuns] may overrule it with its neighbours'. */
		var strength = 0

		/** Whether a neutral run ends a sentence, which is where a
		 *  Devanagari stretch stops and a new one may change language. */
		var endsSentence = false
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
					// Decided by [resolveRuns], which sees the whole sentence:
					// one Devanagari word alone rarely says which of the two
					// languages it is, but the words around it usually do.
					runs.add(RawRun(SCRIPT_DEVANAGARI, word))
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
						} else if ((next == '\''.code || next == '-'.code || next == 0x2019) &&
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
					run.language = classifyLatin(word, baseLanguage, run)
					runs.add(run)
				}
				else -> {
					neutral.appendCodePoint(cp)
					at += count
				}
			}
		}
		if (neutral.isNotEmpty()) {
			val run = RawRun(SCRIPT_NEUTRAL, neutral.toString())
			for (c in run.text) {
				if (c == '।' || c == '॥' || c == '.' || c == '!' || c == '?' || c == '\n') {
					run.endsSentence = true
					break
				}
			}
			runs.add(run)
		}
		resolveRuns(runs, baseLanguage)
		return runs
	}

	private fun flushNeutral(runs: MutableList<RawRun>, neutral: StringBuilder) {
		if (neutral.isNotEmpty()) {
			val run = RawRun(SCRIPT_NEUTRAL, neutral.toString())
			for (c in run.text) {
				if (c == '।' || c == '॥' || c == '.' || c == '!' || c == '?' || c == '\n') {
					run.endsSentence = true
					break
				}
			}
			runs.add(run)
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
		// The dandas (। ॥) sit inside the Devanagari block but are sentence
		// punctuation, not word letters: a word carrying one (`है।`) would
		// never match a lexicon entry, and the stretch logic needs them in
		// the neutral run to know the sentence ended.
		cp != 0x0964 && cp != 0x0965 &&
			((cp in 0x0900..0x097F) || (cp in 0xA8E0..0xA8FF))

	private fun isLatinLetter(cp: Int): Boolean =
		(cp in 'a'.code..'z'.code) || (cp in 'A'.code..'Z'.code) || (cp in 0x00C0..0x024F)

	// ---- Devanagari: Hindi versus Nepali ---------------------------------

	/** Strength of evidence a single Devanagari word carries: positive is
	 *  Nepali, negative is Hindi, zero is shared or unknown vocabulary. Whole
	 *  words are looked up in hash sets (O(1)); only a short list of clitic and
	 *  verb endings is matched as a suffix, because Nepali case markers and
	 *  polite imperatives attach to stems (मानिसहरू, गर्नुहोस्). */
	private fun devScore(word: String): Int {
		if (word in NEPALI_WORDS) return 2
		if (word in HINDI_WORDS) return -2
		for (ending in NEPALI_ENDINGS) if (word.endsWith(ending)) return 1
		for (ending in HINDI_ENDINGS) if (word.endsWith(ending)) return -1
		return 0
	}

	/**
	 * Decide every Devanagari and Latin word in [runs], using the sentence
	 * around each word rather than the word alone.
	 *
	 * Devanagari words are grouped into stretches -- consecutive words of the
	 * script with no Latin word and no sentence ender between them. A stretch
	 * is spoken in the language its marked words vote for, so an unmarked word
	 * such as `नमस्ते` or `घर` is Hindi inside a Hindi sentence and Nepali
	 * inside a Nepali one, whichever side of it the copula (है / छ) sits. A
	 * stretch with no marked word at all takes the previous Devanagari
	 * decision, then the listener's own Indic language, then Hindi.
	 *
	 * Latin words keep their own decision when they carry real evidence (a
	 * lexicon word, a distinctive letter, a romanized Indic word). A word with
	 * no evidence of its own -- a product name, a coinage, an unknown English
	 * word between two French ones -- takes the language of the Latin words
	 * around it in the same stretch; with nothing around it, it takes English
	 * when the listener's voice is Indic (a Latin word has no Devanagari
	 * pronunciation worth giving it), the listener's own Western family when
	 * there is one, and English otherwise.
	 */
	private fun resolveRuns(runs: MutableList<RawRun>, baseLanguage: Int) {
		val indicDefault = if (baseLanguage == Eci.NEPALI) Eci.NEPALI else Eci.HINDI
		var stretchStart = -1
		var stretchScore = 0
		var lastIndic = 0

		fun closeStretch(endExclusive: Int) {
			if (stretchStart < 0) return
			val language = when {
				stretchScore > 0 -> Eci.NEPALI
				stretchScore < 0 -> Eci.HINDI
				lastIndic != 0 -> lastIndic
				else -> indicDefault
			}
			for (at in stretchStart until endExclusive) {
				if (runs[at].script == SCRIPT_DEVANAGARI) runs[at].language = language
			}
			lastIndic = language
			stretchStart = -1
			stretchScore = 0
		}

		for (at in runs.indices) {
			val run = runs[at]
			when (run.script) {
				SCRIPT_DEVANAGARI -> {
					if (stretchStart < 0) stretchStart = at
					stretchScore += devScore(run.text)
				}
				SCRIPT_LATIN -> closeStretch(at)
				else -> if (run.endsSentence) closeStretch(at)
			}
		}
		closeStretch(runs.size)

		// Latin words with no evidence of their own borrow from their stretch.
		var latinStart = -1
		fun closeLatin(endExclusive: Int) {
			if (latinStart < 0) return
			// Words with no evidence are un-decided until their neighbours
			// have had their say, so the guesses [classifyLatin] made for
			// them are set aside first and only the fallback restores one.
			for (at in latinStart until endExclusive) {
				val run = runs[at]
				if (run.script == SCRIPT_LATIN && run.strength == 0) run.language = 0
			}
			var lastStrong = 0
			for (at in latinStart until endExclusive) {
				val run = runs[at]
				if (run.script != SCRIPT_LATIN) continue
				if (run.strength > 0) lastStrong = run.language
				else if (lastStrong != 0) run.language = lastStrong
			}
			var nextStrong = 0
			for (at in endExclusive - 1 downTo latinStart) {
				val run = runs[at]
				if (run.script != SCRIPT_LATIN) continue
				if (run.strength > 0) nextStrong = run.language
				else if (run.language == 0 && nextStrong != 0) run.language = nextStrong
			}
			for (at in latinStart until endExclusive) {
				val run = runs[at]
				if (run.script == SCRIPT_LATIN && run.language == 0) {
					run.language = latinFallback(baseLanguage)
				}
			}
			latinStart = -1
		}
		for (at in runs.indices) {
			when (runs[at].script) {
				SCRIPT_LATIN -> if (latinStart < 0) latinStart = at
				SCRIPT_DEVANAGARI -> closeLatin(at)
			}
		}
		closeLatin(runs.size)
	}

	/** Where a Latin word with no evidence and no Latin neighbour goes. */
	private fun latinFallback(baseLanguage: Int): Int {
		val family = Eci.languageOf(baseLanguage).iso2Language
		if (family == "hi" || family == "ne") return Eci.ENGLISH_US
		if (family in FAMILIES) return familyToModule(family, baseLanguage)
		return Eci.ENGLISH_US
	}

	// ---- Latin: the five Western families and romanized Indic ------------

	private fun classifyLatin(word: String, baseLanguage: Int, run: RawRun): Int {
		val lower = word.lowercase()

		// Romanized Indic words keep their language even in Latin letters:
		// "Kanchi" is Nepali, "dhanyavad" Hindi, whatever the English words
		// around them do.
		if (lower in NEPALI_ROMAN) { run.strength = 3; return Eci.NEPALI }
		if (lower in HINDI_ROMAN) { run.strength = 3; return Eci.HINDI }

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

		// A short Latin word does not carry enough evidence for reliable
		// language identification from character n-grams alone. In particular,
		// ordinary English words can share two-letter sequences with French,
		// Spanish, German and Italian. When the configured voice is Hindi or
		// Nepali, the safe and useful default for an otherwise unknown Latin
		// token is English, not an accidental Indic or unrelated Western voice.
		// Explicit lexicon words and distinctive diacritics above still switch
		// normally; romanized Indic words were handled before this point.
		if (scores.all { it == 0 }) {
			// No evidence of its own. [resolveRuns] gives it the language of
			// the Latin words around it where there are any; the bigram vote
			// and the standing defaults are what is left when there are not,
			// and they are only a guess, which is why the strength stays at
			// zero and the neighbours win.
			val baseFamily = Eci.languageOf(baseLanguage).iso2Language
			if (baseFamily == "hi" || baseFamily == "ne") return Eci.ENGLISH_US
			return familyToModule(bigramFamily(lower, baseLanguage), baseLanguage)
		}
		run.strength = 3
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
	/** Nepali-only words. Whole-word lookup, so the postpositions the two
	 *  languages share (को, का, की, ले, मा) are absent on purpose: they say
	 *  nothing about which language a sentence is in, and the stretch vote in
	 *  [resolveRuns] is what decides sentences built out of them. */
	private val NEPALI_WORDS = setOf(
		"हुन्छ", "हुँदैन", "हुने", "हुनु", "भएको", "भएका", "भएन", "गएन", "भयो", "थियो", "थिई", "थिए", "थिएन",
		"गर्छु", "गर्छौं", "गर्छ", "गर्छन्", "गर्दै", "गरेको", "गरेर", "पर्छ", "पर्दैन", "सक्छ", "सक्दैन", "सकिन्छ",
		"आउँछ", "आउँदैन", "आयो", "आएन", "जान्छ", "गयो", "गएन", "खायो", "खाएन", "दियो", "दिएन",
		"छ", "छु", "छौँ", "छौ", "छन्", "छैन", "छैनन्", "हो", "होइन", "हैन", "चाहिन्छ", "चाहिँदैन", "थाहा",
		"यो", "यी", "त्यो", "ती", "यिनी", "तिनी", "उनी", "उहाँ", "हामी", "तिमी", "तपाईं", "तपाईँ", "हजुर",
		"मेरो", "तिम्रो", "हाम्रो", "उनको", "यसको", "त्यसको", "कसको", "कसले", "कसलाई", "कसरी",
		"केटा", "केटी", "मान्छे", "मानिस", "राम्रो", "नराम्रो", "ठूलो", "ठुलो", "सानो", "नयाँ", "पुरानो", "साथी",
		"पछि", "पछाडि", "अगाडि", "अघि", "भित्र", "बाहिर", "माथि", "मुनि", "वरिपरि", "देखि", "सम्म", "सँग", "सित",
		"बाट", "लागि", "बारेमा", "अनुसार", "किनभने", "त्यसैले", "अनि", "अथवा", "सबै", "धेरै", "थोरै", "अझ", "अझै",
		"कस्तो", "कस्ता", "कति", "कहिले", "कता", "कुन", "कुनै", "कोही", "केही",
		"रुपैयाँ", "रुपैयां", "काठमाडौँ", "काठमाडौं", "विराटनगर", "पोखरा", "लुम्बिनी", "धरान", "इटहरी", "जनकपुर",
		"नेपाली", "तरकारी", "गाउँ", "सहर", "पहाड", "हिमाल", "चोक", "बजार", "दाजु", "दाइ", "दिदी", "भाइ",
		"बहिनी", "छोरा", "छोरी", "बुबा", "बुवा", "आमा", "कान्छी", "कान्छा", "अहिले", "भोलि", "हिजो", "पर्सि",
		"बिहान", "दिउँसो", "साँझ", "राति", "मिनेट", "सेकेन्ड", "बजे", "बजेर", "चिया", "भात", "दाल",
		"सगरमाथा", "कोशी", "बागमती", "गण्डकी", "कर्णाली", "मोरङ", "सुनसरी", "झापा", "नेपालगन्ज", "बुटवल", "भरतपुर"
	)

	/** Hindi-only words, on the same whole-word terms. */
	private val HINDI_WORDS = setOf(
		"हैं", "है", "हूँ", "हूं", "होगा", "होगी", "होंगे", "होता", "होती", "होते", "हुआ", "हुए", "हुई",
		"रहा", "रही", "रहे", "रहता", "रहती", "रहते", "जाता", "जाती", "जाते", "करता", "करती", "करते",
		"था", "थी", "थे", "थीं", "नहीं", "क्या", "क्यों", "कैसे", "कैसा", "कैसी", "कौन", "कब", "कितना", "कितनी",
		"मुझे", "मुझको", "मुझसे", "हमें", "तुम्हें", "तुझे", "इसे", "उसे", "किसे", "जिसे", "इन्हें", "उन्हें", "किन्हें", "जिन्हें",
		"मेरे", "तेरा", "तेरी", "तुम्हारा", "तुम्हारी", "आपका", "आपकी", "इसका", "उसका", "इनका", "उनका", "अपना", "अपनी", "अपने",
		"वाला", "वाली", "वाले", "लिए", "लिये", "द्वारा", "चाहिए", "चाहिये", "चाहता", "चाहती", "बहुत", "अभी", "फिर", "लोग", "लोगों",
		"शुक्रिया", "अलविदा", "कृपया", "हिंदुस्तान", "हिन्दुस्तान", "दिल्ली", "मुंबई", "बंबई", "कोलकाता", "चेन्नई", "लखनऊ",
		"लड़का", "लड़की", "लड़के", "बच्चा", "बच्ची", "आदमी", "औरत", "सुबह", "दोपहर", "महीना", "महीने", "हफ़्ता", "हफ्ता",
		"सप्ताह", "रुपये", "रुपया", "रुपए", "पैसा", "पैसे", "वक़्त", "वक्त", "ज़िंदगी", "जिन्दगी",
		"इधर", "उधर", "ऊपर", "नीचे", "सामने", "पुराना", "पुरानी", "अंदर", "बाहर",
		"यह", "ये", "वह", "वे", "इस", "उस", "हिंदी", "हिन्दी", "बात",
		"अच्छा", "अच्छी", "ज़्यादा", "ज्यादा", "बिल्कुल", "ज़रूर", "शायद", "हमेशा", "कभी"
	)

	/** Nepali endings that attach to stems: polite imperatives, the plural
	 *  and the case clitics. Matched as suffixes because the stem in front of
	 *  them is any noun or verb at all. */
	private val NEPALI_ENDINGS = listOf(
		"नुहोस्", "नोस्", "हरू", "हरु", "लाई", "देखि", "सँग", "बाट", "हरूमा", "हरूले", "हरूबाट"
	)

	/** Hindi endings with no Nepali life: the -वाला family and -रहा forms
	 *  written as one word. */
	private val HINDI_ENDINGS = listOf(
		"वाला", "वाली", "वाले", "रहा", "रही", "रहे", "चाहिए", "चाहिये"
	)

	/** Romanized words that are Nepali even though they are Latin letters. */
	private val NEPALI_ROMAN = setOf(
		"kanchi", "kanchhi", "kanchha", "namaskar",
		"tapai", "tapain", "tapailai", "hunchha", "huncha", "hunxa", "chha", "chhaina", "chaina", "hoina", "haina",
		"mero", "timro", "hamro", "timi", "hami", "hajur", "sanchai", "ramro", "ramri", "thik",
		"khana", "pani", "ghar", "bato", "khola", "pahad", "himal", "mauka",
		"kathmandu", "biratnagar", "pokhara", "dharan", "itahari", "janakpur", "lumbini", "gorkha",
		"sherpa", "thapa", "gurung", "basnet", "basnyat", "nepal", "nepali", "dhanyabaad"
	)

	/** Romanized words that are Hindi even though they are Latin letters. */
	private val HINDI_ROMAN = setOf(
		"dhanyavad", "dhanyavaad", "dhanyawad", "shukriya", "accha", "acha", "theek", "thik",
		"kaisa", "kaisi", "kaise", "kahan", "kidhar", "idhar", "udhar", "kya", "kyon", "kyu", "kab",
		"nahin", "nahi", "aap", "aapka", "hum", "tum", "mera", "meri", "tumhara", "bhai", "bhaiya", "didi",
		"behan", "mata", "pita", "rupaya", "rupaye", "paise", "paisa", "namaste",
		"hindi", "hindustan", "bharat", "delhi", "mumbai", "bambai", "kolkata", "chennai", "lucknow",
		"kripya", "alvida", "bahut", "bohot", "abhi", "phir", "log", "chahiye", "mein", "mera"
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
