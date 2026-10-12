package org.zarroboogs.maps.cruise;

import android.content.Context;
import org.zarroboogs.maps.utils.FileLogger;

import com.amap.api.maps.AMapException;
import com.amap.api.maps.model.LatLng;
import com.amap.api.navi.AMapNavi;
import com.amap.api.navi.AMapNaviListener;
import com.amap.api.navi.AimlessModeListener;
import com.amap.api.navi.ParallelRoadListener;
import com.amap.api.navi.enums.AimLessMode;
import com.amap.api.navi.model.AMapCalcRouteResult;
import com.amap.api.navi.model.AMapLaneInfo;
import com.amap.api.navi.model.AMapModelCross;
import com.amap.api.navi.model.AMapNaviCameraInfo;
import com.amap.api.navi.model.AMapNaviCross;
import com.amap.api.navi.model.AMapNaviLocation;
import com.amap.api.navi.model.AMapNaviRouteNotifyData;
import com.amap.api.navi.model.AMapNaviTrafficFacilityInfo;
import com.amap.api.navi.model.AMapServiceAreaInfo;
import com.amap.api.navi.model.AimLessModeCongestionInfo;
import com.amap.api.navi.model.AimLessModeStat;
import com.amap.api.navi.model.NaviInfo;
import com.amap.api.navi.model.NaviLatLng;

import org.zarroboogs.maps.data.local.entity.Camera;
import org.zarroboogs.maps.MapsApplication;
import org.zarroboogs.maps.module.GeoFenceManager;

import java.util.List;

/**
 * 巡航模式管理器。
 */
public class CruiseModeManager implements AMapNaviListener, AimlessModeListener, ParallelRoadListener {

    private static final String TAG = "MotoNav";

    private static CruiseModeManager sInstance;

    private Context mContext;
    private AMapNavi mAMapNavi;
    private boolean mIsRunning;

    private LatLng mCurrentLatLng;
    private float mCurrentBearing;
    private float mCurrentSpeed;

    private int mCruiseDistance;
    private int mCruiseTime;
    private int mLastLoggedDistance = -1;
    private int mLastLoggedTime = -1;

    private CruiseModeListener mListener;
    private GeoFenceManager mGeoFenceManager;

    public interface CruiseModeListener {
        void onCruiseStarted();
        void onCruiseStopped();
        void onCameraDetected(Camera camera, int distanceM);
        void onLocationChanged(LatLng latLng, float bearing, float speed);
        void onCruiseStatisticsUpdated(int distanceM, int timeS);
    }

    private CruiseModeManager(Context context) {
        mContext = context.getApplicationContext();
    }

