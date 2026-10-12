package org.zarroboogs.maps.ui.navi;

import android.Manifest;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import org.zarroboogs.maps.utils.FileLogger;
import android.view.View;
import android.view.KeyEvent;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.amap.api.navi.AMapNavi;
import com.amap.api.navi.AMapNaviListener;
import com.amap.api.navi.AMapNaviView;
import com.amap.api.navi.AMapNaviViewListener;
import com.amap.api.navi.AMapNaviViewOptions;
import com.amap.api.navi.AmapNaviParams;
import com.amap.api.navi.AmapPageType;
import com.amap.api.navi.enums.MapStyle;
import com.amap.api.navi.enums.NaviType;
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

import org.zarroboogs.maps.R;
import org.zarroboogs.maps.core.road.model.LatLngPoint;
import org.zarroboogs.maps.utils.SettingUtils;
import org.zarroboogs.maps.ble.BleDeviceListDialog;
import org.zarroboogs.maps.ble.BleDisconnectDialog;
import org.zarroboogs.maps.ble.BleService;
import org.zarroboogs.maps.ble.BleServiceListener;
import org.zarroboogs.maps.hub.NavStyleManager;
import org.zarroboogs.maps.hub.NavigationHubManager;
import org.zarroboogs.maps.cruise.CruiseModeManager;
import org.zarroboogs.maps.hub.AlarmController;
import org.zarroboogs.maps.ui.maps.CameraOverlayManager;

import java.util.List;

/**
 * GPS实时导航页面 — 对标官方demo RouteNaviActivity。
 * 不做任何路线计算，路线已由 NaviRouteActivity 算好并 selectRouteId。
 * 直接使用 AMapNavi 单例中已有的路线数据启动 GPS 导航。
 * AMapNaviView 内部自动绘制路线。
 */
public class NaviCustomActivity extends AppCompatActivity implements AMapNaviListener, AMapNaviViewListener {
    private static final String TAG = "NaviCustom";

    private AMapNaviView mAmapNaviView;
    private AMapNavi mAmapNavi;
    // 报警三件套（CameraRouteMatcher + CameraVoiceManager + 音量按钮）
    private AlarmController mAlarmController;
    private boolean mIsGps = true;
    private int mTravelMode = NaviRouteActivity.MODE_DRIVE;

    // BLE 按钮 + Service 绑定
    private BleService mBleService;
    private boolean mBound;
    private ImageButton mBleScanButton;
    private ImageView mBleConnectionStatus;
    private View mBleButtonContainer;
    private BleDeviceListDialog mBleDeviceListDialog;
    private BleDisconnectDialog mBleDisconnectDialog;

    // 自定义语音播报按钮（由 AlarmController 管理）
    private ImageButton mVoiceButton;
    private View mVoiceButtonContainer;

    // 导航样式切换 (R-08: 箭头↔路网)
    private ImageButton mStyleButton;
    private View mStyleButtonContainer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        mIsGps = getIntent().getBooleanExtra("gps", true);
        mTravelMode = getIntent().getIntExtra(NaviRouteActivity.EXTRA_TRAVEL_MODE, NaviRouteActivity.MODE_DRIVE);

        // 关键：setIsNaviTravelView 必须在 setContentView 之前调用
        // 对标官方demo GPSNaviActivity / RideRouteCalculateActivity
        try {
            mAmapNavi = AMapNavi.getInstance(getApplicationContext());
            boolean isTravelView = (mTravelMode == NaviRouteActivity.MODE_RIDE || mTravelMode == NaviRouteActivity.MODE_WALK);
            mAmapNavi.setIsNaviTravelView(isTravelView);
            FileLogger.d(TAG, "onCreate: setIsNaviTravelView=" + isTravelView + ", mode=" + mTravelMode);
        } catch (Exception e) {
            FileLogger.e(TAG, "init AMapNavi failed (pre-setContentView)", e);
        }

        setContentView(R.layout.activity_navicustom);

        mAmapNaviView = findViewById(R.id.customnavimap);
        mAmapNaviView.onCreate(savedInstanceState);
        mAmapNaviView.setAMapNaviViewListener(this);

