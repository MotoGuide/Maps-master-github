package org.zarroboogs.maps.hiking.activity

/**
 * 户外活动类型定义 — 可扩展架构的核心。
 *
 * 当前实现：HIKING（徒步）
 * 预留扩展：CYCLING（骑行）、TRAIL_RUNNING（越野跑）、SKIING（滑雪）
 *
 * 扩展新活动类型只需在此枚举新增一项 + 在 OutdoorActivityFactory 注册映射。
 *
 * @property id 唯一标识符
 * @property displayName 中文显示名
 * @property defaultSpeedKmh 默认速度（用于 ETA 计算）
 * @property trackColor 轨迹线颜色（ARGB）
 * @property iconResName Tab 图标资源名
 * @property navProtocolPrefix BLE 导航协议前缀（H=徒步, B=骑行, T=越野跑, S=滑雪）
 */
enum class OutdoorActivityType(
    val id: String,
    val displayName: String,
    val defaultSpeedKmh: Float,
    val trackColor: Int,
    val iconResName: String,
    val navProtocolPrefix: String
) {
    /** 徒步 — 默认 4 km/h，黄色轨迹线 */
    HIKING(
        id = "hiking",
        displayName = "徒步",
        defaultSpeedKmh = 4.0f,
        trackColor = 0xFFFFC107.toInt(),  // 黄色 #FFC107
        iconResName = "ic_hiking",
        navProtocolPrefix = "H"
    ),

    // ===== 未来扩展（已预留，暂不启用）=====

    /** 骑行 — 18 km/h，绿色轨迹线 */
    CYCLING(
        id = "cycling",
        displayName = "骑行",
        defaultSpeedKmh = 18.0f,
        trackColor = 0xFF4CAF50.toInt(),  // 绿色
        iconResName = "ic_cycling",
        navProtocolPrefix = "B"
    ),

    /** 越野跑 — 8 km/h，橙色轨迹线 */
    TRAIL_RUNNING(
        id = "trail_running",
        displayName = "越野跑",
        defaultSpeedKmh = 8.0f,
        trackColor = 0xFFFF9800.toInt(),  // 橙色
        iconResName = "ic_trail_run",
        navProtocolPrefix = "T"
    ),

    /** 滑雪 — 30 km/h，蓝色轨迹线 */
    SKIING(
        id = "skiing",
        displayName = "滑雪",
        defaultSpeedKmh = 30.0f,
        trackColor = 0xFF2196F3.toInt(),  // 蓝色
        iconResName = "ic_skiing",
        navProtocolPrefix = "S"
    );
}
