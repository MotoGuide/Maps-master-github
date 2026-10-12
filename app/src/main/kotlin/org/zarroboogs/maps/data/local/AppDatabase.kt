package org.zarroboogs.maps.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import org.zarroboogs.maps.data.local.dao.BookmarkDao
import org.zarroboogs.maps.data.local.dao.CameraDao
import org.zarroboogs.maps.data.local.dao.FuelRecordDao
import org.zarroboogs.maps.data.local.dao.NaviRecordDao
import org.zarroboogs.maps.data.local.dao.SearchHistoryDao
import org.zarroboogs.maps.data.local.entity.Bookmark
import org.zarroboogs.maps.data.local.entity.Camera
import org.zarroboogs.maps.data.local.entity.FuelRecord
import org.zarroboogs.maps.data.local.entity.NaviRecord
import org.zarroboogs.maps.data.local.entity.SearchHistory

@Database(
    entities = [Camera::class, Bookmark::class, FuelRecord::class, NaviRecord::class, SearchHistory::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun cameraDao(): CameraDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun fuelRecordDao(): FuelRecordDao
    abstract fun naviRecordDao(): NaviRecordDao
    abstract fun searchHistoryDao(): SearchHistoryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "maps.db"
                )
                    .fallbackToDestructiveMigration()
                    .allowMainThreadQueries()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
