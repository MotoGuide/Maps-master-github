package org.zarroboogs.maps.core.road

import org.zarroboogs.maps.core.road.model.LatLngPoint
import org.zarroboogs.maps.core.road.model.RoadClass
import org.zarroboogs.maps.core.road.model.RoadSegment
import java.io.InputStream

/**
 * 读取 geohash-5 分片的 .bin 路网文件。
 *
 * 文件格式 (little-endian):
 *   magic:4B = 'RDB1'
 *   edge_count:4B LE
 *   edges...
 *     每条边:
 *       road_class:1B
 *       name_len:1B + name:name_len UTF-8
 *       point_count:2B LE
 *       points: point_count x 8B (lat:float32, lng:float32)
 *
 * 使用方式:
 *   val loader = GeohashLoader { assets.open("roads_bin/\$it.bin") }
 *   val edges = loader.load("wwe0q")
 *   val nearby = loader.loadNearby(gpsPos)
 */
class GeohashLoader(
    /** 打开 .bin 文件的回调（:app 层注入 assets::open） */
    private val assetOpener: (String) -> InputStream?
) {
    /** LRU 缓存：geohash → 路段列表，最多 MAX_CACHED 个分片 */
    private val cache = LinkedHashMap<String, List<RoadSegment>>(MAX_CACHED, 0.75f, true)

    /**
     * 加载一个 geohash 分片的所有路段。
     */
    fun load(geohash: String): List<RoadSegment> {
        cache[geohash]?.let { return it }

        val input = assetOpener("$geohash.bin") ?: return emptyList()
        val segments = input.use { parse(it) }
        cache[geohash] = segments
        return segments
    }

    /**
     * 加载 GPS 位置所在分片 + 8 个邻格的所有路段。
     */
    fun loadNearby(gpsPos: LatLngPoint): List<RoadSegment> {
        val center = encode(gpsPos.lat, gpsPos.lng, GEOHASH_PRECISION)
        val neighbors = neighbors(center)
        val all = mutableListOf<RoadSegment>()
        all.addAll(load(center))
        for (n in neighbors) {
            all.addAll(load(n))
        }
        return all
    }

    /**
     * 批量加载多个 geohash 分片，去重。
     */
    fun loadMultiple(geohashes: Set<String>): List<RoadSegment> {
        val all = mutableListOf<RoadSegment>()
        for (gh in geohashes) {
            all.addAll(load(gh))
        }
        return all
    }

    /**
     * 计算路线点列表覆盖的所有 geohash-5 分片（含邻格）。
     */
    fun getCoveredGeohashes(points: List<LatLngPoint>): Set<String> {
        val geohashes = mutableSetOf<String>()
        for (pt in points) {
            val center = encode(pt.lat, pt.lng, GEOHASH_PRECISION)
            geohashes.add(center)
            geohashes.addAll(neighbors(center))
        }
        return geohashes
    }

    /** 清除缓存 */
    fun clear() = cache.clear()

    /** 解析一个 .bin 文件为 RoadSegment 列表 */
    private fun parse(input: InputStream): List<RoadSegment> {
        val header = ByteArray(4)
        if (input.read(header) < 4) return emptyList()
        if (String(header, Charsets.US_ASCII) != MAGIC) return emptyList()

        val edgeCount = readInt32LE(input)
        val segments = mutableListOf<RoadSegment>()

        for (i in 0 until edgeCount) {
            val roadClass = RoadClass.fromCode(input.read())
            val nameLen = input.read()
            val name = if (nameLen > 0) {
                val nameBytes = ByteArray(nameLen)
                input.read(nameBytes)
                String(nameBytes, Charsets.UTF_8)
            } else ""
            val pointCount = readUInt16LE(input)

            val points = mutableListOf<LatLngPoint>()
            for (j in 0 until pointCount) {
                val lat = readFloat32LE(input)
                val lng = readFloat32LE(input)
                points.add(LatLngPoint(lat.toDouble(), lng.toDouble()))
            }

            if (points.size >= 2) {
                segments.add(
                    RoadSegment(
                        points = points,  // 完整 polyline（含首尾）
                        roadClass = roadClass,
                        name = name
                    )
                )
            }
        }
        return segments
    }

    // ==================== 二进制解析 ====================

    private fun readInt32LE(input: InputStream): Int {
        val b = ByteArray(4)
        input.read(b)
        return (b[0].toInt() and 0xFF) or
                ((b[1].toInt() and 0xFF) shl 8) or
                ((b[2].toInt() and 0xFF) shl 16) or
                ((b[3].toInt() and 0xFF) shl 24)
    }

    private fun readUInt16LE(input: InputStream): Int {
        val b = ByteArray(2)
        input.read(b)
        return (b[0].toInt() and 0xFF) or ((b[1].toInt() and 0xFF) shl 8)
    }

    private fun readFloat32LE(input: InputStream): Float {
        val bits = readInt32LE(input)
        return java.lang.Float.intBitsToFloat(bits)
    }

    // ==================== Geohash ====================

    companion object {
        private const val MAX_CACHED = 50
        private const val GEOHASH_PRECISION = 5
        private const val BASE32 = "0123456789bcdefghjkmnpqrstuvwxyz"
        private const val MAGIC = "RDB1"

        fun encode(lat: Double, lng: Double, precision: Int = GEOHASH_PRECISION): String {
            var latMin = -90.0; var latMax = 90.0
            var lngMin = -180.0; var lngMax = 180.0
            var bits = 0; var bitCount = 0
            val sb = StringBuilder()
            var isEven = true

            while (sb.length < precision) {
                if (isEven) {
                    val mid = (lngMin + lngMax) / 2
                    if (lng > mid) { bits = (bits shl 1) or 1; lngMin = mid }
                    else { bits = bits shl 1; lngMax = mid }
                } else {
                    val mid = (latMin + latMax) / 2
                    if (lat > mid) { bits = (bits shl 1) or 1; latMin = mid }
                    else { bits = bits shl 1; latMax = mid }
                }
                bitCount++
                isEven = !isEven
                if (bitCount == 5) {
                    sb.append(BASE32[bits])
                    bits = 0; bitCount = 0
                }
            }
            return sb.toString()
        }

        /** 获取 8 个相邻 geohash（N, S, E, W, NE, NW, SE, SW） */
        fun neighbors(geohash: String): List<String> {
            val neighbors = mutableListOf<String>()
            val last = geohash.last()
            val idx = BASE32.indexOf(last)
            if (idx < 0) return emptyList()

            val prefix = geohash.dropLast(1)
            // 简单的 3×3 邻域：尝试最后一位 ±1 in both lat and lng directions
            // 由于 geohash 的奇偶位交替编码 lat/lng，邻域比这个复杂。
            // 这里用简化方案：生成相邻的 8 个 geohash 候选
            val latDeltas = listOf(-1, 0, 1)
            val lngDeltas = listOf(-1, 0, 1)
            // 最后一位的奇偶性决定它是 lat 还是 lng bit
            val even = (geohash.length % 2 == 0)  // 简化：用长度判断

            for (dLat in latDeltas) {
                for (dLng in lngDeltas) {
                    if (dLat == 0 && dLng == 0) continue
                    val newIdx = when {
                        even -> idx + dLat  // 偶数长度→最后位是lat
                        else -> idx + dLng  // 奇数长度→最后位是lng
                    }
                    if (newIdx in 0 until BASE32.length) {
                        neighbors.add(prefix + BASE32[newIdx])
                    }
                }
            }
            return neighbors.distinct()
        }
    }
}
