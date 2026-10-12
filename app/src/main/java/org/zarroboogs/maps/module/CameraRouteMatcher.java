package org.zarroboogs.maps.module;

import org.zarroboogs.maps.utils.FileLogger;

import com.amap.api.maps.model.LatLng;
import com.amap.api.navi.model.NaviLatLng;

import org.zarroboogs.maps.MapsApplication;
import org.zarroboogs.maps.data.local.entity.Camera;
import org.zarroboogs.maps.beans.CameraNoteType;

import java.util.ArrayList;
import java.util.List;

/**
 * 摄像头路线匹配器。
 * 导航模式下，基于路线 polyline 匹配本地 DB 中的摩托车摄像头，
 * 仅推送在路线前方一定范围内且距路线足够近的摄像头。
 *
 * 参照高德 Demo BaseCustomActivity.onCalculateRouteSuccess 中
 * naviPath.getCoordList() 获取路线坐标。
 */
public class CameraRouteMatcher {

    private static final String TAG = "CameraRouteMatcher";
    private static final double ROUTE_TOLERANCE_M = 50;   // 摄像头距路线容差 (m)
    private static final double LOOK_AHEAD_M = 500;        // 前方提前量 (m)

    private List<LatLng> mRoutePoints;         // 路线 polyline 坐标
    private double[] mCumulativeDist;           // 每个路线点的累计距离 (m)
    private double mRouteTotalLength;           // 路线总长 (m)
    private List<Camera> mCameraCache;        // 摄像头列表缓存，避免每次 findNextCamera loadAll

    private int mMatchedCameraDistance = 0;     // 最近匹配的摄像头前方距离 (m)

    // 日志去重：上次打印的 camera id + 距离
    private long mLastLoggedCameraId = -1;
    private int mLastLoggedDistance = -1;
    private static final int LOG_DEDUP_DISTANCE_THRESHOLD = 5;  // 距离变化 <5m 不重复打日志

    // 路线重算期间锁定旧缓存
    private boolean mRecalculating;
    private List<LatLng> mOldRoutePoints;
    private double[] mOldCumulativeDist;
    private double mOldRouteTotalLength;

    /** 导航开始时缓存路线（SDK 无关，接受通用 LatLng） */
    public void setRouteFromGeneric(List<LatLng> points) {
        if (points == null || points.isEmpty()) return;
        mRoutePoints = new ArrayList<>(points);
        mCumulativeDist = buildCumulativeDist(mRoutePoints);
        mRouteTotalLength = mCumulativeDist.length > 0
                ? mCumulativeDist[mCumulativeDist.length - 1] : 0;
        mCameraCache = MapsApplication.getDatabase().cameraDao().loadAll();
        FileLogger.d(TAG, "route set from generic: " + points.size() + " points, " + (int) mRouteTotalLength + "m, cameras=" + (mCameraCache != null ? mCameraCache.size() : 0));
    }

    /** 导航开始时缓存路线（AMap 专用） */
    public void setRoute(List<NaviLatLng> coordList) {
        if (coordList == null || coordList.isEmpty()) return;
        List<LatLng> points = new ArrayList<>(coordList.size());
        for (NaviLatLng nll : coordList) {
            points.add(new LatLng(nll.getLatitude(), nll.getLongitude()));
        }
        if (mRecalculating) {
            mOldRoutePoints = points;
            mOldCumulativeDist = buildCumulativeDist(points);
            mOldRouteTotalLength = mOldCumulativeDist.length > 0
                    ? mOldCumulativeDist[mOldCumulativeDist.length - 1] : 0;
        } else {
            mRoutePoints = points;
            mCumulativeDist = buildCumulativeDist(points);
            mRouteTotalLength = mCumulativeDist.length > 0
                    ? mCumulativeDist[mCumulativeDist.length - 1] : 0;
            mCameraCache = MapsApplication.getDatabase().cameraDao().loadAll();
            FileLogger.d(TAG, "route set: " + points.size() + " points, " + (int) mRouteTotalLength + "m, cameras=" + (mCameraCache != null ? mCameraCache.size() : 0));
        }
    }

