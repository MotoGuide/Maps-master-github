package org.zarroboogs.maps.core.road

import org.zarroboogs.maps.core.road.model.LatLngPoint

/**
 * GPS 位置吸附到路线的最近垂足。
 *
 * 使用点-线段垂足投影而非点-点最近匹配，确保车标平滑落在路线上，
 * 避免在顶点间跳跃抖动。
 *
 * 偏航检测：GPS 偏离路线最近线段 > 50m 时抛出 OffRouteException。
 * 防抖由调用方负责（3s 确认窗口）。
 */
object SnapToRoute {
    /** GPS 偏离路线超过此距离（米）视为偏航 */
    const val OFF_ROUTE_THRESHOLD_M = 50.0

    /**
     * 将 GPS 位置吸附到简化路线上的最近线段垂足。
     *
     * @param gpsPos 当前 GPS 位置
     * @param simplifiedRoute DP 简化后的路线点列表（至少 2 个点）
     * @return 吸附后的 LatLngPoint，落在路线上（垂足投影结果）
     * @throws OffRouteException GPS 偏离路线超过 OFF_ROUTE_THRESHOLD_M
     */
    fun snap(gpsPos: LatLngPoint, simplifiedRoute: List<LatLngPoint>): LatLngPoint {
        require(simplifiedRoute.size >= 2) { "Route must have at least 2 points" }

        var minDist = Double.MAX_VALUE
        var snapped = simplifiedRoute.first()

        for (i in 0 until simplifiedRoute.size - 1) {
            val a = simplifiedRoute[i]
            val b = simplifiedRoute[i + 1]
            val foot = perpendicularFoot(gpsPos, a, b)
            val dist = Haversine.distance(gpsPos, foot)
            if (dist < minDist) {
                minDist = dist
                snapped = foot
            }
        }

        if (minDist > OFF_ROUTE_THRESHOLD_M) {
            throw OffRouteException(minDist)
        }

        return snapped
    }

    /**
     * 吸附 GPS 位置，并返回沿线距离（从路线起点到吸附点的累计距离）。
     * 用于计算车辆在路线上的进度。
     *
     * @return Pair(snappedPoint, distanceAlongRouteM)
     */
    fun snapWithProgress(
        gpsPos: LatLngPoint,
        simplifiedRoute: List<LatLngPoint>,
        segmentDistances: List<Double>  // 每段长度（米），size = route.size - 1
    ): Pair<LatLngPoint, Double> {
        require(simplifiedRoute.size >= 2)
        require(segmentDistances.size == simplifiedRoute.size - 1)

        var minDist = Double.MAX_VALUE
        var snapped = simplifiedRoute.first()
        var bestSegIdx = 0
        var bestT = 0.0

        for (i in 0 until simplifiedRoute.size - 1) {
            val a = simplifiedRoute[i]
            val b = simplifiedRoute[i + 1]
            val result = perpendicularFootWithT(gpsPos, a, b)
            val dist = Haversine.distance(gpsPos, result.foot)
            if (dist < minDist) {
                minDist = dist
                snapped = result.foot
                bestSegIdx = i
                bestT = result.t
            }
        }

        if (minDist > OFF_ROUTE_THRESHOLD_M) {
            throw OffRouteException(minDist)
        }

        // 累计距离 = 前面所有完整段的距离 + 当前段内部分距离
        val distBefore = segmentDistances.take(bestSegIdx).sum()
        val distInSegment = segmentDistances[bestSegIdx] * bestT

        return Pair(snapped, distBefore + distInSegment)
    }

    /**
     * 计算点到线段 AB 的垂足坐标（GCJ-02 / WGS-84 下用平面近似）。
     * 当垂足落在线段延长线上时，返回最近的端点。
     */
    fun perpendicularFoot(p: LatLngPoint, a: LatLngPoint, b: LatLngPoint): LatLngPoint {
        val dx = b.lng - a.lng
        val dy = b.lat - a.lat
        if (dx == 0.0 && dy == 0.0) return a

        val t = ((p.lng - a.lng) * dx + (p.lat - a.lat) * dy) / (dx * dx + dy * dy)
        val clampedT = t.coerceIn(0.0, 1.0)
        return LatLngPoint(
            lat = a.lat + clampedT * dy,
            lng = a.lng + clampedT * dx
        )
    }

    /** 带 t 参数的垂足计算 */
    private fun perpendicularFootWithT(
        p: LatLngPoint, a: LatLngPoint, b: LatLngPoint
    ): FootResult {
        val dx = b.lng - a.lng
        val dy = b.lat - a.lat
        if (dx == 0.0 && dy == 0.0) return FootResult(a, 0.0)

        val t = ((p.lng - a.lng) * dx + (p.lat - a.lat) * dy) / (dx * dx + dy * dy)
        val clampedT = t.coerceIn(0.0, 1.0)
        return FootResult(
            LatLngPoint(lat = a.lat + clampedT * dy, lng = a.lng + clampedT * dx),
            clampedT
        )
    }

    private data class FootResult(val foot: LatLngPoint, val t: Double)
}

/**
 * GPS 偏离路线异常。
 * @param distanceM GPS 与最近路线点的距离（米）
 */
class OffRouteException(val distanceM: Double) :
    IllegalStateException("GPS ${distanceM.toInt()}m from route, need recalculation")
