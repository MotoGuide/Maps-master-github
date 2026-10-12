package org.zarroboogs.maps.core.road

import org.zarroboogs.maps.core.road.model.LatLngPoint
import org.zarroboogs.maps.core.road.model.MercatorPoint
import kotlin.math.cos

/**
 * 伪墨卡托投影：GPS(lat,lng) → 米制 XY 坐标。
 *
 * 以 origin 为原点，使用 Equirectangular 近似：
 *   x = (lng - origin.lng) * metersPerDegLng
 *   y = (lat - origin.lat) * METERS_PER_DEG_LAT
 *
 * 其中 metersPerDegLng = 111320 * cos(origin.lat)，随纬度修正。
 *
 * 误差分析（济南纬度 ~36.6°）：
 *   - 200m 视口内 cos(lat) 变化 < 0.003
 *   - 伪墨卡托误差 < 1m（远小于 GPS 定位误差 3-5m）
 *   - 不需要标准 Mercator 公式
 */
object MercatorProjector {
    private const val METERS_PER_DEG_LAT = 111320.0

    fun project(
        points: List<LatLngPoint>,
        origin: LatLngPoint
    ): List<MercatorPoint> {
        val cosLat = cos(Math.toRadians(origin.lat))
        val metersPerDegLng = METERS_PER_DEG_LAT * cosLat
        val originX = origin.lng * metersPerDegLng
        val originY = origin.lat * METERS_PER_DEG_LAT

        return points.map { pt ->
            MercatorPoint(
                x = pt.lng * metersPerDegLng - originX,
                y = pt.lat * METERS_PER_DEG_LAT - originY
            )
        }
    }

    /** 投影单个点，避免分配列表 */
    fun projectSingle(point: LatLngPoint, origin: LatLngPoint): MercatorPoint {
        val cosLat = cos(Math.toRadians(origin.lat))
        val metersPerDegLng = METERS_PER_DEG_LAT * cosLat
        return MercatorPoint(
            x = (point.lng - origin.lng) * metersPerDegLng,
            y = (point.lat - origin.lat) * METERS_PER_DEG_LAT
        )
    }
}

/**
 * Haversine 公式：计算两点间地表距离（米）。
 * 用于偏航检测和距离判断，不用于投影坐标计算。
 */
object Haversine {
    private const val EARTH_RADIUS_M = 6_371_000.0

    fun distance(a: LatLngPoint, b: LatLngPoint): Double {
        val dLat = Math.toRadians(b.lat - a.lat)
        val dLng = Math.toRadians(b.lng - a.lng)
        val sinDLat = kotlin.math.sin(dLat / 2)
        val sinDLng = kotlin.math.sin(dLng / 2)
        val aVal = sinDLat * sinDLat +
                cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sinDLng * sinDLng
        return 2 * EARTH_RADIUS_M * kotlin.math.atan2(kotlin.math.sqrt(aVal), kotlin.math.sqrt(1 - aVal))
    }
}