    /** 路线重算开始，保持旧缓存 */
    public void onRecalculating() {
        if (mRoutePoints != null) {
            mOldRoutePoints = mRoutePoints;
            mOldCumulativeDist = mCumulativeDist;
            mOldRouteTotalLength = mRouteTotalLength;
        }
        mRecalculating = true;
        FileLogger.d(TAG, "recalculating, keeping old cache");
    }

    /** 路线重算完成，切换新缓存 */
    public void onRouteRecalculated(List<NaviLatLng> newCoordList) {
        mRecalculating = false;
        setRoute(newCoordList);
        FileLogger.d(TAG, "recalculated: " + (mRoutePoints != null ? mRoutePoints.size() : 0) + " points");
    }

    /**
     * GPS 更新时调用。返回前方最近摩托车摄像头的 alarm 类型，
     * 无匹配返回 0x00。
     */
    public int findNextCamera(LatLng gpsPos) {
        List<LatLng> pts = mRecalculating ? mOldRoutePoints : mRoutePoints;
        double[] cumDist = mRecalculating ? mOldCumulativeDist : mCumulativeDist;
        if (pts == null || pts.isEmpty() || cumDist == null) return 0;

        // 1. 计算 GPS 在路线上的最近点 + 累计距离
        double gpsCumDist = projectOnRoute(gpsPos, pts, cumDist);

        // 2. 使用缓存摄像头列表（首次或清空后回退加载）
        List<Camera> cameras = mCameraCache;
        if (cameras == null) {
            cameras = MapsApplication.getDatabase().cameraDao().loadAll();
            mCameraCache = cameras;
        }
        if (cameras == null || cameras.isEmpty()) return 0;

        // 3. 遍历找最近的前方摄像头
        Camera best = null;
        double bestDist = Double.MAX_VALUE;
        for (Camera cam : cameras) {
            if (cam.getLatitude() == null || cam.getLongtitude() == null) continue;
            LatLng camPos = new LatLng(cam.getLatitude(), cam.getLongtitude());

            // 摄像头到路线的最近距离
            double distToRoute = minDistanceToRoute(camPos, pts);
            if (distToRoute > ROUTE_TOLERANCE_M) continue;

            // 摄像头在路线上的累计距离
            double camCumDist = projectOnRoute(camPos, pts, cumDist);

            // 需在 GPS 前方 0~500m
            double ahead = camCumDist - gpsCumDist;
            if (ahead < 0 || ahead > LOOK_AHEAD_M) continue;

            if (ahead < bestDist) {
                bestDist = ahead;
                best = cam;
            }
        }

        if (best != null) {
            int alarmType = noteToAlarm(best.getNote());
            mMatchedCameraDistance = (int) bestDist;
            Long id = best.getId();
            long cameraId = id != null ? id : -1;
            int distChange = Math.abs(mMatchedCameraDistance - mLastLoggedDistance);
            if (cameraId != mLastLoggedCameraId || distChange >= LOG_DEDUP_DISTANCE_THRESHOLD) {
                FileLogger.d(TAG, "camera match: " + best.getName() + " ahead=" + mMatchedCameraDistance + "m alarm=" + String.format("0x%02X", alarmType));
                mLastLoggedCameraId = cameraId;
                mLastLoggedDistance = mMatchedCameraDistance;
            }
            return alarmType;
        }
        mMatchedCameraDistance = 0;
        mLastLoggedCameraId = -1;
        mLastLoggedDistance = -1;
        return 0x00;
    }

    // ---------- Geometry helpers ----------

