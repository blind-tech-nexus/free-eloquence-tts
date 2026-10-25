package com.blindtechnexus.freeeloquencetts

/**
 * Dates, times, numbers, currencies, abbreviations, and Devanagari phonological
 * shapes written the way phones and users write them -- `25/10/2026`, `2026-10-25`,
 * `14:30`, `रु. ५००`, `₹५००`, `वि.सं. २०८१` -- turned into authentic Devanagari words
 * that a native Hindi or Nepali voice says the way a native speaker says them.
 *
 * Implements standard spoken Hindi and Nepali pronunciation patterns, including:
 * - Full Gregorian and Bikram Sambat calendar date recognition and vocalization.
 * - Spoken time of day in authentic colloquial Hindi ("बजकर", "बजे") and Nepali
 *   ("बजेर", "बजे").
 * - Currency expressions: रु., रू., Rs., ₹, $.
 * - Standard South Asian administrative, academic, and measurement abbreviations
 *   (वि.सं., ई.सं., डा., प्रा., प्रो., लि., प्रा.लि., कि.मी., कि.ग्रा., नं.).
 * - Difficult Devanagari cluster phonetics (ऋ -> रि, ॐ -> ओम्, word-final ः -> ह).
 * - Full cardinal number system for Hindi and Nepali (0..99 + सय/सौ, हजार/हज़ार,
 *   लाख, करोड/करोड़, अर्ब/अरब, खर्ब/खरब).
 */
object IndicText {

	val MONTHS_HI = arrayOf(
		"जनवरी", "फ़रवरी", "मार्च", "अप्रैल", "मई", "जून",
		"जुलाई", "अगस्त", "सितंबर", "अक्टूबर", "नवंबर", "दिसंबर"
	)

	val MONTHS_NE = arrayOf(
		"जनवरी", "फेब्रुअरी", "मार्च", "अप्रिल", "मे", "जुन",
		"जुलाई", "अगस्ट", "सेप्टेम्बर", "अक्टोबर", "नोभेम्बर", "डिसेम्बर"
	)

	// Bikram Sambat months in Nepali and Hindi
	val BS_MONTHS_NE = arrayOf(
		"बैशाख", "जेठ", "असार", "साउन", "भदौ", "असोज",
		"कात्तिक", "मंसिर", "पुस", "माघ", "फागुन", "चैत"
	)

	val BS_MONTHS_HI = arrayOf(
		"वैशाख", "ज्येष्ठ", "आषाढ़", "श्रावण", "भाद्रपद", "अश्विन",
		"कार्तिक", "मार्गशीर्ष", "पौष", "माघ", "फाल्गुन", "चैत्र"
	)

	// Nepali 0..99 numbers in Devanagari words
	val NUM_0_99_NE = arrayOf(
		"शून्य", "एक", "दुई", "तीन", "चार", "पाँच", "छ", "सात", "आठ", "नौ", "दस",
		"एघार", "बाह्र", "तेह्र", "चौध", "पन्ध्र", "सोह्र", "सत्र", "अठार", "उन्नाइस", "बीस",
		"एक्काइस", "बाइस", "तेइस", "चौबिस", "पच्चिस", "छब्बिस", "सत्ताइस", "अट्ठाइस", "उनन्तीस", "तीस",
		"एकतीस", "बत्तीस", "तेत्तीस", "चौँतीस", "पैँतीस", "छत्तीस", "सैँतीस", "अड्तीस", "उनन्चालीस", "चालीस",
		"एकचालीस", "बयानलीस", "त्रिचालीस", "चवालीस", "पैँतालीस", "छयालीस", "सत्चालीस", "अड्चालीस", "उनन्पचास", "पचास",
		"एकाउन्न", "बाउन्न", "त्रिपन्न", "चौवन्न", "पचपन्न", "छप्पन्न", "संताउन्न", "अन्ठाउन्न", "उनन्साठी", "साठी",
		"एकसट्ठी", "बासट्ठी", "त्रिसट्ठी", "चौँसट्ठी", "पैँसट्ठी", "छ्यासट्ठी", "सतसट्ठी", "अड्सट्ठी", "उनन्सत्तरी", "सत्तरी",
		"एकहत्तर", "बहत्तर", "त्रिहत्तर", "चौहत्तर", "पचहत्तर", "छहत्तर", "सतहत्तर", "अठहत्तर", "उनासी", "अस्सी",
		"एकासी", "बयानसी", "त्रियासी", "चौरासी", "पचासी", "छयासी", "सतासी", "अठासी", "उनानब्बे", "नब्बे",
		"एकानब्बे", "बयानब्बे", "त्रियानब्बे", "चौरानब्बे", "पन्चानब्बे", "छयानब्बे", "संतानब्बे", "अन्ठानब्बे", "उनान्सय"
	)

