package com.blindtechnexus.alteloquencetts

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * Service for tracking productivity and managing reminders in the background.
 * This service ensures that reminders are tracked reliably even when the app is not active.
 */
class ProductivityCompanionService : LifecycleService() {
    
    private lateinit var reminderRepository: ReminderRepository
    private var activeReminderCount: Int = 0
    
    override fun onBind(intent: Intent): IBinder? = super.onBind(intent)
    
    override fun onCreate() {
        super.onCreate()
        reminderRepository = ReminderRepository(this)
        startReminderTracking()
        Log.d(TAG, "ProductivityCompanionService created")
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Handle any specific commands if needed
        return super.onStartCommand(intent, flags, startId)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "ProductivityCompanionService destroyed")
    }
    
    private fun startReminderTracking() {
        lifecycleScope.launch {
            // Observe all active reminders
            reminderRepository.getAllActiveReminders().collect { reminders ->
                activeReminderCount = reminders.size
                Log.d(TAG, "Active reminders count: $activeReminderCount")
                
                // Ensure reminder service is running if there are active reminders
                if (activeReminderCount > 0) {
                    ReminderService.startService(this@ProductivityCompanionService)
                }
            }
        }
    }
    
    /**
     * Check if the service should be running (if there are active reminders).
     */
    suspend fun shouldBeRunning(): Boolean {
        val reminders = reminderRepository.getAllActiveReminders().first()
        return reminders.isNotEmpty()
    }
    
    companion object {
        private const val TAG = "ProductivityCompanionService"
        
        fun startService(context: Context) {
            val intent = Intent(context, ProductivityCompanionService::class.java)
            context.startService(intent)
        }
        
        fun stopService(context: Context) {
            val intent = Intent(context, ProductivityCompanionService::class.java)
            context.stopService(intent)
        }
    }
}