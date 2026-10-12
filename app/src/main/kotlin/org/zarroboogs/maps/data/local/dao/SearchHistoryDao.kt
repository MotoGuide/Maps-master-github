package org.zarroboogs.maps.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import org.zarroboogs.maps.data.local.entity.SearchHistory

@Dao
interface SearchHistoryDao {

    @Query("SELECT * FROM search_history ORDER BY searched_at DESC LIMIT 20")
    fun loadRecent(): List<SearchHistory>

    @Query("SELECT * FROM search_history ORDER BY searched_at DESC")
    fun loadAll(): List<SearchHistory>

    @Insert
    fun insert(history: SearchHistory): Long

    @Delete
    fun delete(history: SearchHistory)

    @Query("DELETE FROM search_history")
    fun deleteAll()

    @Query("SELECT COUNT(*) FROM search_history")
    fun count(): Int

    @Query("DELETE FROM search_history WHERE id NOT IN (SELECT id FROM search_history ORDER BY searched_at DESC LIMIT 20)")
    fun trimTo20()
}
