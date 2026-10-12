package org.zarroboogs.maps.core.hub

import kotlinx.coroutines.flow.StateFlow
import org.zarroboogs.maps.core.data.model.CameraAlarm
import org.zarroboogs.maps.core.data.model.GpsLocation
import org.zarroboogs.maps.core.data.model.NavState

interface NavDataSource {
    val navState: StateFlow<NavState>
    val alarms: StateFlow<CameraAlarm>
    val location: StateFlow<GpsLocation>
    val sessionState: StateFlow<SessionState>

    fun start()
    fun stop()
}
