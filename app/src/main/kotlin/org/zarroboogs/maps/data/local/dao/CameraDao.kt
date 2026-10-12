package org.zarroboogs.maps.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import org.zarroboogs.maps.data.local.entity.Camera

@Dao
interface CameraDao {

    @Query("SELECT * FROM cameras")
    fun loadAll(): List<Camera>

    @Query("SELECT * FROM cameras WHERE id = :id")
    fun loadById(id: Long): Camera?

    @Query("SELECT COUNT(*) FROM cameras")
    fun count(): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertOrReplace(camera: Camera): Long

    @Insert
    fun insert(camera: Camera): Long

    @Delete
    fun delete(camera: Camera)

    @Query("DELETE FROM cameras WHERE id = :id")
    fun deleteById(id: Long)

    @Query("DELETE FROM cameras")
    fun deleteAll()
}
