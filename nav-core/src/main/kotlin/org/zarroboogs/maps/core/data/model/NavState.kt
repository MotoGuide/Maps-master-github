package org.zarroboogs.maps.core.data.model

data class NavState @JvmOverloads constructor(
    val progress: Int = 0,
    val directionIcon: Int = 0,
    val nextDistM: Int = 0,
    val remainDistM: Long = 0,
    val speedKmh: Int = 0,
    val roadName: String = "",
    val limitSpeedKmh: Int = 0,
    val iconSetId: Int = 0   // 0=高德(amap), 1=腾讯(tencent)
)
