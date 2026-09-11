package com.blindtechnexus.alteloquencetts

import android.content.Context
import android.os.Build
import java.io.File

/**
 * Where the engine keeps what it needs before the phone has been unlocked.
 *
 * A screen reader is the first thing wanted on a locked phone and the last
 * thing that can wait for a PIN, so the speech service is marked direct-boot
 * aware and starts while the user's own storage is still encrypted. Ordinary
 * app storage is not there yet at that point: reading it throws, and settings
 * read through it would come back as the defaults, so the voice would be the
 * wrong one until somebody unlocked the phone and restarted the engine.
 *
 * Device-protected storage is readable from the moment the phone boots, and is
 * what everything the engine reads at startup lives in. It is a different
 * directory rather than a different kind of file, so what is stored there is
 * moved once, the first time a build with this in it runs after an unlock.
 */
object DirectBoot {

	/** The context to read settings and the files they name through. */
	fun storage(context: Context): Context {
		val app = context.applicationContext
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return app
		val device = app.createDeviceProtectedStorageContext() ?: return app
		device.moveSharedPreferencesFrom(app, prefsName(app))
		return device
	}

	/** The directory the user dictionary lives in, wherever it is being kept. */
	fun dictionaries(context: Context): File =
		File(storage(context).filesDir, DICTIONARIES)

	private fun prefsName(app: Context) = "${app.packageName}_preferences"

	const val DICTIONARIES = "dictionaries"
}
