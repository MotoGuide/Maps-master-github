package org.zarroboogs.maps.hub

import org.zarroboogs.maps.core.road.model.LatLngPoint

/**
 * 【开源版精简壳】导航样式管理器（箭头 / 路网 / 压弯模式切换）。
 *
 * 说明：路网 / 压弯推送协议实现未包含在本开源版本中。
 * 本类仅保留原有公开接口签名（供 UI 层无改动调用）与本地状态字段，
 * 开源版中推送相关操作不会实际生效。
 */
object NavStyleManager {

    const val STYLE_ARROW = 0
    const val STYLE_ROAD  = 1
    const val STYLE_LEAN  = 2

    @Volatile
    var currentStyle = STYLE_ARROW
        private set

    // ── 由外部（导航界面）更新的实时数据 ──

    @Volatile var currentSpeedKmh: Float = 0f
    @Volatile var currentHeadingDeg: Float = 0f
    @Volatile var currentGps: LatLngPoint? = null

    /** 路网墨卡托原点（推路网时的 GPS 吸附点） */
    @Volatile var roadNetworkOrigin: LatLngPoint? = null

    /** 当前路线 polyline（导航开始时设置） */
    var routePolyline: List<LatLngPoint> = emptyList()

    fun shouldSendNextBatch(currentProgress: Int): Boolean = false

    fun pushNextBatch(): Boolean = false

    fun toggle() {
        // 开源版：仅在本地切换样式标记
        currentStyle = when (currentStyle) {
            STYLE_ARROW -> STYLE_ROAD
            STYLE_ROAD -> STYLE_ARROW
            else -> STYLE_ARROW
        }
    }

    fun setStyle(style: Int) {
        currentStyle = style
    }

    fun addListener(listener: (Int) -> Unit) {}

    fun removeListener(listener: (Int) -> Unit) {}

    fun reset() {
        currentStyle = STYLE_ARROW
        currentGps = null
        roadNetworkOrigin = null
        currentSpeedKmh = 0f
        currentHeadingDeg = 0f
        routePolyline = emptyList()
    }

    fun pushRoadNetwork(): Boolean = false
}
