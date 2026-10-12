package org.zarroboogs.maps.hiking.room

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * GPX Room 数据库。
 * 包含三张表：gpx_tracks（轨迹）、gpx_waypoints（航点）、turn_points（转向点）。
 */
@Database(
    entities = [
        GpxTrackEntity::class,
        GpxWaypointEntity::class,
        TurnPointEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class GpxDatabase : RoomDatabase() {

    abstract fun gpxDao(): GpxDao

    companion object {
        private const val DB_NAME = "gpx_database"

        @Volatile
        private var INSTANCE: GpxDatabase? = null

        /**
         * 获取数据库单例。
         *
         * @param context Application Context
         */
        fun getInstance(context: Context): GpxDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    GpxDatabase::class.java,
                    DB_NAME
                )
                    .fallbackToDestructiveMigration()  // 开发阶段，后续版本升级改为 Migration
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
