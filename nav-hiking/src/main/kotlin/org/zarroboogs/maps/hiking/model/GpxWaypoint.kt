package org.zarroboogs.maps.hiking.model

/**
 * GPX 轨迹中的单个航点（GCJ-02 坐标系）。
 *
 * @property id 数据库主键
 * @property trackId 所属轨迹 ID
 * @property index 在轨迹中的序号（从 0 开始）
 * @property lat 纬度（GCJ-02）
 * @property lng 经度（GCJ-02）
 * @property elevation 海拔高度（米），可为 null
 * @property timestamp GPX 原始时间戳（毫秒），可为 null
 * @property distanceFromStartM 从轨迹起点到当前点的累计距离（米）
 */
data class GpxWaypoint(
    val id: Long = 0,
    val trackId: Long = 0,
    val index: Int,
    val lat: Double,
    val lng: Double,
    val elevation: Double? = null,
    val timestamp: Long? = null,
    val distanceFromStartM: Double = 0.0
)
