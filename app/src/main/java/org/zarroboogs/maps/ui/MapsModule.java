package org.zarroboogs.maps.ui;

import android.content.Context;
import android.content.SharedPreferences;
import org.zarroboogs.maps.utils.FileLogger;
import android.view.MotionEvent;
import android.view.View;

import com.amap.api.location.AMapLocation;
import com.amap.api.location.AMapLocationClient;
import com.amap.api.location.AMapLocationClientOption;
import com.amap.api.location.AMapLocationListener;
import com.amap.api.maps.AMap;
import com.amap.api.maps.AMapOptions;
import com.amap.api.maps.CameraUpdateFactory;
import com.amap.api.maps.model.LatLng;

import org.zarroboogs.maps.cruise.CruiseModeManager;
import org.zarroboogs.maps.module.GeoFenceManager;
import org.zarroboogs.maps.presenters.MapsPresenterImpl;
import com.amap.api.maps.UiSettings;
import com.amap.api.maps.model.BitmapDescriptor;
import com.amap.api.maps.model.BitmapDescriptorFactory;
import com.amap.api.maps.model.CameraPosition;
import com.amap.api.maps.model.LatLng;
import com.amap.api.maps.model.Marker;
import com.amap.api.maps.model.MarkerOptions;

import org.zarroboogs.maps.OnLocationChangedListener;
import org.zarroboogs.maps.R;
import org.zarroboogs.maps.presenters.MapsPresenter;
import org.zarroboogs.maps.presenters.MapsPresenterImpl;
import org.zarroboogs.maps.presenters.iviews.IGaoDeMapsView;
import org.zarroboogs.maps.ui.maps.MapsFragment;
import org.zarroboogs.maps.utils.SettingUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by andforce on 15/7/19.
 */
public class MapsModule implements IGaoDeMapsView, AMap.OnMapLoadedListener, AMap.OnMapTouchListener, View.OnClickListener, SharedPreferences.OnSharedPreferenceChangeListener {
    private ArrayList<Marker> mMarkers = new ArrayList<>();

    private MapsPresenter mMapsPresenter;
    private UiSettings mUiSetting;
    private AMapLocationClient mAMapLocationClient;
    private boolean mIsEnableMyLocation = true;
    private boolean mNeedCenterOnNextLocation = true;
    private AMapLocation mLocation;
    private AMap mGaodeMap;
    private MapsFragment mMapsFragment;
    private Marker marker;
    private List<Marker> mCameras;

    private SharedPreferences mPref;

    private BitmapDescriptor mMyLocationIcon = BitmapDescriptorFactory.fromResource(R.drawable.ic_mylocation);

    private AMapLocationListener myLocationChangedListener = new MyLocationChangedListener();

    public MapsPresenter getMapsPresenter() { return mMapsPresenter; }

    public MapsModule(MapsFragment fragment ,AMap map) {
        this.mMapsFragment = fragment;
        this.mGaodeMap = map;

        mPref = fragment.getActivity().getApplicationContext().getSharedPreferences(fragment.getActivity().getPackageName()+ "_preferences", Context.MODE_PRIVATE);
        mPref.registerOnSharedPreferenceChangeListener(this);

        mMapsPresenter = new MapsPresenterImpl(this);
        mGaodeMap.setOnMapLoadedListener(this);
        mGaodeMap.setOnMapTouchListener(this);


        // location
        mGaodeMap.setMyLocationEnabled(true);

        mMapsFragment.getMyLocationBtn().setOnClickListener(this);

        mUiSetting = mGaodeMap.getUiSettings();

        setMaps();
    }


    public void init() {
        mUiSetting.setCompassEnabled(false);
        mUiSetting.setZoomControlsEnabled(false);
        mUiSetting.setMyLocationButtonEnabled(false);
        mUiSetting.setLogoPosition(AMapOptions.LOGO_POSITION_BOTTOM_CENTER);

        mGaodeMap.setMapType(SettingUtils.readCurrentMapsStyle());

    }

