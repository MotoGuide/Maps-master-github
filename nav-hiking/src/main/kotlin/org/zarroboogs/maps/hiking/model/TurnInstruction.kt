package org.zarroboogs.maps.hiking.model

import org.zarroboogs.maps.core.road.model.LatLngPoint

/**
 * 转向指令（GPX 导入时预计算，导航时查表）。
 *
 * @property direction 转向方向：左转/右转/直行/掉头
 * @property location 转向点的 GPS 坐标（GCJ-02）
 * @property distanceFromStartM 距起点的累计距离（用于导航时排序查询）
 * @property turnAngle 转向角度（绝对值，度）
 * @property nextSegmentBearing 转向后的方位角（0-360°，正北=0）
 */
data class TurnInstruction(
    val direction: TurnDirection,
    val location: LatLngPoint,
    val distanceFromStartM: Double,
    val turnAngle: Double,
    val nextSegmentBearing: Double
)

/** 转向方向枚举 */
enum class TurnDirection {
    /** 左转：方位角变化 +30° ~ +150° */
    LEFT,
    /** 右转：方位角变化 -30° ~ -150° */
    RIGHT,
    /** 直行：方位角变化 < 30° */
    STRAIGHT,
    /** 掉头：方位角变化 > 150° */
    U_TURN
}
