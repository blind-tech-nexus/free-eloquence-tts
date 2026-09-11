package com.blindtechnexus.alteloquencetts

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The contract the keep-alive notification and the wake lock lean on.
 *
 * These two are the parts of the battery and stability work that nothing
 * inside the app can prove by running: the notification only protects the
 * engine if the manifest has named the service a foreground one and asked for
 * the permissions the system checks, and the wake lock only works if its
 * permission is there too. A service whose manifest is wrong still speaks
 * happily on an emulator and dies on a phone, so what is checked here is the
 * manifest itself, read back the way the system reads it, plus the constants
 * the service runs by.
 */
@RunWith(AndroidJUnit4::class)
class ServiceContractTest {

	private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext

	@Test
	fun theManifestDeclaresThePermissionsTheKeepAliveAndWakeLockNeed() {
		val info = context.packageManager.getPackageInfo(
			context.packageName,
			PackageManager.GET_PERMISSIONS
		)
		val asked = info.requestedPermissions?.toSet().orEmpty()
		// The foreground service and its special-use type, which the keep-alive
		// notification promotes the service to on Android 14 and later.
		assertTrue("FOREGROUND_SERVICE is not declared", asked.contains(Manifest.permission.FOREGROUND_SERVICE))
		assertTrue(
			"FOREGROUND_SERVICE_SPECIAL_USE is not declared",
			asked.contains(Manifest.permission.FOREGROUND_SERVICE_SPECIAL_USE)
		)
		// The wake lock and the notification both need their own permissions.
		assertTrue("WAKE_LOCK is not declared", asked.contains(Manifest.permission.WAKE_LOCK))
		assertTrue("POST_NOTIFICATIONS is not declared", asked.contains(Manifest.permission.POST_NOTIFICATIONS))
	}

	/** Reading the service's foregroundServiceType field needs the field to be
	 *  in the framework, which it has been since Android 10. Below that the
	 *  question cannot be asked, so nothing is asserted. */
	@Test
	fun theServiceIsASpecialUseForegroundService() {
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
		val service = serviceInfo()
		assertTrue(
			"the service must declare specialUse so startForeground can promote it",
			service.foregroundServiceType and ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE != 0
		)
	}

	/** The special-use subtype is what says why the service is a foreground
	 *  one, and the stores refuse a special-use service without it. It is a
	 *  property on the service rather than meta-data, and readable as one from
	 *  Android 12. Below that it is simply declared and cannot be read back. */
	@Test
	fun theServiceNamesWhatItsForegroundUseIsFor() {
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
		val component = ComponentName(context, EvvTtsService::class.java)
		val subtype = runCatching {
			context.packageManager
				.getProperty(PackageManager.PROPERTY_SPECIAL_USE_FGS_SUBTYPE, component)
				.getString()
		}.getOrNull()
		assertNotNull("the special-use subtype is not declared", subtype)
		assertFalse("the special-use subtype is empty", subtype.isNullOrBlank())
	}

	/** The numbers the service is written around. They are not behaviour on
	 *  their own, but a change to one without a change to the other is the
	 *  kind of drift that breaks the keep-alive silently. */
	@Test
	fun theKeepAliveAndWakeLockConstantsAgreeWithTheCodeThatRunsOnThem() {
		assertEquals("the notification channel must have a name", "keep_alive", KeepAlive.CHANNEL_ID)
		assertEquals("the notification id must be stable", 1, KeepAlive.ID)
		// The battery screen promises the notification is checked every few
		// seconds and restored; three seconds is the few.
		assertEquals("the notification check is not every 3 seconds", 3000L, KeepAlive.CHECK_MS)
		// The wake lock is a ceiling, not a course: ten minutes is far longer
		// than any utterance, so it is the framework's own bound and never the
		// thing that ends the speech.
		assertEquals("the wake lock ceiling moved", 10 * 60 * 1000L, EvvTtsService.WAKELOCK_TIMEOUT_MS)
		// The pacing the silence fix runs on: a slice is a fraction of the
		// lead, and a chunk is a whole number of samples.
		assertTrue("a pacing slice is bigger than the lead", EvvTtsService.SLICE_MS < EvvTtsService.LEAD_MS)
		assertTrue("a chunk is smaller than a slice of audio", EvvTtsService.MIN_CHUNK < EvvTtsService.MAX_CHUNK)
		assertEquals("samples are not 16-bit", 2, EvvTtsService.BYTES_PER_SAMPLE)
	}

	/** What is done on the battery screen is written to the system log only
	 *  when the user asked for it, and the answer says whether it was. */
	@Test
	fun trackingFollowsTheSwitch() {
		val settings = Settings(context)
		settings.trackActions = false
		assertFalse("tracking was on while the switch was off", Tracker.action(settings, "off"))
		settings.trackActions = true
		assertTrue("tracking was off while the switch was on", Tracker.action(settings, "on"))
		settings.trackActions = false
	}

	private fun serviceInfo(): ServiceInfo =
		context.packageManager.getServiceInfo(
			ComponentName(context, EvvTtsService::class.java),
			PackageManager.GET_META_DATA
		)
}