    public void setMaps(){
        mGaodeMap.getUiSettings().setMyLocationButtonEnabled(true);// 设置默认定位按钮是否显示
        mGaodeMap.setMyLocationEnabled(true);// 设置为true表示显示定位层并可触发定位，false表示隐藏定位层并不可触发定位，默认是false
        //设置定位的类型为定位模式 ，可以由定位、跟随或地图根据面向方向旋转几种

        if (mAMapLocationClient == null) {
            try {
                mAMapLocationClient = new AMapLocationClient(mMapsFragment.getActivity().getApplicationContext());
                AMapLocationClientOption locationOption = new AMapLocationClientOption();
                locationOption.setLocationMode(AMapLocationClientOption.AMapLocationMode.Hight_Accuracy);
                locationOption.setInterval(2000);
                locationOption.setHttpTimeOut(20000);
                locationOption.setLocationCacheEnable(false);
                mAMapLocationClient.setLocationOption(locationOption);
                mAMapLocationClient.setLocationListener(myLocationChangedListener);
                mAMapLocationClient.startLocation();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

    }

    private int readMyLocationMode() {
        return SettingUtils.readCurrentMyLocationMode();
    }


    public void onOrientationChanged(float ori) {


        if (mLocation != null && marker != null) {
            CameraPosition currentCP = mGaodeMap.getCameraPosition();

            int mode = readMyLocationMode();
            if (mode == AMap.LOCATION_TYPE_MAP_ROTATE) {
                marker.setRotateAngle(0);
                CameraPosition newCP = new CameraPosition(new LatLng(mLocation.getLatitude(), mLocation.getLongitude()), currentCP.zoom, currentCP.bearing, ori);
                mGaodeMap.animateCamera(CameraUpdateFactory.newCameraPosition(newCP), null);
            } else {
                marker.setRotateAngle(ori);
            }
        }

    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (key.equals(SettingUtils.SETTING_PREF_JING_CAMERA_ALERT)){
            if (SettingUtils.isEnableBeijingCameraAlert()){
                mMapsPresenter.enableDefaultGeoFences();
            } else {
                disableAutoLocation();
            }
        }
    }

    class MyLocationChangedListener implements AMapLocationListener {

        @Override
        public void onLocationChanged(AMapLocation aMapLocation) {
            if (aMapLocation == null || aMapLocation.getErrorCode() != 0) {
                return;
            }

            if ((mLocation == null || (mLocation.getLatitude() != aMapLocation.getLatitude() || mLocation.getLongitude() != aMapLocation.getLongitude()))) {
                FileLogger.d("MotoNav", "onLocationChanged");

                int mode = readMyLocationMode();
                boolean firstLocation = mLocation == null;
                if (mNeedCenterOnNextLocation || firstLocation || (mIsEnableMyLocation && mode != AMap.LOCATION_TYPE_LOCATE)) {

                    LatLng latLng = new LatLng(aMapLocation.getLatitude(), aMapLocation.getLongitude());
                    mGaodeMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15));
                    mNeedCenterOnNextLocation = false;

                    if (marker == null) {
                        MarkerOptions mMyLocationMarker = new MarkerOptions().anchor(0.5f, 0.5f).position(latLng).icon(mMyLocationIcon);
                        marker = mGaodeMap.addMarker(mMyLocationMarker);
                    } else {
                        marker.setPosition(latLng);
                    }

                } else if (marker == null) {
                    LatLng latLng = new LatLng(aMapLocation.getLatitude(), aMapLocation.getLongitude());
                    MarkerOptions mMyLocationMarker = new MarkerOptions().anchor(0.5f, 0.5f).position(latLng).icon(mMyLocationIcon);
                    marker = mGaodeMap.addMarker(mMyLocationMarker);
                } else {
                    marker.setPosition(new LatLng(aMapLocation.getLatitude(), aMapLocation.getLongitude()));
                }
                mLocation = aMapLocation;
            }

            // 巡航模式下补充喂坐标给 GeoFenceManager（桥接 AMap AimlessMode 冷启动窗口）
            CruiseModeManager cm = CruiseModeManager.getInstance();
            if (cm != null && cm.isRunning()) {
                if (mMapsPresenter instanceof MapsPresenterImpl) {
                    GeoFenceManager gfm = ((MapsPresenterImpl) mMapsPresenter).getGeoFenceManager();
                    if (gfm != null) {
                        gfm.onLocationUpdate(new LatLng(aMapLocation.getLatitude(), aMapLocation.getLongitude()));
                    }
                }
            }
        }

    }


    public void onDestroy() {
        mPref.unregisterOnSharedPreferenceChangeListener(this);
        deactivate();
    }

    public void deactivate() {
        if (mAMapLocationClient != null) {
            mAMapLocationClient.stopLocation();
            mAMapLocationClient.onDestroy();
        }
        mAMapLocationClient = null;
    }

    // 重新启动定位
    public void startLocation() {
        if (mAMapLocationClient != null) {
            mAMapLocationClient.startLocation();
        } else {
            // 如果定位客户端为空，重新初始化
            setMaps();
        }
    }

    @Override
    public void addMarker(MarkerOptions marker) {
        Marker addedMarker = mGaodeMap.addMarker(marker);

    }

    @Override
    public void addMarkers(ArrayList<MarkerOptions> markers) {
        removeCameras();
        // false 不移动到中心
        mCameras = mGaodeMap.addMarkers(markers, false);
    }

