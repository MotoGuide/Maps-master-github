package org.zarroboogs.maps.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity

import androidx.room.PrimaryKey

@Entity(tableName = "bookmarks")
data class Bookmark @JvmOverloads constructor(
    @PrimaryKey(autoGenerate = true)
    var id: Long = 0L,

    var name: String = "",

    var address: String? = null,

    var latitude: Double = 0.0,

    var longitude: Double = 0.0,

    @ColumnInfo(name = "poi_type")
    var poiType: String? = null,

    @ColumnInfo(name = "created_at")
    var createdAt: Long = 0L
)