    /** 点到折线最近投影 + 累计距离 */
    private double projectOnRoute(LatLng p, List<LatLng> pts, double[] cumDist) {
        if (pts.size() == 1) {
            return 0;
        }
        double minDist = Double.MAX_VALUE;
        double bestCumulative = 0;
        for (int i = 0; i < pts.size() - 1; i++) {
            LatLng a = pts.get(i);
            LatLng b = pts.get(i + 1);
            double segLen = distanceM(a, b);
            if (segLen < 0.1) continue;

            // 投影参数 t（0~1）
            double t = projectOnSegment(p, a, b);
            t = Math.max(0, Math.min(1, t));

            LatLng proj = new LatLng(
                    a.latitude + t * (b.latitude - a.latitude),
                    a.longitude + t * (b.longitude - a.longitude));
            double d = distanceM(p, proj);
            if (d < minDist) {
                minDist = d;
                bestCumulative = cumDist[i] + t * segLen;
            }
        }
        return bestCumulative;
    }

    /** 点到折线的最近距离 */
    private double minDistanceToRoute(LatLng p, List<LatLng> pts) {
        double minD = Double.MAX_VALUE;
        for (int i = 0; i < pts.size() - 1; i++) {
            double d = pointToSegmentDist(p, pts.get(i), pts.get(i + 1));
            if (d < minD) minD = d;
        }
        return minD;
    }

    private double pointToSegmentDist(LatLng p, LatLng a, LatLng b) {
        double t = projectOnSegment(p, a, b);
        t = Math.max(0, Math.min(1, t));
        LatLng proj = new LatLng(
                a.latitude + t * (b.latitude - a.latitude),
                a.longitude + t * (b.longitude - a.longitude));
        return distanceM(p, proj);
    }

    /** 点 p 在线段 ab 上的投影参数 */
    private double projectOnSegment(LatLng p, LatLng a, LatLng b) {
        double dx = b.longitude - a.longitude;
        double dy = b.latitude - a.latitude;
        if (dx == 0 && dy == 0) return 0;
        return ((p.longitude - a.longitude) * dx + (p.latitude - a.latitude) * dy) / (dx * dx + dy * dy);
    }

    /** Haversine 距离 (m) */
    private double distanceM(LatLng a, LatLng b) {
        double R = 6371000;
        double dLat = Math.toRadians(b.latitude - a.latitude);
        double dLon = Math.toRadians(b.longitude - a.longitude);
        double lat1 = Math.toRadians(a.latitude);
        double lat2 = Math.toRadians(b.latitude);
        double sinDLat = Math.sin(dLat / 2);
        double sinDLon = Math.sin(dLon / 2);
        double a2 = sinDLat * sinDLat + Math.cos(lat1) * Math.cos(lat2) * sinDLon * sinDLon;
        return R * 2 * Math.atan2(Math.sqrt(a2), Math.sqrt(1 - a2));
    }

    /** 构建累计距离数组 */
    private double[] buildCumulativeDist(List<LatLng> pts) {
        double[] cum = new double[pts.size()];
        double total = 0;
        for (int i = 1; i < pts.size(); i++) {
            total += distanceM(pts.get(i - 1), pts.get(i));
            cum[i] = total;
        }
        return cum;
    }

    /** 使摄像头缓存失效，下次 findNextCamera 会重新从 DB 加载 */
    public void invalidateCameraCache() {
        mCameraCache = null;
    }

    /** 最近一次 findNextCamera 匹配到的摄像头前方距离（米），无匹配返回 0 */
    public int getMatchedCameraDistance() {
        return mMatchedCameraDistance;
    }

    /** note 字符串 → alarm 枚举值，参照 6.2.11 映射表 */
    private int noteToAlarm(String note) {
        if (note == null || note.isEmpty()) return 0xFF;
        String n = CameraNoteType.normalize(note);
        if (n.isEmpty()) return 0xFF;
        String[] std = CameraNoteType.STANDARD_VALUES;
        for (int i = 0; i < std.length; i++) {
            if (std[i].equals(n)) return i + 1;  // 0x01 ~ 0x0D
        }
        return 0xFF;
    }
}
