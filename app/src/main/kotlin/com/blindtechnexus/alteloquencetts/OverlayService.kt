package com.blindtechnexus.alteloquencetts

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

/**
 * Service for displaying reminder notifications as overlays on top of other apps.
 * This requires the SYSTEM_ALERT_WINDOW permission.
 */
class OverlayService : LifecycleService() {
    
    private lateinit var windowManager: WindowManager
    private var overlayView: View? = null
    private var currentReminderId: Long = -1
    
    override fun onBind(intent: Intent): IBinder? = super.onBind(intent)
    
    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SHOW_REMINDER -> {
                val title = intent.getStringExtra("reminder_title") ?: ""
                val message = intent.getStringExtra("reminder_message") ?: ""
                currentReminderId = intent.getLongExtra("reminder_id", -1)
                showOverlay(title, message)
            }
            ACTION_HIDE_OVERLAY -> hideOverlay()
        }
        return super.onStartCommand(intent, flags, startId)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        hideOverlay()
    }
    
    private fun showOverlay(title: String, message: String) {
        lifecycleScope.launch {
            // Create overlay view programmatically
            val layoutParams = WindowManager.LayoutParams().apply {
                type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    WindowManager.LayoutParams.TYPE_PHONE
                }
                format = PixelFormat.TRANSLUCENT
                flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                width = WindowManager.LayoutParams.MATCH_PARENT
                height = WindowManager.LayoutParams.WRAP_CONTENT
                y = 100 // Position from top
            }
            
            val inflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
            overlayView = inflater.inflate(R.layout.overlay_reminder, null)
            
            // Set up the overlay content
            overlayView?.findViewById<TextView>(R.id.overlay_title)?.text = title
            overlayView?.findViewById<TextView>(R.id.overlay_message)?.text = message
            
            overlayView?.findViewById<Button>(R.id.overlay_dismiss)?.setOnClickListener {
                hideOverlay()
                // Dismiss the reminder
                val repository = ReminderRepository(this@OverlayService)
                if (currentReminderId != -1L) {
                    repository.deactivateReminder(currentReminderId)
                }
            }
            
            overlayView?.findViewById<Button>(R.id.overlay_snooze)?.setOnClickListener {
                hideOverlay()
                // Snooze the reminder for 10 minutes
                val repository = ReminderRepository(this@OverlayService)
                if (currentReminderId != -1L) {
                    repository.snoozeReminder(currentReminderId, 10)
                }
            }
            
            try {
                windowManager.addView(overlayView, layoutParams)
            } catch (e: Exception) {
                // Handle case where permission is not granted
                hideOverlay()
            }
        }
    }
    
    private fun hideOverlay() {
        if (overlayView != null) {
            windowManager.removeView(overlayView)
            overlayView = null
        }
        currentReminderId = -1
    }
    
    companion object {
        const val ACTION_SHOW_REMINDER = "com.blindtechnexus.alteloquencetts.action.SHOW_REMINDER"
        const val ACTION_HIDE_OVERLAY = "com.blindtechnexus.alteloquencetts.action.HIDE_OVERLAY"
        
        fun hasOverlayPermission(context: Context): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Settings.canDrawOverlays(context)
            } else {
                true // Permission not required for older versions
            }
        }
        
        fun requestOverlayPermission(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !hasOverlayPermission(context)) {
                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            }
        }
    }
}