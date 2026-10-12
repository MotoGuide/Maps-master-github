package org.zarroboogs.maps.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity

import androidx.room.PrimaryKey

@Entity(tableName = "navi_records")
data class NaviRecord @JvmOverloads constructor(
    @PrimaryKey(autoGenerate = true)
    var id: Long = 0L,

    @ColumnInfo(name = "start_name")
    var startName: String? = null,

    @ColumnInfo(name = "end_name")
    var endName: String? = null,

    @ColumnInfo(name = "start_lat")
    var startLat: Double? = null,

    @ColumnInfo(name = "start_lng")
    var startLng: Double? = null,

    @ColumnInfo(name = "end_lat")
    var endLat: Double? = null,

    @ColumnInfo(name = "end_lng")
    var endLng: Double? = null,

    var distance: Int = 0,

    var duration: Int = 0,

    @ColumnInfo(name = "max_speed")
    var maxSpeed: Int = 0,

    var date: Long = 0L
)
