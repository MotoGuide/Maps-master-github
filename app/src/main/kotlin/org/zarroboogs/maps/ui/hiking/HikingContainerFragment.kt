package org.zarroboogs.maps.ui.hiking

import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetBehavior
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.zarroboogs.maps.R
import org.zarroboogs.maps.core.data.model.GpsLocation
import org.zarroboogs.maps.hiking.gpx.CoordSystem
import org.zarroboogs.maps.hiking.gpx.GpsLocationProvider
import org.zarroboogs.maps.hiking.gpx.GpxNavigationEngine
import org.zarroboogs.maps.hiking.gpx.GpxParser
import org.zarroboogs.maps.hiking.gpx.GpxRecorder
import org.zarroboogs.maps.hiking.gpx.GpxRepository
import org.zarroboogs.maps.hiking.gpx.GpxSimulator
import org.zarroboogs.maps.hiking.model.GpxTrack
import org.zarroboogs.maps.hiking.room.GpxDatabase

/**
 * Tab3 徒步容器 Fragment — 统一日志标签: "Hiking"。
 *
 * v3.1 简化版：去掉腾讯地图，保留收藏地点 + 轨迹管理双Tab，
 * 导航引擎保留，BottomSheet 仪表板保留。
 */
class HikingContainerFragment : Fragment() {

    companion object {
        const val TAG = "Hiking"
        private const val REQUEST_IMPORT = 1001
        private const val TAB_BOOKMARKS = 0
        private const val TAB_TRACKS = 1
    }

    // ===== Tab 切换 =====
    private var currentTab = TAB_BOOKMARKS
    private var tabBookmarks: TextView? = null
    private var tabTracks: TextView? = null
    private var tabContent: ViewGroup? = null
    private var bookmarkListFragment: BookmarkListFragment? = null
    private var gpxListFragment: GpxListFragment? = null

    // ===== BottomSheet =====
    private var bottomSheet: ViewGroup? = null
    private var bottomSheetBehavior: BottomSheetBehavior<ViewGroup>? = null
    private var txtTurnDirection: TextView? = null
    private var txtTurnDistance: TextView? = null
    private var txtRemainDist: TextView? = null
    private var txtETA: TextView? = null
    private var txtProgress: TextView? = null
    private var progressBar: ProgressBar? = null
    private var panelIdle: ViewGroup? = null
    private var panelSim: ViewGroup? = null
    private var txtSimInfo: TextView? = null
    private var txtSimSpeedLabel: TextView? = null
    private var seekSim: SeekBar? = null
    private var btnSimPlay: ImageButton? = null
    private var btnExit: View? = null
    private var simSpeedButtons: Map<Float, TextView> = emptyMap()

    // ===== 导航引擎 =====
    private var navigationEngine: GpxNavigationEngine? = null
    private var currentTrack: GpxTrack? = null
    private var isNavigating = false
    private var isFullscreen = false

    // ===== 模拟导航 =====
    private var simulator: GpxSimulator? = null
    private var simUpdateJob: Job? = null
    private var isSimulating = false
    private var simCurrentSpeed: Float = 1f
    private var simLastArrivalLevel = org.zarroboogs.maps.hiking.gpx.ArrivalLevel.NORMAL

    // ===== GPS =====
    private var locationManager: LocationManager? = null
    private var gpsListener: LocationListener? = null
    @Volatile private var lastKnownLocation: GpsLocation? = null

    // ===== 录制 =====
    private var recorder: GpxRecorder? = null
    private var isRecording = false

    // ===== 真实导航 UI =====
    private var navUpdateJob: Job? = null

    // ===== GPS 定位提供者（供录制使用） =====
    private val gpsLocationProvider = object : GpsLocationProvider {
        override suspend fun getCurrentLocation(): GpsLocation? {
            var location = lastKnownLocation
            var retries = 0
            while (location == null && retries < 10) {
                delay(500)
                location = lastKnownLocation
                retries++
            }
            return location
        }
    }

    // ===== 生命周期 =====

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        Log.d(TAG, "HikingContainer onCreateView")
        return inflater.inflate(R.layout.fragment_hiking_container, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d(TAG, "HikingContainer onViewCreated")
        initViews(view)
        initBottomSheet()
        initSimControls()
        showTab(TAB_BOOKMARKS)
    }

