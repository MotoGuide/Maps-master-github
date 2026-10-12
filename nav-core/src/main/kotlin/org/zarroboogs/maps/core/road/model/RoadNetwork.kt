package org.zarroboogs.maps.core.road.model

/**
 * v2.0 路网数据模型。
 *
 * 道路等级 (roadClass):
 *   0=motorway, 1=trunk, 2=primary, 3=secondary, 4=tertiary,
 *   5=residential, 6=service, 7=track, 8=path, 9=unknown
 */
enum class RoadClass(val code: Int) {
    MOTORWAY(0), TRUNK(1), PRIMARY(2), SECONDARY(3),
    TERTIARY(4), RESIDENTIAL(5), SERVICE(6), TRACK(7),
    PATH(8), UNKNOWN(9);

    companion object {
        fun fromCode(code: Int) = entries.find { it.code == code } ?: UNKNOWN
    }
}

data class RoadNode(
    val id: Int,
    val lat: Double,
    val lng: Double,
    val type: Int = 0           // 0=普通节点, 1=路口节点
)

/**
 * @param polyline 节点间的中间坐标点（不含起止节点），用于非直线路段的高精度绘制
 * @param roadClass 道路等级，影响线宽和岔路搜索半径
 * @param name 道路名称（可选，ESP32 不渲染文字，仅用于调试）
 */
data class RoadEdge(
    val fromNodeId: Int,
    val toNodeId: Int,
    val distanceM: Int = 0,
    val polyline: List<LatLngPoint> = emptyList(),
    val roadClass: RoadClass = RoadClass.UNKNOWN,
    val name: String = "",
    val laneCount: Int = 1,
    val maxSpeedKmh: Int = 0
)

data class RoadNetwork(
    val nodes: List<RoadNode> = emptyList(),
    val edges: List<RoadEdge> = emptyList(),
    val totalDistanceM: Int = 0,
    val routeName: String = ""
)

/** 轻量坐标点，避免依赖 Android LatLng */
data class LatLngPoint(
    val lat: Double,
    val lng: Double
)

/** 米制投影坐标点 */
data class MercatorPoint(
    val x: Double,   // 米，东向
    val y: Double    // 米，北向
)

/**
 * 离线路网中的一个路段（来自 geohash .bin 文件）。
 * 与 RoadEdge 不同，此类型用于离线岔路搜索，包含完整 polyline 几何。
 */
data class RoadSegment(
    val points: List<LatLngPoint>,  // 完整坐标序列（含首尾点）
    val roadClass: RoadClass = RoadClass.UNKNOWN,
    val name: String = ""
)
