package org.zarroboogs.maps.ui.navi

// ============================================================================
// 导入
// ============================================================================

// Android
import android.Manifest
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.SparseArray
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.Window
import android.widget.Button
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat

// 高德地图
import com.amap.api.maps.AMap
import com.amap.api.maps.CameraUpdateFactory
import com.amap.api.maps.MapView
import com.amap.api.maps.model.LatLng

// 高德导航
import com.amap.api.navi.AMapNavi
import com.amap.api.navi.AMapNaviListener
import com.amap.api.navi.AMapNaviView
import com.amap.api.navi.AMapNaviViewListener
import com.amap.api.navi.AMapNaviViewOptions
import com.amap.api.navi.enums.MapStyle
import com.amap.api.navi.enums.NaviType
import com.amap.api.navi.enums.PathPlanningStrategy
import com.amap.api.navi.enums.TransportType
import com.amap.api.navi.model.AMapCalcRouteResult
import com.amap.api.navi.model.AMapCarInfo
import com.amap.api.navi.model.AMapLaneInfo
import com.amap.api.navi.model.AMapModelCross
import com.amap.api.navi.model.AMapNaviCameraInfo
import com.amap.api.navi.model.AMapNaviCross
import com.amap.api.navi.model.AMapNaviLocation
import com.amap.api.navi.model.AMapNaviPath
import com.amap.api.navi.model.AMapNaviRouteNotifyData
import com.amap.api.navi.model.AMapNaviTrafficFacilityInfo
import com.amap.api.navi.model.AMapServiceAreaInfo
import com.amap.api.navi.model.AMapTravelInfo
import com.amap.api.navi.model.AimLessModeCongestionInfo
import com.amap.api.navi.model.AimLessModeStat
import com.amap.api.navi.model.NaviInfo
import com.amap.api.navi.model.NaviLatLng
import com.amap.api.navi.view.RouteOverLay
import com.amap.api.navi.AmapPageType

// 项目内部
import org.zarroboogs.maps.R
import org.zarroboogs.maps.ble.BleDeviceListDialog
import org.zarroboogs.maps.ble.BleDisconnectDialog
import org.zarroboogs.maps.ble.BleService
import org.zarroboogs.maps.ble.BleServiceListener
import org.zarroboogs.maps.core.road.model.LatLngPoint
import org.zarroboogs.maps.core.route.EngineType
import org.zarroboogs.maps.cruise.CruiseModeManager
import org.zarroboogs.maps.data.model.Profile
import org.zarroboogs.maps.data.model.ProfileRepository
import org.zarroboogs.maps.hub.NavStyleManager
import org.zarroboogs.maps.hub.NavigationHubManager
import org.zarroboogs.maps.hub.AlarmController
import org.zarroboogs.maps.ui.BaseActivity
import org.zarroboogs.maps.ui.maps.CameraOverlayManager
import org.zarroboogs.maps.ui.maps.MapsMainActivity
import org.zarroboogs.maps.ui.tencent.MotorProfilePickerDialog
import org.zarroboogs.maps.ui.tencent.TencentMotorRouteActivity
import org.zarroboogs.maps.utils.FileLogger
import org.zarroboogs.maps.utils.SettingUtils

// ============================================================================
// UnifiedNavActivity — 单 Activity 路线规划 + 导航
// ============================================================================
//
// 合并 NaviRouteActivity (884行) + NaviCustomActivity (878行) 为一个 Activity，
// 消除双 Activity 模型带来的路线消失 Bug (BUG-55)。
//
// ## 状态机
// ```
// STATE_ROUTE ──(startNavi)──→ STATE_NAVI ──(back/arrive/cancel)──→ STATE_ROUTE
// ```
//
// ## 核心收益
// 1. 路线数据天然不丢 —— 同一个 Activity，不需要跨 Activity 传路线
// 2. AMapNaviListener 只注册一次 —— 消除双重注册 + deregister/reregister 时序问题
// 3. onResume 不依赖 getNaviPaths() —— 路线数据在成员变量里，stopNavi 不影响
// 4. 对齐 SDK AmapRouteActivity 单 Activity 模型 —— ROUTE↔NAVI 双态切换
//
// ## 布局结构（新文件 activity_unified_nav.xml）
// ```
// FrameLayout
//   ├── MapView (id: route_map_view)          ← ROUTE 态显示
//   ├── AMapNaviView (id: navi_view)          ← NAVI 态显示
//   ├── ROUTE UI 容器 (id: route_ui_container) ← ROUTE 态显示
//   └── NAVI UI 容器 (id: navi_ui_container)   ← NAVI 态显示
// ```
// ROUTE 态和 NAVI 态各用一个独立的 map view，通过 visibility 切换。
// 这是当前双 Activity 模型的最安全映射 —— 两个 View 就是原来两个 Activity 的 View，
// 只是放在了同一个布局里，生命周期统一管理。
//
// ## 不去碰的
// - NaviRouteActivity.java / NaviCustomActivity.java 完全不改
// - BleService / BleWriter / NavigationHubManager / NavStyleManager 不动
// - CameraRouteMatcher / CameraVoiceManager 不动
// - ESP32 端代码不动
// - AndroidManifest 不改
//
// 状态: 🔲 设计参考稿，未接入项目
// @since 2026-06-06
// @see BUG-55

