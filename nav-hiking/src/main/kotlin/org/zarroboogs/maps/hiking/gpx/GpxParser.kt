@file:Suppress("unused")

package org.zarroboogs.maps.hiking.gpx

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import org.zarroboogs.maps.core.road.Haversine
import org.zarroboogs.maps.core.road.model.LatLngPoint
import org.zarroboogs.maps.hiking.model.GpxTrack
import org.zarroboogs.maps.hiking.model.GpxWaypoint
import org.zarroboogs.maps.hiking.model.TrackSource
import java.io.InputStream
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

// ============================================================
//  公开枚举
// ============================================================

enum class TrackFileType(val extension: String, val displayName: String) {
    GPX("gpx", "GPX 轨迹文件"),
    KML("kml", "KML 轨迹文件（两步路等）")
}

enum class CoordSystem(val displayName: String) {
    GCJ02("GCJ-02 火星坐标系"),
    WGS84("WGS-84 GPS原始坐标"),
    BD09("BD-09 百度坐标系")
}

class TrackParseException(message: String) : Exception(message)

// ============================================================
//  双格式解析器 — 不用 nextText()，避免 XmlPullParser 异常
// ============================================================

class GpxParser {

    fun parse(inputStream: InputStream, fileType: TrackFileType, coordSystem: CoordSystem): GpxTrack {
        return when (fileType) {
            TrackFileType.GPX -> parseCommon(inputStream, coordSystem, isGpx = true)
            TrackFileType.KML -> parseCommon(inputStream, coordSystem, isGpx = false)
        }
    }

    fun detectType(fileName: String): TrackFileType {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return when (ext) { "kml" -> TrackFileType.KML; else -> TrackFileType.GPX }
    }

    // ===== 统一解析引擎（不用 nextText）=====

