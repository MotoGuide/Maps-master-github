package org.zarroboogs.maps.hiking.gpx

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.zarroboogs.maps.core.data.model.GpsLocation
import org.zarroboogs.maps.core.road.Haversine
import org.zarroboogs.maps.core.road.OffRouteException
import org.zarroboogs.maps.core.road.SnapToRoute
import org.zarroboogs.maps.core.road.model.LatLngPoint
import org.zarroboogs.maps.hiking.activity.OutdoorActivityType
import org.zarroboogs.maps.hiking.model.GpxTrack
import org.zarroboogs.maps.hiking.model.GpxWaypoint
import org.zarroboogs.maps.hiking.model.TurnInstruction

/**
 * GPX 导航引擎 — 徒步/户外导航的核心。
 *
 * 复用 nav-core 的 SnapToRoute 做 GPS 吸附和偏航检测，
 * 转向点从预计算的 Room 表查询（O(1)），方位角由 BearingCalc 计算。
 *
 * @property track 目标 GPX 轨迹（GCJ-02）
 * @property repository GPX 数据仓库（用于查转向点）
 * @property activityType 户外活动类型（影响默认速度等参数）
 */
class GpxNavigationEngine(
    private val track: GpxTrack,
    private val repository: GpxRepository,
    private val activityType: OutdoorActivityType = OutdoorActivityType.HIKING
) {
    // ===== 状态 =====
    private val _navState = MutableStateFlow(GpxNavState())
    val navState: StateFlow<GpxNavState> = _navState.asStateFlow()

    private val _routeState = MutableStateFlow(RouteState.ON_ROUTE)
    val routeState: StateFlow<RouteState> = _routeState.asStateFlow()

    // ===== 偏航检测状态机 =====
    private var offRouteCount = 0
    private var lastReSnapTimeMs = 0L

    // ===== 导航方向 =====
    private var navDirection: NavDirection = NavDirection.TO_END
    private var isNavigating = false

    /** 缓存的预计算转向点列表 */
    private var turnInstructions: List<TurnInstruction> = emptyList()

    /** 预计算的段距离（用于 SnapToRoute.snapWithProgress） */
    private var segmentDistances: List<Double> = emptyList()

    /** 转换为 LatLngPoint 的简化路线 */
    private var simplifiedRoute: List<LatLngPoint> = emptyList()

    // ===== GPS 插值（平滑 UI） =====
    private var prevGpsLat: Double = 0.0
    private var prevGpsLng: Double = 0.0
    private var prevGpsTimeMs: Long = 0L
    private var currGpsLat: Double = 0.0
    private var currGpsLng: Double = 0.0
    private var currGpsTimeMs: Long = 0L

    // ===== 高程数据（高程感知 ETA） =====
    private var remainingElevationGainM: Double = 0.0
    private var remainingElevationLossM: Double = 0.0

    // ===== GPS 信号监测 =====
    private var lastGpsUpdateTimeMs: Long = 0L
    private var gpsSignalStatus: GpsSignalStatus = GpsSignalStatus.GOOD
    private var gpsWeakStartTimeMs: Long = 0L

    companion object {
        /** GPS 偏离路线超过此距离（米）视为偏航 */
        const val OFF_ROUTE_THRESHOLD_M = 50.0

        /** 连续偏航次数达到此值 → 确认偏航 */
        const val OFF_ROUTE_CONFIRM_COUNT = 3

        /** 确认偏航后重新吸附的间隔（毫秒） */
        const val RE_SNAP_INTERVAL_MS = 3000L

        /** 回到路线内此距离（米） → 清除偏航 */
        const val ON_ROUTE_RECOVER_M = 10.0
    }

    /**
     * 初始化：加载转向点、准备路线数据、预计算高程剖面。
     */
    suspend fun initialize() {
        turnInstructions = repository.loadTurnInstructions(track.id)
        simplifiedRoute = track.waypoints.map { LatLngPoint(it.lat, it.lng) }
        segmentDistances = track.waypoints.zipWithNext { a, b ->
            Haversine.distance(LatLngPoint(a.lat, a.lng), LatLngPoint(b.lat, b.lng))
        }
        // 预计算高程：统计爬升和下降
        computeElevationProfile()
    }

    /** 预计算高程剖面：爬升/下降总量 */
    private fun computeElevationProfile() {
        var gain = 0.0
        var loss = 0.0
        val wps = track.waypoints
        if (wps.size >= 2) {
            for (i in 1 until wps.size) {
                val prev = wps[i - 1].elevation
                val curr = wps[i].elevation
                if (prev != null && curr != null) {
                    val diff = curr - prev
                    if (diff > 0) gain += diff else loss += kotlin.math.abs(diff)
                }
            }
        }
        remainingElevationGainM = gain
        remainingElevationLossM = loss
        if (gain > 0 || loss > 0) {
            android.util.Log.d("Hiking", "[ENGINE] elevation profile: gain=${gain.toInt()}m, loss=${loss.toInt()}m")
        }
    }

    /**
     * 开始导航。
     *
     * @param direction 导航方向：TO_START（去起点）或 TO_END（去终点）
     */
    suspend fun startNavigation(direction: NavDirection = NavDirection.TO_END) {
        navDirection = direction
        isNavigating = true
        if (turnInstructions.isEmpty()) {
            turnInstructions = repository.loadTurnInstructions(track.id)
        }
        if (simplifiedRoute.isEmpty()) {
            simplifiedRoute = track.waypoints.map { LatLngPoint(it.lat, it.lng) }
        }
        if (segmentDistances.isEmpty()) {
            segmentDistances = track.waypoints.zipWithNext { a, b ->
                Haversine.distance(LatLngPoint(a.lat, a.lng), LatLngPoint(b.lat, b.lng))
            }
        }
    }

    /**
     * 停止导航。
     */
    fun stopNavigation() {
        isNavigating = false
        _routeState.value = RouteState.ON_ROUTE
        offRouteCount = 0
        _navState.value = GpxNavState()
    }

    /**
     * 每次 GPS 位置更新时调用。
     */
    fun onGpsUpdate(location: GpsLocation) {
        if (!isNavigating || simplifiedRoute.size < 2) return

        try {
            // ① + ② GPS 吸附到路线 + 计算沿线进度
            val gpsPos = LatLngPoint(location.lat, location.lng)
            val (snappedPoint, distanceAlongRoute) = SnapToRoute.snapWithProgress(
                gpsPos, simplifiedRoute, segmentDistances
            )

            // ③ 吸附置信度：距路线越近越高，50m 为 0%，0m 为 100%
            val snapDist = Haversine.distance(gpsPos, snappedPoint)
            val confidence = (1f - (snapDist / SnapToRoute.OFF_ROUTE_THRESHOLD_M).toFloat()).coerceIn(0f, 1f)

            // ④ 查下一个转向点（Room 预计算表）
            val activeDistance = if (navDirection == NavDirection.TO_END) {
                distanceAlongRoute
            } else {
                track.totalDistanceM - distanceAlongRoute
            }
            val nextTurn = turnInstructions.firstOrNull { it.distanceFromStartM > activeDistance }

            // ⑤ 计算方位角
            val bearing = if (nextTurn != null) {
                BearingCalcHelper.bearing(snappedPoint, nextTurn.location)
            } else {
                val lastWp = track.waypoints.lastOrNull()
                if (lastWp != null) BearingCalcHelper.bearing(snappedPoint, LatLngPoint(lastWp.lat, lastWp.lng))
                else location.bearing.toDouble()
            }

            // ⑥ 剩余距离 + 高程感知 ETA
            val remainDist = track.totalDistanceM - distanceAlongRoute
            val progressPercent = ((distanceAlongRoute / track.totalDistanceM) * 100).toFloat().coerceIn(0f, 100f)
            // 根据剩余路段的高程特征调整预估速度
            val effectiveSpeedMs = computeEffectiveSpeed(location, remainDist)
            val etaSeconds = if (effectiveSpeedMs > 0) (remainDist / effectiveSpeedMs).toLong() else 0L

            // ⑦ 到达分级（参照腾讯 SDK onWillArriveDestination）
            val arrivalLevel = when {
                remainDist <= 10  -> ArrivalLevel.ARRIVED
                remainDist <= 50  -> ArrivalLevel.ARRIVING
                remainDist <= 100 -> ArrivalLevel.NEAR
                remainDist <= 500 -> ArrivalLevel.APPROACHING
                else              -> ArrivalLevel.NORMAL
            }

            // ⑧ GPS 插值数据
            prevGpsLat = currGpsLat; prevGpsLng = currGpsLng; prevGpsTimeMs = currGpsTimeMs
            currGpsLat = location.lat; currGpsLng = location.lng; currGpsTimeMs = location.time
            // 首次 GPS 更新：前一点等于当前点
            if (prevGpsTimeMs == 0L) {
                prevGpsLat = currGpsLat; prevGpsLng = currGpsLng; prevGpsTimeMs = currGpsTimeMs
            }

            // ⑨ 清除偏航（回到路线内）
            if (_routeState.value != RouteState.ON_ROUTE) {
                val minDist = findMinDistanceToRoute(location)
                if (minDist < ON_ROUTE_RECOVER_M) {
                    _routeState.value = RouteState.ON_ROUTE
                    offRouteCount = 0
                }
            }

            // ⑩ 更新状态
            _navState.value = GpxNavState(
                snappedPosition = snappedPoint,
                rawGpsPosition = gpsPos,
                bearing = bearing,
                distanceToNextTurnM = if (nextTurn != null) Haversine.distance(snappedPoint, nextTurn.location) else 0.0,
                nextTurn = nextTurn,
                remainingDistanceM = remainDist.coerceAtLeast(0.0),
                totalDistanceM = track.totalDistanceM,
                progressPercent = progressPercent,
                estimatedTimeRemaining = etaSeconds,
                isOffRoute = _routeState.value != RouteState.ON_ROUTE,
                offRouteDistanceM = if (_routeState.value != RouteState.ON_ROUTE) findMinDistanceToRoute(location) else 0.0,
                snapConfidence = confidence,
                arrivalLevel = arrivalLevel,
                interpolatedPosition = snappedPoint,
                gpsSignalStatus = gpsSignalStatus
            )

        } catch (e: OffRouteException) {
            // 偏航处理
            offRouteCount++
            if (offRouteCount >= OFF_ROUTE_CONFIRM_COUNT) {
                _routeState.value = RouteState.OFF_ROUTE_CONFIRMED
                // 每 3 秒尝试重新吸附
                val now = System.currentTimeMillis()
                if (now - lastReSnapTimeMs >= RE_SNAP_INTERVAL_MS) {
                    lastReSnapTimeMs = now
                    // 重新吸附由下次 onGpsUpdate 的 SnapToRoute.snap() 自动处理
                }
            } else if (offRouteCount >= 1) {
                _routeState.value = RouteState.OFF_ROUTE_WARNING
            }
            _navState.value = _navState.value.copy(
                isOffRoute = true,
                offRouteDistanceM = e.distanceM,
                snapConfidence = (1f - (e.distanceM / SnapToRoute.OFF_ROUTE_THRESHOLD_M).toFloat()).coerceIn(0f, 1f)
            )
        }
    }

    /** 查找 GPS 位置到路线的最近距离 */
    private fun findMinDistanceToRoute(location: GpsLocation): Double {
        var minDist = Double.MAX_VALUE
        val pos = LatLngPoint(location.lat, location.lng)
        for (wp in simplifiedRoute) {
            val dist = Haversine.distance(pos, wp)
            if (dist < minDist) minDist = dist
        }
        return minDist
    }

    /**
     * 高程感知有效速度。
     * 根据剩余路段的高程特征调整预估速度：
     * - 累计爬升多 → 减速（上坡费力）
     * - 累计下降多 → 加速（下坡轻松）
     * - 平坦 → 默认速度
     */
    private fun computeEffectiveSpeed(location: GpsLocation, remainDist: Double): Double {
        val baseSpeedMs = if (location.speed > 0.5f) location.speed.toDouble()
        else (activityType.defaultSpeedKmh / 3.6)

        // 根据剩余距离比例估算剩余爬升/下降
        val progress = 1.0 - (remainDist / track.totalDistanceM.coerceAtLeast(1.0))
        val remainGain = remainingElevationGainM * (1.0 - progress)
        val remainLoss = remainingElevationLossM * (1.0 - progress)

        // 爬升比例和下降比例
        val gainRatio = (remainGain / remainDist.coerceAtLeast(1.0)).coerceIn(0.0, 0.5)
        val lossRatio = (remainLoss / remainDist.coerceAtLeast(1.0)).coerceIn(0.0, 0.5)

        // 调整因子：上坡×0.6~1.0, 下坡×1.0~1.3
        val climbFactor = 1.0 - gainRatio * 0.8    // 0.6 ~ 1.0
        val descentFactor = 1.0 + lossRatio * 0.6  // 1.0 ~ 1.3
        val factor = (climbFactor * descentFactor).coerceIn(0.5, 1.5)

        return baseSpeedMs * factor
    }

    /** 更新 GPS 信号强度（由 UI 层调用，传入 Location.accuracy） */
    fun updateGpsSignal(accuracy: Float) {
        val now = System.currentTimeMillis()
        lastGpsUpdateTimeMs = now
        val newStatus = when {
            accuracy < 3f  -> GpsSignalStatus.EXCELLENT
            accuracy < 8f  -> GpsSignalStatus.GOOD
            accuracy < 15f -> GpsSignalStatus.NORMAL
            accuracy < 30f -> GpsSignalStatus.WEAK
            else           -> GpsSignalStatus.LOST
        }
        if (newStatus == GpsSignalStatus.WEAK || newStatus == GpsSignalStatus.LOST) {
            if (gpsWeakStartTimeMs == 0L) gpsWeakStartTimeMs = now
        } else {
            gpsWeakStartTimeMs = 0L
        }
        if (newStatus != gpsSignalStatus) {
            Log.d("Hiking", "[GPS] signal changed: $gpsSignalStatus → $newStatus (accuracy=${"%.1f".format(accuracy)}m)")
            gpsSignalStatus = newStatus
            _navState.value = _navState.value.copy(gpsSignalStatus = newStatus)
        }
    }

    /** 检查长时间无 GPS 更新（由 UI 层定时调用） */
    fun checkGpsTimeout() {
        if (lastGpsUpdateTimeMs == 0L) return
        val elapsed = System.currentTimeMillis() - lastGpsUpdateTimeMs
        if (elapsed > 10_000L && gpsSignalStatus != GpsSignalStatus.LOST) {
            Log.w("Hiking", "[GPS] timeout: ${elapsed}ms since last fix → LOST")
            gpsSignalStatus = GpsSignalStatus.LOST
            _navState.value = _navState.value.copy(gpsSignalStatus = GpsSignalStatus.LOST)
        }
    }

    /** 计算插值位置（供 UI 层 200ms 平滑） */
    fun getInterpolatedPosition(): LatLngPoint {
        if (prevGpsTimeMs == 0L || currGpsTimeMs == prevGpsTimeMs) {
            return LatLngPoint(currGpsLat, currGpsLng)
        }
        val now = System.currentTimeMillis()
        val elapsed = now - currGpsTimeMs
        val interval = currGpsTimeMs - prevGpsTimeMs
        if (interval <= 0) return LatLngPoint(currGpsLat, currGpsLng)

        val fraction = (elapsed.toDouble() / interval).coerceIn(0.0, 1.0)
        return LatLngPoint(
            lat = prevGpsLat + (currGpsLat - prevGpsLat) * fraction,
            lng = prevGpsLng + (currGpsLng - prevGpsLng) * fraction
        )
    }

    fun isOffRoute(): Boolean = _routeState.value != RouteState.ON_ROUTE
    fun getNextTurn(): TurnInstruction? = _navState.value.nextTurn
    fun getRemainingDistanceM(): Double = _navState.value.remainingDistanceM
    fun getETA(): Long = _navState.value.estimatedTimeRemaining

    /** 模拟导航专用：直接设置吸附位置 */
    fun setSimulatedGpsPosition(lat: Double, lng: Double) {
        onGpsUpdate(
            GpsLocation(
                lat = lat,
                lng = lng,
                speed = (activityType.defaultSpeedKmh / 3.6).toFloat(),
                bearing = 0f,
                accuracy = 3f,
                time = System.currentTimeMillis()
            )
        )
    }

    enum class NavDirection { TO_START, TO_END }
}

