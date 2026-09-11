package com.blindtechnexus.alteloquencetts

/**
 * Emoji and emoticons, read by name or not read at all.
 *
 * The engine knows no emoji: its Western code set stops at 0xFF, and anything
 * above that is dropped to a space on the way in. What the reader hears is a
 * gap where a face or a heart was, which is information lost. The switch here
 * says what to do instead.
 *
 * On, every emoji in the table below is replaced with its name -- "😀" becomes
 * "grinning face" -- and the common typed emoticons are given names too, so
 * ":-)" is "smiley face" rather than "colon hyphen right parenthesis". The
 * names are the words this engine can already say, so nothing depends on a
 * voice knowing an emoji.
 *
 * Off, both are taken out, which is the silence the engine always gave. The
 * table is deliberately a common few rather than the whole of Unicode: a list
 * of three thousand names would be slower to read than a gap, and the ones
 * that turn up in messages are here. An emoji not in the table is left for
 * [WesternText] to drop, which is the same as off.
 */
object Emoji {

	private val NAMES = mapOf(
		// Faces.
		"😀" to "grinning face",
		"😁" to "grinning face",
		"😂" to "face with tears of joy",
		"😃" to "smiling face",
		"😄" to "smiling face",
		"😅" to "grinning face with sweat",
		"😆" to "laughing face",
		"😉" to "winking face",
		"😊" to "smiling face",
		"😋" to "savoring face",
		"😎" to "smiling face with sunglasses",
		"😍" to "smiling face with hearts",
		"😘" to "face blowing a kiss",
		"😗" to "kissing face",
		"😙" to "kissing face",
		"😚" to "kissing face",
		"🙂" to "slightly smiling face",
		"🤗" to "hugging face",
		"🤔" to "thinking face",
		"😐" to "neutral face",
		"😑" to "expressionless face",
		"😶" to "face without mouth",
		"🙄" to "face with rolling eyes",
		"😏" to "smirking face",
		"😣" to "persevering face",
		"😥" to "sad but relieved face",
		"😮" to "face with open mouth",
		"🤐" to "zipper mouth face",
		"😯" to "hushed face",
		"😪" to "sleepy face",
		"😫" to "tired face",
		"😴" to "sleeping face",
		"😌" to "relieved face",
		"😛" to "face with stuck out tongue",
		"😜" to "winking face with tongue",
		"😝" to "squinting face with tongue",
		"🤤" to "drooling face",
		"😒" to "unamused face",
		"😓" to "face with cold sweat",
		"😔" to "pensive face",
		"😕" to "confused face",
		"🙃" to "upside down face",
		"🤑" to "money mouth face",
		"😲" to "astonished face",
		"😳" to "flushed face",
		"😞" to "disappointed face",
		"😟" to "worried face",
		"😠" to "angry face",
		"😡" to "pouting face",
		"😢" to "crying face",
		"😭" to "loudly crying face",
		"😱" to "face screaming in fear",
		"😨" to "fearful face",
		"😰" to "face with open mouth and cold sweat",
		"😷" to "face with medical mask",
		"🤒" to "face with thermometer",
		"🤕" to "face with bandage",
		"🤢" to "nauseated face",
		"🤮" to "face vomiting",
		"🤧" to "sneezing face",
		"😇" to "smiling face with halo",
		"🤠" to "cowboy face",
		"🤡" to "clown face",
		"🤥" to "lying face",
		"🤫" to "shushing face",
		"🤭" to "face with hand over mouth",
		"🤯" to "exploding head",
		"🥳" to "partying face",
		"🥺" to "pleading face",
		"😈" to "smiling face with horns",
		"👿" to "angry face with horns",
		"👻" to "ghost",
		"💀" to "skull",
		"👽" to "alien",
		"🤖" to "robot",
		"😺" to "smiling cat face",
		"😸" to "grinning cat face",
		"😹" to "cat face with tears of joy",
		"😻" to "smiling cat face with hearts",
		"😼" to "cat face with wry smile",
		"😽" to "kissing cat face",
		"🙀" to "weary cat face",
		"😿" to "crying cat face",
		"😾" to "pouting cat face",
		// Hearts and gestures.
		"❤" to "red heart",
		"🧡" to "orange heart",
		"💛" to "yellow heart",
		"💚" to "green heart",
		"💙" to "blue heart",
		"💜" to "purple heart",
		"🖤" to "black heart",
		"💔" to "broken heart",
		"💕" to "two hearts",
		"💖" to "sparkling heart",
		"💗" to "growing heart",
		"💓" to "beating heart",
		"💘" to "heart with arrow",
		"💝" to "heart with ribbon",
		"💞" to "revolving hearts",
		"💟" to "heart decoration",
		"👍" to "thumbs up",
		"👎" to "thumbs down",
		"👌" to "OK hand",
		"👏" to "clapping hands",
		"🙌" to "raised hands",
		"🙏" to "folded hands",
		"👋" to "waving hand",
		"🤝" to "handshake",
		"💪" to "flexed biceps",
		"✌" to "victory hand",
		"🤞" to "crossed fingers",
		"👊" to "fist",
		"✊" to "raised fist",
		"👈" to "pointing left",
		"👉" to "pointing right",
		"👆" to "pointing up",
		"👇" to "pointing down",
		// Marks and symbols.
		"✅" to "check mark",
		"❌" to "cross mark",
		"❓" to "question mark",
		"❗" to "exclamation mark",
		"⚠" to "warning",
		"🚫" to "prohibited",
		"⭐" to "star",
		"🌟" to "glowing star",
		"✨" to "sparkles",
		"🔥" to "fire",
		"🎉" to "party popper",
		"🎁" to "wrapped gift",
		"🎂" to "birthday cake",
		"☕" to "hot beverage",
		"🍕" to "pizza",
		"⚽" to "soccer ball",
		"🏀" to "basketball",
		"🎵" to "musical note",
		"🎶" to "musical notes",
		"💯" to "hundred points",
		"👀" to "eyes",
		"👁" to "eye",
		"🆗" to "OK",
		"🔒" to "locked",
		"🔓" to "unlocked",
		"🔔" to "bell",
		"🔕" to "bell with slash",
		"🔋" to "battery",
		"🔌" to "electric plug",
		"💡" to "light bulb",
		"📱" to "mobile phone",
		"💻" to "laptop",
		"⌚" to "watch",
		"📷" to "camera",
		"✈" to "airplane",
		"🚗" to "car",
		"🚌" to "bus",
		"🚕" to "taxi",
		"🚀" to "rocket",
		"🌙" to "crescent moon",
		"☀" to "sun",
		"🌧" to "cloud with rain",
		"❄" to "snowflake",
		"🌈" to "rainbow",
		"🌍" to "earth"
	)

