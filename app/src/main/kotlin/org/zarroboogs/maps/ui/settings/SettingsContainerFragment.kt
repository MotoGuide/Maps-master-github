package org.zarroboogs.maps.ui.settings

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import com.amap.api.maps.AMap
import org.zarroboogs.maps.R
import org.zarroboogs.maps.ui.AboutActivity
import org.zarroboogs.maps.ui.CameraServerConfigActivity
import org.zarroboogs.maps.ui.offlinemaps.OfflineMapActivity
import org.zarroboogs.maps.hub.NavigationHubManager
import org.zarroboogs.maps.utils.SettingUtils

class SettingsContainerFragment : Fragment() {

    private lateinit var switchSatellite: Switch
    private lateinit var tvGeofenceDistance: TextView

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_settings_container, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 程序化设置状态栏高度的顶部padding，避免内容被通知栏遮挡
        val statusBarHeight = getStatusBarHeight()
        view.setPadding(view.paddingLeft, statusBarHeight, view.paddingRight, view.paddingBottom)

        switchSatellite = view.findViewById(R.id.switch_satellite)

        // 初始化 Switch 状态
        switchSatellite.isChecked =
            SettingUtils.readCurrentMapsStyle() == AMap.MAP_TYPE_SATELLITE

        // 卫星地图切换
        switchSatellite.setOnCheckedChangeListener { _, _ ->
            (activity as? SettingsActionListener)?.onSatelliteToggled()
        }

        // 电子围栏预警距离
        tvGeofenceDistance = view.findViewById(R.id.tv_geofence_distance)
        refreshGeofenceDisplay()
        view.findViewById<View>(R.id.setting_geofence).setOnClickListener {
            showGeofenceDialog()
        }

        // 离线地图
        view.findViewById<View>(R.id.setting_offline).setOnClickListener {
            startActivity(Intent(requireContext(), OfflineMapActivity::class.java))
        }

        // 导出日志
        view.findViewById<View>(R.id.setting_export_log).setOnClickListener {
            exportLogs()
        }

        // 摄像头服务器配置 — 暂时禁用，避免密钥和地址暴露在代码中
        // view.findViewById<View>(R.id.setting_camera_server).setOnClickListener {
        //     startActivity(Intent(requireContext(), CameraServerConfigActivity::class.java))
        // }

        // 关于
        view.findViewById<View>(R.id.setting_about).setOnClickListener {
            startActivity(Intent(requireContext(), AboutActivity::class.java))
        }
    }

    private fun exportLogs() {
        val ctx = requireContext()
        val authority = "${ctx.packageName}.fileprovider"

        // 收集日志文件：JSON 事件 + CSV 回放 + 通知抓取日志
        val files = mutableListOf<java.io.File>()
        NavigationHubManager.getEventLogFiles().forEach { files.add(it) }
        NavigationHubManager.getReplayFiles().forEach { files.add(it) }

        // 通知抓取日志 (files/logs/notify_*.log)
        ctx.filesDir.resolve("logs").listFiles()
            ?.filter { it.isFile && it.name.startsWith("notify_") }
            ?.forEach { files.add(it) }

        if (files.isEmpty()) {
            Toast.makeText(ctx, "暂无日志文件", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val uris = files.map { file ->
                FileProvider.getUriForFile(ctx, authority, file)
            }
            val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "application/octet-stream"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
                putExtra(Intent.EXTRA_SUBJECT, "导航日志 ${files.size} 个文件")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "导出日志"))
        } catch (e: Exception) {
            Toast.makeText(ctx, "导出失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        // 切回设置页时同步最新状态
        switchSatellite.isChecked =
            SettingUtils.readCurrentMapsStyle() == AMap.MAP_TYPE_SATELLITE
    }

    private fun getStatusBarHeight(): Int {
        val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (resourceId > 0) resources.getDimensionPixelSize(resourceId) else 0
    }

    private fun refreshGeofenceDisplay() {
        val dist = SettingUtils.readGeofenceDistance()
        tvGeofenceDistance.text = if (dist >= 1000) "${dist / 1000.0}km" else "${dist}m"
    }

    private fun showGeofenceDialog() {
        val currentDist = SettingUtils.readGeofenceDistance()
        val input = EditText(requireContext()).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(currentDist.toString())
            selectAll()
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
            setBackgroundColor(Color.parseColor("#2A2A2A"))
            setPadding(32, 24, 32, 24)
            textSize = 16f
        }

        AlertDialog.Builder(requireContext(), R.style.DarkDialogTheme)
            .setTitle("电子围栏预警距离")
            .setMessage("单位：米，范围 ${SettingUtils.GEOFENCE_DISTANCE_MIN}~${SettingUtils.GEOFENCE_DISTANCE_MAX}")
            .setView(input)
            .setPositiveButton("确定") { _, _ ->
                val raw = input.text.toString()
                val dist = raw.toIntOrNull()
                if (dist == null || dist < SettingUtils.GEOFENCE_DISTANCE_MIN || dist > SettingUtils.GEOFENCE_DISTANCE_MAX) {
                    Toast.makeText(requireContext(),
                        "请输入 ${SettingUtils.GEOFENCE_DISTANCE_MIN}~${SettingUtils.GEOFENCE_DISTANCE_MAX} 之间的整数",
                        Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                SettingUtils.writeGeofenceDistance(dist)
                refreshGeofenceDisplay()
            }
            .setNegativeButton("取消", null)
            .show()
    }
}
