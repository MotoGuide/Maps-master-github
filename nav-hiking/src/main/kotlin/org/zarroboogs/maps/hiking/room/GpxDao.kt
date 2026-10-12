package org.zarroboogs.maps.hiking.room

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * GPX 数据访问对象。
 * 注意：Room 2.3.0 + Kotlin 1.9 兼容性限制，不使用 suspend 注解方法。
 * 调用方应在 IO 协程上下文中调用。
 */
@Dao
interface GpxDao {

    // ===== 轨迹操作 =====

    /** 获取所有轨迹，按更新时间降序（Flow 自动在后台线程执行） */
    @Query("SELECT * FROM gpx_tracks ORDER BY updatedAt DESC")
    fun getAllTracks(): Flow<List<GpxTrackEntity>>

    /** 按 ID 获取单条轨迹 */
    @Query("SELECT * FROM gpx_tracks WHERE id = :trackId")
    fun getTrackById(trackId: Long): GpxTrackEntity?

    /** 插入新轨迹，返回自增 ID */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertTrack(track: GpxTrackEntity): Long

    /** 更新轨迹 */
    @Update
    fun updateTrack(track: GpxTrackEntity): Int

    /** 删除轨迹（级联删除关联的 waypoints 和 turn_points） */
    @Delete
    fun deleteTrack(track: GpxTrackEntity): Int

    // ===== 航点操作 =====

    /** 按轨迹 ID 获取所有航点，按 index 升序 */
    @Query("SELECT * FROM gpx_waypoints WHERE trackId = :trackId ORDER BY `index` ASC")
    fun getWaypointsByTrackId(trackId: Long): List<GpxWaypointEntity>

    /** 批量插入航点 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertWaypoints(waypoints: List<GpxWaypointEntity>)

    /** 删除轨迹的所有航点 */
    @Query("DELETE FROM gpx_waypoints WHERE trackId = :trackId")
    fun deleteWaypointsByTrackId(trackId: Long): Int

    // ===== 转向点操作 =====

    /** 按轨迹 ID 获取所有转向点，按距离升序 */
    @Query("SELECT * FROM turn_points WHERE trackId = :trackId ORDER BY distanceFromStartM ASC")
    fun getTurnPointsByTrackId(trackId: Long): List<TurnPointEntity>

    /**
     * 查找"下一个转向点"：距起点的累计距离 > currentDistance 的第一个转向点。
     * 导航引擎核心查询。
     */
    @Query("SELECT * FROM turn_points WHERE trackId = :trackId AND distanceFromStartM > :currentDistance ORDER BY distanceFromStartM ASC LIMIT 1")
    fun getNextTurnPoint(trackId: Long, currentDistance: Double): TurnPointEntity?

    /** 批量插入转向点 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertTurnPoints(turnPoints: List<TurnPointEntity>)

    /** 删除轨迹的所有转向点 */
    @Query("DELETE FROM turn_points WHERE trackId = :trackId")
    fun deleteTurnPointsByTrackId(trackId: Long): Int
}
