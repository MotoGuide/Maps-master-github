package org.zarroboogs.maps.ui

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import org.zarroboogs.maps.R
import org.zarroboogs.maps.network.CameraCloudSync
import org.zarroboogs.maps.utils.Constants
import org.zarroboogs.maps.utils.FileUtils

/**
 * 摄像头服务器配置页 — 展示/修改云端同步的服务器地址和 API Key。
 * 保存后写入 SharedPreferences，下次启动 CameraCloudSync 自动读取。
 * 黑黄暗色风格。
 */
class CameraServerConfigActivity : BaseActivity() {

    private lateinit var etUrl: EditText
    private lateinit var etKey: EditText
    private lateinit var btnSave: Button
    private lateinit var btnReset: Button
    private lateinit var btnBack: ImageButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera_server_config)

        // 避开通知栏
        val rootView = findViewById<android.view.View>(android.R.id.content)
        val statusBarHeight = getStatusBarHeight()
        rootView.setPadding(
            rootView.paddingLeft,
            statusBarHeight,
            rootView.paddingRight,
            rootView.paddingBottom
        )

        etUrl = findViewById(R.id.et_server_url)
        etKey = findViewById(R.id.et_api_key)
        btnSave = findViewById(R.id.btn_save_config)
        btnReset = findViewById(R.id.btn_reset_config)
        btnBack = findViewById(R.id.camera_config_back)

        // 读取当前生效的配置（SP 优先，Constants 兜底）
        loadCurrentConfig()

        btnBack.setOnClickListener { finish() }

        btnSave.setOnClickListener {
            val url = etUrl.text.toString().trim()
            val key = etKey.text.toString().trim()

            if (url.isEmpty()) {
                Toast.makeText(this, "服务器地址不能为空", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // 保存到 SharedPreferences
            FileUtils.writeSharedPreference("camera_server_url", url)
            FileUtils.writeSharedPreference("camera_server_key", key)

            // 重置 CameraCloudSync 单例，下次调用时用新配置
            CameraCloudSync.resetInstance()

            Toast.makeText(this, "配置已保存，请重启应用生效", Toast.LENGTH_LONG).show()
        }

        btnReset.setOnClickListener {
            // 清空 SP 配置，恢复默认
            FileUtils.writeSharedPreference("camera_server_url", "")
            FileUtils.writeSharedPreference("camera_server_key", "")

            CameraCloudSync.resetInstance()

            // 回填默认值到输入框
            etUrl.setText(Constants.CAMERA_API_BASE_URL)
            etKey.setText(Constants.CAMERA_API_KEY)

            Toast.makeText(this, "已恢复默认配置", Toast.LENGTH_SHORT).show()
        }
    }

    private fun getStatusBarHeight(): Int {
        val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (resourceId > 0) resources.getDimensionPixelSize(resourceId) else 0
    }

    /** 读取当前生效的 URL 和 Key 回填到输入框 */
    private fun loadCurrentConfig() {
        val spUrl = FileUtils.readSharedPreference("camera_server_url")
        val spKey = FileUtils.readSharedPreference("camera_server_key")

        etUrl.setText(
            if (spUrl.isNotEmpty()) spUrl else Constants.CAMERA_API_BASE_URL
        )
        etKey.setText(
            if (spKey.isNotEmpty()) spKey else Constants.CAMERA_API_KEY
        )
    }
}
