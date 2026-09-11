package com.blindtechnexus.alteloquencetts

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * The persistent notification that holds the engine in the foreground.
 *
 * A foreground service is the one thing that tells Android, in the one
 * language Android always listens to, that an app is doing something the user
 * asked for and must not be killed. A text to speech engine is the awkward
 * case: idle almost all the time, needed at once when it is not, which is the
 * shape every battery policy is written to kill. The notification makes the
 * engine visible, so the system's own rules leave it alone.
 *
 * The notification is checked every [CHECK_MS] while it is on, and put back
 * if anything took it away -- a notification can be swiped, dismissed, or
 * dropped when the system rebuilds its list, and a foreground service whose
 * notification has gone is a foreground service nothing protects. On Android
 * 13 and later the check looks the other way when the notification permission
 * has not been given, because then the system hides the notification by its
 * own choice and re-posting it every few seconds is noise for nothing.
 */
class KeepAlive(private val service: Service) {

	private val handler = Handler(Looper.getMainLooper())
	private var running = false

	private val check = object : Runnable {
		override fun run() {
			if (!running) return
			if (!isShown()) post()
			handler.postDelayed(this, CHECK_MS)
		}
	}

	/** Turns the notification on or off, matching the setting. No-op when the
	 *  state has not changed. */
	fun sync(enabled: Boolean) {
		if (enabled) start() else stop()
	}

	private fun start() {
		if (running) return
		running = true
		ensureChannel()
		post()
		handler.removeCallbacks(check)
		handler.postDelayed(check, CHECK_MS)
	}

	private fun stop() {
		if (!running) return
		running = false
		handler.removeCallbacks(check)
		runCatching { service.stopForeground(Service.STOP_FOREGROUND_REMOVE) }
	}

	private fun post() {
		try {
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
				service.startForeground(
					ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
				)
			} else {
				service.startForeground(ID, notification())
			}
		} catch (refused: SecurityException) {
			// A build whose manifest does not carry the permission, or a
			// device that has taken it away. The engine speaks on without the
			// protection, which is better than crashing the service.
			Log.e(TAG, "the system refused the keep-alive notification", refused)
		}
	}

	/** Whether the notification is still there. On Android 13 and later a
	 *  missing notification permission hides it on purpose, and that counts as
	 *  shown rather than as lost. */
	private fun isShown(): Boolean {
		if (
			Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
			service.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
			PackageManager.PERMISSION_GRANTED
		) return true
		val manager = service.getSystemService(NotificationManager::class.java) ?: return true
		return manager.activeNotifications.any { it.id == ID }
	}

	private fun notification(): Notification {
		val text = service.getString(R.string.keep_alive_text)
		val title = service.getString(R.string.keep_alive_title)
		val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			Notification.Builder(service, CHANNEL_ID)
		} else {
			@Suppress("DEPRECATION")
			Notification.Builder(service)
		}
		return builder
			.setSmallIcon(R.drawable.ic_launcher_foreground)
			.setContentTitle(title)
			.setContentText(text)
			.setOngoing(true)
			.setShowWhen(false)
			.build()
	}

	private fun ensureChannel() {
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
		val manager = service.getSystemService(NotificationManager::class.java) ?: return
		val channel = NotificationChannel(
			CHANNEL_ID,
			service.getString(R.string.battery_open),
			NotificationManager.IMPORTANCE_LOW
		)
		channel.setShowBadge(false)
		manager.createNotificationChannel(channel)
	}

	companion object {
		const val TAG = "alteloquencetts"
		const val CHANNEL_ID = "keep_alive"
		const val ID = 1
		/** How often the notification is looked for, in milliseconds. */
		const val CHECK_MS = 3000L
	}
}

/**
 * What the user did, written to the system log when they asked for it.
 *
 * A screen reader that has gone quiet has to be fixed by reasoning backwards
 * from what happened, and a log of what was changed is the raw material for
 * that reasoning. Nothing sensitive is ever written: only which switch moved
 * and which way.
 */
object Tracker {

	const val TAG = "alteloquencetts-actions"

	/** Records [what], when tracking is on, and answers whether it was
	 *  written. The answer is what a test can hold onto, since whether a line
	 *  went to the system log is not readable back out of it. */
	fun action(settings: Settings?, what: String): Boolean {
		if (settings == null || !settings.trackActions) return false
		Log.i(TAG, what)
		return true
	}
}
