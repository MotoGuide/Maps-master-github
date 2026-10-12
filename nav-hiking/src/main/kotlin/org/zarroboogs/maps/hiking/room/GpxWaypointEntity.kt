package org.zarroboogs.maps.hiking.room

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * GPX 航点 Room Entity（GCJ-02 坐标系）。
 *
 * 复合索引 (trackId, index) 优化按轨迹+序号排序查询。
 */
@Entity(
    tableName = "gpx_waypoints",
    foreignKeys = [
        ForeignKey(
            entity = GpxTrackEntity::class,
            parentColumns = ["id"],
            childColumns = ["trackId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("trackId"),
        Index(value = ["trackId", "index"], unique = true)  // 复合索引，优化查询
    ]
)
data class GpxWaypointEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val trackId: Long,
    val index: Int,
    val lat: Double,
    val lng: Double,
    val elevation: Double? = null,
    val timestamp: Long? = null,
    val distanceFromStartM: Double = 0.0
)