    private fun initViews(view: View) {
        // Tab 切换栏
        tabBookmarks = view.findViewById(R.id.hiking_tab_bookmarks)
        tabTracks = view.findViewById(R.id.hiking_tab_tracks)
        tabContent = view.findViewById(R.id.hiking_tab_content) as ViewGroup
        tabBookmarks?.setOnClickListener { showTab(TAB_BOOKMARKS) }
        tabTracks?.setOnClickListener { showTab(TAB_TRACKS) }

        // 退出按钮（外层 fitsSystemWindows 包裹，避开状态栏）
        btnExit = view.findViewById(R.id.hiking_exit_wrapper)
        btnExit?.setOnClickListener {
            Log.i(TAG, "[BTN] exit nav clicked, isNavigating=$isNavigating, isSimulating=$isSimulating")
            if (isSimulating) stopSimulation() else stopNavigation()
        }

        // BottomSheet
        bottomSheet = view.findViewById(R.id.hiking_bottom_sheet) as? ViewGroup
        txtTurnDirection = view.findViewById(R.id.hiking_txt_turn_direction)
        txtTurnDistance = view.findViewById(R.id.hiking_txt_turn_distance)
        txtRemainDist = view.findViewById(R.id.hiking_txt_remain_dist)
        txtETA = view.findViewById(R.id.hiking_txt_eta)
        txtProgress = view.findViewById(R.id.hiking_txt_progress)
        progressBar = view.findViewById(R.id.hiking_progress_bar)
        panelIdle = view.findViewById(R.id.hiking_panel_idle) as? ViewGroup
        panelSim = view.findViewById(R.id.hiking_panel_sim) as? ViewGroup
        txtSimInfo = view.findViewById(R.id.hiking_sim_info)
        txtSimSpeedLabel = view.findViewById(R.id.hiking_sim_speed_label)
        seekSim = view.findViewById(R.id.hiking_sim_seekbar)
        btnSimPlay = view.findViewById(R.id.hiking_sim_play)
        simSpeedButtons = mapOf(
            1f to view.findViewById<TextView>(R.id.hiking_sim_speed_1x),
            2f to view.findViewById<TextView>(R.id.hiking_sim_speed_2x),
            5f to view.findViewById<TextView>(R.id.hiking_sim_speed_5x),
            10f to view.findViewById<TextView>(R.id.hiking_sim_speed_10x)
        )

        // 空闲面板按钮
        view.findViewById<View>(R.id.hiking_btn_select_track)?.setOnClickListener {
            Log.i(TAG, "[BTN] selectTrack clicked → switch to tracks tab")
            showTab(TAB_TRACKS)
        }
        view.findViewById<View>(R.id.hiking_btn_import)?.setOnClickListener {
            Log.i(TAG, "[BTN] import clicked")
            openGpxImport()
        }
        view.findViewById<View>(R.id.hiking_btn_record)?.setOnClickListener {
            Log.i(TAG, "[BTN] record clicked, isRecording=$isRecording")
            toggleRecording()
        }
    }

    // ===== Tab 切换 =====

    private fun showTab(tab: Int) {
        if (currentTab == tab && tabContent?.childCount ?: 0 > 0) return
        currentTab = tab
        Log.d(TAG, "showTab: $tab")

        // 更新 Tab 样式
        tabBookmarks?.apply {
            setTextColor(if (tab == TAB_BOOKMARKS) 0xFFFFC107.toInt() else 0xFF888888.toInt())
            setBackgroundResource(if (tab == TAB_BOOKMARKS) R.drawable.bg_tab_active else R.drawable.bg_tab_inactive)
        }
        tabTracks?.apply {
            setTextColor(if (tab == TAB_TRACKS) 0xFFFFC107.toInt() else 0xFF888888.toInt())
            setBackgroundResource(if (tab == TAB_TRACKS) R.drawable.bg_tab_active else R.drawable.bg_tab_inactive)
        }

        // 切换子 Fragment
        val fm = childFragmentManager
        val tx = fm.beginTransaction()
        // 移除旧 Fragment
        fm.findFragmentByTag("tab_bookmarks")?.let { tx.remove(it) }
        fm.findFragmentByTag("tab_tracks")?.let { tx.remove(it) }

        when (tab) {
            TAB_BOOKMARKS -> {
                val f = BookmarkListFragment { bookmark ->
                    Log.i(TAG, "[BOOKMARK] clicked: ${bookmark.name}")
                    // 后期扩展：点击收藏地点可查看详情或导航
                }
                tx.add(R.id.hiking_tab_content, f, "tab_bookmarks")
                bookmarkListFragment = f
            }
            TAB_TRACKS -> {
                val f = GpxListFragment(
                    onTrackNavigate = { track -> startNavigation(track) },
                    onTrackSimulate = { track -> startSimulation(track) }
                )
                tx.add(R.id.hiking_tab_content, f, "tab_tracks")
                gpxListFragment = f
            }
        }
        tx.commit()
    }

