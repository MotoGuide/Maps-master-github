package org.zarroboogs.maps.core.road

import org.zarroboogs.maps.core.road.model.LatLngPoint
import org.zarroboogs.maps.core.road.model.RoadClass
import org.zarroboogs.maps.core.road.model.RoadSegment
import kotlin.math.abs

/**
 * 岔路搜索：对简化后的路线每点，在离线路网数据中搜索交叉路段。
 *
 * 2026-05-28 定案 — 每条岔路仅输出 2 个点（路口吸附点 + 沿岔路 25m 外的终点），
 * 足以在 Canvas 上表达"此处有路口"，无需推送整段 polyline。
 *
 * 性能：0.01° 网格桶排序，每次搜索仅检查路线点所在桶及相邻桶。
 */
class BranchSearch(
    private val allSegments: List<RoadSegment>
) {
    private val bucketPrecision = 0.01

    /** 桶 key → 路段索引列表 */
    private val bucketIndex: Map<Long, List<Int>> by lazy { buildIndex() }

    data class BranchResult(
        val intersectionPoint: LatLngPoint,
        val endPoint: LatLngPoint,
        val direction: BranchDirection,
        val distanceOnRoute: Double,
        val roadClass: RoadClass
    )

    enum class BranchDirection { LEFT, RIGHT }

    /**
     * @param routePoints 简化后的路线坐标点（DP 简化后的关键点序列）
     * @param radiusMeters 搜索半径，根据道路等级动态调整（普通=30m, 高速/快速路=80m）
     * @param branchLengthMeters 岔路显示长度（默认 25m）
     * @return 岔路结果列表，按 distanceOnRoute 升序排列
     */
    fun search(
        routePoints: List<LatLngPoint>,
        radiusMeters: Double = DEFAULT_RADIUS_M,
        branchLengthMeters: Double = 25.0
    ): List<BranchResult> {
        if (allSegments.isEmpty() || routePoints.size < 2) return emptyList()

        val cumulativeDist = precomputeCumulativeDistances(routePoints)
        val routeBearings = precomputeRouteBearings(routePoints)

        // (segmentIndex, result) — 用于去重
        val rawResults = mutableListOf<Pair<Int, BranchResult>>()

        for (ri in routePoints.indices) {
            val rp = routePoints[ri]
            val distOnRoute = cumulativeDist[ri]
            val routeBearing = routeBearings[ri]
            val buckets = affectedBuckets(rp, radiusMeters)

            for (bk in buckets) {
                val segIndices = bucketIndex[bk] ?: continue
                for (si in segIndices) {
                    val seg = allSegments[si]

                    val effectiveRadius = when (seg.roadClass) {
                        RoadClass.MOTORWAY, RoadClass.TRUNK -> 80.0
                        else -> radiusMeters
                    }

                    val closest = closestPointOnPolyline(rp, seg.points)
                    if (closest.distanceM > effectiveRadius) continue

                    // 同路过滤：岔路方向与路线方向夹角 < 30° 视为同一条路，排除
                    val segBearing = segmentBearingAt(seg.points, closest.segIdx)
                    if (abs(angleDiff(routeBearing, segBearing)) < SAME_ROAD_ANGLE_DEG) continue

                    val endPt = pointAtDistance(
                        seg.points, closest.segIdx, closest.t,
                        branchLengthMeters
                    )

                    val direction = classifyDirection(routeBearing, rp, closest.point)

                    rawResults.add(
                        si to BranchResult(
                            intersectionPoint = closest.point,
                            endPoint = endPt,
                            direction = direction,
                            distanceOnRoute = distOnRoute,
                            roadClass = seg.roadClass
                        )
                    )
                }
            }
        }

        // 去重：同一 segment 只保留第一次出现（distanceOnRoute 最小）
        val seen = mutableSetOf<Int>()
        val deduped = mutableListOf<BranchResult>()
        for ((si, result) in rawResults) {
            if (seen.add(si)) {
                deduped.add(result)
            }
        }

        return deduped.sortedBy { it.distanceOnRoute }
    }

    // ==================== 几何计算 ====================

    private data class ClosestResult(
        val point: LatLngPoint,
        val distanceM: Double,
        val segIdx: Int,
        val t: Double
    )

    /** 计算点到 polyline 的最近垂足 */
    private fun closestPointOnPolyline(
        point: LatLngPoint, polyline: List<LatLngPoint>
    ): ClosestResult {
        var minDist = Double.MAX_VALUE
        var bestPoint = polyline.first()
        var bestSeg = 0
        var bestT = 0.0

        for (i in 0 until polyline.size - 1) {
            val a = polyline[i]
            val b = polyline[i + 1]
            val (foot, t) = perpendicularFoot(point, a, b)
            val dist = Haversine.distance(point, foot)
            if (dist < minDist) {
                minDist = dist
                bestPoint = foot
                bestSeg = i
                bestT = t
            }
        }
        return ClosestResult(bestPoint, minDist, bestSeg, bestT)
    }

    /**
     * 从 polyline 上指定位置出发，沿 polyline 前进指定距离，返回终点。
     * 优先向 toNode 方向走（t → 1.0 → 下一段 → ...）。
     */
    private fun pointAtDistance(
        polyline: List<LatLngPoint>,
        startSegIdx: Int,
        startT: Double,
        distanceM: Double
    ): LatLngPoint {
        var remaining = distanceM
        var current = interpolate(
            polyline[startSegIdx], polyline[startSegIdx + 1], startT
        )

        // 先走完当前段剩余部分
        val firstSegLen = Haversine.distance(current, polyline[startSegIdx + 1])
        if (remaining <= firstSegLen) {
            return interpolate(current, polyline[startSegIdx + 1], remaining / firstSegLen)
        }
        remaining -= firstSegLen
        current = polyline[startSegIdx + 1]

        // 继续走后续完整段
        for (i in startSegIdx + 1 until polyline.size - 1) {
            val segLen = Haversine.distance(current, polyline[i + 1])
            if (remaining <= segLen) {
                return interpolate(current, polyline[i + 1], remaining / segLen)
            }
            remaining -= segLen
            current = polyline[i + 1]
        }

        return polyline.last()
    }

    private fun interpolate(a: LatLngPoint, b: LatLngPoint, t: Double): LatLngPoint {
        val clamped = t.coerceIn(0.0, 1.0)
        return LatLngPoint(
            lat = a.lat + clamped * (b.lat - a.lat),
            lng = a.lng + clamped * (b.lng - a.lng)
        )
    }

    /** polyline 在指定段的方向角（度，0=北） */
    private fun segmentBearingAt(polyline: List<LatLngPoint>, segIdx: Int): Double {
        val a = polyline[segIdx]
        val b = polyline[segIdx + 1]
        return bearing(a.lat, a.lng, b.lat, b.lng)
    }

    private fun bearing(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val dLng = Math.toRadians(lng2 - lng1)
        val rLat1 = Math.toRadians(lat1)
        val rLat2 = Math.toRadians(lat2)
        val y = kotlin.math.sin(dLng) * kotlin.math.cos(rLat2)
        val x = kotlin.math.cos(rLat1) * kotlin.math.sin(rLat2) -
                kotlin.math.sin(rLat1) * kotlin.math.cos(rLat2) * kotlin.math.cos(dLng)
        return (Math.toDegrees(kotlin.math.atan2(y, x)) + 360) % 360
    }

    private fun angleDiff(a: Double, b: Double): Double {
        val diff = abs(a - b) % 360
        return if (diff > 180) 360 - diff else diff
    }

    /** 点到线段 AB 的垂足（平面近似），返回 (垂足坐标, 参数 t∈[0,1]) */
    private fun perpendicularFoot(
        p: LatLngPoint, a: LatLngPoint, b: LatLngPoint
    ): Pair<LatLngPoint, Double> {
        val dx = b.lng - a.lng
        val dy = b.lat - a.lat
        if (dx == 0.0 && dy == 0.0) return Pair(a, 0.0)

        val t = ((p.lng - a.lng) * dx + (p.lat - a.lat) * dy) / (dx * dx + dy * dy)
        val clamped = t.coerceIn(0.0, 1.0)
        return Pair(
            LatLngPoint(a.lat + clamped * dy, a.lng + clamped * dx),
            clamped
        )
    }

    /**
     * 判定岔路在路线左侧还是右侧。
     * 使用 2D 叉积：heading 向量 × (intersection − routePoint) 向量。
     * 正值 → 左侧，负值 → 右侧。
     */
    private fun classifyDirection(
        routeBearingDeg: Double,
        routePoint: LatLngPoint,
        intersectionPoint: LatLngPoint
    ): BranchDirection {
        val headingRad = Math.toRadians(routeBearingDeg)
        val hx = kotlin.math.sin(headingRad)
        val hy = kotlin.math.cos(headingRad)

        val cosLat = kotlin.math.cos(Math.toRadians(routePoint.lat))
        val dx = (intersectionPoint.lng - routePoint.lng) * 111320.0 * cosLat
        val dy = (intersectionPoint.lat - routePoint.lat) * 111320.0

        val cross = hx * dy - hy * dx
        return if (cross >= 0) BranchDirection.LEFT else BranchDirection.RIGHT
    }

    // ==================== 空间索引 ====================

    private fun buildIndex(): Map<Long, List<Int>> {
        val map = mutableMapOf<Long, MutableList<Int>>()
        for ((idx, seg) in allSegments.withIndex()) {
            if (seg.points.isEmpty()) continue
            val mid = seg.points[seg.points.size / 2]
            val key = bucketKey(
                (mid.lat / bucketPrecision).toLong(),
                (mid.lng / bucketPrecision).toLong()
            )
            map.getOrPut(key) { mutableListOf() }.add(idx)
        }
        return map
    }

    private fun affectedBuckets(gpsPos: LatLngPoint, radiusM: Double): Set<Long> {
        val latCos = kotlin.math.cos(Math.toRadians(gpsPos.lat))
        val deltaDeg = radiusM / (111320.0 * latCos)
        val deltaBucket = (deltaDeg / bucketPrecision).toInt().coerceAtLeast(1)

        val centerLatBucket = (gpsPos.lat / bucketPrecision).toLong()
        val centerLngBucket = (gpsPos.lng / bucketPrecision).toLong()

        val buckets = mutableSetOf<Long>()
        for (dLat in -deltaBucket..deltaBucket) {
            for (dLng in -deltaBucket..deltaBucket) {
                buckets.add(bucketKey(centerLatBucket + dLat, centerLngBucket + dLng))
            }
        }
        return buckets
    }

    private fun bucketKey(latBucket: Long, lngBucket: Long): Long =
        (latBucket shl 32) or (lngBucket and 0xFFFFFFFF)

    // ==================== 辅助 ====================

    /** 预计算路线各点的累计距离（米），从起点起算 */
    private fun precomputeCumulativeDistances(routePoints: List<LatLngPoint>): List<Double> {
        val distances = mutableListOf(0.0)
        var cumulative = 0.0
        for (i in 1 until routePoints.size) {
            cumulative += Haversine.distance(routePoints[i - 1], routePoints[i])
            distances.add(cumulative)
        }
        return distances
    }

    /** 预计算路线各点的切线方向（度，0=北），端点用相邻段方向 */
    private fun precomputeRouteBearings(routePoints: List<LatLngPoint>): List<Double> {
        return routePoints.indices.map { i ->
            when {
                i == 0 -> bearing(
                    routePoints[0].lat, routePoints[0].lng,
                    routePoints[1].lat, routePoints[1].lng
                )
                i == routePoints.size - 1 -> bearing(
                    routePoints[i - 1].lat, routePoints[i - 1].lng,
                    routePoints[i].lat, routePoints[i].lng
                )
                else -> bearing(
                    routePoints[i].lat, routePoints[i].lng,
                    routePoints[i + 1].lat, routePoints[i + 1].lng
                )
            }
        }
    }

    companion object {
        private const val DEFAULT_RADIUS_M = 30.0
        private const val SAME_ROAD_ANGLE_DEG = 30.0
    }
}