        try {
            mAmapNavi.addAMapNaviListener(this);
            FileLogger.d(TAG, "NaviListener registered");
            mAmapNavi.setUseInnerVoice(true);
            mAmapNavi.setEmulatorNaviSpeed(60);
            // 导航启动前停止巡航 + 电子围栏（导航中不允许巡航/围栏重启）
            CruiseModeManager.getInstance().stop();
            stopGeofenceForNavi();
            NavigationHubManager.INSTANCE.startSession(
                    mAmapNavi != null && mAmapNavi.getNaviPath() != null
                            ? mAmapNavi.getNaviPath().getAllLength() : 0);
            if (mIsGps) {
                mAmapNavi.startNavi(NaviType.GPS);
                FileLogger.e(TAG, ">>> startNavi: GPS 真实导航");
            } else {
                mAmapNavi.startNavi(NaviType.EMULATOR);
                FileLogger.e(TAG, ">>> startNavi: EMULATOR 模拟导航");
            }
            FileLogger.w("MotoNav", "[NaviCustom] startNavi isGps=" + mIsGps);
        } catch (Exception e) {
            FileLogger.e(TAG, "startNavi failed", e);
        }

        // 报警三件套：CameraRouteMatcher + CameraVoiceManager + 音量按钮
        mAlarmController = new AlarmController(getApplicationContext());
        mVoiceButtonContainer = findViewById(R.id.navi_voice_button_container);
        mVoiceButton = findViewById(R.id.navi_voice_button);
        mAlarmController.bind(mVoiceButton, mTravelMode);

        // 导航样式切换按钮 (R-08: 箭头 ↔ 路网)
        // 每次导航开始默认箭头模式，不持久化
        NavStyleManager.INSTANCE.reset();
        mStyleButtonContainer = findViewById(R.id.navi_style_button_container);
        mStyleButton = findViewById(R.id.navi_style_button);
        mStyleButton.setOnClickListener(v -> {
            NavStyleManager.INSTANCE.toggle();
            updateStyleButtonIcon();
        });
        updateStyleButtonIcon();

        // BLE 按钮（导航中可重连，与首页统一：直接弹设备列表 + 自动扫描 + 关闭即停）
        mBleButtonContainer = findViewById(R.id.navi_ble_button_container);
        mBleScanButton = findViewById(R.id.navi_ble_scan_button);
        mBleConnectionStatus = findViewById(R.id.navi_ble_connection_status);
        mBleScanButton.setOnClickListener(v -> {
            if (mBleService != null && mBleService.isConnected()) {
                if (mBleDisconnectDialog == null) {
                    mBleDisconnectDialog = new BleDisconnectDialog(this, mBleService);
                }
                mBleDisconnectDialog.show();
            } else if (mBleService != null) {
                if (!checkAndRequestPermissionsForBleScan()) return;
                BluetoothAdapter adapter = ((android.bluetooth.BluetoothManager)
                        getSystemService(Context.BLUETOOTH_SERVICE)).getAdapter();
                if (adapter == null || !adapter.isEnabled()) {
                    Intent enableBt = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                    startActivityForResult(enableBt, REQUEST_ENABLE_BT);
                    return;
                }
                openBleDeviceListAndScan();
            }
        });
        updateBleButtonUi(false);

