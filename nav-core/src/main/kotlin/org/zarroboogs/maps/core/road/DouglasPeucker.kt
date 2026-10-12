package org.zarroboogs.maps.core.road

import org.zarroboogs.maps.core.road.model.LatLngPoint
import kotlin.math.sqrt

/**
 * Douglas-Peucker 路线简化算法。
 *
 * 将高德 SDK 返回的密集路线坐标点（数百个点）简化为关键拐点（~50-80 个），
 * 大幅减少 BLE 传输数据量，同时保持路线几何精度。
 *
 * 简化阈值 EPSILON = 3m：小于 3m 的偏移肉眼不可见（屏幕 1px ≈ 0.5-1m），
 * 可调至 5m 进一步压缩数据量。
 */
object DouglasPeucker {
    /** 默认简化阈值（米），可在构造时覆盖 */
    const val DEFAULT_EPSILON_M = 3.0

    /**
     * 简化路线点列表。
     *
     * @param points 原始路线坐标点（WGS-84 或 GCJ-02，算法与坐标系无关）
     * @param epsilonM 简化阈值（米），小于此偏移的点被移除
     * @return 简化后的点列表，首尾点保证保留
     */
    fun simplify(
        points: List<LatLngPoint>,
        epsilonM: Double = DEFAULT_EPSILON_M
    ): List<LatLngPoint> {
        if (points.size <= 2) return points.toList()

        // 找到距离首尾连线最远的点
        var maxDist = 0.0
        var maxIdx = 0
        val first = points.first()
        val last = points.last()

        for (i in 1 until points.size - 1) {
            val d = perpendicularDistance(points[i], first, last)
            if (d > maxDist) {
                maxDist = d
                maxIdx = i
            }
        }

        return if (maxDist > epsilonM) {
            val left = simplify(points.subList(0, maxIdx + 1), epsilonM)
            val right = simplify(points.subList(maxIdx, points.size), epsilonM)
            // 合并时去重中间点
            left.dropLast(1) + right
        } else {
            listOf(first, last)
        }
    }

    /**
     * 计算点 p 到线段 ab 的垂直距离（米）。
     * 使用平面近似：先将经纬度差转为米制，再计算点到直线距离。
     *
     * 对于 200m 以内的线段，此近似误差 < 0.1m。
     */
    private fun perpendicularDistance(p: LatLngPoint, a: LatLngPoint, b: LatLngPoint): Double {
        // 简化：用经纬度差在局部平面近似
        val latMid = (a.lat + b.lat) / 2.0
        val cosLat = kotlin.math.cos(Math.toRadians(latMid))
        val metersPerDegLat = 111320.0
        val metersPerDegLng = 111320.0 * cosLat

        val ax = a.lng * metersPerDegLng
        val ay = a.lat * metersPerDegLat
        val bx = b.lng * metersPerDegLng
        val by = b.lat * metersPerDegLat
        val px = p.lng * metersPerDegLng
        val py = p.lat * metersPerDegLat

        val dx = bx - ax
        val dy = by - ay
        val lenSq = dx * dx + dy * dy
        if (lenSq < 1e-9) {
            // a 和 b 重合，返回 p 到 a 的距离
            val pdx = px - ax
            val pdy = py - ay
            return sqrt(pdx * pdx + pdy * pdy)
        }

        // 点到线段 ab 的垂足参数 t
        val t = ((px - ax) * dx + (py - ay) * dy) / lenSq
        val clampedT = t.coerceIn(0.0, 1.0)

        val footX = ax + clampedT * dx
        val footY = ay + clampedT * dy
        val fdx = px - footX
        val fdy = py - footY
        return sqrt(fdx * fdx + fdy * fdy)
    }
}
