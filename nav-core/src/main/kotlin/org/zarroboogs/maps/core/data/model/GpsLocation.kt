package org.zarroboogs.maps.core.data.model

data class GpsLocation(
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val speed: Float = 0f,
    val bearing: Float = 0f,
    val accuracy: Float = 0f,
    val time: Long = 0L
)
