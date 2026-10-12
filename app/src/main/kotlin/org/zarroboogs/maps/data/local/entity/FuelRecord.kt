package org.zarroboogs.maps.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity

import androidx.room.PrimaryKey

@Entity(tableName = "fuel_records")
data class FuelRecord @JvmOverloads constructor(
    @PrimaryKey(autoGenerate = true)
    var id: Long = 0L,

    @ColumnInfo(name = "profile_name")
    var profileName: String = "",

    var mileage: Double = 0.0,

    var liters: Double = 0.0,

    @ColumnInfo(name = "total_paid")
    var totalPaid: Double = 0.0,

    @ColumnInfo(name = "unit_price")
    var unitPrice: Double = 0.0,

    var discount: Double = 0.0,

    var date: Long = 0L,

    var note: String? = null
)
