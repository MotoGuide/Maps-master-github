package org.zarroboogs.maps.hub

/**
 * BLE 健康遥测环形缓冲区（最近 1000 条），内存中维护不写文件。
 * 崩溃时由 CrashHandler dump 到 crash 日志。
 */
class BleTelemetryRing(capacity: Int = 1000) {

    data class Entry(
        val timestampMs: Long = System.currentTimeMillis(),
        val writeLatencyMs: Int = 0,
        val queueDepth: Int = 0,
        val pushIntervalActualMs: Long = 0,
        val consecutiveFailures: Int = 0,
        val timeoutCount: Int = 0
    )

    private val buffer = Array<Entry?>(capacity) { null }
    private var writeIndex = 0
    private var count = 0

    @Synchronized
    fun record(latencyMs: Int, queueDepth: Int, intervalMs: Long, failures: Int, timeoutCount: Int = 0) {
        buffer[writeIndex] = Entry(
            timestampMs = System.currentTimeMillis(),
            writeLatencyMs = latencyMs,
            queueDepth = queueDepth,
            pushIntervalActualMs = intervalMs,
            consecutiveFailures = failures,
            timeoutCount = timeoutCount
        )
        writeIndex = (writeIndex + 1) % buffer.size
        if (count < buffer.size) count++
    }

    @Synchronized
    fun dump(): String {
        if (count == 0) return "BLE Telemetry: (empty)\n"

        val sb = StringBuilder()
        sb.appendLine("===== BLE Telemetry (last $count entries) =====")
        sb.appendLine("ts,latency_ms,queue,interval_ms,failures,timeouts")

        val start = if (count < buffer.size) 0 else writeIndex
        for (i in 0 until count) {
            val idx = (start + i) % buffer.size
            val e = buffer[idx] ?: continue
            sb.appendLine("${e.timestampMs},${e.writeLatencyMs},${e.queueDepth},${e.pushIntervalActualMs},${e.consecutiveFailures},${e.timeoutCount}")
        }

        // 汇总统计
        val entries = (0 until count).mapNotNull { buffer[(start + it) % buffer.size] }
        if (entries.isNotEmpty()) {
            val sortedLatency = entries.map { it.writeLatencyMs }.sorted()
            val sortedQueue = entries.map { it.queueDepth }.sorted()
            sb.appendLine("---")
            sb.appendLine("latency_ms: avg=${avg(sortedLatency)} p50=${pct(sortedLatency,50)} p95=${pct(sortedLatency,95)} p99=${pct(sortedLatency,99)} max=${sortedLatency.lastOrNull()}")
            sb.appendLine("queue:      avg=${avg(sortedQueue)} p95=${pct(sortedQueue,95)} max=${sortedQueue.lastOrNull()}")
            val failCount = entries.count { it.consecutiveFailures > 0 }
            if (failCount > 0) sb.appendLine("failures:   $failCount entries with consecutive failures")
            val lastTimeout = entries.lastOrNull()?.timeoutCount ?: 0
            if (lastTimeout > 0) sb.appendLine("timeouts:   $lastTimeout total (GATT callback lost)")
        }

        return sb.toString()
    }

    fun isNotEmpty(): Boolean = count > 0

    private fun avg(vals: List<Int>) = if (vals.isEmpty()) 0 else vals.sum().toDouble() / vals.size
    private fun pct(sorted: List<Int>, p: Int): Int {
        if (sorted.isEmpty()) return 0
        val idx = (sorted.size * p / 100).coerceAtMost(sorted.size - 1)
        return sorted[idx]
    }
}