    private fun parseCommon(input: InputStream, coordSystem: CoordSystem, isGpx: Boolean): GpxTrack {
        val factory = XmlPullParserFactory.newInstance()
        val parser = factory.newPullParser()
        parser.setInput(input, "UTF-8")

        val waypoints = mutableListOf<GpxWaypoint>()
        var trackName = "未命名轨迹"
        var description = ""

        // 状态标志
        var insideTrackName = false
        var insideDesc = false
        var insideTrkSeg = false       // GPX
        var insideCoords = false       // KML
        var depth = 0
        // ★ 关键修复：coordinates 文本可能跨多个 TEXT 事件，需累积
        val coordsBuffer = StringBuilder()

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    depth++
                    val tag = parser.name.lowercase()
                    when {
                        isGpx && tag == "name" && depth <= 3 -> insideTrackName = true
                        !isGpx && tag == "name" && trackName == "未命名轨迹" -> insideTrackName = true
                        tag == "desc" || tag == "description" -> insideDesc = true
                        isGpx && tag == "trkseg" -> insideTrkSeg = true
                        isGpx && tag == "trkpt" && insideTrkSeg -> {
                            val latStr = parser.getAttributeValue(null, "lat")
                            val lonStr = parser.getAttributeValue(null, "lon")
                            if (latStr != null && lonStr != null) {
                                val c = convert(latStr.toDouble(), lonStr.toDouble(), coordSystem)
                                waypoints.add(GpxWaypoint(index = waypoints.size, lat = c.first, lng = c.second))
                            }
                        }
                        // KML: <LineString><coordinates>lng,lat,alt ...</coordinates></LineString>
                        !isGpx && tag == "coordinates" -> {
                            insideCoords = true
                            coordsBuffer.clear()
                        }
                        // KML: <gx:Track><gx:coord>lng lat alt</gx:coord>...  (两步路格式)
                        // 兼容 XmlPullParser 不同实现：有的返回 "coord"，有的返回 "gx:coord"
                        !isGpx && (tag == "coord" || tag.endsWith(":coord")) -> {
                            // 每个 <coord> 标签的文本就是一组坐标
                            insideCoords = true
                            coordsBuffer.clear()
                        }
                    }
                }
                XmlPullParser.TEXT -> {
                    val text = parser.text
                    if (text.isBlank()) { eventType = parser.next(); continue }
                    if (insideTrackName) {
                        trackName = text.trim(); insideTrackName = false
                    } else if (insideDesc) {
                        description = text.trim().take(200); insideDesc = false
                    } else if (insideCoords) {
                        coordsBuffer.append(text)
                    }
                }
                XmlPullParser.END_TAG -> {
                    depth--
                    val tag = parser.name.lowercase()
                    if (isGpx && tag == "trkseg") insideTrkSeg = false
                    if (tag == "name") insideTrackName = false
                    if (tag == "description" || tag == "desc") insideDesc = false
                    // <coordinates> 或 <coord> 结束 → 解析累积的坐标文本
                    if (!isGpx && (tag == "coordinates" || tag == "coord" || tag.endsWith(":coord"))) {
                        insideCoords = false
                        val allText = coordsBuffer.toString().trim()
                        if (allText.isNotEmpty()) {
                            parseCoordinatesText(allText, coordSystem, waypoints)
                        }
                    }
                }
            }
            eventType = parser.next()
        }

        if (waypoints.isEmpty()) {
            val msg = if (isGpx) "GPX 文件不包含有效航点" else "KML 文件不包含有效坐标点，请检查文件内容"
            throw TrackParseException(msg)
        }
        return buildTrack(trackName, description, waypoints)
    }

    /** 解析 KML coordinates 文本（兼容两种格式） */
    private fun parseCoordinatesText(
        text: String, coordSystem: CoordSystem, waypoints: MutableList<GpxWaypoint>
    ) {
        val cleaned = text.replace(Regex("[\\n\\r\\t]+"), " ").replace(Regex("\\s+"), " ").trim()
        if (cleaned.isEmpty()) return

        // 判断格式：含逗号 → "lng,lat,alt" (LineString), 否则 → "lng lat alt" (gx:coord)
        if (cleaned.contains(",")) {
            // 格式1: <coordinates>lng,lat,alt lng,lat,alt ...</coordinates>
            val tuples = cleaned.split(" ")
            for (tuple in tuples) {
                val parts = tuple.trim().split(",")
                if (parts.size < 2) continue
                val lng = parts[0].toDoubleOrNull() ?: continue
                val lat = parts[1].toDoubleOrNull() ?: continue
                val c = convert(lat, lng, coordSystem)
                waypoints.add(GpxWaypoint(index = waypoints.size, lat = c.first, lng = c.second))
            }
        } else {
            // 格式2: <gx:coord>lng lat alt</gx:coord> (两步路格式)
            val parts = cleaned.split(" ")
            if (parts.size >= 2) {
                val lng = parts[0].toDoubleOrNull() ?: return
                val lat = parts[1].toDoubleOrNull() ?: return
                val c = convert(lat, lng, coordSystem)
                waypoints.add(GpxWaypoint(index = waypoints.size, lat = c.first, lng = c.second))
            }
        }
    }

    // ===== 公共构建逻辑 =====

    private fun buildTrack(name: String, desc: String, waypoints: List<GpxWaypoint>): GpxTrack {
        val computed = mutableListOf<GpxWaypoint>()
        var cumulativeDistance = 0.0
        for (i in waypoints.indices) {
            val wp = waypoints[i]
            if (i > 0) {
                val prev = waypoints[i - 1]
                cumulativeDistance += Haversine.distance(
                    LatLngPoint(prev.lat, prev.lng),
                    LatLngPoint(wp.lat, wp.lng)
                )
            }
            computed.add(wp.copy(index = i, distanceFromStartM = cumulativeDistance))
        }
        return GpxTrack(
            name = name, description = desc, source = TrackSource.IMPORTED,
            waypoints = computed, totalDistanceM = cumulativeDistance
        )
    }

    // ===== 坐标转换 =====

    private fun convert(lat: Double, lng: Double, system: CoordSystem): Pair<Double, Double> = when (system) {
        CoordSystem.WGS84 -> wgs84ToGcj02(lat, lng)
        CoordSystem.BD09 -> { val w = bd09ToWgs84(lat, lng); wgs84ToGcj02(w.first, w.second) }
        CoordSystem.GCJ02 -> Pair(lat, lng)
    }

    private fun wgs84ToGcj02(wgLat: Double, wgLng: Double): Pair<Double, Double> {
        if (isOutOfChina(wgLat, wgLng)) return Pair(wgLat, wgLng)
        var dLat = transformLat(wgLng - 105.0, wgLat - 35.0)
        var dLng = transformLng(wgLng - 105.0, wgLat - 35.0)
        val radLat = wgLat / 180.0 * Math.PI
        var magic = sin(radLat)
        magic = 1 - 0.00669342162296594323 * magic * magic
        val sqrtMagic = sqrt(magic)
        dLat = dLat * 180.0 / ((6378245.0 * (1 - 0.00669342162296594323)) / (magic * sqrtMagic) * Math.PI)
        dLng = dLng * 180.0 / (6378245.0 / sqrtMagic * cos(radLat) * Math.PI)
        return Pair(wgLat + dLat, wgLng + dLng)
    }

    private fun bd09ToWgs84(bdLat: Double, bdLng: Double): Pair<Double, Double> {
        val x = bdLng - 0.0065; val y = bdLat - 0.006
        val z = sqrt(x * x + y * y) - 0.00002 * sin(y * Math.PI)
        val theta = kotlin.math.atan2(y, x) - 0.000003 * cos(x * Math.PI)
        return Pair(z * sin(theta), z * cos(theta))
    }

    private fun isOutOfChina(lat: Double, lng: Double): Boolean =
        lng < 72.004 || lng > 137.8347 || lat < 0.8293 || lat > 55.8271

    private fun transformLat(x: Double, y: Double): Double {
        var ret = -100.0 + 2.0 * x + 3.0 * y + 0.2 * y * y + 0.1 * x * y + 0.2 * sqrt(abs(x))
        ret += (20.0 * sin(6.0 * x * Math.PI) + 20.0 * sin(2.0 * x * Math.PI)) * 2.0 / 3.0
        ret += (20.0 * sin(y * Math.PI) + 40.0 * sin(y / 3.0 * Math.PI)) * 2.0 / 3.0
        ret += (160.0 * sin(y / 12.0 * Math.PI) + 320.0 * sin(y * Math.PI / 30.0)) * 2.0 / 3.0
        return ret
    }

    private fun transformLng(x: Double, y: Double): Double {
        var ret = 300.0 + x + 2.0 * y + 0.1 * x * x + 0.1 * x * y + 0.1 * sqrt(abs(x))
        ret += (20.0 * sin(6.0 * x * Math.PI) + 20.0 * sin(2.0 * x * Math.PI)) * 2.0 / 3.0
        ret += (20.0 * sin(x * Math.PI) + 40.0 * sin(x / 3.0 * Math.PI)) * 2.0 / 3.0
        ret += (150.0 * sin(x / 12.0 * Math.PI) + 300.0 * sin(x / 30.0 * Math.PI)) * 2.0 / 3.0
        return ret
    }
}
