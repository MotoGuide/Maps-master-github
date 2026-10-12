package org.zarroboogs.maps.hiking.room

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 预计算的转向点 Room Entity（GCJ-02 坐标系）。
 * GPX 导入时由 TurnDetector 一次性计算，导航时直接查表。
 */
@Entity(
    tableName = "turn_points",
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
        Index(value = ["trackId", "distanceFromStartM"])   // 导航时查找"下一个转向点"
    ]
)
data class TurnPointEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val trackId: Long,
    val direction: String,           // "LEFT" / "RIGHT" / "STRAIGHT" / "U_TURN"
    val lat: Double,
    val lng: Double,
    val distanceFromStartM: Double,  // 距起点累计距离（米），用于导航时查表
    val turnAngle: Double,           // 转向角度（绝对值，度）
    val nextSegmentBearing: Double   // 转向后的方位角（0-360°）
)
