package com.blindtechnexus.alteloquencetts

import android.content.Context
import kotlinx.coroutines.flow.Flow
import java.util.Date

/**
 * Repository for managing reminders with Room database.
 */
class ReminderRepository(context: Context) {
    
    private val reminderDao = ReminderDatabase.getDatabase(context).reminderDao()
    
    /**
     * Add a new reminder.
     */
    suspend fun addReminder(
        title: String,
        message: String,
        reminderTime: Date,
        repeatType: ReminderRepeatType = ReminderRepeatType.ONCE
    ): Long {
        val reminder = ReminderEntity(
            title = title,
            message = message,
            reminderTime = reminderTime,
            repeatType = repeatType,
            isActive = true
        )
        return reminderDao.insert(reminder)
    }
    
    /**
     * Update an existing reminder.
     */
    suspend fun updateReminder(
        id: Long,
        title: String,
        message: String,
        reminderTime: Date,
        repeatType: ReminderRepeatType
    ) {
        val existing = reminderDao.getReminderById(id)
        existing?.let {
            val updated = it.copy(
                title = title,
                message = message,
                reminderTime = reminderTime,
                repeatType = repeatType
            )
            reminderDao.update(updated)
        }
    }
    
    /**
     * Delete a reminder by ID.
     */
    suspend fun deleteReminder(id: Long) {
        reminderDao.deleteById(id)
    }
    
    /**
     * Deactivate a reminder (mark as inactive).
     */
    suspend fun deactivateReminder(id: Long) {
        reminderDao.deactivateById(id)
    }
    
    /**
     * Get all active reminders as a flow.
     */
    fun getAllActiveReminders(): Flow<List<ReminderEntity>> {
        return reminderDao.getAllActiveReminders()
    }
    
    /**
     * Get all reminders as a flow.
     */
    fun getAllReminders(): Flow<List<ReminderEntity>> {
        return reminderDao.getAllReminders()
    }
    
    /**
     * Get a specific reminder by ID.
     */
    suspend fun getReminderById(id: Long): ReminderEntity? {
        return reminderDao.getReminderById(id)
    }
    
    /**
     * Snooze a reminder by adding time to its reminder time.
     */
    suspend fun snoozeReminder(id: Long, minutes: Int) {
        val existing = reminderDao.getReminderById(id)
        existing?.let {
            val snoozeTime = Date(it.reminderTime.time + minutes * 60 * 1000)
            val updated = it.copy(reminderTime = snoozeTime)
            reminderDao.update(updated)
        }
    }
}