package org.zarroboogs.maps.hiking.gpx

import kotlinx.coroutines.flow.Flow
import org.zarroboogs.maps.core.road.model.LatLngPoint
import org.zarroboogs.maps.hiking.bearing.TurnDetector
import org.zarroboogs.maps.hiking.model.GpxTrack
import org.zarroboogs.maps.hiking.model.GpxWaypoint
import org.zarroboogs.maps.hiking.model.TrackSource
import org.zarroboogs.maps.hiking.model.TurnInstruction
import org.zarroboogs.maps.hiking.room.*

/**
 * GPX 数据仓库 — 封装 Room 操作 + TurnDetector 预计算。
 *
 * 导入 GPX 轨迹时：
 * 1. 存入 gpx_tracks 表
 * 2. 存入 gpx_waypoints 表
 * 3. 调用 TurnDetector 预计算转向点 → 存入 turn_points 表
 */
class GpxRepository(private val dao: GpxDao) {

    private val turnDetector = TurnDetector()

    /** 获取所有轨迹（响应式） */
    fun getAllTracks(): Flow<List<GpxTrackEntity>> = dao.getAllTracks()

    /** 按 ID 获取轨迹 */
    suspend fun getTrackById(trackId: Long): GpxTrackEntity? = dao.getTrackById(trackId)

    /**
     * 导入 GPX 轨迹（含预计算转向点）。
     *
     * @param track 已解析的 GPX 轨迹
     * @param source 轨迹来源（IMPORTED / RECORDED）
     * @return 数据库生成的轨迹 ID
     */
    suspend fun importTrack(track: GpxTrack, source: TrackSource): Long {
        // 1. 插入轨迹记录
        val trackId = dao.insertTrack(
            GpxTrackEntity(
                name = track.name,
                description = track.description,
                source = source.name,
                totalDistanceM = track.totalDistanceM,
                totalElevationGainM = track.totalElevationGainM,
                waypointCount = track.waypointCount,
                createdAt = track.createdAt,
                updatedAt = track.updatedAt
            )
        )

        // 2. 批量插入航点
        if (track.waypoints.isNotEmpty()) {
            val entities = track.waypoints.map { wp ->
                GpxWaypointEntity(
                    trackId = trackId,
                    index = wp.index,
                    lat = wp.lat,
                    lng = wp.lng,
                    elevation = wp.elevation,
                    timestamp = wp.timestamp,
                    distanceFromStartM = wp.distanceFromStartM
                )
            }
            dao.insertWaypoints(entities)
        }

        // 3. 预计算转向点并批量插入
        if (track.waypoints.size >= 3) {
            val turnInstructions = turnDetector.detectTurns(track.waypoints)
            if (turnInstructions.isNotEmpty()) {
                val turnEntities = turnInstructions.map { ti ->
                    TurnPointEntity(
                        trackId = trackId,
                        direction = ti.direction.name,
                        lat = ti.location.lat,
                        lng = ti.location.lng,
                        distanceFromStartM = ti.distanceFromStartM,
                        turnAngle = ti.turnAngle,
                        nextSegmentBearing = ti.nextSegmentBearing
                    )
                }
                dao.insertTurnPoints(turnEntities)
            }
        }

        return trackId
    }

    /**
     * 完整加载轨迹（含航点和转向点）。
     */
    suspend fun loadFullTrack(trackId: Long): GpxTrack {
        val entity = dao.getTrackById(trackId)
            ?: throw IllegalArgumentException("轨迹不存在: id=$trackId")

        val waypointEntities = dao.getWaypointsByTrackId(trackId)
        val waypoints = waypointEntities.map { wp ->
            GpxWaypoint(
                id = wp.id,
                trackId = wp.trackId,
                index = wp.index,
                lat = wp.lat,
                lng = wp.lng,
                elevation = wp.elevation,
                timestamp = wp.timestamp,
                distanceFromStartM = wp.distanceFromStartM
            )
        }

        return GpxTrack(
            id = entity.id,
            name = entity.name,
            description = entity.description,
            source = TrackSource.valueOf(entity.source),
            waypoints = waypoints,
            totalDistanceM = entity.totalDistanceM,
            totalElevationGainM = entity.totalElevationGainM,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt
        )
    }

    /**
     * 加载轨迹的预计算转向点。
     */
    suspend fun loadTurnInstructions(trackId: Long): List<TurnInstruction> {
        return dao.getTurnPointsByTrackId(trackId).map { tp ->
            TurnInstruction(
                direction = org.zarroboogs.maps.hiking.model.TurnDirection.valueOf(tp.direction),
                location = LatLngPoint(tp.lat, tp.lng),
                distanceFromStartM = tp.distanceFromStartM,
                turnAngle = tp.turnAngle,
                nextSegmentBearing = tp.nextSegmentBearing
            )
        }
    }

    /**
     * 查找下一个转向点（导航引擎核心查询）。
     */
    suspend fun getNextTurnPoint(trackId: Long, currentDistanceM: Double): TurnInstruction? {
        val entity = dao.getNextTurnPoint(trackId, currentDistanceM) ?: return null
        return TurnInstruction(
            direction = org.zarroboogs.maps.hiking.model.TurnDirection.valueOf(entity.direction),
            location = LatLngPoint(entity.lat, entity.lng),
            distanceFromStartM = entity.distanceFromStartM,
            turnAngle = entity.turnAngle,
            nextSegmentBearing = entity.nextSegmentBearing
        )
    }

    /** 删除轨迹及其所有关联数据 */
    suspend fun deleteTrack(trackId: Long) {
        val entity = dao.getTrackById(trackId) ?: return
        dao.deleteTrack(entity)
    }
}
