package org.zarroboogs.maps.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import org.zarroboogs.maps.data.local.entity.FuelRecord

@Dao
interface FuelRecordDao {

    @Query("SELECT * FROM fuel_records ORDER BY date DESC")
    fun loadAll(): List<FuelRecord>

    @Query("SELECT * FROM fuel_records WHERE profile_name = :profileName ORDER BY date DESC")
    fun loadByProfile(profileName: String): List<FuelRecord>

    @Query("SELECT * FROM fuel_records WHERE profile_name = :profileName ORDER BY date ASC")
    fun loadByProfileAsc(profileName: String): List<FuelRecord>

    @Insert
    fun insert(record: FuelRecord): Long

    @androidx.room.Update
    fun update(record: FuelRecord)

    @Delete
    fun delete(record: FuelRecord)
}
