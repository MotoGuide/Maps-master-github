package org.zarroboogs.maps.data.model

data class VehicleSubType(
    val label: String,
    val routingType: String
) {
    override fun toString(): String = label
}
