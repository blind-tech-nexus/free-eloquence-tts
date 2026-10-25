package com.blindtechnexus.freeeloquencetts

/**
 * Dates and times written the way phones write them -- `25/10/2026`,
 * `2026-10-25`, `14:30` -- turned into words a Hindi or Nepali voice says
 * the way a person says them, before the engine sees the text.
 *
 * The native Indic romanizer already reads bare numbers natively (cardinal
 * numbers with लाख/करोड़ grouping, decimal fractions, percentages and
 * ₹ amounts), so none of that is touched here. What it cannot know is that
 * the slashes in `25/10/2026` are a date and not three numbers with the
 * word for "divided by" between them, or that `14:30` is a time of day.
 * That is a fact about the shape of the text rather than about any word in
 * it, so it is settled here, in the language the segment will be spoken in.
 *
 * The answers are written with Devanagari digits and Devanagari month
 * names, because the segment is on its way to a Devanagari voice: the
 * romanizer reads the digits back as the same numbers and the month as an
 * ordinary word. A date that does not validate -- month 13, day 40, a
 * version number with dots -- is left exactly as written for the number
 * reading to deal with.
 *
 * Only ever called for Hindi and Nepali segments, and it answers the text
 * unchanged, without allocating, when there is no digit-and-separator
 * shape in it at all.
 */
object IndicText {

	private val MONTHS_HI = arrayOf(
		"जनवरी", "फ़रवरी", "मार्च", "अप्रैल", "मई", "जून",
		"जुलाई", "अगस्त", "सितंबर", "अक्टूबर", "नवंबर", "दिसंबर"
	)

	private val MONTHS_NE = arrayOf(
		"जनवरी", "फेब्रुअरी", "मार्च", "अप्रिल", "मे", "जुन",
		"जुलाई", "अगस्ट", "सेप्टेम्बर", "अक्टोबर", "नोभेम्बर", "डिसेम्बर"
	)

	// A date, day first or year first, with slashes or dashes. Dots are
	// deliberately not separators: 2.5.1 is a version, not a birthday.
	private val DATE_DMY = Regex("""(?<!\d)(\d{1,2})[/-](\d{1,2})[/-](\d{2,4})(?!\d)""")
	private val DATE_YMD = Regex("""(?<!\d)(\d{4})[/-](\d{1,2})[/-](\d{1,2})(?!\d)""")

	// A time of day. The minute must be two digits and no third `:56` part
	// may follow, so a ratio or a timestamp's tail is not half-eaten.
	private val TIME = Regex("""(?<!\d)(\d{1,2}):(\d{2})(?!\d)(?!:\d)""")

	/** [text] with its dates and times said in [language] (Hindi or Nepali). */
	fun apply(text: String, language: Int): String {
		if (language != Eci.HINDI && language != Eci.NEPALI) return text
		if (text.isEmpty()) return text
		// Nothing to do without a digit next to a separator, which is most
		// text; saying so costs one scan and no copy.
		var shaped = false
		var digitSeen = false
		for (c in text) {
			if (c.isDigit() || c in '०'..'९') digitSeen = true
			else if (digitSeen && (c == '/' || c == '-' || c == ':')) { shaped = true; break }
		}
		if (!shaped) return text

		val months = if (language == Eci.NEPALI) MONTHS_NE else MONTHS_HI
		var out = DATE_YMD.replace(text) { m ->
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
		return out
	}

	private fun valid(day: Int, month: Int): Boolean = month in 1..12 && day in 1..31

	/** [n] written in Devanagari digits, which the Indic voices read as the
	 *  number itself. */
	private fun dev(n: Int): String {
		val digits = "०१२३४५६७८९"
		val raw = n.toString()
		val out = StringBuilder(raw.length)
		for (c in raw) out.append(if (c in '0'..'9') digits[c - '0'] else c)
		return out.toString()
	}
}
