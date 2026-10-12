package org.zarroboogs.maps.hub

import android.content.Context
import android.view.View
import android.widget.ImageButton
import com.amap.api.maps.model.LatLng
import org.zarroboogs.maps.R
import org.zarroboogs.maps.module.CameraRouteMatcher
import org.zarroboogs.maps.module.CameraVoiceManager
import org.zarroboogs.maps.ui.navi.NaviRouteActivity

/**
 * 报警三件套统一封装：CameraRouteMatcher + CameraVoiceManager + 音量按钮。
 *
 * 替代 NaviCustomActivity / TencentMotorRouteActivity / UnifiedNavActivity 中
 * 各自内联的重复报警处理代码，按驾驶模式自动分流。
 */
class AlarmController(private val context: Context) {

    // ── 三件套 ──
    val matcher = CameraRouteMatcher()
    private val voice = CameraVoiceManager.getInstance(context)
    private var button: ImageButton? = null
    private var mode = NaviRouteActivity.MODE_DRIVE

    // ── 初始化（Activity onCreate 时调一次）──
    fun bind(btn: ImageButton, travelMode: Int) {
        button = btn
        mode = travelMode
        voice.init()
        voice.reset()
        btn.setOnClickListener {
            voice.toggleMute()
            updateIcon()
        }
        updateIcon()
        // 驾车模式：高德 SDK 自带 TTS，隐藏自定义按钮
        if (mode == NaviRouteActivity.MODE_DRIVE) {
            btn.visibility = View.GONE
        }
    }

    // ── GPS 更新时调（替代原 onLocationChange 中的内联代码）──
    fun onLocationUpdate(latLng: LatLng) {
        when (mode) {
            NaviRouteActivity.MODE_DRIVE -> {
                // 驾车模式：报警由 updateCameraInfo 负责，不跑自有体系
            }
            NaviRouteActivity.MODE_WALK -> {
                // 步行模式：不推任何报警
                NavigationHubManager.updateAlarm(0x00)
            }
            else -> {
                // 骑行 / 腾讯摩托：走自有 CameraRouteMatcher 体系
                val alarm = matcher.findNextCamera(latLng)
                NavigationHubManager.updateAlarm(alarm)
                if (alarm != 0x00) {
                    voice.onAlarmChanged(alarm, matcher.matchedCameraDistance)
                }
            }
        }
    }

    // ── 路线缓存转发 ──
    fun setRoute(points: List<LatLng>) = matcher.setRouteFromGeneric(points)
    fun onRerouteStart() = matcher.onRecalculating()
    fun onRerouteComplete(points: List<LatLng>) = matcher.setRouteFromGeneric(points)

    // ── 内部 ──
    private fun updateIcon() {
        button?.setImageResource(
            if (voice.isMuted()) R.drawable.jingyin else R.drawable.yuyinbobao
        )
    }
}
