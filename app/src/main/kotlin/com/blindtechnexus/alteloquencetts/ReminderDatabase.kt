package com.blindtechnexus.alteloquencetts

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/**
 * Room database for storing reminders locally.
 */
@Database(entities = [ReminderEntity::class], version = 1, exportSchema = false)
@TypeConverters(ReminderTypeConverters::class)
abstract class ReminderDatabase : RoomDatabase() {
    
    abstract fun reminderDao(): ReminderDao
    
    companion object {
        @Volatile
        private var INSTANCE: ReminderDatabase? = null
        
        fun getDatabase(context: Context): ReminderDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ReminderDatabase::class.java,
                    "reminder_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}

/**
 * Type converters for Room database to handle custom types.
 */
class ReminderTypeConverters {
    @androidx.room.TypeConverter
    fun fromTimestamp(value: Long?): java.util.Date? {
        return value?.let { java.util.Date(it) }
    }
    
    @androidx.room.TypeConverter
    fun dateToTimestamp(date: java.util.Date?): Long? {
        return date?.time
    }
    
    @androidx.room.TypeConverter
    fun fromRepeatType(value: String?): ReminderRepeatType {
        return value?.let { 
            try {
                ReminderRepeatType.valueOf(it)
            } catch (e: IllegalArgumentException) {
                ReminderRepeatType.ONCE
            }
        } ?: ReminderRepeatType.ONCE
    }
    
    @androidx.room.TypeConverter
    fun repeatTypeToString(type: ReminderRepeatType?): String? {
        return type?.name
    }
}

/**
 * Reminder repeat types.
 */
enum class ReminderRepeatType {
    ONCE, DAILY, WEEKLY, MONTHLY, YEARLY
}

/**
 * Reminder entity for Room database.
 */
androidx.room.Entity(tableName = "reminders")
data class ReminderEntity(
    @androidx.room.PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val message: String,
    val reminderTime: java.util.Date,
    val repeatType: ReminderRepeatType = ReminderRepeatType.ONCE,
    val isActive: Boolean = true,
    val createdAt: java.util.Date = java.util.Date()
)

/**
 * Data Access Object for reminders.
 */
@androidx.room.Dao
interface ReminderDao {
    
    @androidx.room.Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    suspend fun insert(reminder: ReminderEntity): Long
    
    @androidx.room.Update
    suspend fun update(reminder: ReminderEntity)
    
    @androidx.room.Delete
    suspend fun delete(reminder: ReminderEntity)
    
    @androidx.room.Query("SELECT * FROM reminders WHERE isActive = 1 ORDER BY reminderTime ASC")
    fun getAllActiveReminders(): androidx.room.Flow<List<ReminderEntity>>
    
    @androidx.room.Query("SELECT * FROM reminders ORDER BY reminderTime ASC")
    fun getAllReminders(): androidx.room.Flow<List<ReminderEntity>>
    
    @androidx.room.Query("SELECT * FROM reminders WHERE id = :id")
    suspend fun getReminderById(id: Long): ReminderEntity?
    
    @androidx.room.Query("DELETE FROM reminders WHERE id = :id")
    suspend fun deleteById(id: Long)
    
    @androidx.room.Query("UPDATE reminders SET isActive = 0 WHERE id = :id")
    suspend fun deactivateById(id: Long)
}