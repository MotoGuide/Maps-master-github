package org.zarroboogs.maps.ui.tencent

import android.os.Bundle
import android.os.Looper
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.tencent.navix.api.NavigatorZygote
import com.tencent.navix.api.layer.NavigatorLayerRootDrive
import com.tencent.navix.api.layer.NavigatorViewStub
import com.tencent.navix.api.model.*
import com.tencent.navix.api.navigator.NavigatorDrive
import com.tencent.navix.api.observer.SimpleNavigatorDriveObserver
import com.tencent.navix.api.plan.DriveRoutePlanOptions
import com.tencent.navix.api.plan.DriveRoutePlanRequestCallback
import com.tencent.navix.api.plan.RoutePlanRequester
import com.tencent.navix.api.tts.NavigatorTTSPlayer
import com.tencent.navix.tts.DefaultTTSPlayer
import com.tencent.navix.tts.api.TTSPlayer
import com.tencent.navix.ui.NavigatorLayerViewDrive
import com.tencent.navix.ui.api.config.UIComponentConfig
import com.tencent.navix.ui.internal.NavPreviewButtonView
import org.zarroboogs.maps.R
import org.zarroboogs.maps.core.data.model.NavState
import org.zarroboogs.maps.core.route.EngineType
import org.zarroboogs.maps.hub.NavigationHubManager
import java.util.Locale

class TencentNavActivity : AppCompatActivity() {

    private lateinit var navigatorDrive: NavigatorDrive
    private lateinit var layerRootDrive: NavigatorLayerRootDrive
    private lateinit var layerViewDrive: NavigatorLayerViewDrive

    private var navInfoPanel: LinearLayout? = null
    private var turnIcon: ImageView? = null
    private var distanceInfo: TextView? = null
    private var guideLane: ImageView? = null
    private var speedPanel: LinearLayout? = null
    private var speedText: TextView? = null
    private var previewButton: NavPreviewButtonView? = null

