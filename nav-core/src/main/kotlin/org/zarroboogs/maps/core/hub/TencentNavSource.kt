package org.zarroboogs.maps.core.hub

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.zarroboogs.maps.core.data.model.CameraAlarm
import org.zarroboogs.maps.core.data.model.GpsLocation
import org.zarroboogs.maps.core.data.model.NavState

/**
 * 腾讯地图 SDK 导航数据源 — 桥接 NavigatorDrive observer → StateFlow。
 *
 * 接入时实现：
 * - 持有 NavigatorDrive 实例
 * - 注册 SimpleNavigatorDriveObserver
 * - onNavDataInfoUpdate → _navState.value
 * - onNavLocationInfoUpdate → _location.value
 * - onOffRoute → _sessionState.value = OFF_ROUTE
 *
 * iconSetId 固定为 1（腾讯），由推送层自动携带。
 */
class TencentNavSource : NavDataSource {

    override val navState: MutableStateFlow<NavState> = MutableStateFlow(
        NavState()
    )

    override val alarms: MutableStateFlow<CameraAlarm> = MutableStateFlow(
        CameraAlarm.NONE
    )

    override val location: MutableStateFlow<GpsLocation> = MutableStateFlow(
        GpsLocation()
    )

    override val sessionState: MutableStateFlow<SessionState> = MutableStateFlow(
        SessionState.IDLE
    )

    override fun start() {
        // TODO: 注册 NavigatorDrive observer
        // val observer = object : SimpleNavigatorDriveObserver() {
        //     override fun onNavDataInfoUpdate(data: NavDriveDataInfo) {
        //         val routeData = data.navRoutes[0]
        //         _navState.value = NavState(
        //             directionIcon = routeData.nextIntersectionType,
        //             nextDistM = routeData.nextIntersectionRemainingDistance,
        //             speedKmh = data.speedKmh,
        //             iconSetId = 1  // 腾讯图标集
        //         )
        //     }
        //     override fun onNavLocationInfoUpdate(info: NavLocationInfo) {
        //         _location.value = GpsLocation(
        //             lat = info.routeLocation.adsorbLocation.latitude,
        //             lng = info.routeLocation.adsorbLocation.longitude,
        //             heading = info.routeLocation.direction
        //         )
        //     }
        //     override fun onOffRoute() {
        //         _sessionState.value = SessionState.OFF_ROUTE
        //     }
        // }
    }

    override fun stop() {
        // TODO: 注销 observer，停止导航
    }
}
