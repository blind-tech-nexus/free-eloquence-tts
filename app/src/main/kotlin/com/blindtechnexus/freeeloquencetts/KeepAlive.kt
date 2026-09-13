package com.blindtechnexus.freeeloquencetts

import android.app.Service
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * The persistent notification that keeps the engine alive.
 *
 * A foreground service is the one thing that tells Android, in the one language
 * Android always listens to, that an app is doing something the user asked for
 * and must not be killed. A text to speech engine is the awkward case: idle
 * almost all the time, needed at once when it is not, which is the shape every
 * battery policy is written to kill. The notification makes the engine visible,
 * so the system's own rules leave it alone.
 *
 * It belongs to the app rather than to the speech service, which is what this
 * file used to get wrong. The speech service is a service the *system* binds
 * and unbinds: once the last reader has let go of it, it is destroyed, and a
 * notification posted by it went with it. Somebody who was told "this is what
 * keeps your voice working" then had a phone with no voice and no sign of one,
 * with nothing to swipe at. So the notification is posted by
 * [KeepAliveService], which the app starts and nothing else stops, and which
 * outlives the engine being unbound, the process being cached, and a reboot
 * when that has been asked for.
 *
 * Two things can still go wrong, and both have an answer here:
 *
 * Android 12 and later refuse a foreground service started from the
 * background, which is what starting one from a speech service the system
 * bound looks like to the system. Refused, this falls back to what it used to
 * do: the speech service posts the notification on itself for as long as it is
 * bound, and hands it over to the app's service the moment that one is up.
 * That is less than the app service gives, and a great deal more than nothing.
 *
 * A notification can be swiped, dismissed, or dropped when the system rebuilds
 * its list, and a foreground service whose notification has gone is a
 * foreground service nothing protects. So it is looked for every [CHECK_MS] and
 * put back if it is not there. On Android 13 and later the check leaves it
 * alone when the notification permission has not been given, because then the
 * system hides it by the user's own choice and re-posting it every few seconds
 * is noise for nothing.
 */
class KeepAlive(private val service: Service) {

	private val handler = Handler(Looper.getMainLooper())

	/** Whether the speech service is the one posting the notification, which it
	 *  is only while the app's own service cannot. */
	private var holding = false

	/** Whether the user has asked for any of this. */
	private var wanted = false

	private val check = object : Runnable {
		override fun run() {
			if (!wanted) return
			// The setting is read again rather than trusted, because it can move
			// while this is running: the switch on the battery screen, the same
			// settings on another device, an update that replaced the service. A
			// check that only knew what it was told to want would put the
			// notification back three seconds after the person who turned it off
			// had turned it off, which is the sound of an app that will not take
			// no for an answer.
			if (!Settings.flag(service, Settings.KEY_PERSISTENT_NOTIFICATION, false)) {
				sync(false)
				return
			}
			if (holding && builtWith != stopButton()) {
				// The button on the notification is the setting's, and the
				// notification that is up was built with whatever it said when
				// this started. Posting it again is the whole of the update, and
				// the check is where anybody who changed it finds out.
				hold()
			}
			if (KeepAliveService.isRunning) {
				// The app owns it. Handing over happens once rather than on
				// every check, and the notification stays up because the
				// service that has taken it over posts it as well.
				if (holding) {
					holding = false
					KeepAliveNotification.release(service, detach = true)
				}
			} else if (!isShown()) {
				hold()
			}
			handler.postDelayed(this, checkMs(service))
		}
	}

	/** Turns the notification on or off, matching the setting. No-op when the
	 *  state has not changed. */
	fun sync(enabled: Boolean) {
		if (enabled == wanted) return
		wanted = enabled
		handler.removeCallbacks(check)
		if (enabled) {
			KeepAlive.ensureChannel(service)
			if (!KeepAliveService.start(service)) hold()
			handler.postDelayed(check, checkMs(service))
		} else {
			KeepAliveService.stop(service)
			if (holding) {
				holding = false
				KeepAliveNotification.release(service, detach = false)
			}
		}
	}