class UnifiedNavActivity : BaseActivity(),
    AMapNaviListener,
    AMapNaviViewListener {

    // ========================================================================
    // 常量
    // ========================================================================

    companion object {
        private const val TAG = "UnifiedNav"

        // Activity 状态
        private const val STATE_ROUTE = 0
        private const val STATE_NAVI = 1

        // 导航模式
        const val MODE_DRIVE = 0
        const val MODE_MOTOR = 1
        const val MODE_RIDE = 2
        const val MODE_WALK = 3
        const val EXTRA_TRAVEL_MODE = "travel_mode"

        // 权限请求码
        private const val REQUEST_LOCATION_PERMISSION = 100
        private const val REQUEST_BLUETOOTH_PERMISSION = 101
        private const val REQUEST_ENABLE_BT = 102
        private const val PERM_BLUETOOTH_SCAN = "android.permission.BLUETOOTH_SCAN"
        private const val PERM_BLUETOOTH_CONNECT = "android.permission.BLUETOOTH_CONNECT"
    }

    // ========================================================================
    // 通用成员
    // ========================================================================

    private var mState = STATE_ROUTE
    private var mNaviMode = MODE_DRIVE
    private var mAmapNavi: AMapNavi? = null

    // ========================================================================
    // ROUTE 态成员（原 NaviRouteActivity）
    // ========================================================================

    // MapView（ROUTE 态专用，对标原 NaviRouteActivity.mapView）
    private var mRouteMapView: MapView? = null
    private var mRouteMap: AMap? = null
    private var mMapLoaded = false

    // 起终点
    private var mStartList = ArrayList<NaviLatLng>()
    private var mEndList = ArrayList<NaviLatLng>()

    // 路线叠加层 + 缓存（BUG-55 修复核心）
    private var mRouteOverlays = SparseArray<RouteOverLay>()
    private var mCalculateSuccess = false
    private var mCurrentRouteIds: IntArray? = null
    private var mSelectedRouteIndex = 0
    private var mZIndex = 1

    // 路线数据缓存（stopNavi 前保存，onResume 从此恢复，不依赖 getNaviPaths()）
    private var mCachedRouteIds: IntArray? = null
    private var mCachedNaviPaths: HashMap<Int, AMapNaviPath>? = null
    private var mCachedStartName: String? = null
    private var mCachedEndName: String? = null

    // 偏好设置
    private var mCongestion = true
    private var mAvoidHighway = false
    private var mCostFirst = false
    private var mHighwayFirst = false

    // ROUTE UI 控件
    private var mRouteUiContainer: View? = null
    private var mStartBtn: ImageButton? = null
    private var mStartEmulatorBtn: Button? = null
    private var mBtnLocate: ImageButton? = null
    private var mBtnRefresh: ImageButton? = null
    private var mBtnPreference: ImageButton? = null
    private var mBackView: ImageView? = null
    private var mDistanceView: TextView? = null
    private var mTimeView: TextView? = null
    private var mCostView: TextView? = null
    private var mCostColumn: LinearLayout? = null
    private var mNavModeSelector: TextView? = null
    private var mRouteSelectorScroll: HorizontalScrollView? = null
    private var mRouteSelectorContainer: LinearLayout? = null
    private var mRouteActionButtons: LinearLayout? = null

    // ========================================================================
    // NAVI 态成员（原 NaviCustomActivity）
    // ========================================================================

    // AMapNaviView（NAVI 态专用，对标原 NaviCustomActivity.mAmapNaviView）
    private var mAmapNaviView: AMapNaviView? = null

    private var mIsGps = true
    private var mTravelMode = MODE_DRIVE
    private lateinit var mAlarmController: AlarmController

    // BLE
    private var mBleService: BleService? = null
    private var mBound = false
    private var mPendingBleScanAfterPermission = false

    // NAVI UI 控件
    private var mNaviUiContainer: View? = null
    private var mBleScanButton: ImageButton? = null
    private var mBleConnectionStatus: ImageView? = null
    private var mBleButtonContainer: View? = null
    private var mBleDeviceListDialog: BleDeviceListDialog? = null
    private var mBleDisconnectDialog: BleDisconnectDialog? = null
    private var mVoiceButton: ImageButton? = null
    private var mVoiceButtonContainer: View? = null
    private var mStyleButton: ImageButton? = null
    private var mStyleButtonContainer: View? = null

    // ========================================================================
    // BLE Service 绑定
    // ========================================================================

    private val mBleServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            mBleService = (service as BleService.LocalBinder).service
            mBleService?.registerListener(mBleServiceListener)
            mBound = true
            mBleService?.setNavActive(true)
            if (mIsGps) mBleService?.startGpsInjection()
            if (!SettingUtils.isGeofenceEnabled()) mBleService?.stopGpsForGeofence()
            updateBleButtonUi(mBleService?.isConnected ?: false)
        }

        override fun onServiceDisconnected(name: ComponentName) {
            mBleService = null
            mBound = false
        }
    }

    private val mBleServiceListener = object : BleServiceListener {
        override fun onConnectionStateChanged(connected: Boolean, deviceName: String) {
            runOnUiThread {
                if (isFinishing) return@runOnUiThread
                updateBleButtonUi(connected)
            }
        }

        override fun onScanStarted() {}

        override fun onScanStopped() {
            runOnUiThread {
                if (isFinishing) return@runOnUiThread
                if (mBleDeviceListDialog?.isShowing == true) {
                    mBleDeviceListDialog?.setScanning(false)
                    mBleDeviceListDialog?.show()
                }
            }
        }

        override fun onScanResult(devices: List<BleServiceListener.BleDeviceEntry>) {
            runOnUiThread {
                if (isFinishing) return@runOnUiThread
                if (mBleDeviceListDialog?.isShowing == true) {
                    mBleDeviceListDialog?.show()
                }
            }
        }

        override fun onError(message: String) {
            runOnUiThread {
                if (isFinishing) return@runOnUiThread
                Toast.makeText(this@UnifiedNavActivity, message, Toast.LENGTH_SHORT).show()
            }
        }

        override fun onDeviceTelemetry(batteryPercent: Int, firmwareVersion: String, heapKb: Int, fps: Int) {
            NavigationHubManager.onDeviceTelemetry(batteryPercent, firmwareVersion, heapKb, fps)
        }
    }

    // ========================================================================
    // 生命周期
    // ========================================================================

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.activity_unified_nav)

        // --- 1. 提取 Intent 参数（原 NaviRouteActivity.onCreate） ---
        val startLat = intent.getDoubleExtra("start_lat", 0.0)
        val startLng = intent.getDoubleExtra("start_lng", 0.0)
        val endLat = intent.getDoubleExtra("end_lat", 0.0)
        val endLng = intent.getDoubleExtra("end_lng", 0.0)
        mStartList.add(NaviLatLng(startLat, startLng))
        mEndList.add(NaviLatLng(endLat, endLng))
        mNaviMode = intent.getIntExtra("navi_mode", MODE_DRIVE)

        FileLogger.d(TAG, "onCreate: start=($startLat,$startLng), end=($endLat,$endLng)")

        // 读取偏好默认值
        mCongestion = NaviPrefs.getAvoidCongestion()
        mAvoidHighway = NaviPrefs.getAvoidHighspeed()
        mCostFirst = NaviPrefs.getAvoidCost()
        mHighwayFirst = NaviPrefs.getHighspeedPriority()

        // --- 2. 初始化 AMapNavi 单例（只做一次） ---
        try {
            mAmapNavi = AMapNavi.getInstance(applicationContext)
            mAmapNavi?.addAMapNaviListener(this)  // ← 只注册一次！
        } catch (e: Exception) {
            FileLogger.e(TAG, "initNavi failed", e)
        }

        // --- 3. 初始化两个 Map View ---
        initRouteMapView(savedInstanceState)
        initNaviMapView(savedInstanceState)

        // --- 4. 初始化 UI ---
        initRouteUI()
        initNaviUI()

        // --- 5. 首次算路 或 从系统回收恢复 ---
        if (savedInstanceState == null) {
            calculateRoute()
        } else {
            mCachedRouteIds = savedInstanceState.getIntArray("cached_route_ids")
            mCachedStartName = savedInstanceState.getString("cached_start_name")
            mCachedEndName = savedInstanceState.getString("cached_end_name")
            if (mCachedRouteIds != null) {
                calculateRoute()  // 系统回收后需要重算路
            }
        }

        // --- 6. 初始状态：ROUTE 态 ---
        setState(STATE_ROUTE)

        // --- 7. 加载摄像头 ---
        loadCameraMarkers()
    }

    override fun onResume() {
        super.onResume()
        when (mState) {
            STATE_ROUTE -> {
                mRouteMapView?.onResume()
                // BUG-55 修复核心：从缓存恢复路线，不依赖 getNaviPaths()
                if (mRouteOverlays.size() == 0 && mCachedNaviPaths != null) {
                    restoreRouteFromCache()
                }
            }
            STATE_NAVI -> {
                // 对标原 NaviCustomActivity.onResume()：
                // 每次 onResume 恢复 AMapNaviView + 重新应用设置
                mAmapNaviView?.onResume()
                applyNaviSettings()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        when (mState) {
            STATE_ROUTE -> mRouteMapView?.onPause()
            STATE_NAVI -> mAmapNaviView?.onPause()
        }
        NavigationHubManager.logEvent("ACTIVITY_PAUSE",
            java.util.Collections.singletonMap("ts", System.currentTimeMillis()))
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mRouteMapView?.onSaveInstanceState(outState)
        mAmapNaviView?.onSaveInstanceState(outState)

        // 保存路线缓存
        mCachedRouteIds?.let { outState.putIntArray("cached_route_ids", it) }
        outState.putString("cached_start_name", mCachedStartName)
        outState.putString("cached_end_name", mCachedEndName)
    }

    override fun onDestroy() {
        // 按状态清理
        if (mState == STATE_NAVI) {
            cleanupNavi()
        }

        // 清理 ROUTE 态资源
        clearRoute()
        mRouteMapView?.onDestroy()

        // 清理 NAVI 态资源（如果 cleanupNavi 中已调 removeListener，此处幂等）
        mAmapNaviView?.onDestroy()
        mAmapNavi?.removeAMapNaviListener(this)

        // 仅在用户主动退出时销毁 AMapNavi 单例
        // （对标 Demo：系统回收时不销毁，保护正在使用的路线数据）
        if (isFinishing) {
            AMapNavi.destroy()
        }

        super.onDestroy()
    }

    // ========================================================================
    // 按键处理
    // ========================================================================

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && mState == STATE_ROUTE) {
            val intent = Intent(this, MapsMainActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            startActivity(intent)
            finish()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onBackPressed() {
        when (mState) {
            STATE_NAVI -> {
                // 对齐高德 Demo：委托给 SDK 处理（弹出红色退出确认框）
                mAmapNaviView?.onBackPressed()
                // SDK 处理后 → onNaviCancel() → switchToRouteMode()
            }
            STATE_ROUTE -> {
                val intent = Intent(this, MapsMainActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                startActivity(intent)
                finish()
            }
        }
    }

    // ========================================================================
    // 状态切换
    // ========================================================================

    private fun setState(state: Int) {
        mState = state
        when (state) {
            STATE_ROUTE -> {
                mRouteMapView?.visibility = View.VISIBLE
                mAmapNaviView?.visibility = View.GONE
                mRouteUiContainer?.visibility = View.VISIBLE
                mNaviUiContainer?.visibility = View.GONE
            }
            STATE_NAVI -> {
                mRouteMapView?.visibility = View.GONE
                mAmapNaviView?.visibility = View.VISIBLE
                mRouteUiContainer?.visibility = View.GONE
                mNaviUiContainer?.visibility = View.VISIBLE
            }
        }
    }

    /**
     * ROUTE → NAVI
     */
    private fun switchToNaviMode(gps: Boolean) {
        // BUG-55 修复核心：缓存路线数据
        cacheCurrentRoute()

        mIsGps = gps
        setState(STATE_NAVI)

        // 停止巡航
        stopCruiseIfRunning()

        // 导航前设置 travel view（必须在 setContentView 之后但必须在 startNavi 之前）
        // 注意：原 NaviCustomActivity 在 setContentView 之前调用了setIsNaviTravelView，
        // 但这里 AMapNaviView 的 MapView 在 onCreate 已创建，所以可以安全调用
        val isTravelView = (mTravelMode == MODE_RIDE || mTravelMode == MODE_WALK)
        mAmapNavi?.setIsNaviTravelView(isTravelView)

        // 启动导航会话
        val totalLen = mAmapNavi?.naviPath?.allLength ?: 0
        NavigationHubManager.startSession(totalLen, EngineType.AMAP)

        // 启动导航
        mAmapNavi?.setEmulatorNaviSpeed(60)
        mAmapNavi?.setUseInnerVoice(true)
        if (gps) {
            mAmapNavi?.startNavi(NaviType.GPS)
            FileLogger.e(TAG, ">>> startNavi: GPS")
        } else {
            mAmapNavi?.startNavi(NaviType.EMULATOR)
            FileLogger.e(TAG, ">>> startNavi: EMULATOR")
        }

        // 绑定 BLE
        bindBleService()

        // 恢复摄像头覆盖层（第二次进入 NAVI 态时 onNaviViewLoaded 不会再次触发）
        mAmapNaviView?.map?.let { map ->
            CameraOverlayManager.getInstance().rebindMap(map)
        }
    }

    /**
     * NAVI → ROUTE
     */
    private fun switchToRouteMode() {
        cleanupNavi()
        setState(STATE_ROUTE)

        // BUG-55 修复核心：从缓存恢复路线
        restoreRouteFromCache()

        // 重新注册 AMapNaviListener（cleanupNavi 中已 remove）
        mAmapNavi?.addAMapNaviListener(this)

        // 恢复 travel view 设置
        mAmapNavi?.setIsNaviTravelView(mTravelMode == MODE_RIDE || mTravelMode == MODE_WALK)
    }

    // ========================================================================
    // 路线缓存与恢复（BUG-55 修复核心）
    // ========================================================================

    /**
     * 缓存当前路线数据。在 stopNavi 前调用。
     *
     * 注意：保存的是 AMapNaviPath 对象引用（非深拷贝）。
     * 如果 SDK 版本在 stopNavi() 后修改/清空了这些对象，
     * 缓存数据将损坏。接入时需实测验证。
     * 保险方案：对 coordList、allLength、allTime 做深拷贝。
     */
    private fun cacheCurrentRoute() {
        val navi = mAmapNavi ?: return
        val routeIds = mCurrentRouteIds ?: return

        mCachedRouteIds = routeIds.copyOf()
        mCachedNaviPaths = HashMap()

        for (id in routeIds) {
            navi.naviPaths?.get(id)?.let { path ->
                mCachedNaviPaths?.put(id, path)
            }
        }
        mCachedStartName = intent.getStringExtra("start_name") ?: "起点"
        mCachedEndName = intent.getStringExtra("end_name") ?: "终点"

        FileLogger.i(TAG, "路线已缓存: ${routeIds.size} 条")
    }

    /**
     * 从缓存恢复路线。不依赖 getNaviPaths()。
     */
    private fun restoreRouteFromCache() {
        val routeIds = mCachedRouteIds ?: return
        val paths = mCachedNaviPaths ?: return
        if (routeIds.isEmpty() || paths.isEmpty()) return

        clearRoute()

        for (id in routeIds) {
            paths[id]?.let { path -> drawRoute(id, path) }
        }

        if (routeIds.size > 1) {
            buildRouteCards(routeIds, paths)
            highlightRouteUI(mSelectedRouteIndex)
        } else if (routeIds.size == 1) {
            updateRouteInfoUI(routeIds[0])
        }

        FileLogger.i(TAG, "路线已从缓存恢复: ${routeIds.size} 条")
    }

    // ========================================================================
    // ROUTE 态 — Map 初始化
    // ========================================================================

    private fun initRouteMapView(savedInstanceState: Bundle?) {
        mRouteMapView = findViewById(R.id.route_map_view)
        mRouteMapView?.onCreate(savedInstanceState)
        mRouteMap = mRouteMapView?.map

        // 暗黑模式
        val dayNightMode = SettingUtils.readDarkMapMode()
        if (dayNightMode == SettingUtils.DAY_NIGHT_MODE_NIGHT
            || (dayNightMode == SettingUtils.DAY_NIGHT_MODE_AUTO && SettingUtils.isNightTime())) {
            mRouteMap?.setMapType(AMap.MAP_TYPE_NIGHT)
        }

        mRouteMap?.setOnMapLoadedListener {
            mMapLoaded = true
            zoomToRouteSpan()
        }
    }

    // ========================================================================
    // ROUTE 态 — UI 初始化
    // ========================================================================

    private fun initRouteUI() {
        mRouteUiContainer = findViewById(R.id.route_ui_container)

        mStartBtn = findViewById(R.id.routestartnavi)
        mBackView = findViewById(R.id.route_back_view)
        mDistanceView = findViewById(R.id.navi_route_distance)
        mTimeView = findViewById(R.id.navi_route_time)
        mCostView = findViewById(R.id.navi_route_cost)
        mCostColumn = findViewById(R.id.route_cost_column)
        mNavModeSelector = findViewById(R.id.nav_mode_selector)
        mRouteSelectorScroll = findViewById(R.id.route_selector_scroll)
        mRouteSelectorContainer = findViewById(R.id.route_selector_container)
        mRouteActionButtons = findViewById(R.id.route_action_buttons)
        mBtnLocate = findViewById(R.id.btn_locate)
        mBtnRefresh = findViewById(R.id.btn_refresh)
        mBtnPreference = findViewById(R.id.btn_preference)

        mStartBtn?.isEnabled = false
        mStartBtn?.setOnClickListener { switchToNaviMode(true) }

        mStartEmulatorBtn = findViewById(R.id.routestartemulator)
        mStartEmulatorBtn?.isEnabled = false
        mStartEmulatorBtn?.setOnClickListener { switchToNaviMode(false) }

        mBackView?.setOnClickListener { finish() }
        mBtnLocate?.setOnClickListener { animateToStart() }
        mBtnRefresh?.setOnClickListener { calculateRoute() }
        mBtnPreference?.setOnClickListener { showPreferenceDialog() }
        mNavModeSelector?.setOnClickListener { showNavModePopup() }
    }

    // ========================================================================
    // ROUTE 态 — 算路
    // ========================================================================

    private fun calculateRoute() {
        // 摩托车模式不调 AMap API
        if (mNaviMode == MODE_MOTOR) return

        if (mAmapNavi == null || mStartList.isEmpty() || mEndList.isEmpty()) {
            FileLogger.e(TAG, "calculateRoute failed: navi=${mAmapNavi != null} startList=${mStartList.size} endList=${mEndList.size}")
            handleRouteFailure()
            return
        }

        mCalculateSuccess = false
        mStartBtn?.isEnabled = false
        mStartEmulatorBtn?.isEnabled = false
        clearRoute()

        val from = mStartList[0]
        val to = mEndList[0]

        when (mNaviMode) {
            MODE_RIDE -> {
                mAmapNavi?.calculateRideRoute(from, to)
                FileLogger.d(TAG, "calculateRoute: RIDE")
            }
            MODE_WALK -> {
                mAmapNavi?.calculateWalkRoute(from, to)
                FileLogger.d(TAG, "calculateRoute: WALK")
            }
            MODE_DRIVE -> {
                // 冲突检测（对标 Demo）
                if (mAvoidHighway && mHighwayFirst) {
                    Toast.makeText(this, "不走高速与高速优先不能同时为true", Toast.LENGTH_SHORT).show()
                }
                if (mCostFirst && mHighwayFirst) {
                    Toast.makeText(this, "高速优先与避免收费不能同时为true", Toast.LENGTH_SHORT).show()
                }
                val strategy = mAmapNavi?.strategyConvert(
                    mCongestion, mAvoidHighway, mCostFirst, mHighwayFirst, true) ?: 0
                mAmapNavi?.calculateDriveRoute(mStartList, mEndList, null, strategy)
                FileLogger.d(TAG, "calculateRoute: DRIVE strategy=$strategy")
            }
        }
    }

    // ========================================================================
    // ROUTE 态 — 路线绘制
    // ========================================================================

    /**
     * 对标 Demo RestRouteShowActivity.drawRoutes。
     * RouteOverLay 绑定在 ROUTE 态的 MapView 上。
     */
    private fun drawRoute(routeId: Int, path: AMapNaviPath) {
        mCalculateSuccess = true
        mRouteMap?.moveCamera(CameraUpdateFactory.changeTilt(0f))
        val overlay = RouteOverLay(mRouteMap, path, this)
        overlay.setTrafficLine(false)
        overlay.addToMap()
        mRouteOverlays.put(routeId, overlay)
    }

    /**
     * 对标 Demo RestRouteShowActivity.clearRoute。
     */
    private fun clearRoute() {
        for (i in 0 until mRouteOverlays.size()) {
            mRouteOverlays.valueAt(i).removeFromMap()
        }
        mRouteOverlays.clear()
        mCurrentRouteIds = null
        mSelectedRouteIndex = 0
        mRouteSelectorContainer?.removeAllViews()
        mRouteSelectorScroll?.visibility = View.GONE
        updateActionButtonsPosition(false)
    }

    private fun zoomToRouteSpan() {
        if (mRouteOverlays.size() > 0) {
            mRouteOverlays.valueAt(0).zoomToSpan()
        }
    }

    private fun animateToStart() {
        if (mStartList.isNotEmpty()) {
            val start = mStartList[0]
            mRouteMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(
                LatLng(start.latitude, start.longitude), 15f))
        }
    }

    // ========================================================================
    // ROUTE 态 — 路线选择卡片
    // ========================================================================

    /**
     * 对标 Demo RestRouteShowActivity 路线选择。
     * 单路线时隐藏卡片，多路线时构建水平可滚动卡片。
     */
    private fun buildRouteCards(routeIds: IntArray, paths: Map<Int, AMapNaviPath>) {
        mCurrentRouteIds = routeIds
        mRouteSelectorContainer?.removeAllViews()

        if (routeIds.size <= 1) {
            mRouteSelectorScroll?.visibility = View.GONE
            updateActionButtonsPosition(false)
            if (routeIds.size == 1) {
                mSelectedRouteIndex = 0
                updateRouteInfoUI(routeIds[0])
            }
            return
        }

        mRouteSelectorScroll?.visibility = View.VISIBLE
        updateActionButtonsPosition(true)
        val inflater = LayoutInflater.from(this)

        for (i in routeIds.indices) {
            val path = paths[routeIds[i]] ?: continue

            val card = inflater.inflate(R.layout.item_route_card, mRouteSelectorContainer, false)
            val labelView = card.findViewById<TextView>(R.id.route_card_label)
            val timeView = card.findViewById<TextView>(R.id.route_card_time)
            val distView = card.findViewById<TextView>(R.id.route_card_dist)

            val labels = path.labels
            labelView.text = if (labels.isNullOrEmpty()) "路线${i + 1}" else labels

            val minutes = (path.allTime + 59) / 60
            timeView.text = "${minutes}分钟"

            val km = (path.allLength / 100.0).toInt() / 10.0
            val toll = path.tollCost
            distView.text = if (toll > 0) "${km}km · ¥${toll}" else "${km}km · 免费"

            val index = i
            card.setOnClickListener { onRouteCardClicked(index) }
            mRouteSelectorContainer?.addView(card)
        }

        // 默认高亮第一条（不在 SDK 回调栈中调 selectRouteId）
        highlightRouteUI(0)
    }

    /**
     * 高亮选中路线（不调 selectRouteId）。
     * 用于 onCalculateRouteSuccess 回调中的默认选中。
     */
    private fun highlightRouteUI(index: Int) {
        if (mCurrentRouteIds == null || index < 0 || index >= mCurrentRouteIds!!.size) return
        mSelectedRouteIndex = index
        val routeId = mCurrentRouteIds!![index]

        // 地图路线高亮
        for (i in 0 until mRouteOverlays.size()) {
            mRouteOverlays.valueAt(i).setTransparency(0.4f)
        }
        val selected = mRouteOverlays.get(routeId)
        if (selected != null) {
            selected.setTransparency(1f)
            selected.setZindex(mZIndex++)
        }

        // 更新底部信息
        updateRouteInfoUI(routeId)

        // 卡片选中态
        val container = mRouteSelectorContainer ?: return
        for (i in 0 until container.childCount) {
            val card = container.getChildAt(i)
            if (i == index) {
                card.setBackgroundResource(R.drawable.route_card_selected)
                card.findViewById<TextView>(R.id.route_card_label).setTextColor(0xFF000000.toInt())
                card.findViewById<TextView>(R.id.route_card_time).setTextColor(0xFF333333.toInt())
                card.findViewById<TextView>(R.id.route_card_dist).setTextColor(0xFF555555.toInt())
            } else {
                card.setBackgroundResource(R.drawable.route_card_normal)
                card.findViewById<TextView>(R.id.route_card_label).setTextColor(0xFFFFFFFF.toInt())
                card.findViewById<TextView>(R.id.route_card_time).setTextColor(0xFFB0B0B0.toInt())
                card.findViewById<TextView>(R.id.route_card_dist).setTextColor(0xFF808080.toInt())
            }
        }
    }

    /**
     * 用户点击路线卡片（onClick 事件，不在 SDK 回调栈中）。
     */
    private fun onRouteCardClicked(index: Int) {
        highlightRouteUI(index)
        mCurrentRouteIds?.let { ids ->
            if (index in ids.indices) {
                mAmapNavi?.selectRouteId(ids[index])
            }
        }
    }

    private fun updateRouteInfoUI(routeId: Int) {
        val navi = mAmapNavi
        if (navi == null) {
            handleRouteFailure()
            return
        }
        val path = navi.naviPaths[routeId]
        if (path == null) {
            handleRouteFailure()
            return
        }
        mCalculateSuccess = true
        mDistanceView?.text = "${(path.allLength / 100.0).toInt() / 10.0}"
        mTimeView?.text = "${(path.allTime + 59) / 60}"

        if (mTravelMode == MODE_RIDE || mTravelMode == MODE_WALK) {
            mCostColumn?.visibility = View.GONE
        } else {
            mCostColumn?.visibility = View.VISIBLE
            mCostView?.text = "${path.tollCost}"
        }

        mStartBtn?.isEnabled = true
        mStartEmulatorBtn?.isEnabled = true

        if (mMapLoaded) {
            zoomToRouteSpan()
        }
    }

    private fun handleRouteFailure() {
        mCalculateSuccess = false
        mStartBtn?.isEnabled = false
        mStartEmulatorBtn?.isEnabled = false
        Toast.makeText(this, "规划失败", Toast.LENGTH_SHORT).show()
    }

    private fun updateActionButtonsPosition(selectorVisible: Boolean) {
        val lp = mRouteActionButtons?.layoutParams as? RelativeLayout.LayoutParams ?: return
        lp.removeRule(RelativeLayout.ABOVE)
        if (selectorVisible) {
            lp.addRule(RelativeLayout.ABOVE, R.id.route_selector_scroll)
        } else {
            lp.addRule(RelativeLayout.ABOVE, R.id.navi_cast_root_rl)
        }
        mRouteActionButtons?.layoutParams = lp
    }

    // ========================================================================
    // ROUTE 态 — 偏好 & 模式选择
    // ========================================================================

    private fun showNavModePopup() {
        val popup = PopupMenu(this, mNavModeSelector)
        popup.menu.add(0, MODE_DRIVE, 0, R.string.nav_mode_drive)
        popup.menu.add(0, MODE_MOTOR, 1, R.string.nav_mode_motor)
        popup.menu.add(0, MODE_RIDE, 2, R.string.nav_mode_ride)
        popup.menu.add(0, MODE_WALK, 3, R.string.nav_mode_walk)
        popup.setOnMenuItemClickListener { item ->
            val newMode = item.itemId
            if (newMode != mNaviMode) {
                mNaviMode = newMode
                mNavModeSelector?.text = item.title
                onNavModeChanged()
            }
            true
        }
        popup.show()
    }

    private fun onNavModeChanged() {
        clearRoute()
        when (mNaviMode) {
            MODE_DRIVE -> {
                mAmapNavi?.carInfo = AMapCarInfo()
                mBtnPreference?.visibility = View.VISIBLE
            }
            MODE_RIDE -> {
                mAmapNavi?.setIsNaviTravelView(true)
                mAmapNavi?.travelInfo = AMapTravelInfo(TransportType.Ride)
                mBtnPreference?.visibility = View.GONE
            }
            MODE_MOTOR -> {
                mBtnPreference?.visibility = View.GONE
                showMotorProfilePicker()
                mNaviMode = MODE_DRIVE
                mNavModeSelector?.setText(R.string.nav_mode_drive)
                return
            }
            MODE_WALK -> {
                mAmapNavi?.setIsNaviTravelView(true)
                mAmapNavi?.travelInfo = AMapTravelInfo(TransportType.Walk)
                mBtnPreference?.visibility = View.GONE
            }
        }
        calculateRoute()
    }

    private fun showPreferenceDialog() {
        if (mNaviMode == MODE_RIDE || mNaviMode == MODE_WALK || mNaviMode == MODE_MOTOR) {
            Toast.makeText(this, "当前模式不支持路线偏好设置", Toast.LENGTH_SHORT).show()
            return
        }
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_route_preference, null)

        val btnCongestion = dialogView.findViewById<TextView>(R.id.btn_congestion)
        val btnAvoidHightspeed = dialogView.findViewById<TextView>(R.id.btn_avoidhightspeed)
        val btnCost = dialogView.findViewById<TextView>(R.id.btn_cost)
        val btnHightspeed = dialogView.findViewById<TextView>(R.id.btn_hightspeed)

        updateButtonBackground(btnCongestion, mCongestion)
        updateButtonBackground(btnAvoidHightspeed, mAvoidHighway)
        updateButtonBackground(btnCost, mCostFirst)
        updateButtonBackground(btnHightspeed, mHighwayFirst)

        btnCongestion.setOnClickListener {
            mCongestion = !mCongestion
            updateButtonBackground(btnCongestion, mCongestion)
            calculateRoute()
        }
        btnAvoidHightspeed.setOnClickListener {
            mAvoidHighway = !mAvoidHighway
            if (mAvoidHighway && mHighwayFirst) {
                mHighwayFirst = false
                updateButtonBackground(btnHightspeed, false)
            }
            updateButtonBackground(btnAvoidHightspeed, mAvoidHighway)
            calculateRoute()
        }
        btnCost.setOnClickListener {
            mCostFirst = !mCostFirst
            if (mCostFirst && mHighwayFirst) {
                mHighwayFirst = false
                updateButtonBackground(btnHightspeed, false)
            }
            updateButtonBackground(btnCost, mCostFirst)
            calculateRoute()
        }
        btnHightspeed.setOnClickListener {
            mHighwayFirst = !mHighwayFirst
            if (mHighwayFirst) {
                mAvoidHighway = false
                mCostFirst = false
                updateButtonBackground(btnAvoidHightspeed, false)
                updateButtonBackground(btnCost, false)
            }
            updateButtonBackground(btnHightspeed, mHighwayFirst)
            calculateRoute()
        }

        AlertDialog.Builder(this).setView(dialogView).show()
    }

    private fun updateButtonBackground(button: TextView, isSelected: Boolean) {
        if (isSelected) {
            button.setBackgroundResource(R.drawable.route_card_selected)
            button.setTextColor(0xFF000000.toInt())
        } else {
            button.setBackgroundResource(R.drawable.route_card_normal)
            button.setTextColor(0xFFFFFFFF.toInt())
        }
    }

    // ========================================================================
    // NAVI 态 — 初始化
    // ========================================================================

    private fun initNaviMapView(savedInstanceState: Bundle?) {
        mAmapNaviView = findViewById(R.id.navi_view)
        mAmapNaviView?.onCreate(savedInstanceState)
        mAmapNaviView?.setAMapNaviViewListener(this)
        mAmapNaviView?.visibility = View.GONE  // 初始隐藏
    }

    private fun initNaviUI() {
        mNaviUiContainer = findViewById(R.id.navi_ui_container)
        mNaviUiContainer?.visibility = View.GONE  // 初始隐藏

        // 报警三件套：CameraRouteMatcher + CameraVoiceManager + 音量按钮
        mAlarmController = AlarmController(applicationContext)
        mVoiceButtonContainer = findViewById(R.id.navi_voice_button_container)
        mVoiceButton = findViewById(R.id.navi_voice_button)
        mAlarmController.bind(mVoiceButton!!, mTravelMode)

        // 样式切换按钮（每次导航默认箭头模式）
        NavStyleManager.reset()
        mStyleButtonContainer = findViewById(R.id.navi_style_button_container)
        mStyleButton = findViewById(R.id.navi_style_button)
        mStyleButton?.setOnClickListener {
            NavStyleManager.toggle()
            updateStyleButtonIcon()
        }
        updateStyleButtonIcon()
        mStyleButton?.isEnabled = false
        mStyleButton?.alpha = 0.35f

        // BLE 按钮
        mBleButtonContainer = findViewById(R.id.navi_ble_button_container)
        mBleScanButton = findViewById(R.id.navi_ble_scan_button)
        mBleConnectionStatus = findViewById(R.id.navi_ble_connection_status)
        mBleScanButton?.setOnClickListener {
            if (mBleService?.isConnected == true) {
                if (mBleDisconnectDialog == null) {
                    mBleDisconnectDialog = BleDisconnectDialog(this, mBleService!!)
                }
                mBleDisconnectDialog?.show()
            } else if (mBleService != null) {
                if (!checkAndRequestPermissionsForBleScan()) return@setOnClickListener
                val adapter = (getSystemService(Context.BLUETOOTH_SERVICE) as android.bluetooth.BluetoothManager).adapter
                if (adapter == null || !adapter.isEnabled) {
                    startActivityForResult(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE), REQUEST_ENABLE_BT)
                    return@setOnClickListener
                }
                openBleDeviceListAndScan()
            }
        }
        updateBleButtonUi(false)
    }

    // ========================================================================
    // NAVI 态 — 导航设置
    // ========================================================================

    /**
     * 对标原 NaviCustomActivity.applyNaviSettings()。
     */
    private fun applyNaviSettings() {
        val navi = mAmapNavi ?: return
        val naviView = mAmapNaviView ?: return

        val viewMode = if (mTravelMode == MODE_WALK) {
            NaviPrefs.getNaviViewMode(AMapNaviView.NORTH_UP_MODE)
        } else {
            NaviPrefs.getNaviViewMode()
        }
        val dayNightMode = NaviPrefs.getDayNightMode()
        val broadcastMode = NaviPrefs.getBroadcastMode()
        val autoZoom = NaviPrefs.getScaleAutoZoom()

        val viewOptions = naviView.viewOptions
        if (viewOptions != null) {
            viewOptions.setTrafficLine(NaviPrefs.getTrafficInfoUpdateEnabled())
            viewOptions.setSettingMenuEnabled(true)
            viewOptions.setShowSettingsPanel(true)
            viewOptions.setBroadcastModeEnabled(true)
            viewOptions.setShowRouteStrategyPreferencePanel(true)
            viewOptions.setNaviMode(viewMode)

            val mapStyle = when (dayNightMode) {
                1 -> MapStyle.DAY
                2 -> MapStyle.NIGHT
                else -> MapStyle.AUTO
            }
            viewOptions.setMapStyle(mapStyle, null)
            viewOptions.setAutoChangeZoom(autoZoom)
            naviView.setViewOptions(viewOptions)
        }

        navi.naviSetting?.setScreenAlwaysBright(NaviPrefs.getScreenAlwaysBright())

        val sdkMode = when (broadcastMode) {
            1 -> 1   // CONCISE
            2 -> 3   // MUTE
            else -> 2 // DETAIL
        }
        navi.setBroadcastMode(sdkMode)

        FileLogger.d(TAG, "Nav settings: viewMode=$viewMode dayNight=$dayNightMode broadcast=$broadcastMode autoZoom=$autoZoom")
    }

    /**
     * 清理导航资源（不销毁 AMapNavi 单例）。
     */
    private fun cleanupNavi() {
        mBleService?.stopGpsInjection()
        unbindBleService()

        NavigationHubManager.stopSession()

        mAmapNavi?.stopNavi()
        mAmapNavi?.removeAMapNaviListener(this)

        mBleService?.setNavActive(false)

        CameraOverlayManager.getInstance().detachMap()

        NavStyleManager.reset()
    }

    // ========================================================================
    // BLE
    // ========================================================================

    private fun bindBleService() {
        val intent = Intent(applicationContext, BleService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            applicationContext.startForegroundService(intent)
        } else {
            applicationContext.startService(intent)
        }
        applicationContext.bindService(intent, mBleServiceConnection, Context.BIND_AUTO_CREATE)
    }

    private fun unbindBleService() {
        if (mBound) {
            applicationContext.unbindService(mBleServiceConnection)
            mBound = false
        }
    }

    private fun updateBleButtonUi(connected: Boolean) {
        if (connected) {
            mBleConnectionStatus?.visibility = View.GONE
            mBleScanButton?.setImageResource(R.drawable.ble_active)
            mBleScanButton?.clearColorFilter()
        } else {
            mBleConnectionStatus?.visibility = View.GONE
            mBleScanButton?.setImageResource(R.drawable.ble)
            mBleScanButton?.clearColorFilter()
        }
        mStyleButton?.isEnabled = connected
        mStyleButton?.alpha = if (connected) 1.0f else 0.35f
    }

    private fun adjustBleButtonPosition(sdkButtonsVisible: Boolean) {
        val density = resources.displayMetrics.density
        mStyleButtonContainer?.let {
            val lp = it.layoutParams as FrameLayout.LayoutParams
            lp.bottomMargin = (if (sdkButtonsVisible) 380 else 260 * density).toInt()
            it.layoutParams = lp
        }
        mVoiceButtonContainer?.let {
            val lp = it.layoutParams as FrameLayout.LayoutParams
            lp.bottomMargin = (if (sdkButtonsVisible) 320 else 200 * density).toInt()
            it.layoutParams = lp
        }
        mBleButtonContainer?.let {
            val lp = it.layoutParams as FrameLayout.LayoutParams
            lp.bottomMargin = (if (sdkButtonsVisible) 260 else 140 * density).toInt()
            it.layoutParams = lp
        }
    }

    private fun updateStyleButtonIcon() {
        mStyleButton?.setImageResource(
            when (NavStyleManager.currentStyle) {
                NavStyleManager.STYLE_ARROW -> R.drawable.jiantou
                NavStyleManager.STYLE_ROAD  -> R.drawable.luwang
                else                        -> R.drawable.wandao  // STYLE_LEAN
            }
        )
    }

    private fun openBleDeviceListAndScan() {
        val service = mBleService ?: return
        if (mBleDeviceListDialog == null) {
            mBleDeviceListDialog = BleDeviceListDialog(this, service)
        }
        mBleDeviceListDialog?.setScanning(true)
        mBleDeviceListDialog?.show()
        service.startScan()
    }

    // ========================================================================
    // 权限
    // ========================================================================

    private fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun checkAndRequestPermissionsForBleScan(): Boolean {
        if (Build.VERSION.SDK_INT >= 31) {
            val hasScan = hasPermission(PERM_BLUETOOTH_SCAN)
            val hasConnect = hasPermission(PERM_BLUETOOTH_CONNECT)
            if (!hasScan || !hasConnect) {
                mPendingBleScanAfterPermission = true
                requestBluetoothPermissions()
                return false
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
                mPendingBleScanAfterPermission = true
                requestBluetoothPermissions()
                return false
            }
        }
        return true
    }

    private fun requestBluetoothPermissions() {
        if (Build.VERSION.SDK_INT >= 31) {
            requestPermissions(arrayOf(PERM_BLUETOOTH_SCAN, PERM_BLUETOOTH_CONNECT),
                REQUEST_BLUETOOTH_PERMISSION)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                REQUEST_LOCATION_PERMISSION)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        val allGranted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }
        if (allGranted && mPendingBleScanAfterPermission) {
            mPendingBleScanAfterPermission = false
            if (mBleService != null) {
                openBleDeviceListAndScan()
            }
        } else if (!allGranted) {
            mPendingBleScanAfterPermission = false
            Toast.makeText(this, "需要蓝牙权限才能扫描设备", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_ENABLE_BT && resultCode == RESULT_OK) {
            if (mBleService != null) {
                openBleDeviceListAndScan()
            }
        }
    }

    // ========================================================================
    // AMapNaviListener
    // ========================================================================

    override fun onCalculateRouteSuccess(routeIds: IntArray?) {
        // 驾车模式 int[] 版本回调 — 骑行/步行模式用
        if ((mNaviMode == MODE_RIDE || mNaviMode == MODE_WALK) && routeIds != null && routeIds.isNotEmpty()) {
            FileLogger.d(TAG, "onCalculateRouteSuccess(int[]) travel mode, count=${routeIds.size}")
            clearRoute()
            val paths = mAmapNavi?.naviPaths ?: return
            for (id in routeIds) {
                paths[id]?.let { drawRoute(id, it) }
            }
            buildRouteCards(routeIds, paths)
        }
    }

    override fun onCalculateRouteSuccess(result: AMapCalcRouteResult?) {
        if (mState == STATE_ROUTE) {
            // === ROUTE 态：绘制路线（原 NaviRouteActivity） ===
            FileLogger.d(TAG, "onCalculateRouteSuccess(AMapCalcRouteResult) ROUTE mode")
            if (result == null || mAmapNavi == null) return

            clearRoute()
            val routeIds = result.routeid ?: return
            if (routeIds.isEmpty()) return

            val paths = mAmapNavi!!.naviPaths
            for (id in routeIds) {
                paths[id]?.let { drawRoute(id, it) }
            }

            Toast.makeText(this, "路线规划成功", Toast.LENGTH_SHORT).show()
            buildRouteCards(routeIds, paths)

        } else {
            // === NAVI 态：偏航重算（原 NaviCustomActivity） ===
            FileLogger.d(TAG, "onCalculateRouteSuccess(AMapCalcRouteResult) NAVI mode (reroute)")

            // 缓存新路线到 CameraRouteMatcher
            val path = mAmapNavi?.naviPath
            if (path != null) {
                mAlarmController.matcher.onRouteRecalculated(path.coordList)
                // v2.0: 偏航重算后更新路线 + 路网模式下重推
                val pts = ArrayList<LatLngPoint>()
                for (c in path.coordList) {
                    pts.add(LatLngPoint(c.latitude, c.longitude))
                }
                NavStyleManager.routePolyline = pts
                if (NavStyleManager.currentStyle == NavStyleManager.STYLE_ROAD) {
                    FileLogger.i(TAG, "偏航重算 → 路网模式重推")
                    val ok = NavStyleManager.pushRoadNetwork()
                    if (!ok) {
                        FileLogger.e(TAG, "偏航重算路网推送失败，退回箭头")
                        NavStyleManager.setStyle(NavStyleManager.STYLE_ARROW)
                    }
                }
            }

            // 重算路后 SDK 会重置部分设置，重新应用
            applyNaviSettings()
            NavigationHubManager.logEvent("REROUTE_COMPLETE",
                mapOf("ts" to System.currentTimeMillis()))
        }
    }

    override fun onCalculateRouteFailure(errorCode: Int) {
        FileLogger.e(TAG, "onCalculateRouteFailure errorCode=$errorCode")
        handleRouteFailure()
    }

    override fun onCalculateRouteFailure(result: AMapCalcRouteResult?) {
        FileLogger.e(TAG, "onCalculateRouteFailure result=${result?.errorCode ?: -1}")
        handleRouteFailure()
    }

    override fun onInitNaviFailure() {
        if (mState == STATE_ROUTE) handleRouteFailure()
    }

    override fun onInitNaviSuccess() {}

    override fun onStartNavi(type: Int) {
        FileLogger.w("MotoNav", "[UnifiedNav] onStartNavi type=$type")
        // 缓存路线到 CameraRouteMatcher + NavStyleManager
        val path = mAmapNavi?.naviPath ?: return
        mAlarmController.matcher.setRoute(path.coordList)

        val pts = ArrayList<LatLngPoint>()
        for (c in path.coordList) {
            pts.add(LatLngPoint(c.latitude, c.longitude))
        }
        NavStyleManager.routePolyline = pts
    }

    override fun onTrafficStatusUpdate() {}

    override fun onLocationChange(location: AMapNaviLocation?) {
        if (mState != STATE_NAVI) return
        if (location == null) return

        NavigationHubManager.updateSpeed(location.speed)

        // v2.0: 同步 GPS/速度/航向到 NavStyleManager
        NavStyleManager.currentGps = LatLngPoint(location.coord.latitude, location.coord.longitude)
        NavStyleManager.currentSpeedKmh = location.speed
        var h = location.roadBearing  // 道路方向优先（静止也稳定）
        if (h <= 0f) h = location.bearing
        NavStyleManager.currentHeadingDeg = h

        // 报警三件套：按驾驶模式自动分流
        mAlarmController.onLocationUpdate(
            LatLng(location.coord.latitude, location.coord.longitude))
    }

    override fun onGetNavigationText(type: Int, text: String?) {}
    override fun onGetNavigationText(s: String?) {}
    override fun onEndEmulatorNavi() {
        mBleService?.stopGpsInjection()
        NavigationHubManager.stopSession()
        mBleService?.setNavActive(false)
    }
    override fun onArriveDestination() {
        mBleService?.stopGpsInjection()
        NavigationHubManager.stopSession()
        mBleService?.setNavActive(false)
    }
    override fun onReCalculateRouteForYaw() {
        FileLogger.d(TAG, "onReCalculateRouteForYaw")
        NavigationHubManager.logEvent("REROUTE_START", java.util.Collections.singletonMap("reason", "yaw"))
        mAlarmController.onRerouteStart()
    }
    override fun onReCalculateRouteForTrafficJam() {
        FileLogger.d(TAG, "onReCalculateRouteForTrafficJam")
        NavigationHubManager.logEvent("REROUTE_START", java.util.Collections.singletonMap("reason", "trafficJam"))
        mAlarmController.onRerouteStart()
    }
    override fun onArrivedWayPoint(wayID: Int) {}
    override fun onGpsOpenStatus(enabled: Boolean) {
        FileLogger.d(TAG, "onGpsOpenStatus=$enabled")
    }

    override fun onNaviInfoUpdate(naviinfo: NaviInfo?) {
        if (mState != STATE_NAVI) return
        if (naviinfo == null) return

        NavigationHubManager.updateNavState(
            naviinfo.iconType,
            naviinfo.curStepRetainDistance,
            naviinfo.pathRetainDistance,
            mAmapNavi?.naviPath?.allLength ?: 0)

        // 速度仅走 onLocationChange（SDK 官方推荐，NaviInfo.currentSpeed 已 deprecated）
    }

    override fun onServiceAreaUpdate(serviceAreaInfos: Array<out AMapServiceAreaInfo>?) {}

    /**
     * 高德电子眼回调不用于摩托车报警。
     * 报警逻辑由 onLocationChange → CameraRouteMatcher → 本地 DB 处理。
     */
    /** 高德电子眼回调 — 驾车模式报警来源（v1.3 含限速值） */
    override fun updateCameraInfo(cameraInfos: Array<out AMapNaviCameraInfo>?) {
        if (mTravelMode != MODE_DRIVE) return
        if (cameraInfos.isNullOrEmpty()) {
            NavigationHubManager.updateAlarm(0x00, 0)
            return
        }
        // 取最近摄像头
        var nearest = cameraInfos[0]
        for (i in 1 until cameraInfos.size) {
            if (cameraInfos[i].cameraDistance < nearest.cameraDistance) {
                nearest = cameraInfos[i]
            }
        }
        val alarm = (nearest.cameraType + 0x10) and 0xFF
        val limitSpeed = if (nearest.cameraSpeed > 0) nearest.cameraSpeed else 0
        NavigationHubManager.updateAlarm(alarm, limitSpeed)
    }
    override fun updateIntervalCameraInfo(a: AMapNaviCameraInfo?, b: AMapNaviCameraInfo?, i: Int) {}
    override fun onPlayRing(playRing: Int) {}
    override fun showCross(cross: AMapNaviCross?) {}
    override fun hideCross() {}
    override fun showLaneInfo(laneInfo: AMapLaneInfo?) {}
    override fun showLaneInfo(laneInfos: Array<out AMapLaneInfo>?, bg: ByteArray?, rec: ByteArray?) {}
    override fun hideLaneInfo() {}
    override fun showModeCross(modelCross: AMapModelCross?) {}
    override fun hideModeCross() {}
    override fun notifyParallelRoad(parallelRoadStatus: Int) {}
    override fun OnUpdateTrafficFacility(info: AMapNaviTrafficFacilityInfo?) {}
    override fun OnUpdateTrafficFacility(infos: Array<out AMapNaviTrafficFacilityInfo>?) {}
    override fun updateAimlessModeStatistics(stat: AimLessModeStat?) {}
    override fun updateAimlessModeCongestionInfo(info: AimLessModeCongestionInfo?) {}

    override fun onNaviRouteNotify(routeNotifyData: AMapNaviRouteNotifyData?) {}
    override fun onGpsSignalWeak(isWeak: Boolean) {}

    // ========================================================================
    // AMapNaviViewListener
    // ========================================================================

    override fun onNaviCancel() {
        FileLogger.e(TAG, ">>> onNaviCancel, switching to ROUTE mode")
        switchToRouteMode()
    }

    override fun onNaviSetting() {}
    override fun onNaviMapMode(naviMode: Int) {}
    override fun onNaviTurnClick() {}
    override fun onNextRoadClick() {}
    override fun onScanViewButtonClick() {}

    override fun onLockMap(isLock: Boolean) {
        adjustBleButtonPosition(!isLock)
    }

    override fun onNaviViewLoaded() {
        FileLogger.d(TAG, "onNaviViewLoaded")
        applyNaviSettings()
        mAmapNaviView?.map?.let { map ->
            CameraOverlayManager.getInstance().rebindMap(map)
        }
    }

    override fun onMapTypeChanged(i: Int) {}
    override fun onNaviViewShowMode(i: Int) {
        FileLogger.d(TAG, "onNaviViewShowMode=$i")
    }
    override fun onStopSpeaking() {}
    override fun onViewTypeChanged(amapPageType: AmapPageType?) {}

    override fun onAMapNaviViewExit() {
        FileLogger.e(TAG, ">>> onAMapNaviViewExit, isGps=$mIsGps")
        if (!mIsGps) {
            FileLogger.e(TAG, ">>> onAMapNaviViewExit: emulator, switching to ROUTE")
            switchToRouteMode()
        }
    }

    override fun onStrategyChanged(i: Int) {}

    override fun onBroadcastModeChanged(mode: Int) {
        val appMode = when (mode) {
            1 -> 1  // CONCISE
            3 -> 2  // MUTE
            else -> 0 // DETAIL
        }
        NaviPrefs.setBroadcastMode(appMode)
    }

    override fun onDayAndNightModeChanged(i: Int) {}
    override fun onScaleAutoChanged(b: Boolean) {}
    override fun onListenToVoiceDuringCallChanged(b: Boolean) {}
    override fun onControlMusicVolumeModeChanged(i: Int) {}
    override fun onEagleChanged(b: Boolean) {}
    override fun onNaviRouteHighlightChange(l: Long, i: Int) {}

    /**
     * 对齐高德 Demo：返回 false 让 SDK 弹出退出确认框。
     */
    override fun onNaviBackClick(): Boolean {
        FileLogger.e(TAG, ">>> onNaviBackClick, isGps=$mIsGps")
        return false
    }

    // ========================================================================
    // 辅助方法
    // ========================================================================

    private fun loadCameraMarkers() {
        mRouteMap?.let { map ->
            val mgr = CameraOverlayManager.getInstance()
            if (mgr.isShowing) {
                mgr.show(map)
            }
        }
    }

    private fun stopCruiseIfRunning() {
        val cruiseMgr = CruiseModeManager.getInstance()
        if (cruiseMgr != null && cruiseMgr.isRunning) {
            cruiseMgr.stop()
            SettingUtils.writeCurrentCruiseState(SettingUtils.SWITCH_OFF)
            FileLogger.i(TAG, "Cruise mode auto-cancelled before navigation")
        }
    }

    private fun stopGeofenceForNavi() {
        SettingUtils.writeGeofenceEnabled(false)
        mBleService?.stopGpsForGeofence()
    }

    // ========================================================================
    // 摩托车型号选择
    // ========================================================================

    private fun showMotorProfilePicker() {
        val allProfiles = ProfileRepository.loadProfiles(this)
        val motorProfiles = ArrayList<Profile>()
        for (p in allProfiles) {
            if ("摩托车" == p.type) {
                motorProfiles.add(p)
            }
        }

        val dialog = MotorProfilePickerDialog.newInstance(motorProfiles)
        dialog.setOnProfileSelectedListener(object : MotorProfilePickerDialog.OnProfileSelectedListener {
            override fun onProfileSelected(index: Int) {
                if (index >= 0 && index < motorProfiles.size) {
                    launchTencentRoute(motorProfiles[index])
                }
            }

            override fun onNoConfigSelected() {
                launchTencentRoute(null)
            }
        })
        dialog.show(supportFragmentManager, "motor_profile_picker")
    }

    private fun launchTencentRoute(profile: Profile?) {
        if (mStartList.isEmpty() || mEndList.isEmpty()) {
            Toast.makeText(this, "缺少起终点信息", Toast.LENGTH_SHORT).show()
            return
        }
        val start = mStartList[0]
        val end = mEndList[0]

        val intent = Intent(this, TencentMotorRouteActivity::class.java)
        intent.putExtra("start_lat", start.latitude)
        intent.putExtra("start_lng", start.longitude)
        intent.putExtra("end_lat", end.latitude)
        intent.putExtra("end_lng", end.longitude)
        if (profile != null) {
            intent.putExtra("motor_displacement", profile.displacement)
            intent.putExtra("motor_energy_type", "GAS")
        }
        startActivity(intent)
    }

    // ========================================================================
    // 外部接口（供 NavStyleManager 回调）
    // ========================================================================

    fun setNavStyle(style: Int) {
        NavStyleManager.setStyle(style)
    }

    fun getNavStyle(): Int {
        return NavStyleManager.currentStyle
    }
}
