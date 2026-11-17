package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        DrivingSessionEntity::class,
        DrivingEventEntity::class,
        TelemetrySampleEntity::class,
        SparePartWearEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class DriveIqDatabase : RoomDatabase() {
    abstract fun driveIqDao(): DriveIqDao

    companion object {
        @Volatile
        private var INSTANCE: DriveIqDatabase? = null

        fun getInstance(context: Context): DriveIqDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    DriveIqDatabase::class.java,
                    "driveiq_telemetry.db"
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