    private var mRouteId: String = ""
    private var mStartLat = 0.0
    private var mStartLng = 0.0
    private var mEndLat = 0.0
    private var mEndLng = 0.0
    private var mRouteTotalLen = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tencent_nav)

        readIntentExtras()
        initNav()

        searchAndStartNavigation()
    }

    private fun readIntentExtras() {
        mRouteId = intent.getStringExtra("route_id") ?: ""
        mStartLat = intent.getDoubleExtra("start_lat", 0.0)
        mStartLng = intent.getDoubleExtra("start_lng", 0.0)
        mEndLat = intent.getDoubleExtra("end_lat", 0.0)
        mEndLng = intent.getDoubleExtra("end_lng", 0.0)
    }

    private fun initNav() {
        // NavigatorDrive
        navigatorDrive = NavigatorZygote.with(applicationContext).navigator(NavigatorDrive::class.java)

        // ViewStub → map layer
        val stub = findViewById<NavigatorViewStub>(R.id.navigator_view_stub)
        stub.setTravelMode(NavRouteReqParam.TravelMode.TravelModeDriving)
        stub.inflate()
        layerRootDrive = stub.getNavigatorView() as NavigatorLayerRootDrive

        // Default panel (we'll hide components and add our own)
        layerViewDrive = NavigatorLayerViewDrive(this)
        layerRootDrive.addViewLayer(layerViewDrive)
        navigatorDrive.bindView(layerRootDrive)

        // Hide default panel, keep enlarged map
        layerViewDrive.setUIComponentConfig(
            UIComponentConfig.builder()
                .setAllComponentVisibility(false)
                .setComponentVisibility(UIComponentConfig.UIComponent.ENLARGE_INFO_VIEW, false)
                .build()
        )

        // UI refs
        navInfoPanel = findViewById(R.id.nav_info_panel)
        turnIcon = findViewById(R.id.turn_icon)
        distanceInfo = findViewById(R.id.distance_info)
        guideLane = findViewById(R.id.guide_lane)
        speedPanel = findViewById(R.id.speed_panel)
        speedText = findViewById(R.id.speed_text)
        previewButton = findViewById(R.id.preview_button)

        // Preview button — toggle 3D ↔ overview
        previewButton?.setOnClickListener {
            val currentMode = layerRootDrive.navMode
            layerRootDrive.setNavMode(
                if (currentMode == NavMode.MODE_OVERVIEW) NavMode.MODE_3D_TOWARDS_UP
                else NavMode.MODE_OVERVIEW
            )
        }

        // TTS
        navigatorDrive.setTTSPlayer(DefaultTTSPlayer())

        // Observer
        navigatorDrive.registerObserver(driveObserver, Looper.getMainLooper())
    }

    private fun searchAndStartNavigation() {
        val builder = RoutePlanRequester.Companion.newBuilder(NavRouteReqParam.TravelMode.TravelModeDriving)
            .start(NavSearchPoint(mStartLat, mStartLng))
            .end(NavSearchPoint(mEndLat, mEndLng))
            .options(DriveRoutePlanOptions.Companion.newBuilder().build())

        navigatorDrive.searchRoute(builder.build(), DriveRoutePlanRequestCallback { plan, error ->
            if (error != null) {
                runOnUiThread {
                    Toast.makeText(this, "路线规划失败", Toast.LENGTH_SHORT).show()
                }
                return@DriveRoutePlanRequestCallback
            }
            // Cache total length + route ID from plan result
            mRouteTotalLen = plan?.routeDatas?.getOrNull(0)?.distance ?: 0
            mRouteId = plan?.routeDatas?.getOrNull(0)?.routeId ?: mRouteId
            runOnUiThread {
                // 启动 BLE 导航会话（腾讯引擎 → iconSetId=1）
                NavigationHubManager.startSession(mRouteTotalLen, EngineType.TENCENT)
                navigatorDrive.startNavigation(mRouteId)
            }
        })
    }

    // ---- Nested observer ----

    private val driveObserver = object : SimpleNavigatorDriveObserver() {

        override fun onDidStartNavigation() {
            navInfoPanel?.post { navInfoPanel?.visibility = View.VISIBLE }
            speedPanel?.post { speedPanel?.visibility = View.VISIBLE }
        }

        override fun onNavDataInfoUpdate(info: NavDriveDataInfo) {
            val mainRoute = info.mainRouteData ?: return
            val iconType = mainRoute.nextIntersectionType
            val dist = mainRoute.nextIntersectionRemainingDistance
            val remainDist = mainRoute.remainingDist
            val speed = info.speedKMH

            // 推送 BLE 导航数据到 ESP32（iconSetId=1 由 NavigationHubManager 自动填）
            NavigationHubManager.updateNavState(iconType, dist, remainDist, mRouteTotalLen)
            NavigationHubManager.updateSpeed(speed.toFloat())

            // 本地 UI
            val bmp = mainRoute.nextIntersectionBitmap
            if (bmp != null) {
                turnIcon?.setImageBitmap(bmp)
            }
            distanceInfo?.text = formatDistance(dist)
        }

        override fun onWillShowLaneGuide(laneInfo: NavLaneInfo) {
            val bmp = laneInfo.guideLaneBitmap
            if (bmp != null) {
                guideLane?.setImageBitmap(bmp)
                guideLane?.visibility = View.VISIBLE
            }
        }

        override fun onHideLaneGuide() {
            guideLane?.setImageBitmap(null)
            guideLane?.visibility = View.GONE
        }
    }

    // ---- Helpers ----

    private fun formatDistance(distMeters: Int): String {
        if (distMeters <= 0) return "现在"
        if (distMeters > 1000) {
            return String.format(Locale.getDefault(), "%.1f公里", distMeters / 1000f)
        }
        return "${distMeters}米"
    }

    // ---- Lifecycle ----

    override fun onStart() { super.onStart(); layerRootDrive.onStart() }
    override fun onResume() { super.onResume(); layerRootDrive.onResume() }
    override fun onPause() { super.onPause(); layerRootDrive.onPause() }
    override fun onStop() { super.onStop(); layerRootDrive.onStop() }

    override fun onDestroy() {
        // 停止 BLE 导航推送
        NavigationHubManager.stopSession()

        navigatorDrive.unregisterObserver(driveObserver)
        layerRootDrive.removeViewLayer(layerViewDrive)
        navigatorDrive.unbindView(layerRootDrive)

        val tts = navigatorDrive.getTTSPlayer()
        if (tts is TTSPlayer) tts.stop()
        navigatorDrive.stopNavigation()
        layerRootDrive.onDestroy()
        super.onDestroy()
    }
}
