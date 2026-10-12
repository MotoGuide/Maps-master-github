package org.zarroboogs.maps.hiking.bearing

import org.junit.Assert.assertEquals
import org.junit.Test
import org.zarroboogs.maps.core.road.model.LatLngPoint

/**
 * BearingCalc 单元测试 — 验证方位角计算正确性。
 */
class BearingCalcTest {

    companion object {
        private const val DELTA = 0.5  // 允许 0.5° 误差
        // 杭州西湖附近坐标
        private val HANGZHOU = LatLngPoint(30.2495, 120.1400)
    }

    @Test
    fun `bearing 正北 — 同经度，纬度增大`() {
        val from = LatLngPoint(30.0, 120.0)
        val to = LatLngPoint(30.1, 120.0)
        val result = BearingCalc.bearing(from, to)
        assertEquals("正北应为 0°", 0.0, result, DELTA)
    }

    @Test
    fun `bearing 正东 — 同纬度，经度增大`() {
        val from = LatLngPoint(30.0, 120.0)
        val to = LatLngPoint(30.0, 120.1)
        val result = BearingCalc.bearing(from, to)
        assertEquals("正东应为 90°", 90.0, result, DELTA)
    }

    @Test
    fun `bearing 正南 — 同经度，纬度减小`() {
        val from = LatLngPoint(30.1, 120.0)
        val to = LatLngPoint(30.0, 120.0)
        val result = BearingCalc.bearing(from, to)
        assertEquals("正南应为 180°", 180.0, result, DELTA)
    }

    @Test
    fun `bearing 正西 — 同纬度，经度减小`() {
        val from = LatLngPoint(30.0, 120.1)
        val to = LatLngPoint(30.0, 120.0)
        val result = BearingCalc.bearing(from, to)
        assertEquals("正西应为 270°", 270.0, result, DELTA)
    }

    @Test
    fun `bearingDelta 右转 90 度 — 正北到正东`() {
        val delta = BearingCalc.bearingDelta(0.0, 90.0)
        assertEquals("右转 90°", 90.0, delta, DELTA)
    }

    @Test
    fun `bearingDelta 左转 90 度 — 正北到正西`() {
        val delta = BearingCalc.bearingDelta(0.0, 270.0)
        assertEquals("左转 90°（跨 0°）", -90.0, delta, DELTA)
    }

    @Test
    fun `bearingDelta 跨 0 度边界`() {
        val delta = BearingCalc.bearingDelta(350.0, 10.0)
        assertEquals("右转 20°", 20.0, delta, DELTA)
    }

    @Test
    fun `averageBearing 多方位平均`() {
        val avg = BearingCalc.averageBearing(listOf(350.0, 10.0, 0.0))
        assertEquals("平均值应接近 0°", 0.0, avg, DELTA * 2)
    }

    @Test
    fun `averageBearing 90 度方向`() {
        val avg = BearingCalc.averageBearing(listOf(85.0, 90.0, 95.0))
        assertEquals("平均值应接近 90°", 90.0, avg, DELTA)
    }
}