	// Hindi 0..99 numbers in Devanagari words
	val NUM_0_99_HI = arrayOf(
		"शून्य", "एक", "दो", "तीन", "चार", "पाँच", "छह", "सात", "आठ", "नौ", "दस",
		"ग्यारह", "बारह", "तेरह", "चौदह", "पंद्रह", "सोलह", "सत्रह", "अठारह", "उन्नीस", "बीस",
		"इक्कीस", "बाइस", "तेईस", "चौबीस", "पच्चीस", "छब्बीस", "सत्ताईस", "अट्ठाईस", "उनतीस", "तीस",
		"इकतीस", "बत्तीस", "तैंतीस", "चौंतीस", "पैंतीस", "छत्तीस", "सैंतीस", "अड़तीस", "उनतालीस", "चालीस",
		"इकतालीस", "बयालीस", "तैंतालीस", "चवालीस", "पैंतालीस", "छियालीस", "सैंतालीस", "अड़तालीस", "उनचास", "पचास",
		"इक्यावन", "बावन", "तिरपन", "चौवन", "पचपन", "छप्पन", "सत्तावन", "अट्ठावन", "उनसठ", "साठ",
		"इकसठ", "बासठ", "तिरसठ", "चौंसठ", "पैंसठ", "छियासठ", "सड़सठ", "अड़सठ", "उनहत्तर", "सत्तर",
		"इकहत्तर", "बहत्तर", "तिहत्तर", "चौहत्तर", "पचहत्तर", "छिहत्तर", "सतहत्तर", "अठहत्तर", "उन्यासी", "अस्सी",
		"इक्यासी", "बयासी", "तिरासी", "चौरासी", "पचासी", "छियासी", "सत्तासी", "अट्ठासी", "नवासी", "नब्बे",
		"इक्यानवे", "बानवे", "तिरानवे", "चौरानवे", "पचानवे", "छियानवे", "सत्तानवे", "अट्ठानवे", "निन्यानवे"
	)

	// A date, day first or year first, with slashes or dashes. Dots are
	// deliberately not separators: 2.5.1 is a version, not a birthday.
	private val DATE_DMY = Regex("""(?<!\d)(\d{1,2})[/-](\d{1,2})[/-](\d{2,4})(?!\d)""")
	private val DATE_YMD = Regex("""(?<!\d)(\d{4})[/-](\d{1,2})[/-](\d{1,2})(?!\d)""")

	// A time of day. The minute must be two digits and no third `:56` part
	// may follow, so a ratio or a timestamp's tail is not half-eaten.
	private val TIME = Regex("""(?<!\d)(\d{1,2}):(\d{2})(?!\d)(?!:\d)""")

	// Common abbreviations
	private val ABBREV_REGEX = Regex("""(?<![\p{L}\d])(वि\.सं\.|वि०सं०|ई\.सं\.|ई०सं०|डा\.|डा०|प्रा\.|प्रा०|प्रो\.|प्रो०|इ\.|ई\.|लि\.|प्रा\.लि\.|नं\.|कि\.मी\.|कि\.ग्रा\.|मि\.मि\.|से\.मी\.)(?=\s|[।॥,\.!?]|$)""")

	// Currency expressions like रु. 500, रु.५००, रू 500, Rs. 500, ₹500, ₹ 500
	private val CURRENCY_REGEX = Regex("""(?<![\p{L}\d])(रु\.|रू\.|रू|Rs\.|Rs|₹)\s*([0-9०-९]+(?:,[0-9०-९]+)*(?:\.[0-9०-९]+)?)(?![\p{L}\d])""")

