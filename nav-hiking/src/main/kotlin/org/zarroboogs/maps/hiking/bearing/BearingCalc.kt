package org.zarroboogs.maps.hiking.bearing

import org.zarroboogs.maps.core.road.model.LatLngPoint
import kotlin.math.*

/**
 * 方位角计算工具。
 * 使用 Haversine bearing 公式计算两点之间的方位角。
 *
 * 方位角定义：正北=0°，正东=90°，正南=180°，正西=270°。
 */
object BearingCalc {

    /**
     * 计算从 [from] 到 [to] 的方位角（0-360°）。
     *
     * @param from 起始坐标
     * @param to 目标坐标
     * @return 方位角（度，0-360），正北为 0
     */
    fun bearing(from: LatLngPoint, to: LatLngPoint): Double {
        val dLon = Math.toRadians(to.lng - from.lng)
        val lat1 = Math.toRadians(from.lat)
        val lat2 = Math.toRadians(to.lat)

        val y = sin(dLon) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)

        val bearingRad = atan2(y, x)
        return (Math.toDegrees(bearingRad) + 360) % 360
    }

    /**
     * 计算两个方位角之间的最小角度差（-180° ~ +180°）。
     * 正值表示顺时针（右转），负值表示逆时针（左转）。
     *
     * @param fromBearing 起始方位角
     * @param toBearing 目标方位角
     * @return 角度差（度）
     */
    fun bearingDelta(fromBearing: Double, toBearing: Double): Double {
        return ((toBearing - fromBearing + 540) % 360) - 180
    }

    /**
     * 计算一组方位角的平均值（向量平均法）。
     * 避免普通平均值在 0°/360° 边界上的问题。
     *
     * @param bearings 方位角列表（度）
     * @return 平均方位角（度）
     */
    fun averageBearing(bearings: List<Double>): Double {
        if (bearings.isEmpty()) return 0.0
        var sumSin = 0.0
        var sumCos = 0.0
        for (b in bearings) {
            val rad = Math.toRadians(b)
            sumSin += sin(rad)
            sumCos += cos(rad)
        }
        return (Math.toDegrees(atan2(sumSin, sumCos)) + 360) % 360
    }
}
