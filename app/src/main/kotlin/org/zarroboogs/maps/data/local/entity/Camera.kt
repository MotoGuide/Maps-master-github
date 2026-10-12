package org.zarroboogs.maps.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity

import androidx.room.PrimaryKey

@Entity(tableName = "cameras")
data class Camera @JvmOverloads constructor(
    @PrimaryKey(autoGenerate = true)
    var id: Long = 0L,

    var name: String = "",

    var address: String? = null,

    var latitude: Double? = null,

    @ColumnInfo(name = "longtitude")
    var longtitude: Double? = null,

    var direction: String? = null,

    var note: String? = null,

    var city: String? = null
)