	/** The typed emoticons, longest first so ":-)" is read before ")". */
	private val EMOTICONS = listOf(
		Regex("(?<![A-Za-z0-9]):-\\)(?![A-Za-z0-9])") to "smiley face",
		Regex("(?<![A-Za-z0-9]):\\)(?![A-Za-z0-9])") to "smiley face",
		Regex("(?<![A-Za-z0-9]):-D(?![A-Za-z0-9])") to "big grin",
		Regex("(?<![A-Za-z0-9]):D(?![A-Za-z0-9])") to "big grin",
		Regex("(?<![A-Za-z0-9]);-\\)(?![A-Za-z0-9])") to "wink",
		Regex("(?<![A-Za-z0-9]);\\)(?![A-Za-z0-9])") to "wink",
		Regex("(?<![A-Za-z0-9]):-\\((?![A-Za-z0-9])") to "frowning face",
		Regex("(?<![A-Za-z0-9]):\\((?![A-Za-z0-9])") to "frowning face",
		Regex("(?<![A-Za-z0-9]):-P(?![A-Za-z0-9])") to "tongue out",
		Regex("(?<![A-Za-z0-9]):P(?![A-Za-z0-9])") to "tongue out",
		Regex("(?<![A-Za-z0-9]):'\\((?![A-Za-z0-9])") to "crying face",
		Regex("(?<![A-Za-z0-9]):-\\|(?![A-Za-z0-9])") to "neutral face",
		Regex("(?<![A-Za-z0-9]):\\|(?![A-Za-z0-9])") to "neutral face",
		Regex("(?<![A-Za-z0-9]):-/(?![A-Za-z0-9])") to "confused face",
		Regex("(?<![A-Za-z0-9]):/(?![A-Za-z0-9])") to "confused face",
		Regex("(?<![A-Za-z0-9])XD(?![A-Za-z0-9])") to "laughing",
		Regex("(?<![A-Za-z0-9])<3(?![A-Za-z0-9])") to "heart",
		Regex("(?<![A-Za-z0-9])</3(?![A-Za-z0-9])") to "broken heart",
		Regex("(?<![A-Za-z0-9])8-\\)(?![A-Za-z0-9])") to "cool",
		Regex("(?<![A-Za-z0-9])B-\\)(?![A-Za-z0-9])") to "cool",
		Regex("(?<![A-Za-z0-9])O_O(?![A-Za-z0-9])") to "surprised face",
		Regex("(?<![A-Za-z0-9])\\^_\\^(?![A-Za-z0-9])") to "happy face",
		Regex("(?<![A-Za-z0-9])T_T(?![A-Za-z0-9])") to "crying face",
		Regex("(?<![A-Za-z0-9])>_<(?![A-Za-z0-9])") to "frustrated face"
	)

	private val SPACES = Regex(" {2,}")

	/** [text] with emoji and emoticons named when [read] is on, and taken out
	 *  when it is off. */
	fun apply(text: String, read: Boolean): String {
		if (text.isEmpty()) return text
		val withEmoji = StringBuilder(text.length + 16)
		var at = 0
		while (at < text.length) {
			val c = text[at]
			val here = if (c.isHighSurrogate() && at + 1 < text.length && text[at + 1].isLowSurrogate()) {
				text.substring(at, at + 2)
			} else {
				c.toString()
			}
			val name = NAMES[here]
			if (name != null) {
				withEmoji.append(if (read) " $name " else " ")
			} else {
				withEmoji.append(here)
			}
			at += here.length
		}
		val flat = SPACES.replace(withEmoji.toString(), " ")
		var out = flat
		for ((pattern, name) in EMOTICONS) {
			out = pattern.replace(out, if (read) " $name " else " ")
		}
		return SPACES.replace(out, " ").trim()
	}
}
