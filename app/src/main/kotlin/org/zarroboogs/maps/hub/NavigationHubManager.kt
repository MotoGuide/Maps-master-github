package org.zarroboogs.maps.hub

import org.zarroboogs.maps.core.route.EngineType

/**
 * 【开源版精简壳】导航推送编排管理器。
 *
 * 说明：导航数据推送实现未包含在本开源版本中。
 * 本类仅保留原有公开接口签名（供 UI 层无改动调用），所有实现均为空操作。
 */
object NavigationHubManager {

    private val telemetry = BleTelemetryRing()

    /** BLE 健康遥测环形缓冲区（供崩溃日志 dump 使用） */
    val telemetryRing: BleTelemetryRing get() = telemetry

    fun startSession(routeTotalLen: Int, engineType: EngineType = EngineType.AMAP) {}

    fun stopSession() {}

    fun updateNavState(iconType: Int, curStepLen: Int, routeLen: Int, routeTotalLen: Int) {}

    fun updateAlarm(alarmCode: Int) {}

    fun updateAlarm(alarmCode: Int, limitSpeed: Int) {}

    fun updateSpeed(kmh: Float) {}

    fun getSessionId(): Short = 0

    fun isSessionRunning(): Boolean = false

    fun startCruise() {}

    fun stopCruise() {}

    fun pushCruiseAlarm(alarmCode: Int) {}

    fun isCruiseRunning(): Boolean = false

    fun rotateSessionId(): Short = 0

    fun startHeadingPush() {}

    fun stopHeadingPush() {}

    fun onDeviceTelemetry(battery: Int, fw: String, heapKb: Int, fps: Int) {}

    fun logEvent(event: String, params: Map<String, Any> = emptyMap()) {}

    fun getEventLogFiles(): Array<java.io.File> = emptyArray()

    fun getReplayFiles(): Array<java.io.File> = emptyArray()
}
