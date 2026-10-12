package org.zarroboogs.maps.hiking.bearing

import org.zarroboogs.maps.core.road.model.LatLngPoint
import org.zarroboogs.maps.hiking.model.GpxWaypoint
import org.zarroboogs.maps.hiking.model.TurnDirection
import org.zarroboogs.maps.hiking.model.TurnInstruction
import kotlin.math.abs

/**
 * GPX 转向点检测器 — 导入时一次性预计算。
 *
 * 设计原则：
 * 1. 仅在 GPX 导入时调用一次，结果存入 Room turn_points 表
 * 2. 导航引擎运行时直接查表，O(1)复杂度，零 CPU 开销
 * 3. 使用窗口平滑 + 最小转向间距，彻底消除 GPS 抖动导致的重复转向
 *
 * @property turnThresholdDegrees 转向判定阈值（度），默认 30°
 * @property minTurnDistanceM 两个转向点之间的最小距离（米），默认 50m
 * @property smoothWindowSize 窗口平滑大小，默认 3 点
 */
class TurnDetector(
    private val turnThresholdDegrees: Double = 30.0,
    private val minTurnDistanceM: Double = 50.0,
    private val smoothWindowSize: Int = 3
) {

    /**
     * 对整条 GPX 轨迹一次性预计算所有转向点。
     *
     * @param waypoints 航点列表（按 index 升序，含 distanceFromStartM）
     * @return 转向指令列表，按 distanceFromStartM 升序
     */
    fun detectTurns(waypoints: List<GpxWaypoint>): List<TurnInstruction> {
        if (waypoints.size < smoothWindowSize * 2 + 1) {
            return emptyList() // 轨迹太短，无转向
        }

        val turns = mutableListOf<TurnInstruction>()
        var lastTurnDistance = -minTurnDistanceM // 确保第一个转向点可被记录

        // 从第 smoothWindowSize 个点开始，到倒数第 smoothWindowSize 个点结束
        for (i in smoothWindowSize until waypoints.size - smoothWindowSize) {
            // 窗口平滑：取前后各 N 个点计算平均入/出方位角
            val bearingsIn = mutableListOf<Double>()
            val bearingsOut = mutableListOf<Double>()

            for (j in i - smoothWindowSize until i) {
                bearingsIn.add(
                    BearingCalc.bearing(
                        LatLngPoint(waypoints[j].lat, waypoints[j].lng),
                        LatLngPoint(waypoints[j + 1].lat, waypoints[j + 1].lng)
                    )
                )
            }
            for (j in i until i + smoothWindowSize) {
                bearingsOut.add(
                    BearingCalc.bearing(
                        LatLngPoint(waypoints[j].lat, waypoints[j].lng),
                        LatLngPoint(waypoints[j + 1].lat, waypoints[j + 1].lng)
                    )
                )
            }

            val avgBearingIn = BearingCalc.averageBearing(bearingsIn)
            val avgBearingOut = BearingCalc.averageBearing(bearingsOut)
            val delta = BearingCalc.bearingDelta(avgBearingIn, avgBearingOut)
            val absDelta = abs(delta)

            // 角度变化不够大 → 非转向
            if (absDelta < turnThresholdDegrees) continue

            val currentDist = waypoints[i].distanceFromStartM

            // 距上一个转向点太近 → 跳过（防抖动）
            if (currentDist - lastTurnDistance < minTurnDistanceM) continue

            lastTurnDistance = currentDist

            turns.add(
                TurnInstruction(
                    direction = when {
                        delta > turnThresholdDegrees && delta <= 150 -> TurnDirection.LEFT
                        delta < -turnThresholdDegrees && delta >= -150 -> TurnDirection.RIGHT
                        absDelta > 150 -> TurnDirection.U_TURN
                        else -> TurnDirection.STRAIGHT
                    },
                    location = LatLngPoint(waypoints[i].lat, waypoints[i].lng),
                    distanceFromStartM = currentDist,
                    turnAngle = absDelta,
                    nextSegmentBearing = avgBearingOut
                )
            )
        }

        return turns
    }
}
