package org.zarroboogs.maps.hiking.model

/**
 * GPX 轨迹数据类。
 * 所有坐标统一使用 GCJ-02 坐标系（导入时已完成转换）。
 *
 * @property id 数据库主键（0 = 新轨迹）
 * @property name 轨迹名称（来自 GPX <trk><name>）
 * @property description 轨迹描述（来自 GPX <metadata><desc>）
 * @property source 来源：IMPORTED（导入）或 RECORDED（录制）
 * @property waypoints 航点列表（GCJ-02 坐标，按 index 升序）
 * @property totalDistanceM 总距离（米）
 * @property totalElevationGainM 累计爬升（米）
 * @property createdAt 创建时间戳（毫秒）
 * @property updatedAt 最后更新时间戳（毫秒）
 */
data class GpxTrack(
    val id: Long = 0,
    val name: String,
    val description: String = "",
    val source: TrackSource,
    val waypoints: List<GpxWaypoint> = emptyList(),
    val totalDistanceM: Double = 0.0,
    val totalElevationGainM: Double = 0.0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    /** 航点数量 */
    val waypointCount: Int get() = waypoints.size

    /** DouglasPeucker 简化后的航点（用于 BLE 传输，≤ 500 点） */
    val simplifiedWaypoints: List<GpxWaypoint> by lazy {
        @Suppress("UNCHECKED_CAST")
        // TODO: DouglasPeucker.simplify() 接受 LatLngPoint，需要适配
        if (waypoints.size <= 500) waypoints
        else waypoints.filterIndexed { index, _ -> index % (waypoints.size / 500 + 1) == 0 || index == waypoints.lastIndex }
            .mapIndexed { i, wp -> wp.copy(index = i) }
    }
}

/** 轨迹来源 */
enum class TrackSource { IMPORTED, RECORDED }
