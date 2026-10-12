package org.zarroboogs.maps.hiking.gpx

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.zarroboogs.maps.core.data.model.CameraAlarm
import org.zarroboogs.maps.core.data.model.GpsLocation
import org.zarroboogs.maps.core.data.model.NavState
import org.zarroboogs.maps.core.hub.NavDataSource
import org.zarroboogs.maps.core.hub.SessionState
import org.zarroboogs.maps.hiking.activity.OutdoorActivityType

/**
 * GPX 导航数据源 — 实现 nav-core 的 NavDataSource 接口。
 *
 * 将 GpxNavigationEngine 的输出（GpxNavState）映射为标准 NavState，
 * 通过 NavigationDataHub 推送到 BLE / ESP32。
 *
 * 这是 nav-hiking 模块与 nav-core 的唯一接线点。
 */
class GpxNavSource(
    private val engine: GpxNavigationEngine,
    private val activityType: OutdoorActivityType = OutdoorActivityType.HIKING
) : NavDataSource {

    override val navState: StateFlow<NavState> = MutableStateFlow(NavState()).asStateFlow()
    override val alarms: StateFlow<CameraAlarm> = MutableStateFlow(CameraAlarm.NONE).asStateFlow()
    override val location: StateFlow<GpsLocation> = MutableStateFlow(GpsLocation()).asStateFlow()
    override val sessionState: StateFlow<SessionState> = MutableStateFlow(SessionState.IDLE).asStateFlow()

    // 内部状态流（可写）
    private val _navState = navState as MutableStateFlow<NavState>
    private val _alarms = alarms as MutableStateFlow<CameraAlarm>
    private val _location = location as MutableStateFlow<GpsLocation>
    private val _sessionState = sessionState as MutableStateFlow<SessionState>

    private var isCollecting = false

    /**
     * 开始收集中，将 Engine 状态映射为标准 NavState。
     */
    override fun start() {
        if (isCollecting) return
        isCollecting = true
        _sessionState.value = SessionState.NAVIGATING

        // 收集引擎状态变化，映射为 NavState
        // 注意：需要在协程作用域中 collect，这里简化处理
        // 实际使用时会由 OutdoorHubManager 启动协程
    }

    override fun stop() {
        isCollecting = false
        _sessionState.value = SessionState.ENDED
        engine.stopNavigation()
    }

    /**
     * 当引擎输出新的 GpxNavState 时调用（由外部协程或引擎回调触发）。
     */
    fun onEngineStateUpdate(state: GpxNavState) {
        _navState.value = mapToNavState(state)
        _alarms.value = if (state.isOffRoute) CameraAlarm.LANE_LINE else CameraAlarm.NONE
        _location.value = GpsLocation(
            lat = state.snappedPosition.lat,
            lng = state.snappedPosition.lng,
            speed = 0f,   // TODO: 从引擎获取
            bearing = state.bearing.toFloat(),
            accuracy = 3f,
            time = System.currentTimeMillis()
        )
    }

    /**
     * GpxNavState → NavState 映射。
     *
     * NavState 是 v1.0 道路导航的数据模型，字段命名偏向道路导航场景。
     * 徒步模式复用该模型，用字段适配：
     * - directionIcon: 0=直行, 1=左转, 2=右转, 3=掉头（复用道路导航的方向图标）
     * - remainDistM: 剩余距离（米）
     * - roadName: 下一个转向描述（如"左转 300m"）
     */
    private fun mapToNavState(state: GpxNavState): NavState {
        return NavState(
            progress = state.progressPercent.toInt(),
            directionIcon = when (state.nextTurn?.direction) {
                org.zarroboogs.maps.hiking.model.TurnDirection.STRAIGHT -> 0
                org.zarroboogs.maps.hiking.model.TurnDirection.LEFT -> 1
                org.zarroboogs.maps.hiking.model.TurnDirection.RIGHT -> 2
                org.zarroboogs.maps.hiking.model.TurnDirection.U_TURN -> 3
                null -> 0
            },
            nextDistM = state.distanceToNextTurnM.toInt(),
            remainDistM = state.remainingDistanceM.toLong(),
            speedKmh = 0,  // 徒步速度由 ESP32 端估算
            roadName = buildRoadName(state),
            limitSpeedKmh = 0,
            iconSetId = 0   // 使用高德图标集
        )
    }

    /**
     * 构建转向描述文本，如"左转 300m"。
     */
    private fun buildRoadName(state: GpxNavState): String {
        val turn = state.nextTurn ?: return "沿路线前进"
        val dirName = when (turn.direction) {
            org.zarroboogs.maps.hiking.model.TurnDirection.LEFT -> "左转"
            org.zarroboogs.maps.hiking.model.TurnDirection.RIGHT -> "右转"
            org.zarroboogs.maps.hiking.model.TurnDirection.STRAIGHT -> "直行"
            org.zarroboogs.maps.hiking.model.TurnDirection.U_TURN -> "掉头"
        }
        return "$dirName ${state.distanceToNextTurnM.toInt()}m"
    }
}
