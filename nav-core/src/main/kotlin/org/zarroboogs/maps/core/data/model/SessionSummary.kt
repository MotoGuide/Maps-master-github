package org.zarroboogs.maps.core.data.model

data class SessionSummary(
    val sessionId: Short = 0,
    val totalDistanceM: Int = 0,
    val totalTimeS: Int = 0,
    val maxSpeedKmh: Int = 0,
    val avgSpeedKmh: Int = 0,
    val startTimeMs: Long = 0L,
    val endTimeMs: Long = 0L
)