        bindBleService();
    }

    // ======================== 生命周期 ========================

    @Override
    protected void onResume() {
        super.onResume();
        mAmapNaviView.onResume();
        applyNaviSettings();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mAmapNaviView.onPause();
        NavigationHubManager.INSTANCE.logEvent("ACTIVITY_PAUSE",
            java.util.Collections.singletonMap("ts", System.currentTimeMillis()));
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        mAmapNaviView.onSaveInstanceState(outState);
    }

    /**
     * 对标官方demo RouteNaviActivity.onDestroy：
     * stopNavi + removeAMapNaviListener（不调用destroy，由NaviRouteActivity负责）。
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        mAmapNaviView.onDestroy();
        if (mBleService != null) mBleService.stopGpsInjection();
        NavigationHubManager.INSTANCE.stopSession();
        if (mAmapNavi != null) {
            mAmapNavi.stopNavi();
            mAmapNavi.removeAMapNaviListener(this);
            FileLogger.d(TAG, "NaviListener removed in onDestroy");
        }
        if (mBleService != null) mBleService.setNavActive(false);
        unbindBleService();
        CameraOverlayManager.getInstance().detachMap();
    }

    

    /**
     * 应用导航设置（从 NaviPrefs SharedPreferences 读取）。
     */
    private void applyNaviSettings() {
        if (mAmapNavi == null || mAmapNaviView == null) return;

        // 提前读取所有设置值（用于日志）
        int viewMode;
        if (mTravelMode == NaviRouteActivity.MODE_WALK) {
            viewMode = NaviPrefs.getNaviViewMode(AMapNaviView.NORTH_UP_MODE);
        } else {
            viewMode = NaviPrefs.getNaviViewMode();
        }
        int dayNightMode = NaviPrefs.getDayNightMode();
        int broadcastMode = NaviPrefs.getBroadcastMode();
        boolean autoZoom = NaviPrefs.getScaleAutoZoom();

        AMapNaviViewOptions viewOptions = mAmapNaviView.getViewOptions();
        if (viewOptions != null) {
            viewOptions.setTrafficLine(NaviPrefs.getTrafficInfoUpdateEnabled());
            viewOptions.setSettingMenuEnabled(true);
            viewOptions.setShowSettingsPanel(true);
            viewOptions.setBroadcastModeEnabled(true);
            viewOptions.setShowRouteStrategyPreferencePanel(true);
            viewOptions.setNaviMode(viewMode);

            MapStyle mapStyle;
            switch (dayNightMode) {
                case 1:  mapStyle = MapStyle.DAY;   break;
                case 2:  mapStyle = MapStyle.NIGHT; break;
                default: mapStyle = MapStyle.AUTO;  break;
            }
            viewOptions.setMapStyle(mapStyle, null);
            viewOptions.setAutoChangeZoom(autoZoom);
            mAmapNaviView.setViewOptions(viewOptions);
        }

        if (mAmapNavi.getNaviSetting() != null) {
            mAmapNavi.getNaviSetting().setScreenAlwaysBright(NaviPrefs.getScreenAlwaysBright());
        }

        int sdkMode;
        switch (broadcastMode) {
            case 1:  sdkMode = 1; break;  // CONCISE
            case 2:  sdkMode = 3; break;  // MUTE
            default: sdkMode = 2; break;  // DETAIL
        }
        mAmapNavi.setBroadcastMode(sdkMode);

        FileLogger.d(TAG, "Nav settings: viewMode=" + viewMode + " dayNight=" + dayNightMode
                + " broadcast=" + broadcastMode + " autoZoom=" + autoZoom);
    }

    // ======================== AMapNaviListener 回调 ========================

    @Override public void onInitNaviFailure() { FileLogger.e(TAG, "onInitNaviFailure"); }
    @Override public void onInitNaviSuccess() { FileLogger.d(TAG, "onInitNaviSuccess"); }
    @Override public void onStartNavi(int type) {
        FileLogger.w("MotoNav", "[NaviCustom] onStartNavi type=" + type);
        // 路线已在 NaviRouteActivity 算好，缓存到 AlarmController
        if (mAmapNavi != null && mAmapNavi.getNaviPath() != null) {
            mAlarmController.getMatcher().setRoute(mAmapNavi.getNaviPath().getCoordList());
            // v2.0: 同步路线到 NavStyleManager，供路网推送使用
            List<NaviLatLng> coords = mAmapNavi.getNaviPath().getCoordList();
            java.util.ArrayList<LatLngPoint> pts = new java.util.ArrayList<>();
            for (NaviLatLng c : coords) {
                pts.add(new LatLngPoint(c.getLatitude(), c.getLongitude()));
            }
            NavStyleManager.INSTANCE.setRoutePolyline(pts);
        }
    }

    @Override public void onTrafficStatusUpdate() {}
    @Override public void onLocationChange(AMapNaviLocation location) {
        if (location == null) return;
        NavigationHubManager.INSTANCE.updateSpeed(location.getSpeed());
        // v2.0: 同步 GPS/速度/航向到 NavStyleManager，供路网推送和航向推送使用
        NavStyleManager.INSTANCE.setCurrentGps(
                new LatLngPoint(location.getCoord().getLatitude(), location.getCoord().getLongitude()));
        NavStyleManager.INSTANCE.setCurrentSpeedKmh(location.getSpeed());
        // RoadBearing 优先（道路匹配方向，静止也稳定），GPS Bearing 兜底
        float h = location.getRoadBearing();
        if (h <= 0) h = location.getBearing();
        NavStyleManager.INSTANCE.setCurrentHeadingDeg(h);
        // 报警三件套：按驾驶模式自动分流（驾车→updateCameraInfo / 骑行→CameraRouteMatcher / 步行→0x00）
        mAlarmController.onLocationUpdate(
                new com.amap.api.maps.model.LatLng(location.getCoord().getLatitude(), location.getCoord().getLongitude()));
    }
    @Override public void onGetNavigationText(int type, String text) {}
    @Override public void onGetNavigationText(String s) {}
    @Override public void onEndEmulatorNavi() {
        if (mBleService != null) mBleService.stopGpsInjection();
        NavigationHubManager.INSTANCE.stopSession();
        if (mBleService != null) mBleService.setNavActive(false);
    }
    @Override public void onArriveDestination() {
        if (mBleService != null) mBleService.stopGpsInjection();
        NavigationHubManager.INSTANCE.stopSession();
        if (mBleService != null) mBleService.setNavActive(false);
    }
    @Override public void onCalculateRouteFailure(int errorInfo) { FileLogger.e(TAG, "onCalculateRouteFailure errorInfo=" + errorInfo); }
    @Override public void onReCalculateRouteForYaw() {
        FileLogger.d(TAG, "onReCalculateRouteForYaw");
        NavigationHubManager.INSTANCE.logEvent("REROUTE_START", java.util.Collections.singletonMap("reason", "yaw"));
        mAlarmController.onRerouteStart();
    }
    @Override public void onReCalculateRouteForTrafficJam() {
        FileLogger.d(TAG, "onReCalculateRouteForTrafficJam");
        NavigationHubManager.INSTANCE.logEvent("REROUTE_START", java.util.Collections.singletonMap("reason", "trafficJam"));
        mAlarmController.onRerouteStart();
    }
    @Override public void onArrivedWayPoint(int wayID) {}
    @Override public void onGpsOpenStatus(boolean enabled) { FileLogger.d(TAG, "onGpsOpenStatus=" + enabled); }

    @Override
    public void onNaviInfoUpdate(NaviInfo naviinfo) {
        if (naviinfo == null) return;
        NavigationHubManager.INSTANCE.updateNavState(
            naviinfo.getIconType(),
            naviinfo.getCurStepRetainDistance(),
            naviinfo.getPathRetainDistance(),
            mAmapNavi != null && mAmapNavi.getNaviPath() != null
                ? mAmapNavi.getNaviPath().getAllLength() : 0);

        // 速度仅走 onLocationChange（SDK 文档：AMapNaviLocation.getSpeed() 返回 km/h，
        // NaviInfo.getCurrentSpeed() 已 deprecated，官方推荐用 onLocationChange）
    }
    @Override public void onServiceAreaUpdate(AMapServiceAreaInfo[] serviceAreaInfos) {}
    /**
     * 高德电子眼回调 — 驾车模式报警来源。
     * 仅驾车模式处理，骑行/步行由 AlarmController 自有体系负责。
     * 取最近摄像头，编码 alarm=cameraType+0x10，附带限速值 limitSpeed。
     */
    @Override
    public void updateCameraInfo(AMapNaviCameraInfo[] cameraInfos) {
        if (mTravelMode != NaviRouteActivity.MODE_DRIVE) return;
        if (cameraInfos == null || cameraInfos.length == 0) {
            NavigationHubManager.INSTANCE.updateAlarm(0x00, 0);
            return;
        }
        // 取最近摄像头
        AMapNaviCameraInfo nearest = cameraInfos[0];
        for (int i = 1; i < cameraInfos.length; i++) {
            if (cameraInfos[i].getCameraDistance() < nearest.getCameraDistance()) {
                nearest = cameraInfos[i];
            }
        }
        int cameraType = nearest.getCameraType();
        int cameraSpeed = nearest.getCameraSpeed(); // -2=无数据, -1=禁行, 0=不限速, >0=限速值
        int alarm = (cameraType + 0x10) & 0xFF;
        int limitSpeed = (cameraSpeed > 0) ? cameraSpeed : 0;
        NavigationHubManager.INSTANCE.updateAlarm(alarm, limitSpeed);
    }
    @Override public void updateIntervalCameraInfo(AMapNaviCameraInfo a, AMapNaviCameraInfo b, int i) {}
    @Override public void onPlayRing(int playRing) {}
    @Override public void showCross(AMapNaviCross cross) {}
    @Override public void hideCross() {}
    @Override public void showLaneInfo(AMapLaneInfo laneInfo) {}
    @Override public void showLaneInfo(AMapLaneInfo[] laneInfos, byte[] bg, byte[] rec) {}
    @Override public void hideLaneInfo() {}
    @Override public void showModeCross(AMapModelCross modelCross) {}
    @Override public void hideModeCross() {}
    @Override public void notifyParallelRoad(int parallelRoadStatus) {}
    @Override public void OnUpdateTrafficFacility(AMapNaviTrafficFacilityInfo info) {}
    @Override public void OnUpdateTrafficFacility(AMapNaviTrafficFacilityInfo[] infos) {}
    @Override public void updateAimlessModeStatistics(AimLessModeStat stat) {}
    @Override public void updateAimlessModeCongestionInfo(AimLessModeCongestionInfo info) {}
    @Override public void onCalculateRouteSuccess(int[] ints) { FileLogger.d(TAG, "onCalculateRouteSuccess(int[])"); }
    @Override public void onCalculateRouteFailure(AMapCalcRouteResult result) { FileLogger.e(TAG, "onCalculateRouteFailure result=" + (result != null ? result.getErrorCode() : "null")); }
    @Override
    public void onCalculateRouteSuccess(AMapCalcRouteResult result) {
        FileLogger.d(TAG, "onCalculateRouteSuccess(AMapCalcRouteResult)");
        // 缓存新路线到 AlarmController
        if (mAmapNavi != null && mAmapNavi.getNaviPath() != null) {
            mAlarmController.getMatcher().onRouteRecalculated(mAmapNavi.getNaviPath().getCoordList());
            // v2.0: 偏航重算后更新路线 + 路网模式下重推
            List<NaviLatLng> coords = mAmapNavi.getNaviPath().getCoordList();
            java.util.ArrayList<LatLngPoint> pts = new java.util.ArrayList<>();
            for (NaviLatLng c : coords) {
                pts.add(new LatLngPoint(c.getLatitude(), c.getLongitude()));
            }
            NavStyleManager.INSTANCE.setRoutePolyline(pts);
            if (NavStyleManager.INSTANCE.getCurrentStyle() == NavStyleManager.STYLE_ROAD) {
                FileLogger.i(TAG, "偏航重算 → 路网模式重推");
                boolean ok = NavStyleManager.INSTANCE.pushRoadNetwork();
                if (!ok) {
                    FileLogger.e(TAG, "偏航重算路网推送失败，退回箭头");
                    NavStyleManager.INSTANCE.setStyle(NavStyleManager.STYLE_ARROW);
                }
            }
        }
        // 重算路后 SDK 会重置部分设置为默认值，重新应用全部用户设置
        // (播报模式、日夜模式、比例尺缩放、导航视角等)。
        applyNaviSettings();
        NavigationHubManager.INSTANCE.logEvent("REROUTE_COMPLETE",
            java.util.Collections.singletonMap("ts", System.currentTimeMillis()));
    }
    @Override public void onNaviRouteNotify(AMapNaviRouteNotifyData routeNotifyData) {}
    @Override public void onGpsSignalWeak(boolean isWeak) {}

    // ======================== AMapNaviViewListener 回调 ========================

    @Override public void onNaviSetting() {}
    @Override public void onNaviMapMode(int naviMode) {}
    @Override public void onNaviCancel() {
        FileLogger.e(TAG, ">>> onNaviCancel CALLED, finishing activity");
        finish();
    }
    @Override public void onNaviTurnClick() {}
    @Override public void onNextRoadClick() {}
    @Override public void onScanViewButtonClick() {}
    @Override public void onLockMap(boolean isLock) {
        // 地图解锁时 SDK 显示缩放/恢复按钮，BLE按钮上移避免遮盖
        adjustBleButtonPosition(!isLock);
    }
    @Override public void onNaviViewLoaded() {
        FileLogger.d(TAG, "onNaviViewLoaded");
        applyNaviSettings();
        // 叠加本地摄像头 markers 到导航地图（跟随首页全局开关）
        if (mAmapNaviView != null && mAmapNaviView.getMap() != null) {
            CameraOverlayManager.getInstance().rebindMap(mAmapNaviView.getMap());
        }
    }
    @Override public void onMapTypeChanged(int i) {}
    @Override public void onNaviViewShowMode(int i) { FileLogger.d(TAG, "onNaviViewShowMode=" + i); }
    @Override public void onStopSpeaking() {}
    @Override public void onViewTypeChanged(AmapPageType amapPageType) {}
    @Override public void onAMapNaviViewExit() {
        FileLogger.e(TAG, ">>> onAMapNaviViewExit, isGps=" + mIsGps);
        if (!mIsGps) {
            FileLogger.e(TAG, ">>> onAMapNaviViewExit: emulator mode, finishing");
            finish();
        }
    }
    @Override public void onStrategyChanged(int i) {}
    @Override public void onBroadcastModeChanged(int mode) {
        // SDK BroadcastMode: DETAIL=2, CONCISE=1, MUTE=3
        // 映射到 NaviPrefs: 0=详细, 1=简洁, 2=静音
        int appMode;
        switch (mode) {
            case 1:  appMode = 1; break;  // CONCISE → 简洁
            case 3:  appMode = 2; break;  // MUTE → 静音
            default: appMode = 0; break;  // DETAIL → 详细
        }
        NaviPrefs.setBroadcastMode(appMode);
    }
    @Override public void onDayAndNightModeChanged(int i) {}
    @Override public void onScaleAutoChanged(boolean b) {}
    @Override public void onListenToVoiceDuringCallChanged(boolean b) {}
    @Override public void onControlMusicVolumeModeChanged(int i) {}
    @Override public void onEagleChanged(boolean b) {}
    @Override public void onNaviRouteHighlightChange(long l, int i) {}
    /**
     * 对齐高德Demo：返回false让SDK内部处理返回逻辑（弹出退出确认框）。
     */
    @Override public boolean onNaviBackClick() {
        FileLogger.e(TAG, ">>> onNaviBackClick, isGps=" + mIsGps);
        return false;
    }

    /**
     * 同步系统返回键与 SDK 内部退出逻辑。
     * 对齐高德 Demo：调用 AMapNaviView.onBackPressed()，
     * SDK 处理了（true）则信任 SDK，否则走系统默认 finish。
     */
    @Override
    public void onBackPressed() {
        if (mAmapNaviView != null) {
            boolean sdkHandled = mAmapNaviView.onBackPressed();
            FileLogger.e(TAG, ">>> onBackPressed: SDK returned " + sdkHandled);
            // 信任 SDK 处理，不 fallback 到 super。GPS 模式由 onNaviCancel 负责退出，
            // 模拟模式由 onAMapNaviViewExit 负责退出。super.onBackPressed() 会绕过
            // SDK 的红色退出按钮直接 finish，破坏正常退出流程。
            return;
        }
        super.onBackPressed();
    }

    // ---- Permission helpers ----

    private static final int REQUEST_LOCATION_PERMISSION = 100;
    private static final int REQUEST_BLUETOOTH_PERMISSION = 101;
    private static final int REQUEST_ENABLE_BT = 102;
    private static final String PERM_BLUETOOTH_SCAN = "android.permission.BLUETOOTH_SCAN";
    private static final String PERM_BLUETOOTH_CONNECT = "android.permission.BLUETOOTH_CONNECT";
    private boolean mPendingBleScanAfterPermission;

    private boolean hasPermission(String permission) {
        return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED;
    }

    private boolean checkAndRequestPermissionsForBleScan() {
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            boolean hasScan = hasPermission(PERM_BLUETOOTH_SCAN);
            boolean hasConnect = hasPermission(PERM_BLUETOOTH_CONNECT);
            if (!hasScan || !hasConnect) {
                mPendingBleScanAfterPermission = true;
                requestBluetoothPermissions();
                return false;
            }
        } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            if (!hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
                mPendingBleScanAfterPermission = true;
                requestBluetoothPermissions();
                return false;
            }
        }
        return true;
    }

    private void requestBluetoothPermissions() {
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            requestPermissions(new String[]{PERM_BLUETOOTH_SCAN, PERM_BLUETOOTH_CONNECT},
                    REQUEST_BLUETOOTH_PERMISSION);
        } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    REQUEST_LOCATION_PERMISSION);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        boolean allGranted = true;
        for (int result : grantResults) {
            if (result != PackageManager.PERMISSION_GRANTED) {
                allGranted = false;
                break;
            }
        }
        if (allGranted && mPendingBleScanAfterPermission) {
            mPendingBleScanAfterPermission = false;
            if (mBleService != null) {
                openBleDeviceListAndScan();
            }
        } else if (!allGranted) {
            mPendingBleScanAfterPermission = false;
            Toast.makeText(this, "需要蓝牙权限才能扫描设备", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_ENABLE_BT && resultCode == RESULT_OK) {
            if (mBleService != null) {
                openBleDeviceListAndScan();
            }
        }
    }

    /** 导航启动时停电子围栏 */
    private void stopGeofenceForNavi() {
        SettingUtils.writeGeofenceEnabled(false);
        if (mBleService != null) mBleService.stopGpsForGeofence();
    }

    // ---- BleService binding ----

    private final ServiceConnection mBleServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mBleService = ((BleService.LocalBinder) service).getService();
            mBleService.registerListener(mBleServiceListener);
            mBound = true;
            mBleService.setNavActive(true);
            if (mIsGps) mBleService.startGpsInjection();
            // 确保导航时围栏 GPS 已停止（BleService 可能绑得晚）
            if (!SettingUtils.isGeofenceEnabled()) mBleService.stopGpsForGeofence();
            updateBleButtonUi(mBleService.isConnected());
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mBleService = null;
            mBound = false;
        }
    };

    private final BleServiceListener mBleServiceListener = new BleServiceListener() {
        @Override
        public void onConnectionStateChanged(boolean connected, String deviceName) {
            runOnUiThread(() -> {
                if (isFinishing()) return;
                updateBleButtonUi(connected);
            });
        }

        @Override
        public void onScanStarted() {}

        @Override
        public void onScanStopped() {
            runOnUiThread(() -> {
                if (isFinishing()) return;
                if (mBleDeviceListDialog != null && mBleDeviceListDialog.isShowing()) {
                    mBleDeviceListDialog.setScanning(false);
                    mBleDeviceListDialog.show();
                }
            });
        }

        @Override
        public void onScanResult(List<BleDeviceEntry> devices) {
            runOnUiThread(() -> {
                if (isFinishing()) return;
                if (mBleDeviceListDialog != null && mBleDeviceListDialog.isShowing()) {
                    mBleDeviceListDialog.show();
                }
            });
        }

        @Override
        public void onError(String message) {
            runOnUiThread(() -> {
                if (isFinishing()) return;
                Toast.makeText(NaviCustomActivity.this, message, Toast.LENGTH_SHORT).show();
            });
        }

        @Override
        public void onDeviceTelemetry(int batteryPercent, String firmwareVersion, int heapKb, int fps) {
            NavigationHubManager.INSTANCE.onDeviceTelemetry(batteryPercent, firmwareVersion, heapKb, fps);
        }
    };

    private void bindBleService() {
        Intent intent = new Intent(getApplicationContext(), BleService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getApplicationContext().startForegroundService(intent);
        } else {
            getApplicationContext().startService(intent);
        }
        getApplicationContext().bindService(intent, mBleServiceConnection, Context.BIND_AUTO_CREATE);
    }

    private void unbindBleService() {
        if (mBound) {
            getApplicationContext().unbindService(mBleServiceConnection);
            mBound = false;
        }
    }

    private void updateBleButtonUi(boolean connected) {
        if (mBleScanButton != null) {
            if (connected) {
                if (mBleConnectionStatus != null) mBleConnectionStatus.setVisibility(View.GONE);
                mBleScanButton.setImageResource(R.drawable.ble_active);
                mBleScanButton.clearColorFilter();
            } else {
                if (mBleConnectionStatus != null) mBleConnectionStatus.setVisibility(View.GONE);
                mBleScanButton.setImageResource(R.drawable.ble);
                mBleScanButton.clearColorFilter();
            }
        }
        // R-08: 样式按钮 BLE 联动 — 未连接时置灰禁用
        if (mStyleButton != null) {
            mStyleButton.setEnabled(connected);
            mStyleButton.setAlpha(connected ? 1.0f : 0.35f);
        }
    }

    // Device list dialog now uses BleDeviceListDialog (shared with MapsFragment)

    /**
     * 动态调整 BLE + 语音按钮位置：当 SDK 缩放/恢复按钮出现时上移，消失时恢复。
     */
    private void adjustBleButtonPosition(boolean sdkButtonsVisible) {
        float density = getResources().getDisplayMetrics().density;
        if (mStyleButtonContainer != null) {
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) mStyleButtonContainer.getLayoutParams();
            int dp = sdkButtonsVisible ? 380 : 260;
            lp.bottomMargin = (int) (dp * density);
            mStyleButtonContainer.setLayoutParams(lp);
        }
        if (mVoiceButtonContainer != null) {
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) mVoiceButtonContainer.getLayoutParams();
            int dp = sdkButtonsVisible ? 320 : 200;
            lp.bottomMargin = (int) (dp * density);
            mVoiceButtonContainer.setLayoutParams(lp);
        }
        if (mBleButtonContainer != null) {
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) mBleButtonContainer.getLayoutParams();
            int dp = sdkButtonsVisible ? 260 : 140;
            lp.bottomMargin = (int) (dp * density);
            mBleButtonContainer.setLayoutParams(lp);
        }
    }

    private void updateStyleButtonIcon() {
        if (mStyleButton != null) {
            int style = NavStyleManager.INSTANCE.getCurrentStyle();
            int iconRes;
            if (style == org.zarroboogs.maps.hub.NavStyleManager.STYLE_ARROW) {
                iconRes = R.drawable.jiantou;
            } else if (style == org.zarroboogs.maps.hub.NavStyleManager.STYLE_ROAD) {
                iconRes = R.drawable.luwang;
            } else {
                iconRes = R.drawable.wandao;  // STYLE_LEAN
            }
            mStyleButton.setImageResource(iconRes);
        }
    }

    /** 外部调用：NavStyleManager 通知 UI 更新。 */
    public void setNavStyle(int style) {
        NavStyleManager.INSTANCE.setStyle(style);
    }

    public int getNavStyle() {
        return NavStyleManager.INSTANCE.getCurrentStyle();
    }

    private void openBleDeviceListAndScan() {
        if (mBleService == null) return;
        if (mBleDeviceListDialog == null) {
            mBleDeviceListDialog = new BleDeviceListDialog(this, mBleService);
        }
        mBleDeviceListDialog.setScanning(true);
        mBleDeviceListDialog.show();
        mBleService.startScan();
    }
}