	/** [text] with its dates, times, currencies, abbreviations and Devanagari shapes said in [language] (Hindi or Nepali). */
	fun apply(text: String, language: Int): String {
		if (language != Eci.HINDI && language != Eci.NEPALI) return text
		if (text.isEmpty()) return text

		var out = text

		// Expand standard South Asian abbreviations
		if (out.contains('.') || out.contains('०')) {
			out = expandAbbreviations(out, language)
		}

		// Expand currency
		if (out.contains("रु") || out.contains("रू") || out.contains("Rs") || out.contains("₹")) {
			out = expandCurrency(out, language)
		}

		// Quick check for dates or times (digit next to '/', '-', ':')
		var shaped = false
		var digitSeen = false
		for (c in out) {
			if (c.isDigit() || c in '०'..'९') digitSeen = true
			else if (digitSeen && (c == '/' || c == '-' || c == ':')) { shaped = true; break }
		}

		if (shaped) {
			val months = if (language == Eci.NEPALI) MONTHS_NE else MONTHS_HI
			out = DATE_YMD.replace(out) { m ->
				val year = m.groupValues[1].toIntOrNull()
				val month = m.groupValues[2].toIntOrNull()
				val day = m.groupValues[3].toIntOrNull()
				if (year == null || month == null || day == null || !valid(day, month)) m.value
				else "${dev(day)} ${months[month - 1]} ${dev(year)}"
			}
			out = DATE_DMY.replace(out) { m ->
				val day = m.groupValues[1].toIntOrNull()
				val month = m.groupValues[2].toIntOrNull()
				val year = m.groupValues[3].toIntOrNull()
				if (day == null || month == null || year == null || !valid(day, month)) m.value
				else "${dev(day)} ${months[month - 1]} ${dev(year)}"
			}
			out = TIME.replace(out) { m ->
				val hour = m.groupValues[1].toIntOrNull()
				val minute = m.groupValues[2].toIntOrNull()
				if (hour == null || minute == null || hour > 23 || minute > 59) m.value
				else if (language == Eci.NEPALI) {
					if (minute == 0) "${dev(hour)} बजे" else "${dev(hour)} बजेर ${dev(minute)} मिनेट"
				} else {
					if (minute == 0) "${dev(hour)} बजे" else "${dev(hour)} बजकर ${dev(minute)} मिनट"
				}
			}
		}

		// Phonological normalization for difficult Devanagari clusters
		if (out.contains('ऋ') || out.contains('ॐ') || out.contains('ः')) {
			out = normalizePhonology(out, language)
		}

		return out
	}

	private fun valid(day: Int, month: Int): Boolean = month in 1..12 && day in 1..31

	/** [n] written in Devanagari digits, which the Indic voices read as the number itself. */
	fun dev(n: Int): String {
		val digits = "०१२३४५६७८९"
		val raw = n.toString()
		val out = StringBuilder(raw.length)
		for (c in raw) out.append(if (c in '0'..'9') digits[c - '0'] else c)
		return out.toString()
	}

	/** Convert ASCII digits string into Devanagari digits string. */
	fun toDevanagariDigits(s: String): String {
		val digits = "०१२३४५६७८९"
		val out = StringBuilder(s.length)
		for (c in s) out.append(if (c in '0'..'9') digits[c - '0'] else c)
		return out.toString()
	}

