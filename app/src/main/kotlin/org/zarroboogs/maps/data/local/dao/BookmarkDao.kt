package org.zarroboogs.maps.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import org.zarroboogs.maps.data.local.entity.Bookmark

@Dao
interface BookmarkDao {

    @Query("SELECT * FROM bookmarks")
    fun loadAll(): List<Bookmark>

    @Insert
    fun insert(bookmark: Bookmark): Long

    @Delete
    fun delete(bookmark: Bookmark)
}
