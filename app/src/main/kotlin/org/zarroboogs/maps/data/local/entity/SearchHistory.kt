package org.zarroboogs.maps.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "search_history")
data class SearchHistory @JvmOverloads constructor(
    @PrimaryKey(autoGenerate = true)
    var id: Long = 0L,

    var name: String = "",

    var address: String? = null,

    var latitude: Double = 0.0,

    var longitude: Double = 0.0,

    @ColumnInfo(name = "searched_at")
    var searchedAt: Long = System.currentTimeMillis()
)
