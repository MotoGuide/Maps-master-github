package org.zarroboogs.maps.presenters;

import com.amap.api.maps.AMap;
import com.amap.api.maps.model.LatLng;
import com.amap.api.maps.model.MarkerOptions;
import org.zarroboogs.maps.MapsApplication;
import org.zarroboogs.maps.data.local.entity.Camera;
import org.zarroboogs.maps.utils.FileLogger;
import org.zarroboogs.maps.beans.GeoFenceInfo;
import org.zarroboogs.maps.hub.NavigationHubManager;
import org.zarroboogs.maps.cruise.CruiseModeManager;
import org.zarroboogs.maps.module.GeoFenceManager;
import org.zarroboogs.maps.presenters.iviews.IGaoDeMapsView;
import org.zarroboogs.maps.presenters.MarkerInteractor.OnMarkerCreatedListener;

import java.util.ArrayList;

/**
 * Created by andforce on 15/7/19.
 */
public class MapsPresenterImpl implements MapsPresenter, OnMarkerCreatedListener, MarkerInteractor.OnReadCamerasListener,
        MapsActionInteractor.OnMyLocationModeChangedListener{

    private static final boolean DEBUG = false;

    private IGaoDeMapsView mGaodeMapsView;
    private MarkerInteractor mMapsInteractor;

    private MapsActionInteractor mMapsActionInteractor;

    private GeoFenceManager geoFenceManager;

    private ArrayList<Camera> mMotoCameras;


    public MapsPresenterImpl(IGaoDeMapsView gaoDeMapsView) {
        this.mGaodeMapsView = gaoDeMapsView;
        this.mMapsInteractor = new MarkerInteractorImpl();
        this.mMapsActionInteractor = new MapsActionInteractorImpl();

        initCameras();
    }

    private void initCameras(){
        mMapsInteractor.readCameras(this);
    }

    @Override
    public void loadDefaultCameraMarkers() {
        mMapsInteractor.createMarkers(this);
    }

    @Override
    public void loadDefaultCameraMarkers(String city) {
        mMapsInteractor.createMarkers(this, city);
    }

    @Override
    public void enableDefaultGeoFences() {
        FileLogger.w("MotoNav", "enableDefaultGeoFences: cameras=" + (mMotoCameras != null ? mMotoCameras.size() : 0));
        if (mMotoCameras != null){
            if (geoFenceManager == null) {
                geoFenceManager = new GeoFenceManager(MapsApplication.getAppContext());
                FileLogger.w("MotoNav", "enableDefaultGeoFences: GeoFenceManager created");
            }
            // 无论新建还是已存在，确保注册到 CruiseModeManager（onReadCameras 可能先创建但 CM 未就绪）
            CruiseModeManager cm = CruiseModeManager.getInstance();
            if (cm != null) {
                cm.setGeoFenceManager(geoFenceManager);
                FileLogger.w("MotoNav", "enableDefaultGeoFences: GeoFenceManager registered to CruiseModeManager");
            }
            ArrayList<GeoFenceInfo> geoFenceInfos = new ArrayList<>();
            for (Camera cameraBean : mMotoCameras){
                LatLng latLng = new LatLng(cameraBean.getLatitude(), cameraBean.getLongtitude());
                GeoFenceInfo info = new GeoFenceInfo(MapsApplication.getAppContext(),latLng,cameraBean.getId());
                geoFenceInfos.add(info);
            }

            if (DEBUG){
                GeoFenceInfo geoFenceInfo = new GeoFenceInfo(MapsApplication.getAppContext(), new LatLng(40.09705f, 116.426019f), 100);
                geoFenceInfos.add(geoFenceInfo);
            }

            geoFenceManager.setOnCameraAlarmListener(inZone -> {
                FileLogger.w("MotoNav", "[GeoFenceAlarm] inZone=" + inZone + " -> PUSH");
                NavigationHubManager.INSTANCE.updateAlarm(inZone ? 0xFF : 0);
            });
            geoFenceManager.addAllGeoFenceAler(geoFenceInfos);
            FileLogger.w("MotoNav", "enableDefaultGeoFences: " + geoFenceInfos.size() + " geofences added");
        }
    }

    public GeoFenceManager getGeoFenceManager() {
        return geoFenceManager;
    }

    @Override
    public void disableDefaultGeoFences() {
        if (geoFenceManager != null){
            geoFenceManager.removeAllGeoFenceAlert();
        }
    }

    @Override
    public void changeMyLocationMode() {
        mMapsActionInteractor.changeMyLocationMode(this);
    }

    @Override
    public void stopFollowMode() {
        mMapsActionInteractor.stopFollowMode(this);
    }

    @Override
    public void onMarkerCreated(ArrayList<MarkerOptions> markerOptions) {
        mGaodeMapsView.addMarkers(markerOptions);
    }

    @Override
    public void onReadCameras(ArrayList<Camera> cameraBeans) {
        mMotoCameras = cameraBeans;
        FileLogger.w("MotoNav", "onReadCameras: cameras=" + (cameraBeans != null ? cameraBeans.size() : 0));

        if (geoFenceManager == null){
            geoFenceManager = new GeoFenceManager(MapsApplication.getAppContext());
            FileLogger.w("MotoNav", "onReadCameras: GeoFenceManager created");
        }
        // 尝试注册到 CruiseModeManager（可能尚未创建，enableDefaultGeoFences 会补注册）
        CruiseModeManager cm = CruiseModeManager.getInstance();
        if (cm != null) {
            cm.setGeoFenceManager(geoFenceManager);
            FileLogger.w("MotoNav", "onReadCameras: GeoFenceManager registered to CruiseModeManager");
        }
        geoFenceManager.setOnCameraAlarmListener(inZone -> {
            FileLogger.w("MotoNav", "[GeoFenceAlarm] inZone=" + inZone + " -> PUSH");
            NavigationHubManager.INSTANCE.updateAlarm(inZone ? 0xFF : 0);
        });

        // 填充围栏数据到 GeoFenceManager（修复首次启动围栏为空）
        if (cameraBeans != null && !cameraBeans.isEmpty()) {
            ArrayList<GeoFenceInfo> infos = new ArrayList<>();
            for (Camera c : cameraBeans) {
                LatLng ll = new LatLng(c.getLatitude(), c.getLongtitude());
                infos.add(new GeoFenceInfo(MapsApplication.getAppContext(), ll, c.getId()));
            }
            geoFenceManager.addAllGeoFenceAler(infos);
            FileLogger.w("MotoNav", "onReadCameras: " + infos.size() + " geofences added");
        }

        geoFenceManager.requestLocation();
    }

    @Override
    public void onMyLocationModeChanged(int mode) {
        mGaodeMapsView.changeMyLocationMode(mode);
    }

    @Override
    public void onStopFollowMode() {
        mGaodeMapsView.stopFollowMode();
    }
}