/**
 * 导航引擎内部状态。
 */
data class GpxNavState(
    val snappedPosition: LatLngPoint = LatLngPoint(0.0, 0.0),
    val rawGpsPosition: LatLngPoint = LatLngPoint(0.0, 0.0),
    val bearing: Double = 0.0,
    val distanceToNextTurnM: Double = 0.0,
    val nextTurn: TurnInstruction? = null,
    val remainingDistanceM: Double = 0.0,
    val totalDistanceM: Double = 0.0,
    val progressPercent: Float = 0f,
    val estimatedTimeRemaining: Long = 0L,
    val isOffRoute: Boolean = false,
    val offRouteDistanceM: Double = 0.0,
    /** 吸附置信度：0.0=50m偏离, 1.0=完美匹配 */
    val snapConfidence: Float = 1f,
    /** 到达分级 */
    val arrivalLevel: ArrivalLevel = ArrivalLevel.NORMAL,
    /** 插值后当前位置（UI 平滑用，200ms 更新） */
    val interpolatedPosition: LatLngPoint = LatLngPoint(0.0, 0.0),
    /** GPS 信号强度 */
    val gpsSignalStatus: GpsSignalStatus = GpsSignalStatus.GOOD
)

/** 到达分级（参照腾讯 SDK onWillArriveDestination） */
enum class ArrivalLevel { NORMAL, APPROACHING, NEAR, ARRIVING, ARRIVED }

