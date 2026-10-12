package org.zarroboogs.maps.data.model

import android.net.Uri

data class Profile(
    val name: String,
    val type: String,
    val subType: VehicleSubType,
    val displacement: Int,
    val plate: String,
    val imageUri: Uri? = null
) {
    /** 仅燃油类型车辆需要油耗统计 */
    fun isFuelType(): Boolean =
        type == "摩托车" || subType.label.contains("燃油")
}
