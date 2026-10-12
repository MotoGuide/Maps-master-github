package org.zarroboogs.maps.data.model

data class EspDeviceInfo(
    val deviceName: String = "未知设备",
    val macAddress: String = "",
    val batteryLevel: Int = -1,
    val firmwareVersion: String = "未知",
    val rssi: Int = 0,
    val connectedTime: Long = 0
)
