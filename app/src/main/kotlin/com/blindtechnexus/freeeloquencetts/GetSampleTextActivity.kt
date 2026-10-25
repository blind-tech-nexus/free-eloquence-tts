package com.blindtechnexus.freeeloquencetts

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.speech.tts.TextToSpeech

/** The sentence the system settings screen speaks when someone taps Listen. */
class GetSampleTextActivity : Activity() {

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		val lang = intent?.getStringExtra("language")
		val country = intent?.getStringExtra("country")
		val res = when (Eci.languageFor(lang, country)?.code) {
			Eci.GERMAN -> R.string.preview_text_de
			Eci.SPANISH_SPAIN, Eci.SPANISH_MEXICO -> R.string.preview_text_es
			Eci.FRENCH_FRANCE, Eci.FRENCH_CANADA -> R.string.preview_text_fr
			Eci.ITALIAN -> R.string.preview_text_it
			Eci.HINDI -> R.string.sample_text_hi
			Eci.NEPALI -> R.string.sample_text_ne
			else -> R.string.sample_text
		}
		val result = Intent().apply {
			putExtra(TextToSpeech.Engine.EXTRA_SAMPLE_TEXT, getString(res))
		}
		setResult(TextToSpeech.LANG_AVAILABLE, result)
		finish()
	}
}
