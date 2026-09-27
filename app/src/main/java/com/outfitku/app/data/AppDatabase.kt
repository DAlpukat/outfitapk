package com.outfitku.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [Item::class, OutfitLog::class, WashEvent::class, DailyState::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun itemDao(): ItemDao
    abstract fun outfitLogDao(): OutfitLogDao
    abstract fun washEventDao(): WashEventDao
    abstract fun dailyStateDao(): DailyStateDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "outfitku.db"
                ).build().also { instance = it }
            }
    }
}
