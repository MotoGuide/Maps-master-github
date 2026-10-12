package org.zarroboogs.maps.core.route

import kotlinx.coroutines.flow.Flow
import org.zarroboogs.maps.core.hub.NavDataSource

enum class EngineType { AMAP, TENCENT }

enum class TravelMode { DRIVE, RIDE, WALK, MOTORCYCLE }

data class PoiResult(
    val name: String,
    val address: String = "",
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val poiId: String = ""
)

data class RouteInfo(
    val totalDistanceM: Int = 0,
    val totalTimeS: Int = 0,
    val engineType: EngineType = EngineType.AMAP
)

data class RouteResult(
    val routes: List<RouteInfo>,
    val engineType: EngineType
)

interface RouteEngine {
    val engineType: EngineType

    fun searchPoi(query: String, city: String = ""): Flow<Result<List<PoiResult>>>

    fun calculateRoute(
        start: PoiResult,
        end: PoiResult,
        mode: TravelMode
    ): Flow<Result<RouteResult>>

    fun createNavDataSource(): NavDataSource
}
