package org.zarroboogs.maps.hiking.bearing

import org.junit.Assert.*
import org.junit.Test
import org.zarroboogs.maps.hiking.model.GpxWaypoint
import org.zarroboogs.maps.hiking.model.TurnDirection

/**
 * TurnDetector 单元测试 — 验证转向点检测正确性。
 */
class TurnDetectorTest {

    private val detector = TurnDetector(
        turnThresholdDegrees = 30.0,
        minTurnDistanceM = 50.0,
        smoothWindowSize = 3
    )

    @Test
    fun `空轨迹 — 无转向点`() {
        val result = detector.detectTurns(emptyList())
        assertTrue("空轨迹应返回空列表", result.isEmpty())
    }

    @Test
    fun `轨迹太短 — 无转向点`() {
        val waypoints = (0..5).map { i ->
            GpxWaypoint(index = i, lat = 30.0 + i * 0.001, lng = 120.0, distanceFromStartM = i * 100.0)
        }
        val result = detector.detectTurns(waypoints)
        assertTrue("轨迹不够长应返回空列表", result.isEmpty())
    }

    @Test
    fun `直线轨迹 — 无转向点`() {
        val waypoints = (0..20).map { i ->
            GpxWaypoint(
                index = i,
                lat = 30.0 + i * 0.001,   // 朝正北
                lng = 120.0,
                distanceFromStartM = i * 100.0
            )
        }
        val result = detector.detectTurns(waypoints)
        assertTrue("直线应无转向点", result.isEmpty())
    }

    @Test
    fun `90 度右转 — 应检测到右转`() {
        val waypoints = buildRightAngleTrack(turnAt = 15)
        val result = detector.detectTurns(waypoints)
        assertTrue("应有转向点", result.isNotEmpty())
        assertEquals("应为右转", TurnDirection.RIGHT, result.first().direction)
    }

    @Test
    fun `90 度左转 — 应检测到左转`() {
        val waypoints = buildLeftAngleTrack(turnAt = 15)
        val result = detector.detectTurns(waypoints)
        assertTrue("应有转向点", result.isNotEmpty())
        assertEquals("应为左转", TurnDirection.LEFT, result.first().direction)
    }

    @Test
    fun `180 度掉头 — 应检测到 U_TURN`() {
        val waypoints = buildUTurnTrack(turnAt = 15)
        val result = detector.detectTurns(waypoints)
        assertTrue("应有转向点", result.isNotEmpty())
        assertEquals("应为掉头", TurnDirection.U_TURN, result.first().direction)
    }

    @Test
    fun `小角度弯曲 — 不触发转向`() {
        val waypoints = buildSmallBendTrack()
        val result = detector.detectTurns(waypoints)
        assertTrue("15°弯曲不应触发转向", result.isEmpty())
    }

    @Test
    fun `两个转向点间距过近 — 仅保留第一个`() {
        val waypoints = buildDoubleTurnCloseTrack()
        val result = detector.detectTurns(waypoints)
        assertEquals("应只有 1 个转向点", 1, result.size)
    }

    // ===== 辅助方法：构建测试轨迹 =====

    /** 构建向右 90° 转弯的轨迹 */
    private fun buildRightAngleTrack(turnAt: Int): List<GpxWaypoint> {
        val points = mutableListOf<GpxWaypoint>()
        for (i in 0..turnAt + 5) {
            val lat = if (i <= turnAt) 30.0 + i * 0.001 else 30.0 + turnAt * 0.001  // 先朝北
            val lng = if (i <= turnAt) 120.0 else 120.0 + (i - turnAt) * 0.001       // 再朝东
            points.add(GpxWaypoint(index = i, lat = lat, lng = lng, distanceFromStartM = i * 100.0))
        }
        return points
    }

    /** 构建向左 90° 转弯的轨迹 */
    private fun buildLeftAngleTrack(turnAt: Int): List<GpxWaypoint> {
        val points = mutableListOf<GpxWaypoint>()
        for (i in 0..turnAt + 5) {
            val lat = if (i <= turnAt) 30.0 + i * 0.001 else 30.0 + turnAt * 0.001  // 先朝北
            val lng = if (i <= turnAt) 120.0 else 120.0 - (i - turnAt) * 0.001       // 再朝西
            points.add(GpxWaypoint(index = i, lat = lat, lng = lng, distanceFromStartM = i * 100.0))
        }
        return points
    }

    /** 构建 180° 掉头的轨迹 */
    private fun buildUTurnTrack(turnAt: Int): List<GpxWaypoint> {
        val points = mutableListOf<GpxWaypoint>()
        for (i in 0..turnAt + 5) {
            val lat = if (i <= turnAt) 30.0 + i * 0.001 else 30.0 + (turnAt * 0.001) - (i - turnAt) * 0.001  // 朝北 → 朝南
            val lng = 120.0
            points.add(GpxWaypoint(index = i, lat = lat, lng = lng, distanceFromStartM = i * 100.0))
        }
        return points
    }

    /** 构建小角度弯曲（15°）的轨迹 — 不应触发转向 */
    private fun buildSmallBendTrack(): List<GpxWaypoint> {
        val points = mutableListOf<GpxWaypoint>()
        for (i in 0..20) {
            val lat = 30.0 + i * 0.001
            val lng = if (i < 10) 120.0 else 120.0 + (i - 10) * 0.0002  // 微小的东偏
            points.add(GpxWaypoint(index = i, lat = lat, lng = lng, distanceFromStartM = i * 100.0))
        }
        return points
    }

    /** 构建两个很近的转向点 — 仅保留第一个 */
    private fun buildDoubleTurnCloseTrack(): List<GpxWaypoint> {
        val points = mutableListOf<GpxWaypoint>()
        // 先朝北
        for (i in 0..10) {
            points.add(GpxWaypoint(index = i, lat = 30.0 + i * 0.001, lng = 120.0, distanceFromStartM = i * 100.0))
        }
        // 第一个右转 90°（距离 1000m）
        val base = 1100.0
        for (i in 0..5) {
            points.add(GpxWaypoint(index = 11 + i, lat = 30.01, lng = 120.0 + i * 0.001, distanceFromStartM = base + i * 100.0))
        }
        // 第二个左转（距离仅 30m 后 — 应被过滤）
        for (i in 0..5) {
            points.add(GpxWaypoint(index = 17 + i, lat = 30.01 - i * 0.001, lng = 120.006, distanceFromStartM = base + 600 + i * 30.0))
        }
        return points
    }
}
