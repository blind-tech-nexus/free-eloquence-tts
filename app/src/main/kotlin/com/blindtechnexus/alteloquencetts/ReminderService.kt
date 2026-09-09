package com.blindtechnexus.alteloquencetts

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Date
import java.util.Timer
import java.util.TimerTask

/**
 * Foreground service for handling reminders in the background.
 * This service tracks active reminders and shows notifications when they trigger.
 */
class ReminderService : Service() {
    
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var reminderRepository: ReminderRepository
    private var timer: Timer? = null
    private var tts: TextToSpeech? = null
    private var notificationManager: NotificationManager? = null
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onCreate() {
        super.onCreate()
        reminderRepository = ReminderRepository(this)
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        createNotificationChannel()
        startForegroundNotification()
        
        // Initialize TTS for voice guidance
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                Log.d(TAG, "TTS initialized successfully")
            } else {
                Log.e(TAG, "TTS initialization failed")
            }
        }
        
        startReminderTracking()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CHECK_REMINDERS -> checkAndNotifyReminders()
            ACTION_STOP_SERVICE -> stopSelf()
        }
        return START_STICKY
    }
    
    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        timer?.cancel()
        tts?.shutdown()
        Log.d(TAG, "ReminderService destroyed")
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelId = CHANNEL_ID
            val channelName = getString(R.string.reminder_title)
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            
            val channel = NotificationChannel(channelId, channelName, importance).apply {
                description = getString(R.string.reminder_description)
                setShowBadge(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            
            notificationManager?.createNotificationChannel(channel)
        }
    }
    
    private fun startForegroundNotification() {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.reminder_title))
            .setContentText(getString(R.string.productivity_active_reminders, 0))
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
        
        startForeground(NOTIFICATION_ID_FOREGROUND, notification)
    }
    
    private fun startReminderTracking() {
        // Check reminders every minute
        timer = Timer().apply {
            scheduleAtFixedRate(object : TimerTask() {
                override fun run() {
                    checkAndNotifyReminders()
                }
            }, 0, 60000) // Every minute
        }
    }
    
    private fun checkAndNotifyReminders() {
        serviceScope.launch {
            try {
                val now = Date()
                val activeReminders = reminderRepository.getAllActiveReminders().first()
                
                for (reminder in activeReminders) {
                    if (reminder.reminderTime <= now) {
                        showReminderNotification(reminder)
                        
                        // Handle repeat logic
                        if (reminder.repeatType != ReminderRepeatType.ONCE) {
                            val nextTime = calculateNextReminderTime(reminder)
                            reminderRepository.updateReminder(
                                reminder.id,
                                reminder.title,
                                reminder.message,
                                nextTime,
                                reminder.repeatType
                            )
                        } else {
                            // For one-time reminders, deactivate after notification
                            reminderRepository.deactivateReminder(reminder.id)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking reminders", e)
            }
        }
    }
    
    private fun calculateNextReminderTime(reminder: ReminderEntity): Date {
        val calendar = java.util.Calendar.getInstance()
        calendar.time = reminder.reminderTime
        
        return when (reminder.repeatType) {
            ReminderRepeatType.DAILY -> {
                calendar.add(java.util.Calendar.DAY_OF_MONTH, 1)
                calendar.time
            }
            ReminderRepeatType.WEEKLY -> {
                calendar.add(java.util.Calendar.WEEK_OF_MONTH, 1)
                calendar.time
            }
            ReminderRepeatType.MONTHLY -> {
                calendar.add(java.util.Calendar.MONTH, 1)
                calendar.time
            }
            ReminderRepeatType.YEARLY -> {
                calendar.add(java.util.Calendar.YEAR, 1)
                calendar.time
            }
            else -> Date() // Should not happen for repeatable reminders
        }
    }
    
    private fun showReminderNotification(reminder: ReminderEntity) {
        val notificationId = reminder.id.toInt()
        
        // Create intent for when notification is tapped
        val intent = Intent(this, SettingsActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("reminder_id", reminder.id)
        }
        
        val pendingIntent = PendingIntent.getActivity(
            this, 
            notificationId, 
            intent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Create silent notification for upcoming reminders
        val notificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.reminder_notification_title, reminder.title))
            .setContentText(reminder.message)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setDefaults(Notification.DEFAULT_LIGHTS)
            .setSound(null) // Silent notification
            
        // Add vibration if enabled in settings
        val sharedPrefs = getSharedPreferences(VOICE_GUIDANCE_PREFS, Context.MODE_PRIVATE)
        val vibrationEnabled = sharedPrefs.getBoolean(VIBRATION_ENABLED, true)
        
        if (vibrationEnabled) {
            notificationBuilder.setDefaults(Notification.DEFAULT_VIBRATE)
            
            // Custom vibration pattern based on intensity
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            
            val intensity = sharedPrefs.getString(VIBRATION_INTENSITY, VIBRATION_MEDIUM)
            val vibrationPattern = when (intensity) {
                VIBRATION_WEAK -> longArrayOf(0, 100, 200, 300)
                VIBRATION_MEDIUM -> longArrayOf(0, 200, 100, 200)
                VIBRATION_STRONG -> longArrayOf(0, 300, 100, 300, 100, 300)
                else -> longArrayOf(0, 200, 100, 200)
            }
            
            val amplitudes = when (intensity) {
                VIBRATION_WEAK -> intArrayOf(0, 50, 0, 50)
                VIBRATION_MEDIUM -> intArrayOf(0, 128, 0, 128)
                VIBRATION_STRONG -> intArrayOf(0, 255, 0, 255, 0, 255)
                else -> intArrayOf(0, 128, 0, 128)
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(
                    VibrationEffect.createWaveform(vibrationPattern, amplitudes, -1)
                )
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(vibrationPattern, -1)
            }
        }
        
        // Speak the reminder using TTS if voice guidance is enabled
        val voiceGuidanceEnabled = sharedPrefs.getBoolean(VOICE_GUIDANCE_ENABLED, true)
        if (voiceGuidanceEnabled && tts != null) {
            val textToSpeak = getString(R.string.reminder_alert_message, reminder.title)
            tts?.speak(textToSpeak, TextToSpeech.QUEUE_ADD, null, null)
        }
        
        notificationManager?.notify(notificationId, notificationBuilder.build())
        
        // Also show overlay if permission is granted
        if (OverlayService.hasOverlayPermission(this)) {
            showOverlayNotification(reminder)
        }
    }
    
    private fun showOverlayNotification(reminder: ReminderEntity) {
        val intent = Intent(this, OverlayService::class.java).apply {
            action = OverlayService.ACTION_SHOW_REMINDER
            putExtra("reminder_title", reminder.title)
            putExtra("reminder_message", reminder.message)
            putExtra("reminder_id", reminder.id)
        }
        startService(intent)
    }
    
    companion object {
        private const val TAG = "ReminderService"
        private const val CHANNEL_ID = "reminder_channel"
        private const val NOTIFICATION_ID_FOREGROUND = 1001
        private const val VOICE_GUIDANCE_PREFS = "VoiceGuidancePrefs"
        private const val VOICE_GUIDANCE_ENABLED = "voice_guidance_enabled"
        private const val VIBRATION_ENABLED = "vibration_enabled"
        private const val VIBRATION_INTENSITY = "vibration_intensity"
        private const val VIBRATION_WEAK = "weak"
        private const val VIBRATION_MEDIUM = "medium"
        private const val VIBRATION_STRONG = "strong"
        
        const val ACTION_CHECK_REMINDERS = "com.blindtechnexus.alteloquencetts.action.CHECK_REMINDERS"
        const val ACTION_STOP_SERVICE = "com.blindtechnexus.alteloquencetts.action.STOP_SERVICE"
        
        fun startService(context: Context) {
            val intent = Intent(context, ReminderService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
        
        fun stopService(context: Context) {
            val intent = Intent(context, ReminderService::class.java).apply {
                action = ACTION_STOP_SERVICE
            }
            context.startService(intent)
        }
    }
}