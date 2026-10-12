package org.zarroboogs.maps.ui.tencent

/**
 * 腾讯 SDK nextIntersectionType → ESP32 iconType 映射
 *
 * ESP32 iconType (推送协议):
 *   1=左转  2=右转  3=微左  4=微右  5=急左  6=急右
 *   7=掉头  8=禁掉头  9=直行  10=岔左  11=岔右
 *   12=进环岛  13=出环岛
 *
 * 腾讯 nextIntersectionType 对应关系需路测验证，初期默认直行(9)。
 * 日志会记录每个未见过的值，后续根据实际数据完善映射表。
 */
object TencentIconMapper {

    private const val ICON_STRAIGHT = 9

    // 运行时记录：首次见的 nextIntersectionType 值会写 warn 日志，路测后填充 typeMap
    private val knownTypes = mutableSetOf<Int>()

    fun toEspIcon(nextIntersectionType: Int): Int {
        val icon = typeMap[nextIntersectionType] ?: run {
            if (knownTypes.add(nextIntersectionType)) {
                android.util.Log.w("TencentIconMapper",
                    "Unknown nextIntersectionType=$nextIntersectionType, defaulting to STRAIGHT(9)")
            }
            ICON_STRAIGHT
        }
        return icon
    }

    // 腾讯值 → ESP32 值（待路测验证具体方向语义）
    private val typeMap: Map<Int, Int> = mapOf(
        // TODO: 路测后填充实际映射
        // 示例: 1 → 9 (直行), 2 → 1 (左转) ...
    )
}
