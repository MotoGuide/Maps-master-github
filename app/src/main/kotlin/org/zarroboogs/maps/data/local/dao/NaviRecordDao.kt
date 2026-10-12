package org.zarroboogs.maps.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import org.zarroboogs.maps.data.local.entity.NaviRecord

@Dao
interface NaviRecordDao {

    @Query("SELECT * FROM navi_records ORDER BY date DESC")
    fun loadAll(): List<NaviRecord>

    @Insert
    fun insert(record: NaviRecord): Long

    @Delete
    fun delete(record: NaviRecord)
}