    // ===== BottomSheet =====

    private fun initBottomSheet() {
        bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet!!)
        bottomSheetBehavior?.apply {
            state = BottomSheetBehavior.STATE_COLLAPSED
            peekHeight = resources.getDimensionPixelSize(R.dimen.hiking_bottom_sheet_peek)
            isHideable = false
        }
    }

    // ===== 模拟导航控制 =====

    private fun initSimControls() {
        btnSimPlay?.setOnClickListener {
            val sim = simulator ?: return@setOnClickListener
            if (sim.isRunning.value) {
                Log.i(TAG, "[SIM] pause")
                sim.pause()
            } else {
                Log.i(TAG, "[SIM] resume from index=${sim.currentIndex.value}")
                sim.resume()
            }
            updateSimPlayButton()
        }

        simSpeedButtons.forEach { (speed, btn) ->
            btn.setOnClickListener {
                Log.i(TAG, "[SIM] speed selected: ${speed}x")
                simCurrentSpeed = speed
                simulator?.setSpeed(speed)
                updateSimSpeedButtons()
                txtSimSpeedLabel?.text = "${speed.toInt()}x"
            }
        }

        seekSim?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val sim = simulator ?: return
                    val total = sim.waypointCount - 1
                    if (total > 0) {
                        val targetIndex = (progress / 1000f * total).toInt().coerceIn(0, total)
                        Log.d(TAG, "[SIM] seek to $progress/1000 → index=$targetIndex/$total")
                        sim.seekTo(targetIndex)
                    }
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun updateSimPlayButton() {
        val isRunning = simulator?.isRunning?.value == true
        btnSimPlay?.setImageResource(
            if (isRunning) android.R.drawable.ic_media_pause
            else android.R.drawable.ic_media_play
        )
    }

    private fun updateSimSpeedButtons() {
        simSpeedButtons.forEach { (speed, btn) ->
            val isActive = speed == simCurrentSpeed
            btn.setBackgroundResource(if (isActive) R.drawable.bg_speed_btn_active else R.drawable.bg_speed_btn)
            btn.setTextColor(if (isActive) 0xFFFFC107.toInt() else 0xFF888888.toInt())
        }
    }

    // ===== 导航 =====

    /**
     * 启动真实导航（GPS 跟踪）。
     * 无地图可视化，仅 BottomSheet 面板显示进度。
     */
    fun startNavigation(track: GpxTrack) {
        Log.i(TAG, "[NAV] startNavigation: name=${track.name}, points=${track.waypointCount}, dist=${"%.2f".format(track.totalDistanceM)}m")
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val db = GpxDatabase.getInstance(requireContext())
                val repo = GpxRepository(db.gpxDao())
                val engine = GpxNavigationEngine(track, repo)
                Log.d(TAG, "[NAV] engine initializing...")
                engine.initialize()
                engine.startNavigation()
                navigationEngine = engine
                currentTrack = track
                Log.i(TAG, "[NAV] engine ready: turns=${engine.getNextTurn() != null}, remain=${"%.0f".format(engine.getRemainingDistanceM())}m")

                withContext(Dispatchers.Main) {
                    enterFullscreen()
                    isNavigating = true
                    btnExit?.visibility = View.VISIBLE
                    panelIdle?.visibility = View.GONE
                    bottomSheetBehavior?.state = BottomSheetBehavior.STATE_HALF_EXPANDED
                    Log.d(TAG, "[NAV] UI ready: navigation panel active")

                    // 启动真实导航 UI 更新循环
                    startNavUIUpdate(engine)
                    // 启动 GPS 监听
                    startGpsUpdates()
                }
            } catch (e: Exception) {
                Log.e(TAG, "[NAV] start failed: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "导航启动失败: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    fun stopNavigation() {
        Log.i(TAG, "[NAV] stopNavigation: isNavigating=$isNavigating, isSimulating=$isSimulating")
        if (isSimulating) {
            simUpdateJob?.cancel()
            simUpdateJob = null
            simulator?.stop()
            simulator = null
        }
        stopGpsUpdates()
        navUpdateJob?.cancel()
        navUpdateJob = null
        navigationEngine?.stopNavigation()
        navigationEngine = null
        currentTrack = null
        isNavigating = false
        isSimulating = false
        btnExit?.visibility = View.GONE
        panelSim?.visibility = View.GONE
        exitFullscreen()
        panelIdle?.visibility = View.VISIBLE
        bottomSheetBehavior?.state = BottomSheetBehavior.STATE_COLLAPSED
        Log.d(TAG, "[NAV] UI reset: gps stopped, idle panel visible")
    }

    // ===== GPS =====

    /** 启动 GPS 监听，实时推送位置到引擎。幂等：重复调用先移除旧监听再注册。 */
    private fun startGpsUpdates() {
        if (gpsListener != null) {
            Log.d(TAG, "[GPS] already started, skip")
            return
        }
        if (locationManager == null) {
            locationManager = requireContext().getSystemService(Context.LOCATION_SERVICE) as LocationManager
        }
        gpsListener = LocationListener { loc ->
            val gps = GpsLocation(
                lat = loc.latitude,
                lng = loc.longitude,
                speed = loc.speed,
                bearing = loc.bearing,
                accuracy = loc.accuracy,
                time = loc.time
            )
            lastKnownLocation = gps
            val engine = navigationEngine ?: return@LocationListener
            engine.updateGpsSignal(loc.accuracy)
            if (!isSimulating) {
                engine.onGpsUpdate(gps)
            }
        }
        try {
            @Suppress("MissingPermission")
            locationManager?.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 1f, gpsListener!!)
            Log.i(TAG, "[GPS] started: GPS_PROVIDER, interval=1000ms, minDist=1m")
        } catch (e: SecurityException) {
            Log.e(TAG, "[GPS] no permission: ${e.message}")
            gpsListener = null
            Toast.makeText(requireContext(), "请授予定位权限", Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopGpsUpdates() {
        gpsListener?.let { locationManager?.removeUpdates(it) }
        gpsListener = null
        Log.d(TAG, "[GPS] stopped")
    }

    // ===== 导航 UI 循环 =====

    /** 真实导航 UI 更新循环（200ms 周期）：同步引擎状态到 BottomSheet 面板 */
    private fun startNavUIUpdate(engine: GpxNavigationEngine) {
        navUpdateJob?.cancel()
        navUpdateJob = lifecycleScope.launch(Dispatchers.Main) {
            Log.d(TAG, "[NAV] UI update loop started (200ms period)")
            var lastArrivalLevel = org.zarroboogs.maps.hiking.gpx.ArrivalLevel.NORMAL
            var lastSignalStatus: org.zarroboogs.maps.hiking.gpx.GpsSignalStatus? = null
            while (isActive && isNavigating && !isSimulating) {
                try {
                    engine.checkGpsTimeout()
                    val state = engine.navState.value
                    updateNavPanels(state)
                    // GPS 信号变化通知
                    if (state.gpsSignalStatus != lastSignalStatus) {
                        lastSignalStatus = state.gpsSignalStatus
                        onGpsSignalChanged(state.gpsSignalStatus)
                    }
                    // 到达分级通知
                    if (state.arrivalLevel != lastArrivalLevel) {
                        lastArrivalLevel = state.arrivalLevel
                        onArrivalLevelChanged(state)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "[NAV] UI update error: ${e.message}")
                }
                delay(200)
            }
            Log.d(TAG, "[NAV] UI update loop stopped")
        }
    }

    private fun onGpsSignalChanged(status: org.zarroboogs.maps.hiking.gpx.GpsSignalStatus) {
        val msg = when (status) {
            org.zarroboogs.maps.hiking.gpx.GpsSignalStatus.EXCELLENT -> null
            org.zarroboogs.maps.hiking.gpx.GpsSignalStatus.WEAK -> "GPS 信号弱，请移至开阔地带"
            org.zarroboogs.maps.hiking.gpx.GpsSignalStatus.LOST -> "GPS 信号丢失！请检查定位权限"
            else -> null
        }
        if (msg != null) {
            Log.w(TAG, "[GPS] signal alert: $status")
            Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show()
        }
    }

    private fun onArrivalLevelChanged(state: org.zarroboogs.maps.hiking.gpx.GpxNavState) {
        val msg = when (state.arrivalLevel) {
            org.zarroboogs.maps.hiking.gpx.ArrivalLevel.APPROACHING -> "距离终点约 500m"
            org.zarroboogs.maps.hiking.gpx.ArrivalLevel.NEAR -> "距离终点约 100m"
            org.zarroboogs.maps.hiking.gpx.ArrivalLevel.ARRIVING -> "即将到达！"
            org.zarroboogs.maps.hiking.gpx.ArrivalLevel.ARRIVED -> "已到达目的地"
            else -> return
        }
        Log.i(TAG, "[NAV] arrival: ${state.arrivalLevel}, remain=${"%.0f".format(state.remainingDistanceM)}m")
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
    }

    /** 更新底部面板：转向/进度/距离/ETA */
    private fun updateNavPanels(state: org.zarroboogs.maps.hiking.gpx.GpxNavState) {
        val prog = (state.progressPercent * 10).toInt().coerceIn(0, 1000)
        progressBar?.progress = prog
        txtProgress?.text = "%.0f%%".format(state.progressPercent)

        val turn = state.nextTurn
        if (turn != null) {
            txtTurnDirection?.text = when (turn.direction) {
                org.zarroboogs.maps.hiking.model.TurnDirection.LEFT -> "← 左转"
                org.zarroboogs.maps.hiking.model.TurnDirection.RIGHT -> "→ 右转"
                org.zarroboogs.maps.hiking.model.TurnDirection.STRAIGHT -> "↑ 直行"
                org.zarroboogs.maps.hiking.model.TurnDirection.U_TURN -> "↩ 调头"
            }
            txtTurnDistance?.text = formatDist(state.distanceToNextTurnM)
        } else {
            txtTurnDirection?.text = "→ 终点"
            txtTurnDistance?.text = "--"
        }

        val signalIcon = when (state.gpsSignalStatus) {
            org.zarroboogs.maps.hiking.gpx.GpsSignalStatus.EXCELLENT -> "★★★★"
            org.zarroboogs.maps.hiking.gpx.GpsSignalStatus.GOOD -> "★★★☆"
            org.zarroboogs.maps.hiking.gpx.GpsSignalStatus.NORMAL -> "★★☆☆"
            org.zarroboogs.maps.hiking.gpx.GpsSignalStatus.WEAK -> "★☆☆☆"
            org.zarroboogs.maps.hiking.gpx.GpsSignalStatus.LOST -> "☆☆☆☆"
        }
        val confidencePct = "%.0f%%".format(state.snapConfidence * 100)
        txtRemainDist?.text = formatDist(state.remainingDistanceM) + "  $signalIcon  ⓘ$confidencePct"

        val etaSec = state.estimatedTimeRemaining
        txtETA?.text = if (etaSec > 0) {
            val min = etaSec / 60
            if (min >= 60) "${min / 60}h${min % 60}min" else "${min}min"
        } else "--"

        if (state.isOffRoute) {
            txtTurnDirection?.setTextColor(0xFFF44336.toInt())
        } else {
            txtTurnDirection?.setTextColor(0xFFFFC107.toInt())
        }
    }

    // ===== 模拟导航 =====

    /** 启动模拟导航（GPX 回放）。引擎运行，面板显示进度，无地图可视化。 */
    private fun startSimulation(track: GpxTrack) {
        Log.i(TAG, "[SIM] startSimulation: name=${track.name}, points=${track.waypointCount}, dist=${"%.2f".format(track.totalDistanceM)}m")
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val db = GpxDatabase.getInstance(requireContext())
                val repo = GpxRepository(db.gpxDao())
                val engine = GpxNavigationEngine(track, repo)
                Log.d(TAG, "[SIM] engine initializing...")
                engine.initialize()
                engine.startNavigation()

                val sim = GpxSimulator(track.waypoints, engine, lifecycleScope)
                simulator = sim
                navigationEngine = engine
                currentTrack = track
                simCurrentSpeed = 1f
                Log.i(TAG, "[SIM] simulator created: ${track.waypoints.size} waypoints, baseInterval=1000ms")

                withContext(Dispatchers.Main) {
                    enterFullscreen()
                    isSimulating = true
                    btnExit?.visibility = View.VISIBLE
                    panelIdle?.visibility = View.GONE
                    panelSim?.visibility = View.VISIBLE
                    updateSimSpeedButtons()
                    updateSimPlayButton()
                    bottomSheetBehavior?.state = BottomSheetBehavior.STATE_EXPANDED
                    Log.d(TAG, "[SIM] UI ready: sim panel active")
                }

                Log.i(TAG, "[SIM] starting playback at 1x speed")
                sim.start(fromIndex = 0)

                Log.i(TAG, "[SIM] playback started, starting UI update loop")
                startSimUIUpdate(engine, sim)
            } catch (e: Exception) {
                Log.e(TAG, "[SIM] start failed: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    stopSimulation()
                    Toast.makeText(requireContext(), "模拟导航启动失败: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun stopSimulation() {
        Log.i(TAG, "[SIM] stopSimulation")
        simUpdateJob?.cancel()
        simUpdateJob = null
        simulator?.stop()
        simulator = null
        navigationEngine?.stopNavigation()
        navigationEngine = null
        currentTrack = null
        isSimulating = false
        btnExit?.visibility = View.GONE
        panelSim?.visibility = View.GONE
        exitFullscreen()
        panelIdle?.visibility = View.VISIBLE
        bottomSheetBehavior?.state = BottomSheetBehavior.STATE_COLLAPSED
        Log.d(TAG, "[SIM] UI reset: idle panel visible")
    }

    /** 模拟导航 UI 更新循环（200ms 周期），同步模拟器状态到面板 */
    private fun startSimUIUpdate(engine: GpxNavigationEngine, sim: GpxSimulator) {
        simUpdateJob?.cancel()
        simUpdateJob = lifecycleScope.launch(Dispatchers.Main) {
            Log.d(TAG, "[SIM] UI update loop started (200ms period)")
            while (isActive && isSimulating) {
                try {
                    val state = engine.navState.value
                    val idx = sim.currentIndex.value
                    val total = sim.waypointCount - 1
                    val prog = if (total > 0) (idx.toFloat() / total * 1000).toInt() else 0

                    seekSim?.progress = prog.coerceIn(0, 1000)

                    val doneKm = if (total > 0) {
                        state.totalDistanceM * (idx.toDouble() / total) / 1000.0
                    } else 0.0
                    val confPct = "%.0f%%".format(state.snapConfidence * 100)
                    val etaStr = if (state.estimatedTimeRemaining > 0) {
                        val m = state.estimatedTimeRemaining / 60
                        if (m >= 60) "${m / 60}h${m % 60}min" else "${m}min"
                    } else "--"
                    txtSimInfo?.text = "%.1f / %.1f km  ⓘ$confPct  ⏱$etaStr".format(doneKm, state.totalDistanceM / 1000.0)

                    // 模拟导航到达通知
                    if (total > 0 && state.arrivalLevel != org.zarroboogs.maps.hiking.gpx.ArrivalLevel.NORMAL) {
                        if (state.arrivalLevel != simLastArrivalLevel) {
                            simLastArrivalLevel = state.arrivalLevel
                            onArrivalLevelChanged(state)
                        }
                    }

                    // 检测播放完毕
                    val finished = !sim.isRunning.value && idx >= total
                    if (finished) {
                        Log.i(TAG, "[SIM] playback finished: idx=$idx/$total, auto-stopping")
                        break
                    }

                    if (state.isOffRoute) {
                        Log.w(TAG, "[SIM] OFF-ROUTE: dist=${"%.1f".format(state.offRouteDistanceM)}m")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "[SIM] UI update error: ${e.message}")
                }
                delay(200)
            }
            Log.d(TAG, "[SIM] UI update loop stopped")

            if (isSimulating) {
                Log.i(TAG, "[SIM] playback complete, auto-stopping sim")
                stopSimulation()
            }
        }
    }

    // ===== 录制 =====

    private fun toggleRecording() {
        if (isRecording) {
            Log.i(TAG, "[REC] stopping recording")
            isRecording = false
            val rec = recorder
            recorder = null
            updateRecordButton()
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val track = rec?.stop()
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), "录制完成: ${track?.name ?: "未知"} (${"%.1f".format((track?.totalDistanceM ?: 0.0) / 1000)}km)", Toast.LENGTH_SHORT).show()
                        Log.i(TAG, "[REC] saved: name=${track?.name}, points=${track?.waypointCount}, dist=${"%.2f".format(track?.totalDistanceM ?: 0.0)}m")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "[REC] stop failed: ${e.message}", e)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), "录制失败: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        } else {
            Log.i(TAG, "[REC] starting recording")
            startGpsUpdates()
            val db = GpxDatabase.getInstance(requireContext())
            val repo = GpxRepository(db.gpxDao())
            val rec = GpxRecorder(gpsLocationProvider, repo, lifecycleScope)
            val name = "徒步_${System.currentTimeMillis()}"
            rec.start(name)
            recorder = rec
            isRecording = true
            updateRecordButton()
            Toast.makeText(requireContext(), "开始录制: $name", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateRecordButton() {
        val r = view?.findViewById<TextView>(R.id.hiking_btn_record)
        if (isRecording) {
            r?.text = "⏹ 停止录制"
            r?.setTextColor(0xFFF44336.toInt())
        } else {
            r?.text = "录制轨迹"
            r?.setTextColor(0xFFFFC107.toInt())
        }
    }

    // ===== 导入 =====

    private fun openGpxImport() {
        Log.i(TAG, "[IMPORT] openGpxImport Activity")
        startActivityForResult(Intent(requireContext(), GpxImportActivity::class.java), REQUEST_IMPORT)
    }

    /** 导入 GPX/KML 文件 */
    private fun importTrack(inputStream: java.io.InputStream, fileName: String, coordSystem: CoordSystem) {
        Log.i(TAG, "[IMPORT] start: file=$fileName, coordSystem=$coordSystem")
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val parser = GpxParser()
                val fileType = parser.detectType(fileName)
                Log.d(TAG, "[IMPORT] detected type=$fileType")
                val track = parser.parse(inputStream, fileType, coordSystem)
                Log.i(TAG, "[IMPORT] parsed: name=${track.name}, points=${track.waypointCount}, dist=${"%.2f".format(track.totalDistanceM)}m")
                val db = GpxDatabase.getInstance(requireContext())
                val repo = GpxRepository(db.gpxDao())
                val trackId = repo.importTrack(track, org.zarroboogs.maps.hiking.model.TrackSource.IMPORTED)
                Log.i(TAG, "[IMPORT] saved: trackId=$trackId, turns=${repo.loadTurnInstructions(trackId).size}")
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "导入成功: ${track.name} (${"%.1f".format(track.totalDistanceM / 1000)}km, ${track.waypointCount}点)", Toast.LENGTH_SHORT).show()
                    // 导入后切换到轨迹管理 Tab 以显示新轨迹
                    showTab(TAB_TRACKS)
                    Log.i(TAG, "[IMPORT] success: trackId=$trackId, switched to tracks tab")
                }
            } catch (e: Exception) {
                Log.e(TAG, "[IMPORT] failed: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "导入失败: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        Log.d(TAG, "onActivityResult: requestCode=$requestCode, resultCode=$resultCode, data=${data != null}")
        if (requestCode == REQUEST_IMPORT && resultCode == android.app.Activity.RESULT_OK && data != null) {
            val uri = data.data ?: run { Log.w(TAG, "onActivityResult: uri is null"); return }
            val fileName = data.getStringExtra("file_name") ?: "unknown"
            val coordSystemName = data.getStringExtra("coord_system") ?: "GCJ02"
            Log.i(TAG, "onActivityResult: file=$fileName, coord=$coordSystemName")
            val coordSystem = CoordSystem.valueOf(coordSystemName)
            val inputStream = requireContext().contentResolver.openInputStream(uri) ?: run {
                Log.w(TAG, "onActivityResult: cannot open inputStream")
                return
            }
            importTrack(inputStream, fileName, coordSystem)
        }
    }

    // ===== 全屏切换 =====

    private fun enterFullscreen() {
        isFullscreen = true
        (activity as? org.zarroboogs.maps.ui.maps.MapsMainActivity)?.hideTabBar()
    }

    private fun exitFullscreen() {
        isFullscreen = false
        (activity as? org.zarroboogs.maps.ui.maps.MapsMainActivity)?.showTabBar()
    }

    // ===== 工具方法 =====

    private fun formatDist(meters: Double): String = when {
        meters >= 1000 -> "%.1f km".format(meters / 1000)
        else -> "%.0f m".format(meters)
    }

    override fun onDestroy() {
        super.onDestroy()
        stopGpsUpdates()
        if (recorder != null) {
            lifecycleScope.launch(Dispatchers.IO) { try { recorder?.stop() } catch (_: Exception) {} }
        }
        simUpdateJob?.cancel()
        simulator?.stop()
        navUpdateJob?.cancel()
        navigationEngine?.stopNavigation()
    }
}
