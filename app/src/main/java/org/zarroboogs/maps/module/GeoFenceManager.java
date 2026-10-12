package org.zarroboogs.maps.module;

import android.content.Context;
import org.zarroboogs.maps.utils.FileLogger;
import org.zarroboogs.maps.utils.SettingUtils;

import com.amap.api.maps.model.LatLng;

import org.zarroboogs.maps.beans.GeoFenceInfo;

import java.util.ArrayList;

/**
 * 摩托车摄像头地理围栏管理器。
 * 纯计算模块：接收外部坐标，计算是否进入/离开围栏。
 * 不持有自己的 GPS 客户端，由 CruiseModeManager 喂入坐标。
 */
public class GeoFenceManager {

    private static final String TAG = "MotoNav";

    private ArrayList<GeoFenceInfo> mGeoFences = new ArrayList<>();
    private Context mContext;

    private OnGeoFenceListener mOnGenFenceListener;

    private LatLng mCurrentLatLng;
    private CameraVoiceManager mVoiceManager;
    private int mCheckCount;
    private int mUpdateCount;
    private ArrayList<Long> triggeredFences = new ArrayList<>();
    private volatile boolean mActive;

    public GeoFenceManager(Context context) {
        this.mContext = context;
        FileLogger.w(TAG, "GeoFenceManager created (no GPS client)");
        mVoiceManager = CameraVoiceManager.getInstance(mContext);
        mVoiceManager.init();
    }

    /** 重置围栏状态，每次巡航启动时调用，避免 mWasInZone 残留 */
    public void reset() {
        mWasInZone = false;
        triggeredFences.clear();
        FileLogger.w(TAG, "GeoFenceManager reset: wasInZone=false, triggeredFences cleared");
    }

    /** 外部喂入 GPS 坐标，触发围栏检查 */
    public void onLocationUpdate(LatLng latLng) {
        if (latLng == null) return;
        if (mCurrentLatLng != null
                && mCurrentLatLng.latitude == latLng.latitude
                && mCurrentLatLng.longitude == latLng.longitude) {
            return;
        }
        mCurrentLatLng = latLng;
        mUpdateCount++;
        if (mUpdateCount % 10 == 0) {
            FileLogger.w(TAG, "onLocationUpdate #" + mUpdateCount + " lat=" + latLng.latitude + " lng=" + latLng.longitude + " fences=" + mGeoFences.size());
        }
        checkGeoFences();
    }

    public void requestLocation() {
    }

    public interface OnGeoFenceListener {
        public void onGeoFenceIn(LatLng currentLatLng, LatLng geoFenceLatLng);
        public void onGeoFenceOut();
    }

    /** 摄像头告警回调：巡航模式下进入/离开围栏时触发 */
    public interface OnCameraAlarmListener {
        void onCameraAlarmChanged(boolean inZone);
    }

    private OnCameraAlarmListener mCameraAlarmListener;
    private boolean mWasInZone;

    public void setOnCameraAlarmListener(OnCameraAlarmListener listener) {
        mCameraAlarmListener = listener;
    }

    public void setActive(boolean active) {
        mActive = active;
        if (!active) {
            mWasInZone = false;
            triggeredFences.clear();
        }
    }

    public void addAllGeoFenceAler(ArrayList<GeoFenceInfo> infos) {
        mGeoFences.addAll(infos);
        triggeredFences.clear();
        // 新围栏数据就绪后立即基于当前位置检查一次（修复首次启动巡航时数据后到的竞态）
        if (!infos.isEmpty() && mCurrentLatLng != null && mActive) {
            checkGeoFences();
        }
    }

    public void removeAllGeoFenceAlert() {
        mGeoFences.clear();
        triggeredFences.clear();
    }

    public void setOnGeoFenceListener(OnGeoFenceListener listener){
        this.mOnGenFenceListener = listener;
    }

    private void checkGeoFences() {
        if (!mActive) return;
        mCheckCount++;
        if (mCurrentLatLng == null || mGeoFences.isEmpty()) {
            if (mCheckCount % 15 == 0) {
                FileLogger.w(TAG, "GeoFence heartbeat: " + mGeoFences.size() + " fences, latLng=" + (mCurrentLatLng != null ? "set" : "null") + " checkCount=" + mCheckCount);
            }
            return;
        }

        boolean inZone = false;
        double radius = SettingUtils.readGeofenceDistance();
        for (GeoFenceInfo info : mGeoFences) {
            LatLng fenceLatLng = info.getLatLng();
            double distance = calculateDistance(mCurrentLatLng, fenceLatLng);

            if (distance <= radius) {
                long fenceId = info.getId();
                inZone = true;
                if (!triggeredFences.contains(fenceId)) {
                    FileLogger.w(TAG, "[FENCE_IN] cameraId=" + fenceId + " distance=" + (int)distance + "m radius=" + (int)radius + "m");
                    FileLogger.d(TAG, "GeoFence triggered: cameraId=" + fenceId + " distance=" + (int)distance + "m");
                    mVoiceManager.playCruiseAlert((int) radius);
                    if (mOnGenFenceListener != null) {
                        mOnGenFenceListener.onGeoFenceIn(mCurrentLatLng, fenceLatLng);
                    }
                    triggeredFences.add(fenceId);
                }
            } else {
                triggeredFences.remove(info.getId());
            }
        }

        // 摄像头告警状态变化 → 通知监听器（巡航模式推送 BLE alarm）
        if (inZone != mWasInZone) {
            FileLogger.w(TAG, "[FENCE_ZONE] " + mWasInZone + "→" + inZone + " alarmListener=" + (mCameraAlarmListener != null ? "set" : "null"));
        }
        if (inZone != mWasInZone && mCameraAlarmListener != null) {
            FileLogger.w(TAG, "checkGeoFences zone " + mWasInZone + " -> " + inZone + " (firing listener)");
            mCameraAlarmListener.onCameraAlarmChanged(inZone);
        }
        mWasInZone = inZone;
    }

    private double calculateDistance(LatLng latLng1, LatLng latLng2) {
        double earthRadius = 6371000;
        double lat1 = Math.toRadians(latLng1.latitude);
        double lon1 = Math.toRadians(latLng1.longitude);
        double lat2 = Math.toRadians(latLng2.latitude);
        double lon2 = Math.toRadians(latLng2.longitude);

        double dLat = lat2 - lat1;
        double dLon = lon2 - lon1;

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(lat1) * Math.cos(lat2) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return earthRadius * c;
    }

    public void destroy() {
        // 无 GPS 客户端需要清理
    }
}