/** GPS 信号强度（参照腾讯 SDK NavGpsStatusInfo.ChangeStatus） */
enum class GpsSignalStatus {
    /** <3m 精度，信号极佳 */
    EXCELLENT,
    /** 3-8m，正常户外 */
    GOOD,
    /** 8-15m，部分遮挡 */
    NORMAL,
    /** 15-30m，信号弱 */
    WEAK,
    /** >30m 或超过 10s 无更新 */
    LOST
}

/** 偏航状态枚举 */
enum class RouteState { ON_ROUTE, OFF_ROUTE_WARNING, OFF_ROUTE_CONFIRMED }

/** 方位角计算 — 引擎内联使用（避免引入完整 BearingCalc 依赖） */
private object BearingCalcHelper {
    fun bearing(from: LatLngPoint, to: LatLngPoint): Double {
        val dLon = Math.toRadians(to.lng - from.lng)
        val lat1 = Math.toRadians(from.lat)
        val lat2 = Math.toRadians(to.lat)
        val y = kotlin.math.sin(dLon) * kotlin.math.cos(lat2)
        val x = kotlin.math.cos(lat1) * kotlin.math.sin(lat2) - kotlin.math.sin(lat1) * kotlin.math.cos(lat2) * kotlin.math.cos(dLon)
        return (Math.toDegrees(kotlin.math.atan2(y, x)) + 360) % 360
    }
}
