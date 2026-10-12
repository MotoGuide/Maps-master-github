package org.zarroboogs.maps.hiking.activity

import kotlinx.coroutines.CoroutineScope
import org.zarroboogs.maps.core.hub.NavDataSource
import org.zarroboogs.maps.hiking.gpx.*
import org.zarroboogs.maps.hiking.model.GpxTrack

/**
 * 户外活动工厂 — 根据活动类型创建对应的组件。
 *
 * 扩展新活动类型时在此注册映射即可，其余组件全部复用。
 *
 * @property repository GPX 数据仓库
 * @property scope 协程作用域
 */
class OutdoorActivityFactory(
    private val repository: GpxRepository,
    private val scope: CoroutineScope
) {
    /**
     * 创建导航引擎（所有活动类型共用 GpxNavigationEngine）。
     */
    fun createEngine(
        type: OutdoorActivityType,
        track: GpxTrack
    ): GpxNavigationEngine = GpxNavigationEngine(track, repository, type)

    /**
     * 创建轨迹录制器。
     */
    fun createRecorder(
        type: OutdoorActivityType,
        locationClient: GpsLocationProvider
    ): GpxRecorder = GpxRecorder(locationClient, repository, scope, type)

    /**
     * 创建 NavDataSource（接线到 nav-core 的 NavigationDataHub）。
     */
    fun createNavSource(
        type: OutdoorActivityType,
        engine: GpxNavigationEngine
    ): NavDataSource = GpxNavSource(engine, type)

    /**
     * 创建模拟导航播放器。
     */
    fun createSimulator(
        track: GpxTrack,
        engine: GpxNavigationEngine
    ): GpxSimulator = GpxSimulator(track.waypoints, engine, scope)
}
