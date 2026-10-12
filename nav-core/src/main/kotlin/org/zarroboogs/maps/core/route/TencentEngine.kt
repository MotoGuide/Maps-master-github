package org.zarroboogs.maps.core.route

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.zarroboogs.maps.core.hub.NavDataSource
import org.zarroboogs.maps.core.hub.TencentNavSource

/**
 * 腾讯地图 SDK 路线引擎 — 实现 RouteEngine 接口。
 *
 * 接入时实现：
 * - 持有 NavigatorDrive 实例（Service 级生命周期）
 * - searchPoi() → NavigatorDrive 搜索
 * - calculateRoute() → NavigatorDrive.searchRoute(MotorOptions)
 * - createNavDataSource() → TencentNavSource(navigatorDrive)
 */
class TencentEngine : RouteEngine {

    override val engineType: EngineType = EngineType.TENCENT

    override fun searchPoi(query: String, city: String): Flow<Result<List<PoiResult>>> {
        // TODO: 接入腾讯 POI 搜索
        return flow { emit(Result.success(emptyList())) }
    }

    override fun calculateRoute(
        start: PoiResult,
        end: PoiResult,
        mode: TravelMode
    ): Flow<Result<RouteResult>> {
        // TODO: 接入腾讯路线规划 (MotorOptions: 排量 + 能源类型)
        return flow { emit(Result.success(RouteResult(emptyList(), EngineType.TENCENT))) }
    }

    /** 创建腾讯导航数据源 — iconSetId=1 写入每个 NavState */
    override fun createNavDataSource(): NavDataSource {
        return TencentNavSource()
    }
}
