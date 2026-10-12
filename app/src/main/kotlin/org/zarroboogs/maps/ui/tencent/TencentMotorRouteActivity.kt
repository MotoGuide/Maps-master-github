package org.zarroboogs.maps.ui.tencent

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.tencent.navix.api.NavigatorZygote
import com.tencent.navix.api.config.SimulatorConfig
import com.tencent.navix.api.layer.NavigatorLayerRootDrive
import com.tencent.navix.api.layer.NavigatorViewStub
import com.tencent.navix.api.model.NavDriveDataInfo
import com.tencent.navix.api.model.NavDriveRoute
import com.tencent.navix.api.model.NavDriveRoutePlan
import com.tencent.navix.api.model.NavLocationInfo
import com.tencent.navix.api.model.NavNavigationStartFail
import com.tencent.navix.api.model.NavRoutePlan
import com.tencent.navix.api.model.NavRouteReqParam
import com.tencent.navix.api.model.NavSearchPoint
import com.tencent.navix.api.navigator.NavigatorDrive
import com.tencent.navix.api.observer.SimpleNavigatorDriveObserver
import com.tencent.navix.api.plan.DriveRoutePlanOptions
import com.tencent.navix.api.plan.DriveRoutePlanRequestCallback
import com.tencent.navix.api.plan.RoutePlanRequester
import com.tencent.navix.tts.DefaultTTSPlayer
import com.tencent.navix.tts.api.TTSPlayer
import com.tencent.navix.ui.NavigatorLayerViewDrive
import org.zarroboogs.maps.R
import org.zarroboogs.maps.ble.BleDeviceListDialog
import org.zarroboogs.maps.ble.BleDisconnectDialog
import org.zarroboogs.maps.ble.BleService
import org.zarroboogs.maps.core.road.model.LatLngPoint
import org.zarroboogs.maps.core.route.EngineType
import org.zarroboogs.maps.cruise.CruiseModeManager
import org.zarroboogs.maps.hub.NavStyleManager
import org.zarroboogs.maps.hub.NavigationHubManager
import org.zarroboogs.maps.hub.AlarmController
import org.zarroboogs.maps.ui.navi.NaviRouteActivity
import org.zarroboogs.maps.utils.SettingUtils
import com.amap.api.maps.model.LatLng
import org.zarroboogs.maps.ble.BleServiceListener

class TencentMotorRouteActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MotoNav_Tencent"
    }

    private lateinit var navigatorDrive: NavigatorDrive
    private lateinit var layerRootDrive: NavigatorLayerRootDrive
    private lateinit var layerViewDrive: NavigatorLayerViewDrive
    private lateinit var routeView: TencentRouteView

    private var mStartLat = 0.0; private var mStartLng = 0.0
    private var mEndLat = 0.0; private var mEndLng = 0.0
    private var mDisplacement = 0
    private var mEnergyType: String? = null
    private var mRoutePlan: NavRoutePlan<NavDriveRoute>? = null
    private var mRouteTotalLen = 0

    /** 导航激活标志，用于防止 onDidStopNavigation / onWillArriveDestination 重复清理 */
    private var mNavActive = false

    // 底部按钮容器（模拟导航 + 开始导航，算路成功后显示）
    private var btnContainer: LinearLayout? = null

    // 控制面板按钮（BLE / 语音 / 样式，右下角竖排，仅导航中显示）
    private var controlPanel: LinearLayout? = null
    private var mBleService: BleService? = null
    private var mBound = false
    private var mBleButton: ImageButton? = null
    private var mVoiceButton: ImageButton? = null
    private var mStyleButton: ImageButton? = null
    private var mBleDisconnectDialog: BleDisconnectDialog? = null
    private var mBleDeviceListDialog: BleDeviceListDialog? = null

    // ── 报警三件套：CameraRouteMatcher + CameraVoiceManager + 音量按钮 ──
    private lateinit var mAlarmController: AlarmController

    /** BLE Service 监听器：连接状态 + ESP32 设备遥测 → NavigationHubManager */
    private val mBleServiceListener = object : BleServiceListener {
        override fun onConnectionStateChanged(connected: Boolean, deviceName: String?) {
            runOnUiThread {
                if (isFinishing) return@runOnUiThread
                updateBleButtonIcon()
            }
        }
        override fun onScanStarted() {}
        override fun onScanStopped() {
            runOnUiThread {
                if (isFinishing) return@runOnUiThread
                mBleDeviceListDialog?.setScanning(false)
            }
        }
        override fun onScanResult(devices: List<BleServiceListener.BleDeviceEntry>) {}
        override fun onError(message: String) {}
        override fun onDeviceTelemetry(batteryPercent: Int, firmwareVersion: String, heapKb: Int, fps: Int) {
            NavigationHubManager.onDeviceTelemetry(batteryPercent, firmwareVersion, heapKb, fps)
        }
    }

    /** BLE Service 绑定回调 */
    private val bleConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            mBleService = (service as BleService.LocalBinder).getService()
            mBleService?.registerListener(mBleServiceListener)  // 注册遥测回调（对齐高德）
            mBound = true
            updateBleButtonIcon()
        }
        override fun onServiceDisconnected(name: ComponentName) {
            mBleService = null
            mBound = false
            updateBleButtonIcon()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tencent_motor_route)
        readIntentExtras()

        // — 对齐 BaseNavActivity.onCreate —
        navigatorDrive = NavigatorZygote.with(applicationContext).navigator(NavigatorDrive::class.java)
        val stub = findViewById<NavigatorViewStub>(R.id.navigator_view_stub)
        stub.setTravelMode(NavRouteReqParam.TravelMode.TravelModeDriving)
        stub.inflate()
        layerRootDrive = stub.getNavigatorView() as NavigatorLayerRootDrive
        layerViewDrive = NavigatorLayerViewDrive(this)
        layerRootDrive.addViewLayer(layerViewDrive)
        navigatorDrive.bindView(layerRootDrive)
        // 初始化 SDK TTS 播放器（对齐 Demo SimulationNavActivity）
        navigatorDrive.setTTSPlayer(DefaultTTSPlayer())
        navigatorDrive.registerObserver(driveObserver)

        // — 对齐 MotorRouteRequestActivity.initRouteView —
        routeView = TencentRouteView(this, null)
        routeView.injectMap(layerRootDrive.mapApi)
        findViewById<FrameLayout>(R.id.app_root_view).addView(routeView)

        // 底部双按钮：模拟导航 + 开始导航（算路成功后显示）
        btnContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            visibility = View.GONE
            val simBtn = TextView(context).apply {
                text = "模拟导航"
                textSize = 16f; setTextColor(android.graphics.Color.BLACK)
                gravity = Gravity.CENTER
                setPadding(0, 36, 0, 36)
                setBackgroundResource(R.drawable.rounded_wellow_background)
                setOnClickListener { startSimulation() }
            }
            val realBtn = TextView(context).apply {
                text = "开始导航"
                textSize = 16f; setTextColor(android.graphics.Color.BLACK)
                gravity = Gravity.CENTER
                setPadding(0, 36, 0, 36)
                setBackgroundResource(R.drawable.rounded_wellow_background)
                setOnClickListener { startNavigation() }
            }
            addView(simBtn, LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
            ).apply { setMargins(0, 0, 12, 0) })
            addView(realBtn, LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
            ).apply { setMargins(12, 0, 0, 0) })
        }
        findViewById<FrameLayout>(R.id.app_root_view).addView(btnContainer, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply { gravity = Gravity.BOTTOM; setMargins(36, 0, 36, 24) })

        // 右上角控制面板：BLE + 语音 + 样式（对齐 Amap NaviCustomActivity）
        setupControlPanel()

        // 绑定 BLE Service（对齐 Amap 导航页）
        bindService(Intent(this, BleService::class.java), bleConnection, Context.BIND_AUTO_CREATE)

        // 每次进入默认箭头导航模式（对齐 Amap NaviCustomActivity）
        NavStyleManager.reset()

        // — 对齐 MotorRouteRequestActivity.searchRoute —
        searchMotorcycleRoute()
    }

    /** 创建右下角竖排控制按钮：BLE 连接 / 语音播报 / 导航样式，仅导航中可见 */
    private fun setupControlPanel() {
        val root = findViewById<FrameLayout>(R.id.app_root_view)
        val density = resources.displayMetrics.density

        mAlarmController = AlarmController(applicationContext)

        controlPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE  // 路线规划页不显示，导航开始后才显示
            // 样式按钮
            mStyleButton = ImageButton(context).also { btn ->
                btn.setImageResource(R.drawable.jiantou)
                btn.setBackgroundResource(R.drawable.circle_button_black)
                btn.setPadding(0, 0, 0, 0)
                btn.setOnClickListener {
                    NavStyleManager.toggle()
                    updateStyleButtonIcon()
                }
                addView(btn, LinearLayout.LayoutParams(
                    (48 * density).toInt(), (48 * density).toInt()
                ).apply { setMargins(0, 0, 0, (12 * density).toInt()) })
            }
            // 语音按钮（由 AlarmController 管理）
            mVoiceButton = ImageButton(context).also { btn ->
                addView(btn, LinearLayout.LayoutParams(
                    (48 * density).toInt(), (48 * density).toInt()
                ).apply { setMargins(0, 0, 0, (12 * density).toInt()) })
            }
            mAlarmController.bind(mVoiceButton!!, NaviRouteActivity.MODE_MOTOR)
            // BLE 按钮
            mBleButton = ImageButton(context).also { btn ->
                btn.setImageResource(R.drawable.ble)
                btn.setBackgroundResource(R.drawable.circle_button_black)
                btn.setPadding(0, 0, 0, 0)
                btn.setOnClickListener {
                    val svc = mBleService ?: return@setOnClickListener
                    if (svc.isConnected) {
                        if (mBleDisconnectDialog == null) {
                            mBleDisconnectDialog = BleDisconnectDialog(this@TencentMotorRouteActivity, svc)
                        }
                        mBleDisconnectDialog?.show()
                    } else {
                        if (mBleDeviceListDialog == null) {
                            mBleDeviceListDialog = BleDeviceListDialog(this@TencentMotorRouteActivity, svc)
                        }
                        mBleDeviceListDialog?.show()
                    }
                }
                addView(btn, LinearLayout.LayoutParams(
                    (48 * density).toInt(), (48 * density).toInt()
                ))
            }
        }
        root.addView(controlPanel, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.END
            // 避开 SDK "主路/辅路" 按钮（底部约 100dp），右侧留边距
            setMargins(0, 0, (12 * density).toInt(), (100 * density).toInt())
        })
    }

    private fun updateBleButtonIcon() {
        val connected = mBleService?.isConnected == true
        mBleButton?.setImageResource(if (connected) R.drawable.ble_active else R.drawable.ble)
        mStyleButton?.isEnabled = connected
        mStyleButton?.alpha = if (connected) 1.0f else 0.35f
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

    private fun readIntentExtras() {
        mStartLat = intent.getDoubleExtra("start_lat", 0.0)
        mStartLng = intent.getDoubleExtra("start_lng", 0.0)
        mEndLat = intent.getDoubleExtra("end_lat", 0.0)
        mEndLng = intent.getDoubleExtra("end_lng", 0.0)
        mDisplacement = intent.getIntExtra("motor_displacement", 0)
        mEnergyType = intent.getStringExtra("motor_energy_type")
    }

    // — 对齐 MotorRouteRequestActivity.searchRoute —
    private fun searchMotorcycleRoute() {
        val optionsBuilder = DriveRoutePlanOptions.Companion.newBuilder()
        val motorBuilder = DriveRoutePlanOptions.MotorOptions.Companion.newBuilder()
        var hasMotorParams = false
        if (mDisplacement > 0) { motorBuilder.setEngineDisplacement(mDisplacement); hasMotorParams = true }
        if (mEnergyType != null) {
            try {
                motorBuilder.setMotorEnergyType(DriveRoutePlanOptions.MotorOptions.MotorEnergyType.valueOf(mEnergyType!!))
                hasMotorParams = true
            } catch (_: Exception) {}
        }
        if (hasMotorParams) optionsBuilder.motorOptions(motorBuilder.build())

        navigatorDrive.searchRoute(
            RoutePlanRequester.Companion.newBuilder(NavRouteReqParam.TravelMode.TravelModeDriving)
                .start(NavSearchPoint(mStartLat, mStartLng))
                .end(NavSearchPoint(mEndLat, mEndLng))
                .options(optionsBuilder.build())
                .build(),
            DriveRoutePlanRequestCallback { navRoutePlan, error ->
                if (error != null) {
                    runOnUiThread { Toast.makeText(this, "摩托车路线规划失败", Toast.LENGTH_SHORT).show() }
                    return@DriveRoutePlanRequestCallback
                }
                mRoutePlan = navRoutePlan
                runOnUiThread {
                    if (navRoutePlan != null) {
                        routeView.updateRoutePlan(navRoutePlan)
                        btnContainer?.visibility = View.VISIBLE
                    }
                }
            }
        )
    }

    /** 真实导航 —— 显式关闭模拟器，防止被上一次模拟导航污染 */
    private fun startNavigation() {
        val routeData = mRoutePlan?.routeDatas?.getOrNull(routeView.currentIndex()) ?: return
        val routeId = routeData.routeId
        mRouteTotalLen = routeData.distance
        routeView.visibility = View.GONE
        btnContainer?.visibility = View.GONE

        Log.i(TAG, "startNavigation: routeId=$routeId totalLen=$mRouteTotalLen")
        // 导航启动前停止巡航 + 电子围栏（对齐 Amap NaviCustomActivity）
        CruiseModeManager.getInstance().stop()
        SettingUtils.writeGeofenceEnabled(false)
        mBleService?.stopGpsForGeofence()
        mBleService?.setNavActive(true)
        controlPanel?.visibility = View.VISIBLE  // 导航中显示控制面板
        mNavActive = true  // 标记导航激活
        navigatorDrive.simulator().setEnable(false)  // 确保模拟器关闭
        NavigationHubManager.startSession(mRouteTotalLen, EngineType.TENCENT)
        navigatorDrive.startNavigation(routeId)
    }

    /** 模拟导航 —— 启用 SDK 内置 GPS 模拟器沿路线推送 */
    private fun startSimulation() {
        val routeData = mRoutePlan?.routeDatas?.getOrNull(routeView.currentIndex()) ?: return
        val routeId = routeData.routeId
        mRouteTotalLen = routeData.distance
        routeView.visibility = View.GONE
        btnContainer?.visibility = View.GONE

        Log.i(TAG, "startSimulation: routeId=$routeId totalLen=$mRouteTotalLen speed=55km/h")
        // 导航启动前停止巡航 + 电子围栏（对齐 Amap NaviCustomActivity）
        CruiseModeManager.getInstance().stop()
        SettingUtils.writeGeofenceEnabled(false)
        mBleService?.stopGpsForGeofence()
        mBleService?.setNavActive(true)
        controlPanel?.visibility = View.VISIBLE  // 导航中显示控制面板
        mNavActive = true  // 标记导航激活
        NavigationHubManager.startSession(mRouteTotalLen, EngineType.TENCENT)

        // 启用 SDK 内置 GPS 模拟器（沿路线 55km/h）
        navigatorDrive.simulator()
            .setConfig(SimulatorConfig
                .builder(SimulatorConfig.Type.SIMULATE_LOCATIONS_ALONG_ROUTE)
                .setSimulateSpeed(55)
                .build()
            )
            .setEnable(true)
        navigatorDrive.startNavigation(routeId)
    }

    private var mNavDataCount = 0  // 导航数据回调计数

    /** 统一清理导航状态（BLE / 推送 / Session / UI），通过 mNavActive 防重入 */
    private fun cleanupNavState() {
        if (!mNavActive) return
        mNavActive = false
        Log.i(TAG, "cleanupNavState: stopping BLE/session, restoring UI")
        mBleService?.setNavActive(false)
        NavigationHubManager.stopHeadingPush()
        NavigationHubManager.stopSession()
        controlPanel?.visibility = View.GONE
        routeView.visibility = View.VISIBLE
        btnContainer?.visibility = View.VISIBLE
    }

    // — 对齐 BaseNavActivity.driveObserver —
    private val driveObserver = object : SimpleNavigatorDriveObserver() {

        // ── v2.0：路线数据就绪 → 推送路网到 ESP32 ──
        override fun onRouteRequestDidSucceed(plan: NavDriveRoutePlan) {
            Log.i(TAG, "onRouteRequestDidSucceed: routes=${plan.routeDatas?.size}")
            val selectedRoute = plan.routeDatas?.getOrNull(0) ?: return
            val pts = selectedRoute.routePoints
            if (pts.isNullOrEmpty()) return
            // 转换 Tencent LatLng → 内部 LatLngPoint（v2.0 路网）
            NavStyleManager.routePolyline = pts.map { LatLngPoint(it.latitude, it.longitude) }
            // 缓存路线到 AlarmController（摄像头报警用，对齐高德 onStartNavi → setRoute）
            mAlarmController.matcher.setRouteFromGeneric(pts.map { LatLng(it.latitude, it.longitude) })
        }

        override fun onRerouteDidSucceed(type: Int, plan: NavDriveRoutePlan) {
            Log.i(TAG, "onRerouteDidSucceed: type=$type")
            val selectedRoute = plan.routeDatas?.getOrNull(0) ?: return
            val pts = selectedRoute.routePoints
            if (pts.isNullOrEmpty()) return
            NavStyleManager.routePolyline = pts.map { LatLngPoint(it.latitude, it.longitude) }
            // 重算后更新 AlarmController 路线缓存（对齐高德 onCalculateRouteSuccess → onRouteRecalculated）
            mAlarmController.matcher.setRouteFromGeneric(pts.map { LatLng(it.latitude, it.longitude) })
            NavStyleManager.pushRoadNetwork()
        }

        // ── v2.0：GPS 定位 → 航向+坐标推送 + 摄像头报警 ──
        override fun onNavLocationInfoUpdate(info: NavLocationInfo) {
            // 优先 mainRouteLocation，fallback 到列表首条（与 mainRouteData 坑同理）
            val routeLoc = info.mainRouteLocation ?: info.routeLocations?.getOrNull(0) ?: return
            val navLoc = routeLoc.adsorbLocation ?: return
            val ll = navLoc.latLng ?: return
            NavStyleManager.currentGps = LatLngPoint(ll.latitude, ll.longitude)
            NavStyleManager.currentHeadingDeg = navLoc.course

            // ── 摄像头报警检测（AlarmController 封装，对齐高德 NaviCustomActivity）──
            mAlarmController.onLocationUpdate(LatLng(ll.latitude, ll.longitude))
        }

        // ── v2.0 偏航处理 ──
        override fun onOffRoute() {
            Log.w(TAG, "onOffRoute — 暂停推送，等待重算")
            NavigationHubManager.stopHeadingPush()
        }

        override fun onStartRerouting(type: Int) {
            Log.i(TAG, "onStartRerouting: type=$type")
            // 偏航重算开始，保持旧路线缓存（对齐高德 onReCalculateRouteForYaw → onRecalculating）
            mAlarmController.onRerouteStart()
        }

        // ── v1.x：导航数据 → BLE 推送 ──
        override fun onNavDataInfoUpdate(info: NavDriveDataInfo) {
            mNavDataCount++
            // 按官方 API：用 getRouteDataList()[0] 直取第一条路线，
            // 不依赖 getMainRouteData()（其内部按 routeId 匹配可能失败）
            val mainRoute = info.navRoutes.getOrNull(0) ?: return
            val iconType = mainRoute.nextIntersectionType
            val rawDist = mainRoute.nextIntersectionRemainingDistance
            // 腾讯 SDK 在长直路上 nextIntersectionRemainingDistance 可能不更新，
            // 当 dist <= 0 但剩余路程还远 → 用大值触发 500m 强制直行
            val remainDist = mainRoute.remainingDist
            val dist = if (rawDist <= 0 && remainDist > 500) 999 else rawDist
            val speed = info.speedKMH

            // 诊断日志：前 5 次 + 每 50 次打印关键数据
            if (mNavDataCount <= 5 || mNavDataCount % 50 == 0) {
                Log.i(TAG, "onNavDataInfoUpdate #$mNavDataCount: icon=$iconType dist=${dist}m remain=${remainDist}m speed=$speed totalLen=$mRouteTotalLen")
            }

            // 推送 BLE 导航数据到 ESP32
            NavigationHubManager.updateNavState(iconType, dist, remainDist, mRouteTotalLen)
            NavigationHubManager.updateSpeed(speed.toFloat())
            // v2.0: 同步速度到 NavStyleManager（推送比例尺计算依赖）
            NavStyleManager.currentSpeedKmh = speed.toFloat()
        }

        override fun onDidStartNavigation() {
            mNavDataCount = 0
            Log.i(TAG, "onDidStartNavigation")
            // v2.0: 路线数据就绪 → 推路网 + 启动航向推送定时器
            if (NavStyleManager.routePolyline.size >= 2) {
                NavStyleManager.pushRoadNetwork()
            }
            NavigationHubManager.startHeadingPush()
        }

        override fun onNavigationDataReady(routeId: String, isReady: Boolean) {
            Log.i(TAG, "onNavigationDataReady: routeId=$routeId ready=$isReady")
            if (isReady && NavStyleManager.routePolyline.size >= 2) {
                NavStyleManager.pushRoadNetwork()
                NavigationHubManager.startHeadingPush()
            }
        }

        // ── 到达目的地（对齐 Demo BaseNavActivity）──
        override fun onWillArriveDestination() {
            Log.i(TAG, "onWillArriveDestination — stopping")
            cleanupNavState()
            navigatorDrive.stopNavigation()
        }

        // ── 导航停止回调（SDK 退出按钮 / stopNavigation 触发）──
        override fun onDidStopNavigation() {
            Log.i(TAG, "onDidStopNavigation — cleaning up")
            cleanupNavState()
        }

        // ── 导航启动失败回调（对齐 Demo）──
        override fun onDidStartNavigationFail(sessionId: String, fail: NavNavigationStartFail) {
            Log.e(TAG, "onDidStartNavigationFail: sessionId=$sessionId fail=$fail")
            runOnUiThread {
                Toast.makeText(this@TencentMotorRouteActivity, "导航启动失败", Toast.LENGTH_SHORT).show()
                cleanupNavState()
            }
        }
    }

    // — 对齐 BaseNavActivity 生命周期 —
    override fun onStart() { super.onStart(); layerRootDrive.onStart() }
    override fun onRestart() { super.onRestart(); layerRootDrive.onRestart() }
    override fun onResume() { super.onResume(); layerRootDrive.onResume() }
    override fun onPause() { super.onPause(); layerRootDrive.onPause() }
    override fun onStop() { super.onStop(); layerRootDrive.onStop() }

    /** 返回键处理：导航中弹出确认对话框，路线选择页直接返回 */
    override fun onBackPressed() {
        if (mNavActive) {
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("退出导航")
                .setMessage("确定要退出当前导航吗？")
                .setPositiveButton("确定") { _, _ ->
                    // 清理导航状态后关闭 Activity
                    cleanupNavState()
                    navigatorDrive.stopNavigation()
                    super.onBackPressed()
                }
                .setNegativeButton("取消", null)
                .show()
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        // 退出导航状态（对齐 Amap onDestroy，cleanupNavState 防重入）
        cleanupNavState()
        mBleService?.unregisterListener()  // 解注册遥测回调（对齐高德）
        if (mBound) { unbindService(bleConnection); mBound = false }
        layerRootDrive.onDestroy()
        navigatorDrive.unregisterObserver(driveObserver)
        layerRootDrive.removeViewLayer(layerViewDrive)
        navigatorDrive.unbindView(layerRootDrive)
        val tts = navigatorDrive.getTTSPlayer()
        if (tts is TTSPlayer) tts.stop()
        navigatorDrive.stopNavigation()
        super.onDestroy()
    }
}
