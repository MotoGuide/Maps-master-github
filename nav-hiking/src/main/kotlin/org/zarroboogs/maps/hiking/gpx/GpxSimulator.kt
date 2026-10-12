package org.zarroboogs.maps.hiking.gpx

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.zarroboogs.maps.hiking.model.GpxWaypoint

/**
 * GPX 轨迹模拟播放器 — 按时间顺序回放 GPX 航点。
 *
 * 将航点序列以指定倍速逐一注入 GpxNavigationEngine.onGpsUpdate()，
 * 模拟真实 GPS 信号，实现室内全链路测试。
 *
 * 默认 1x 速度 = 4 km/h（普通步行速度），叠加倍速乘数。
 *
 * @property waypoints GPX 航点列表（按 index 升序）
 * @property engine 导航引擎
 * @property scope 协程作用域
 */
class GpxSimulator(
    private val waypoints: List<GpxWaypoint>,
    private val engine: GpxNavigationEngine,
    private val scope: CoroutineScope
) {
    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _currentIndex = MutableStateFlow(0)
    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()

    private val _speedMultiplier = MutableStateFlow(1f)
    val speedMultiplier: StateFlow<Float> = _speedMultiplier.asStateFlow()

    /** 进度百分比 */
    val progressPercent: Float
        get() = if (waypoints.isNotEmpty()) {
            (_currentIndex.value.toFloat() / waypoints.size * 100)
        } else 0f

    /** 航点总数（供外部 UI 使用） */
    val waypointCount: Int get() = waypoints.size

    /** 按索引获取航点（供地图标记更新），超出范围返回 null */
    fun getWaypoint(index: Int): GpxWaypoint? = waypoints.getOrNull(index)

    private var simulationJob: Job? = null
    private var isPaused = false

    /** 点与点之间的基准间隔（毫秒），1x=4km/h 对应约 1s 一点 */
    private val baseIntervalMs: Long = 1000L

    /**
     * 开始模拟回放。
     * @param fromIndex 从第几个航点开始（默认 0 = 起点）
     */
    fun start(fromIndex: Int = 0) {
        if (waypoints.isEmpty()) return
        val startIdx = fromIndex.coerceIn(0, waypoints.size - 1)
        _currentIndex.value = startIdx
        _isRunning.value = true
        isPaused = false

        // 在 IO 线程运行，避免阻塞 UI
        simulationJob = scope.launch(Dispatchers.IO) {
            for (i in startIdx until waypoints.size) {
                if (!isActive || !_isRunning.value) break

                val wp = waypoints[i]
                engine.setSimulatedGpsPosition(wp.lat, wp.lng)
                _currentIndex.value = i

                // 根据倍速计算延迟：间隔 / 倍速
                val delayMs = (baseIntervalMs / _speedMultiplier.value).toLong().coerceAtLeast(50)
                delay(delayMs)
            }
            _isRunning.value = false
            _currentIndex.value = waypoints.lastIndex
        }
    }

    /** 暂停 */
    fun pause() {
        isPaused = true
        simulationJob?.cancel()
        _isRunning.value = false
    }

    /** 继续 */
    fun resume() {
        isPaused = false
        start(_currentIndex.value)
    }

    /** 停止 */
    fun stop() {
        simulationJob?.cancel()
        _isRunning.value = false
        isPaused = false
    }

    /** 跳到指定位置 */
    fun seekTo(index: Int) {
        val wasRunning = _isRunning.value
        pause()
        val targetIdx = index.coerceIn(0, waypoints.lastIndex)
        _currentIndex.value = targetIdx
        val wp = waypoints[targetIdx]
        engine.setSimulatedGpsPosition(wp.lat, wp.lng)
        if (wasRunning) resume()
    }

    /** 设置播放倍速 */
    fun setSpeed(multiplier: Float) {
        _speedMultiplier.value = multiplier.coerceIn(0.5f, 20f)
        // 如果正在运行，重启以应用新倍速
        if (_isRunning.value && !isPaused) {
            val savedIndex = _currentIndex.value
            simulationJob?.cancel()
            start(savedIndex)
        }
    }

    /** 跳到下一个转向点之后 */
    fun skipToNextTurn() {
        val nextTurn = engine.getNextTurn()
        if (nextTurn != null) {
            // 找到转向点之后的第一个航点
            val turnIdx = waypoints.indexOfFirst {
                kotlin.math.abs(it.distanceFromStartM - nextTurn.distanceFromStartM) < 1.0
            }
            if (turnIdx >= 0) {
                seekTo((turnIdx + 3).coerceAtMost(waypoints.lastIndex))
            }
        }
    }
}