    public static CruiseModeManager getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new CruiseModeManager(context);
        }
        return sInstance;
    }

    public static CruiseModeManager getInstance() {
        return sInstance;
    }

    public void setListener(CruiseModeListener listener) {
        mListener = listener;
    }

    public void setGeoFenceManager(GeoFenceManager mgr) {
        mGeoFenceManager = mgr;
    }

    // ======================== 巡航开关 ========================

    public boolean start() {
        FileLogger.w(TAG, "[CALLER] start() called from:\n" + android.util.Log.getStackTraceString(new Exception()));
        if (mIsRunning) {
            FileLogger.d(TAG, "Cruise mode already running");
            return true;
        }

        try {
            mAMapNavi = AMapNavi.getInstance(mContext);
            mAMapNavi.startAimlessMode(AimLessMode.CAMERA_AND_SPECIALROAD_DETECTED);
            mAMapNavi.addAMapNaviListener(this);
            mAMapNavi.addAimlessModeListener(this);
            mAMapNavi.addParallelRoadListener(this);
            mAMapNavi.setUseInnerVoice(true);

            mIsRunning = true;
            FileLogger.w(TAG, "[START] aimlessMode=CAMERA_AND_SPECIALROAD_DETECTED");
            FileLogger.w(TAG, "Cruise mode started");
            if (mListener != null) {
                mListener.onCruiseStarted();
            }
            return true;
        } catch (AMapException e) {
            FileLogger.e(TAG, "Failed to start cruise mode: " + e.getMessage());
            return false;
        } catch (Exception e) {
            FileLogger.e(TAG, "Failed to start cruise mode", e);
            return false;
        }
    }

    public void stop() {
        FileLogger.w(TAG, "[CALLER] stop() called from:\n" + android.util.Log.getStackTraceString(new Exception()));
        if (!mIsRunning) return;

        try {
            if (mAMapNavi != null) {
                mAMapNavi.stopAimlessMode();
                mAMapNavi.removeAMapNaviListener(this);
                mAMapNavi.removeAimlessModeListener(this);
                mAMapNavi.removeParallelRoadListener(this);
                mAMapNavi = null;
            }
        } catch (Exception e) {
            FileLogger.e(TAG, "Error stopping cruise mode", e);
        }

        mIsRunning = false;
        mCruiseDistance = 0;
        mCruiseTime = 0;
        FileLogger.w(TAG, "[STOP]");
        FileLogger.w(TAG, "Cruise mode stopped");
        if (mListener != null) {
            mListener.onCruiseStopped();
        }
    }

    public boolean isRunning() {
        return mIsRunning;
    }

    public LatLng getCurrentLatLng() {
        return mCurrentLatLng;
    }

    public float getCurrentBearing() {
        return mCurrentBearing;
    }

    public float getCurrentSpeed() {
        return mCurrentSpeed;
    }

    public void destroy() {
        stop();
        sInstance = null;
    }

    // ======================== AMapNaviListener ========================

    @Override
    public void onLocationChange(AMapNaviLocation location) {
        if (location != null && location.getCoord() != null) {
            NaviLatLng coord = location.getCoord();
            LatLng latLng = new LatLng(coord.getLatitude(), coord.getLongitude());
            mCurrentLatLng = latLng;
            mCurrentBearing = location.getBearing();
            mCurrentSpeed = location.getSpeed();

            if (mListener != null) {
                mListener.onLocationChanged(latLng, mCurrentBearing, mCurrentSpeed);
            }

            if (mGeoFenceManager != null) {
                mGeoFenceManager.onLocationUpdate(latLng);
            }
        }
    }

    @Override
    public void onUpdateAimlessModeElecCameraInfo(AMapNaviTrafficFacilityInfo[] infos) {
        if (infos == null || infos.length == 0) return;
        FileLogger.w(TAG, "[AIMLESS_CAM] count=" + infos.length + " (no BLE push)");
        FileLogger.d(TAG, "Aimless camera: count=" + infos.length);
    }

    @Override
    public void OnUpdateTrafficFacility(AMapNaviTrafficFacilityInfo info) {
        if (info == null) return;
        FileLogger.w(TAG, "[GAODE_FACILITY] distance=" + info.getDistance() + "m limitSpeed=" + info.getLimitSpeed() + " (no BLE push)");
        FileLogger.d(TAG, "Traffic facility: distance=" + info.getDistance() + "m, limitSpeed=" + info.getLimitSpeed());
    }

    @Override
    public void OnUpdateTrafficFacility(AMapNaviTrafficFacilityInfo[] infos) {
        if (infos == null) return;
        for (AMapNaviTrafficFacilityInfo info : infos) OnUpdateTrafficFacility(info);
    }

    @Override
    public void onUpdateTrafficFacility(AMapNaviTrafficFacilityInfo[] infos) {
        if (infos == null) return;
        for (AMapNaviTrafficFacilityInfo info : infos) {
            FileLogger.d(TAG, "TrafficFacility: coor=(" + info.getCoorX() + "," + info.getCoorY() + "), distance=" + info.getDistance() + "m, limitSpeed=" + info.getLimitSpeed());
        }
    }

    @Override
    public void updateAimlessModeStatistics(AimLessModeStat stat) {
        if (stat != null) {
            mCruiseDistance = stat.getAimlessModeDistance();
            mCruiseTime = stat.getAimlessModeTime();
            if (mCruiseDistance != mLastLoggedDistance || mCruiseTime != mLastLoggedTime) {
                FileLogger.d(TAG, "Cruise statistics: distance=" + mCruiseDistance + "m, time=" + mCruiseTime + "s");
                mLastLoggedDistance = mCruiseDistance;
                mLastLoggedTime = mCruiseTime;
            }
            if (mListener != null) {
                mListener.onCruiseStatisticsUpdated(mCruiseDistance, mCruiseTime);
            }
        }
    }

    @Override
    public void updateAimlessModeCongestionInfo(AimLessModeCongestionInfo info) {
        if (info != null) {
            FileLogger.d(TAG, "Congestion: road=" + info.getRoadName() + ", status=" + info.getCongestionStatus() + ", length=" + info.getLength() + "m");
        }
    }

    @Override public void onInitNaviFailure() {}
    @Override public void onInitNaviSuccess() {}
    @Override public void onStartNavi(int type) {}
    @Override public void onTrafficStatusUpdate() {}
    @Override public void onGetNavigationText(int type, String text) {}
    @Override public void onGetNavigationText(String text) {}
    @Override public void onEndEmulatorNavi() {}
    @Override public void onArriveDestination() {}
    @Override public void onCalculateRouteFailure(int errorCode) {}
    @Override public void onReCalculateRouteForYaw() {}
    @Override public void onReCalculateRouteForTrafficJam() {}
    @Override public void onArrivedWayPoint(int wayID) {}
    @Override public void onGpsOpenStatus(boolean enabled) {}
    @Override public void onGpsSignalWeak(boolean weak) {}
    @Override public void updateCameraInfo(AMapNaviCameraInfo[] cameraInfos) {
        if (cameraInfos != null && cameraInfos.length > 0) {
            FileLogger.w(TAG, "[GAODE_CAM] updateCameraInfo count=" + cameraInfos.length + " (no BLE push)");
        }
    }
    @Override public void updateIntervalCameraInfo(AMapNaviCameraInfo c1, AMapNaviCameraInfo c2, int d) {}
    @Override public void onServiceAreaUpdate(AMapServiceAreaInfo[] infos) {}
    @Override public void showCross(AMapNaviCross cross) {}
    @Override public void hideCross() {}
    @Override public void showLaneInfo(AMapLaneInfo[] laneInfos, byte[] b1, byte[] b2) {}
    @Override public void showLaneInfo(AMapLaneInfo laneInfo) {}
    @Override public void hideLaneInfo() {}
    @Override public void onCalculateRouteSuccess(int[] routeIds) {}
    @Override public void onCalculateRouteSuccess(AMapCalcRouteResult result) {}
    @Override public void onCalculateRouteFailure(AMapCalcRouteResult result) {}
    @Override public void onNaviRouteNotify(AMapNaviRouteNotifyData data) {}
    @Override public void onPlayRing(int type) {}
    @Override public void onNaviInfoUpdate(NaviInfo info) {}
    @Override public void showModeCross(AMapModelCross cross) {}
    @Override public void hideModeCross() {}
    @Override public void notifyParallelRoad(int status) {}
    @Override public void notifyParallelRoad(com.amap.api.navi.enums.AMapNaviParallelRoadStatus status) {}
}
