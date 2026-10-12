package org.zarroboogs.maps.hiking.gpx

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.zarroboogs.maps.core.data.model.GpsLocation
import org.zarroboogs.maps.core.road.Haversine
import org.zarroboogs.maps.core.road.model.LatLngPoint
import org.zarroboogs.maps.hiking.activity.OutdoorActivityType
import org.zarroboogs.maps.hiking.model.GpxTrack
import org.zarroboogs.maps.hiking.model.GpxWaypoint
import org.zarroboogs.maps.hiking.model.TrackSource

/**
 * GPX 轨迹录制器。
 *
 * 状态机：IDLE → RECORDING → PAUSED → RECORDING → ... → IDLE
 *
 * 录制策略：
 * - 默认 1 秒采集一次 GPS 位置
 * - 最小距离过滤：前后两点距离 < 最小距离阈值（默认 5m）不记录，防止 GPS 漂移产生锯齿路线
 * - 停止时自动计算累计距离和爬升
 *
 * @property locationClient 定位提供者（由调用方注入）
 * @property repository GPX 数据仓库
 * @property scope 协程作用域
 * @property activityType 活动类型（影响录制参数）
 */
class GpxRecorder(
    private val locationClient: GpsLocationProvider,
    private val repository: GpxRepository,
    private val scope: CoroutineScope,
    private val activityType: OutdoorActivityType = OutdoorActivityType.HIKING
) {
    private val _state = MutableStateFlow(RecorderState.IDLE)
    val state: StateFlow<RecorderState> = _state.asStateFlow()

    private val _currentWaypoints = MutableStateFlow<List<GpxWaypoint>>(emptyList())
    val currentWaypoints: StateFlow<List<GpxWaypoint>> = _currentWaypoints.asStateFlow()

    private val _recordingDistanceM = MutableStateFlow(0.0)
    val recordingDistanceM: StateFlow<Double> = _recordingDistanceM.asStateFlow()

    private val _recordingDuration = MutableStateFlow(0L)
    val recordingDuration: StateFlow<Long> = _recordingDuration.asStateFlow()

    private var trackName = ""
    private var recordingJob: Job? = null
    private var durationJob: Job? = null
    private var startTimeMs = 0L

    /** 采集间隔（毫秒），默认 1 秒 */
    var collectIntervalMs: Long = 1000L

    /** 最小记录距离（米）：前后两点距离小于此值不记录，防止 GPS 漂移 */
    var minRecordDistanceM: Double = 5.0

    /**
     * 开始录制。
     * @param trackName 轨迹名称
     */
    fun start(trackName: String) {
        if (_state.value != RecorderState.IDLE) return
        this.trackName = trackName
        _state.value = RecorderState.RECORDING
        _currentWaypoints.value = emptyList()
        _recordingDistanceM.value = 0.0
        startTimeMs = System.currentTimeMillis()

        // 协程定时采集 GPS
        recordingJob = scope.launch {
            while (isActive) {
                val location = locationClient.getCurrentLocation()
                if (location != null) {
                    recordPoint(location)
                }
                delay(collectIntervalMs)
            }
        }

        // 协程更新录制时长
        durationJob = scope.launch {
            while (isActive) {
                _recordingDuration.value = System.currentTimeMillis() - startTimeMs
                delay(1000)
            }
        }
    }

    /** 暂停录制 */
    fun pause() {
        if (_state.value != RecorderState.RECORDING) return
        _state.value = RecorderState.PAUSED
        recordingJob?.cancel()
    }

    /** 继续录制 */
    fun resume() {
        if (_state.value != RecorderState.PAUSED) return
        _state.value = RecorderState.RECORDING

        recordingJob = scope.launch {
            while (isActive) {
                val location = locationClient.getCurrentLocation()
                if (location != null) {
                    recordPoint(location)
                }
                delay(collectIntervalMs)
            }
        }
    }

    /**
     * 停止录制并保存 GPX 轨迹。
     * @return 保存后的 GpxTrack（含数据库 ID）
     */
    suspend fun stop(): GpxTrack {
        recordingJob?.cancel()
        durationJob?.cancel()
        _state.value = RecorderState.IDLE

        val waypoints = _currentWaypoints.value
        if (waypoints.isEmpty()) {
            _state.value = RecorderState.IDLE
            throw IllegalStateException("没有录制到任何航点")
        }

        // 计算总距离和累计爬升
        var totalDist = 0.0
        var totalGain = 0.0
        val computedWaypoints = waypoints.mapIndexed { i, wp ->
            if (i > 0) {
                val prev = waypoints[i - 1]
                val segDist = Haversine.distance(
                    LatLngPoint(prev.lat, prev.lng),
                    LatLngPoint(wp.lat, wp.lng)
                )
                totalDist += segDist
                val prevEle = prev.elevation
                val currEle = wp.elevation
                if (prevEle != null && currEle != null && currEle > prevEle) {
                    totalGain += (currEle - prevEle)
                }
            }
            wp.copy(distanceFromStartM = totalDist)
        }

        val track = GpxTrack(
            name = trackName,
            source = TrackSource.RECORDED,
            waypoints = computedWaypoints,
            totalDistanceM = totalDist,
            totalElevationGainM = totalGain
        )

        // 存入数据库（含预计算转向点）
        val trackId = repository.importTrack(track, TrackSource.RECORDED)
        return track.copy(id = trackId)
    }

    /** 记录一个航点（含最小距离过滤） */
    private fun recordPoint(location: GpsLocation) {
        val wp = GpxWaypoint(
            index = _currentWaypoints.value.size,
            lat = location.lat,
            lng = location.lng,
            elevation = null,      // TODO: 从定位回调获取海拔
            timestamp = location.time
        )

        // 最小距离过滤
        val lastWp = _currentWaypoints.value.lastOrNull()
        if (lastWp != null) {
            val dist = Haversine.distance(
                LatLngPoint(lastWp.lat, lastWp.lng),
                LatLngPoint(wp.lat, wp.lng)
            )
            if (dist < minRecordDistanceM) return  // 太近，不记录
        }

        _currentWaypoints.value = _currentWaypoints.value + wp
        // 实时更新录制距离
        if (_currentWaypoints.value.size >= 2) {
            val sz = _currentWaypoints.value.size
            val a = _currentWaypoints.value[sz - 2]
            val b = _currentWaypoints.value[sz - 1]
            _recordingDistanceM.value += Haversine.distance(
                LatLngPoint(a.lat, a.lng),
                LatLngPoint(b.lat, b.lng)
            )
        }
    }

    enum class RecorderState { IDLE, RECORDING, PAUSED }
}

/**
 * GPS 位置提供者接口（由调用方注入，解耦 Android LocationManager）。
 */
interface GpsLocationProvider {
    suspend fun getCurrentLocation(): GpsLocation?
}