    /** 追加 markers 不清除已有标记（用于分批加载） */
    @Override
    public void appendMarkers(ArrayList<MarkerOptions> markers) {
        if (markers == null || markers.isEmpty()) return;
        List<Marker> newMarkers = mGaodeMap.addMarkers(markers, false);
        if (mCameras == null) {
            mCameras = newMarkers;
        } else if (newMarkers != null) {
            mCameras.addAll(newMarkers);
        }
    }

    @Override
    public void removeMarker(int markerId) {

    }

    public AMapLocation getMyLocation(){
        return mLocation;
    }

    @Override
    public void changeMyLocationMode(final int mode) {

        if (mLocation == null){
            return;
        }

        if (mode == AMap.LOCATION_TYPE_MAP_FOLLOW) {
            CameraPosition newCP= new CameraPosition(new LatLng(mLocation.getLatitude(), mLocation.getLongitude()),15, 0, 0);
            mGaodeMap.animateCamera(CameraUpdateFactory.newCameraPosition(newCP), null);

            mMapsFragment.getMyLocationBtn().setImageResource(R.drawable.ic_qu_direction_mylocation);

        } else if (mode == AMap.LOCATION_TYPE_MAP_ROTATE) {
            CameraPosition newCP= new CameraPosition(new LatLng(mLocation.getLatitude(), mLocation.getLongitude()),15, 45, mMapsFragment.getDevicesDirection());
            mGaodeMap.animateCamera(CameraUpdateFactory.newCameraPosition(newCP), null);

            mMapsFragment.getMyLocationBtn().setImageResource(R.drawable.ic_qu_explore);

        } else if (mode == AMap.LOCATION_TYPE_LOCATE){
            CameraPosition currentCP = mGaodeMap.getCameraPosition();
            CameraPosition newCP= new CameraPosition(new LatLng(mLocation.getLatitude(), mLocation.getLongitude()),15, currentCP.tilt, currentCP.bearing);
            mGaodeMap.animateCamera(CameraUpdateFactory.newCameraPosition(newCP), null);

            mMapsFragment.getMyLocationBtn().setImageResource(R.drawable.ic_qu_direction_mylocation_lost);

        }

    }

    @Override
    public void stopFollowMode() {
        mMapsFragment.getMyLocationBtn().setImageResource(R.drawable.ic_qu_direction_mylocation_lost);
    }

    public void disableAutoLocation(){
        mMapsPresenter.stopFollowMode();
    }

    @Override
    public void changeMapStyle(int style) {
        mGaodeMap.setMapType(style);
    }

    @Override
    public void onMapLoaded() {
        FileLogger.w("MotoNav", "MapsModule.onMapLoaded: isEnableBeijingCameraAlert=" + SettingUtils.isEnableBeijingCameraAlert());
        // 地图加载完后尝试启动定位；真正定位回调异步返回，不会阻塞启动
        mNeedCenterOnNextLocation = true;
        startLocation();

        if (SettingUtils.readCurrentCameraState() == SettingUtils.SWITCH_ON){
            mMapsPresenter.loadDefaultCameraMarkers();
        }

        if (SettingUtils.isEnableBeijingCameraAlert()){
            mMapsPresenter.enableDefaultGeoFences();
        } else {
            FileLogger.w("MotoNav", "MapsModule.onMapLoaded: BeijingCameraAlert DISABLED, skipping enableDefaultGeoFences");
        }

    }

    public void loadCameras(){
        String currentCity = null;
        if (mLocation != null) {
            currentCity = mLocation.getCity();
        }
        // 按城市优先加载：当前城市摄像头排在前面，其他城市排在后面
        mMapsPresenter.loadDefaultCameraMarkers(currentCity);
    }

    public void removeCameras(){
        if (mCameras != null && !mCameras.isEmpty()){
            for (Marker marker : mCameras){
                marker.remove();
                marker.destroy();
            }
        }
    }

    private boolean mIsChanged = false;
    @Override
    public void onTouch(MotionEvent motionEvent) {
        if (motionEvent.getAction() == MotionEvent.ACTION_MOVE){
            if (!mIsChanged){
                mIsEnableMyLocation = false;
                mMapsPresenter.stopFollowMode();
                mIsChanged = true;
            }
        } else if (motionEvent.getAction() == MotionEvent.ACTION_UP){
            mIsChanged = false;
        }
    }


    @Override
    public void onClick(View view) {
        if (view.getId() == R.id.my_location_btn) {
            // 重新启动定位，确保能够获取到最新的位置
            startLocation();
            mMapsPresenter.changeMyLocationMode();
            mIsEnableMyLocation = true;
        }
    }
    // Location end
}
