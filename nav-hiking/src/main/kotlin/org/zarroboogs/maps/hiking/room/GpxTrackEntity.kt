package org.zarroboogs.maps.hiking.room

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * GPX 轨迹 Room Entity。
 */
@Entity(tableName = "gpx_tracks")
data class GpxTrackEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String = "",
    val source: String,              // "IMPORTED" 或 "RECORDED"
    val totalDistanceM: Double = 0.0,
    val totalElevationGainM: Double = 0.0,
    val waypointCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