	/** Convert number (0..999999999999) to full spoken Devanagari words. */
	fun numberToWords(n: Long, language: Int): String {
		if (n < 0) return (if (language == Eci.NEPALI) "ऋण " else "माइनस ") + numberToWords(-n, language)
		val words0to99 = if (language == Eci.NEPALI) NUM_0_99_NE else NUM_0_99_HI
		val hundred = if (language == Eci.NEPALI) "सय" else "सौ"
		val thousand = if (language == Eci.NEPALI) "हजार" else "हज़ार"
		val lakh = "लाख"
		val crore = if (language == Eci.NEPALI) "करोड" else "करोड़"
		val arab = if (language == Eci.NEPALI) "अर्ब" else "अरब"
		val kharab = if (language == Eci.NEPALI) "खर्ब" else "खरब"

		if (n < 100) return words0to99[n.toInt()]
		if (n < 1000) {
			val h = (n / 100).toInt()
			val rem = n % 100
			val prefix = "${words0to99[h]} $hundred"
			return if (rem == 0L) prefix else "$prefix ${words0to99[rem.toInt()]}"
		}
		if (n < 100000) {
			val th = (n / 1000).toInt()
			val rem = n % 1000
			val prefix = "${numberToWords(th.toLong(), language)} $thousand"
			return if (rem == 0L) prefix else "$prefix ${numberToWords(rem, language)}"
		}
		if (n < 10000000) {
			val lk = (n / 100000).toInt()
			val rem = n % 100000
			val prefix = "${numberToWords(lk.toLong(), language)} $lakh"
			return if (rem == 0L) prefix else "$prefix ${numberToWords(rem, language)}"
		}
		if (n < 1000000000) {
			val cr = (n / 10000000).toInt()
			val rem = n % 10000000
			val prefix = "${numberToWords(cr.toLong(), language)} $crore"
			return if (rem == 0L) prefix else "$prefix ${numberToWords(rem, language)}"
		}
		if (n < 100000000000) {
			val ar = (n / 1000000000).toInt()
			val rem = n % 1000000000
			val prefix = "${numberToWords(ar.toLong(), language)} $arab"
			return if (rem == 0L) prefix else "$prefix ${numberToWords(rem, language)}"
		}
		val kh = (n / 100000000000).toInt()
		val rem = n % 100000000000
		val prefix = "${numberToWords(kh.toLong(), language)} $kharab"
		return if (rem == 0L) prefix else "$prefix ${numberToWords(rem, language)}"
	}

	private fun expandAbbreviations(text: String, language: Int): String {
		return ABBREV_REGEX.replace(text) { m ->
			when (m.value) {
				"वि.सं.", "वि०सं०" -> "विक्रम संवत्"
				"ई.सं.", "ई०सं०" -> "ईस्वी संवत्"
				"डा.", "डा०" -> "डाक्टर"
				"प्रा.", "प्रा०" -> "प्राध्यापक"
				"प्रो.", "प्रो०" -> "प्रोफेसर"
				"इ.", "ई." -> "इन्जिनियर"
				"लि." -> "लिमिटेड"
				"प्रा.लि." -> "प्राइभेट लिमिटेड"
				"नं." -> "नम्बर"
				"कि.मी." -> if (language == Eci.NEPALI) "किलोमिटर" else "किलोमीटर"
				"कि.ग्रा." -> "किलोग्राम"
				"मि.मि." -> if (language == Eci.NEPALI) "मिलिमिटर" else "मिलीमीटर"
				"से.मी." -> if (language == Eci.NEPALI) "सेन्टिमिटर" else "सेंटीमीटर"
				else -> m.value
			}
		}
	}

	private fun expandCurrency(text: String, language: Int): String {
		return CURRENCY_REGEX.replace(text) { m ->
			val symbol = m.groupValues[1]
			val amt = toDevanagariDigits(m.groupValues[2])
			val unit = when (symbol) {
				"₹" -> if (language == Eci.NEPALI) "रुपैयाँ" else "रुपये"
				"रु.", "रू.", "रू", "Rs.", "Rs" -> if (language == Eci.NEPALI) "रुपैयाँ" else "रुपये"
				else -> "रुपैयाँ"
			}
			"$amt $unit"
		}
	}

	private fun normalizePhonology(text: String, language: Int): String {
		var s = text
		// ॐ -> ओम्
		if (s.contains('ॐ')) s = s.replace("ॐ", "ओम्")

		// ऋ -> रि (in Nepali and spoken Hindi)
		if (s.contains('ऋ')) {
			s = s.replace("ऋषि", "रिषि")
				.replace("ऋण", "रिन")
				.replace("ऋतु", "रितु")
				.replace("ऋषभ", "रिषभ")
				.replace("ऋग्वेद", "रिग्वेद")
		}

		// Final visarga ः -> ह (like प्रातः -> प्रातह, क्रमशः -> क्रमशह, प्रायः -> प्रायह)
		if (s.contains('ः')) {
			s = s.replace(Regex("""(\p{L})ः(?=\s|[।॥,\.!?]|$)""")) { m ->
				"${m.groupValues[1]}ह"
			}
		}
		return s
	}
}
