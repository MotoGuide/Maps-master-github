package org.zarroboogs.maps.core.data.model

/**
 * 报警枚举（统一编码，双体系）
 *
 * 0x01~0x0D: 摩托车违章报警（自有 CameraRouteMatcher 体系，对齐 CameraNoteType.STANDARD_VALUES）
 * 0x10~0x1B: 驾车报警（高德 SDK CameraType 枚举，= cameraType + 0x10）
 * 0x00: 无报警  0xFF: 未知
 *
 * 已修正 v1.x 枚举名错误：原枚举名与 CameraNoteType 实际语义不匹配，仅运行时不依赖名称（fromCode 按数值匹配）所以 Bug 从未暴露。
 */
enum class CameraAlarm(val code: Int) {
    // ── 无报警 / 未知 ──
    NONE(0x00),
    UNKNOWN(0xFF),

    // ── 摩托车违章报警（0x01~0x0D，CameraRouteMatcher 自有体系）──
    RED_LIGHT(0x01),              // 闯红灯（原名碰巧正确）
    KEEP_RIGHT(0x02),             // 靠右行驶（原名 SPEED，错误）
    BUS_LANE_MOTO(0x03),          // 公交车道（原名 INTERVAL_SPEED_START，错误）
    NON_MOTOR_LANE(0x04),         // 非机动车道（原名 INTERVAL_SPEED_END，错误）
    RESTRICTED_AREA(0x05),        // 闯限行（原名 EMERGENCY_LANE，错误）
    OVER_SPEED(0x06),             // 超速（原名 BUS_LANE，错误）
    ILLEGAL_LANE_CHANGE(0x07),    // 违规变道（原名 ONE_WAY，错误）
    WRONG_WAY(0x08),              // 逆行（原名 NO_TURN，错误）
    NO_HELMET(0x09),              // 不戴头盔（原名 RAILWAY，错误）
    EMERGENCY_LANE(0x0A),         // 应急车道（原名 SCHOOL，错误）
    NOISE_EXCEED(0x0B),           // 噪音超标（原名 VILLAGE，错误）
    LANE_LINE(0x0C),              // 压线（原名 DANGER，错误）
    ILLEGAL_LIGHT(0x0D),          // 违规用灯（原名 TOLL，错误）

    // ── 驾车报警（0x10~0x1B，高德 SDK CameraType 枚举）──
    CAR_SPEED(0x10),              // 测速摄像（CameraType.SPEED=0）
    CAR_SURVEILLANCE(0x11),       // 监控摄像（CameraType.SURVEILLANCE=1）
    CAR_TRAFFIC_LIGHT(0x12),      // 闯红灯拍照（CameraType.TRAFFICLIGHT=2）
    CAR_BREAK_RULE(0x13),         // 违章拍照（CameraType.BREAKRULE=3）
    CAR_BUS_WAY(0x14),            // 公交专用道（CameraType.BUSWAY=4）
    CAR_EMERGENCY(0x15),          // 应急车道（CameraType.EMERGENCY=5）
    CAR_BICYCLE(0x16),            // 非机动车道（CameraType.BICYCLE=6，暂未使用）
    CAR_INTERVAL_START(0x18),     // 区间测速起始（CameraType.INTERVALVELOCITYSTART=8）
    CAR_INTERVAL_END(0x19),       // 区间测速终止（CameraType.INTERVALVELOCITYEND=9）
    CAR_FLOW_SPEED(0x1A),         // 流动测速（CameraType.FLOWSPEED=10）
    CAR_ETC(0x1B);                // ETC计费拍照（CameraType.ETC=11）

    companion object {
        /** 按数值查找，未匹配返回 NONE */
        fun fromCode(code: Int): CameraAlarm =
            values().firstOrNull { it.code == code } ?: NONE

        /** 判断是否为驾车报警（高德 SDK 0x10~0x1B） */
        fun isCarAlarm(alarm: CameraAlarm): Boolean =
            alarm.code in 0x10..0x1B

        /** 判断是否有限速值（仅超速类摄像头有 cameraSpeed） */
        fun hasLimitSpeed(alarm: CameraAlarm): Boolean =
            alarm == CAR_SPEED || alarm == CAR_INTERVAL_START || alarm == CAR_FLOW_SPEED
    }
}
