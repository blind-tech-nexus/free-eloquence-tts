package com.blindtechnexus.alteloquencetts

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * The service whose only job is the notification.
 *
 * The engine's own service cannot be the one to hold it. A text to speech
 * service belongs to the system: it is started when a reader binds to it and
 * destroyed when the last one lets go, which for most phones is most of the
 * time. A notification that comes and goes with that is a keep-alive that
 * keeps nothing alive in the one situation it is for -- the phone idle, the
 * reader quiet, and the next thing said arriving some time later.
 *
 * So this service exists on its own. It is started when the setting is turned
 * on, by whatever is in front of the user at the time, and by the boot receiver
 * when that has been asked for; it is stopped when the setting is turned off.
 * All it does is claim the foreground and answer what the notification is
 * asked to do, which is the whole of what the protection is made of.
 *
 * It posts the notification the same way the speech service always did -- the
 * same id, the same channel, the same look for it every few seconds in case
 * anything has taken it away -- so nothing about what a person sees changes
 * because the thing holding it up has moved.
 */
class KeepAliveService : Service() {

	private val handler = Handler(Looper.getMainLooper())

	private var active = false

	private val check = object : Runnable {
		override fun run() {
			if (!active) return
			// A notification that has been swiped away leaves a foreground
			// service that nothing protects, unless the user has refused the
			// permission to show it at all, which is their answer rather than
			// something to fight over every three seconds.
			if (KeepAliveNotification.notificationsAllowed(this@KeepAliveService) &&
				!KeepAliveNotification.isPosted(this@KeepAliveService)
			) {
				claim()
			}
			handler.postDelayed(this, KeepAlive.CHECK_MS)
		}
	}

	override fun onCreate() {
		super.onCreate()
		active = true
		isRunning = true
	}

	override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
		KeepAliveNotification.ensureChannel(this)
		// A redelivery after the system took the process away has no intent to
		// go with it, so the setting is what says whether it is wanted now.
		if (!Settings.flag(this, Settings.KEY_PERSISTENT_NOTIFICATION, false)) {
			stopAndQuit()
			return START_NOT_STICKY
		}
		if (!claim()) {
			// Refused, this service is worth nothing: staying up without the
			// notification would cost the same to the battery and buy the
			// engine no protection at all.
			stopAndQuit()
			return START_NOT_STICKY
		}
		handler.removeCallbacks(check)
		handler.postDelayed(check, KeepAlive.CHECK_MS)
		return START_STICKY
	}

	override fun onDestroy() {
		super.onDestroy()
		active = false
		isRunning = false
		handler.removeCallbacks(check)
		KeepAliveNotification.release(this, detach = false)
	}

	override fun onBind(intent: Intent?): IBinder? = null

	/** Claims the foreground, which is the notification plus the standing the
	 *  system gives whoever posts it. */
	private fun claim(): Boolean = KeepAliveNotification.claim(
		this,
		Settings.flag(this, Settings.KEY_NOTIFICATION_STOP, true)
	)

	private fun stopAndQuit() {
		handler.removeCallbacks(check)
		KeepAliveNotification.cancel(this)
		stopSelf()
	}

	companion object {

		private const val TAG = "alteloquencetts"

		/** The one thing worth knowing about this service from anywhere in the
		 *  app: whether the notification is already being held up by the
		 *  service that exists to hold it. The speech service asks, and holds
		 *  the notification itself only while the answer is no. */
		@Volatile
		var isRunning = false
			private set

		/** Asks for it, and answers whether the system let it be asked for.
		 *  Android 12 and later refuse a foreground service started from the
		 *  background, which is what this looks like when the speech service
		 *  is the one asking, so the answer decides whether the service that
		 *  asked has to hold the notification itself. */
		fun start(context: Context): Boolean = try {
			ContextCompat.startForegroundService(context, intent(context))
			true
		} catch (refused: IllegalStateException) {
			// ForegroundServiceStartNotAllowedException is one of these.
			Log.w(TAG, "the system would not let the keep-alive start", refused)
			false
		} catch (refused: SecurityException) {
			Log.w(TAG, "the keep-alive was refused", refused)
			false
		}

		fun stop(context: Context) {
			runCatching { context.stopService(intent(context)) }
		}

		private fun intent(context: Context) = Intent(context, KeepAliveService::class.java)
	}
}