	/** Called when the speech service goes away, so that a fallback
	 *  notification does not outlive the only thing that can defend it. The
	 *  app's own service is left alone: it is not the speech service's to
	 *  stop, and leaving it up is the whole point. */
	fun release() {
		wanted = false
		handler.removeCallbacks(check)
		if (holding) {
			holding = false
			KeepAliveNotification.release(service, detach = KeepAliveService.isRunning)
		}
	}

	/** Whether the notification that is up carries the stop button, which is
	 *  what says whether it has to be built again after the switch moved. */
	private var builtWith = true

	private fun hold() {
		val withStop = stopButton()
		holding = KeepAliveNotification.claim(service, withStop)
		if (holding) builtWith = withStop
	}

	/** Whether the notification is where it should be, from the point of view
	 *  of a check that is only interested in the ones it can put back. */
	private fun isShown(): Boolean {
		if (!KeepAliveNotification.notificationsAllowed(service)) return true
		return KeepAliveNotification.isPosted(service)
	}

	private fun stopButton(): Boolean =
		Settings.flag(service, Settings.KEY_NOTIFICATION_STOP, true)

	companion object {
		const val TAG = "freeeloquencetts"
		const val CHANNEL_ID = KeepAliveNotification.CHANNEL_ID
		const val ID = KeepAliveNotification.ID
		/** How often the notification is looked for, in milliseconds. */
		const val CHECK_MS = 3000L

		/** How often the notification is looked for now, in milliseconds:
		 *  the setting's, or [CHECK_MS] for anything the screen never
		 *  offered. Read fresh on every check, so a choice made on the
		 *  battery screen is in force seconds later without restarting
		 *  anything. Asked through [Settings.number] rather than an
		 *  instance, which is what a check that runs for ever does. */
		fun checkMs(context: Context): Long {
			val at = Settings.number(context, Settings.KEY_KEEP_ALIVE_CHECK_MS, CHECK_MS.toInt())
			return if (Stability.NOTIFICATION_CHECKS.contains(at)) at.toLong() else CHECK_MS
		}

		private val handler = Handler(Looper.getMainLooper())

		/** What the app itself does with the notification, with no speech
		 *  service in sight: the setting is switched on from the battery
		 *  screen, and the keep-alive starts there rather than waiting for
		 *  somebody to speak. */
		fun sync(context: Context, enabled: Boolean) {
			KeepAlive.ensureChannel(context)
			if (enabled) KeepAliveService.start(context) else KeepAliveService.stop(context)
			// The speech service's own check runs on the same clock, and a
			// notification turned on from the screen has to be looked after
			// while nothing is bound either. The app's service does that; this
			// is here so that a device that refused the foreground service is
			// still told about in the log rather than being silently worse.
			if (enabled && !KeepAliveService.isRunning) {
				handler.postDelayed({
					if (!KeepAliveService.isRunning) {
						Log.w(TAG, "the keep-alive service would not start; the voice is unprotected until the engine speaks")
					}
				}, checkMs(context))
			}
		}

		/** The channel, made before anything is posted to it. */
		fun ensureChannel(context: Context) = KeepAliveNotification.ensureChannel(context)
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

	const val TAG = "freeeloquencetts-actions"

	/** Records [what], when tracking is on, and answers whether it was
	 *  written. The answer is what a test can hold onto, since whether a line
	 *  went to the system log is not readable back out of it. */
	fun action(settings: Settings?, what: String): Boolean {
		if (settings == null || !settings.trackActions) return false
		Log.i(TAG, what)
		return true
	}

	/** The same, for a place that has a context rather than the settings of its
	 *  own. Nothing is registered on the preferences to read one value. */
	fun action(context: Context, what: String) {
		if (!Settings.flag(context, Settings.KEY_TRACK_ACTIONS, false)) return
		Log.i(TAG, what)
	}
}
