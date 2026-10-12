package org.zarroboogs.maps.ui.device

import android.app.Activity
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.*
import org.zarroboogs.maps.R
import org.zarroboogs.maps.ble.BleService
import org.zarroboogs.maps.ble.BleOTASender
import org.zarroboogs.maps.ble.FirmwareChecker
import org.zarroboogs.maps.ble.NordcBleManager
import org.zarroboogs.maps.data.model.EspDeviceInfo
import org.zarroboogs.maps.utils.FileUtils
import org.zarroboogs.maps.widget.BatteryBar
import org.zarroboogs.maps.widget.SignalBar
import java.io.File
import java.io.FileOutputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest

class DeviceDetailBottomSheet : BottomSheetDialogFragment() {

    companion object {
        private const val TAG = "MotoNavOTA"
        private const val ARG_CONNECTED = "connected"
        private const val ARG_DEVICE_NAME = "device_name"
        private const val ARG_MAC_ADDRESS = "mac_address"
        private const val ARG_CONNECTED_TIME = "connected_time"
        private const val KEY_PHOTO_PATH = "esp_device_photo_path"
        private const val KEY_BATTERY_LEVEL = "esp_device_battery"
        private const val KEY_FIRMWARE_VERSION = "esp_device_fw"
        private const val REQUEST_PICK_IMAGE = 1001
        private const val REQUEST_PICK_FIRMWARE = 1002

        @JvmStatic
        fun newInstance(
            connected: Boolean, deviceName: String?, macAddress: String?, connectedTime: Long
        ) = DeviceDetailBottomSheet().apply {
            arguments = Bundle().apply {
                putBoolean(ARG_CONNECTED, connected)
                putString(ARG_DEVICE_NAME, deviceName)
                putString(ARG_MAC_ADDRESS, macAddress)
                putLong(ARG_CONNECTED_TIME, connectedTime)
            }
        }
    }

    var bleService: BleService? = null

    private var isConnected: Boolean = false
    private lateinit var deviceInfo: EspDeviceInfo
    private var currentPhotoPath: String? = null
    private var selectedFirmwareUri: Uri? = null
    private val handler = Handler(Looper.getMainLooper())
    private var refreshRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.DarkBottomSheetDialogTheme)
        isConnected = arguments?.getBoolean(ARG_CONNECTED, false) ?: false
        val name = arguments?.getString(ARG_DEVICE_NAME) ?: "未知设备"
        val mac = arguments?.getString(ARG_MAC_ADDRESS) ?: ""
        val ct = arguments?.getLong(ARG_CONNECTED_TIME, 0L) ?: 0L
        deviceInfo = EspDeviceInfo(deviceName = name, macAddress = mac, connectedTime = ct)
    }

    override fun onStart() {
        super.onStart()
        val dialog = dialog as? BottomSheetDialog
        dialog?.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            ?.setBackgroundColor(Color.TRANSPARENT)
        dialog?.findViewById<View>(com.google.android.material.R.id.container)
            ?.setBackgroundColor(Color.TRANSPARENT)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.bottom_sheet_device_detail, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        refreshUI()
        setupListeners()
        if (isConnected) startRefreshTimer()
    }

    fun updateRssi(rssi: Int) {
        deviceInfo = deviceInfo.copy(rssi = rssi)
        val v = view ?: return
        v.findViewById<SignalBar>(R.id.signal_bar).dbm = rssi
        v.findViewById<TextView>(R.id.tv_signal_dbm).text = "$rssi dBm"
    }

    fun updateDeviceStatus(batteryPercent: Int, firmwareVersion: String) {
        deviceInfo = deviceInfo.copy(batteryLevel = batteryPercent, firmwareVersion = firmwareVersion)
        FileUtils.writeSharedPreference(KEY_BATTERY_LEVEL, batteryPercent.toString())
        FileUtils.writeSharedPreference(KEY_FIRMWARE_VERSION, firmwareVersion)
        val v = view ?: return
        v.findViewById<BatteryBar>(R.id.battery_bar).level = batteryPercent
        v.findViewById<TextView>(R.id.tv_battery_pct).text = "$batteryPercent%"
        v.findViewById<TextView>(R.id.tv_firmware_version).text = firmwareVersion
    }

    private fun refreshUI() {
        // 注册官方 OTA notify 回调 (扇区ACK + 命令ACK) — 双模路径
        //   Legacy: BleService.onCharacteristicChanged → mOtaDataCallback
        //   Nordic: NordcBleManager.setNotificationCallback → onOtaNotify
        bleService?.setOtaDataCallback { data: ByteArray, charId: Int ->
            otaSender?.let { sender ->
                when (charId) {
                    0 -> sender.onSectorAckReceived(data)
                    1 -> sender.onCommandAckReceived(data)
                    else -> Log.d(TAG, "[OTA] unknown charId=$charId")
                }
            }
        }
        (bleService?.bleClient as? NordcBleManager)?.onOtaNotify =
            NordcBleManager.OnOtaNotifyCallback { data: ByteArray, charId: Int ->
                otaSender?.let { sender ->
                    when (charId) {
                        0 -> sender.onSectorAckReceived(data)
                        1 -> sender.onCommandAckReceived(data)
                        else -> Log.d(TAG, "[OTA] unknown charId=$charId")
                    }
                }
            }

        val v = view ?: return
        val photoView = v.findViewById<ImageView>(R.id.iv_device_photo)
        val nameView = v.findViewById<TextView>(R.id.tv_device_name)
        val macView = v.findViewById<TextView>(R.id.tv_device_mac)
        val batteryBar = v.findViewById<BatteryBar>(R.id.battery_bar)
        val batteryPct = v.findViewById<TextView>(R.id.tv_battery_pct)
        val signalBar = v.findViewById<SignalBar>(R.id.signal_bar)
        val signalDbm = v.findViewById<TextView>(R.id.tv_signal_dbm)
        val fwView = v.findViewById<TextView>(R.id.tv_firmware_version)
        val durView = v.findViewById<TextView>(R.id.tv_connected_duration)
        val overlay = v.findViewById<View>(R.id.unconnected_overlay)

        if (isConnected) {
            overlay.visibility = View.GONE
            nameView.text = deviceInfo.deviceName
            macView.text = deviceInfo.macAddress.ifEmpty { "--:--:--:--:--:--" }

            // 恢复持久化的电量/固件（防止 BottomSheet 重建后短暂显示 "--%"）
            val battery = if (deviceInfo.batteryLevel >= 0) deviceInfo.batteryLevel
                else FileUtils.readSharedPreference(KEY_BATTERY_LEVEL).toIntOrNull() ?: -1
            val fw = if (deviceInfo.firmwareVersion != "未知") deviceInfo.firmwareVersion
                else FileUtils.readSharedPreference(KEY_FIRMWARE_VERSION).ifEmpty { "未知" }

            fwView.text = fw
            batteryBar.level = battery
            batteryPct.text = if (battery >= 0) "$battery%" else "--%"
            signalBar.dbm = deviceInfo.rssi
            signalDbm.text = if (deviceInfo.rssi != 0) "${deviceInfo.rssi} dBm" else "-- dBm"
            updateDuration(durView)
        } else {
            overlay.visibility = View.VISIBLE
            nameView.text = "ESP32 导航设备"
            macView.text = "--:--:--:--:--:--"
            fwView.text = "--"
            batteryBar.level = -1
            batteryPct.text = "--%"
            signalBar.dbm = 0
            signalDbm.text = "-- dBm"
            durView.text = "--"
        }
        loadDevicePhoto(photoView)
    }

    private fun updateDuration(durView: TextView) {
        val elapsed = System.currentTimeMillis() - deviceInfo.connectedTime
        if (elapsed <= 0) {
            durView.text = "已连接"
            return
        }
        val totalSec = elapsed / 1000
        val min = totalSec / 60
        val sec = totalSec % 60
        durView.text = if (min > 0) "${min}分${sec}秒" else "${sec}秒"
    }

    private fun startRefreshTimer() {
        stopRefreshTimer()
        refreshRunnable = object : Runnable {
            override fun run() {
                bleService?.readRemoteRssi()
                val durView = view?.findViewById<TextView>(R.id.tv_connected_duration)
                if (durView != null) updateDuration(durView)
                handler.postDelayed(this, 1000L)
            }
        }
        handler.post(refreshRunnable!!)
    }

    private fun stopRefreshTimer() {
        refreshRunnable?.let { handler.removeCallbacks(it) }
        refreshRunnable = null
    }

    private var otaSender: BleOTASender? = null
    private var otaFirmwareBytes: ByteArray? = null
    private var otaFirmwareSha256: ByteArray? = null
    private var otaNewVersion: String = ""
    private val otaScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private fun setupListeners() {
        val v = view ?: return
        v.findViewById<ImageView>(R.id.iv_device_photo).setOnClickListener {
            startActivityForResult(Intent(Intent.ACTION_GET_CONTENT).apply { type = "image/*" }, REQUEST_PICK_IMAGE)
        }
        // 本地文件选择
        v.findViewById<TextView>(R.id.btn_select_firmware).setOnClickListener {
            startActivityForResult(
                Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*"
                    putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/octet-stream", "application/x-binary"))
                }, REQUEST_PICK_FIRMWARE
            )
        }
        // 在线检查更新
        v.findViewById<TextView>(R.id.btn_check_update).setOnClickListener {
            checkOnlineUpdate()
        }
        // 开始推送
        v.findViewById<TextView>(R.id.btn_start_update).setOnClickListener {
            startFirmwareUpdate()
        }
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != Activity.RESULT_OK || data?.data == null) return
        when (requestCode) {
            REQUEST_PICK_IMAGE -> savePhotoToInternal(data.data!!)
            REQUEST_PICK_FIRMWARE -> onFirmwareSelected(data.data!!)
        }
    }

    private fun loadDevicePhoto(photoView: ImageView) {
        val savedPath = FileUtils.readSharedPreference(KEY_PHOTO_PATH)
        if (savedPath.isNotEmpty()) {
            val file = File(savedPath)
            if (file.exists()) {
                currentPhotoPath = savedPath
                photoView.setImageBitmap(BitmapFactory.decodeFile(savedPath, BitmapFactory.Options().apply { inSampleSize = 2 }))
                return
            } else {
                FileUtils.writeSharedPreference(KEY_PHOTO_PATH, "")
            }
        }
        photoView.setImageResource(R.mipmap.esp32shebeitupianmoren)
    }

    private fun savePhotoToInternal(uri: Uri) {
        try {
            val inputStream = requireContext().contentResolver.openInputStream(uri) ?: return
            val dir = File(requireContext().getExternalFilesDir(Environment.DIRECTORY_PICTURES), "device")
            if (!dir.exists()) dir.mkdirs()
            val destFile = File(dir, "esp32_device_photo.jpg")
            FileOutputStream(destFile).use { output -> inputStream.copyTo(output) }
            inputStream.close()
            currentPhotoPath = destFile.absolutePath
            FileUtils.writeSharedPreference(KEY_PHOTO_PATH, currentPhotoPath!!)

            view?.findViewById<ImageView>(R.id.iv_device_photo)?.let {
                it.setImageBitmap(BitmapFactory.decodeFile(currentPhotoPath, BitmapFactory.Options().apply { inSampleSize = 2 }))
            }
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "保存照片失败", Toast.LENGTH_SHORT).show()
        }
    }

    private fun onFirmwareSelected(uri: Uri) {
        selectedFirmwareUri = uri
        val v = view ?: return
        val tvFileName = v.findViewById<TextView>(R.id.tv_firmware_file_name)
        val tvFileSize = v.findViewById<TextView>(R.id.tv_firmware_file_size)
        val btnStart = v.findViewById<TextView>(R.id.btn_start_update)

        var displayName = uri.lastPathSegment ?: "未知文件"
        var sizeText = ""
        var fileBytes: ByteArray? = null

        try {
            requireContext().contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                    if (nameIdx >= 0) displayName = cursor.getString(nameIdx)
                    if (sizeIdx >= 0) {
                        val bytes = cursor.getLong(sizeIdx)
                        sizeText = when {
                            bytes < 1024 -> "${bytes} B"
                            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
                            else -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
                        }
                    }
                }
            }
            // 读入内存 (用于本地文件推送)
            val inputStream = requireContext().contentResolver.openInputStream(uri)
            fileBytes = inputStream?.readBytes()
            inputStream?.close()
        } catch (_: Exception) {}

        if (fileBytes != null && fileBytes.isNotEmpty()) {
            val md = MessageDigest.getInstance("SHA-256")
            otaFirmwareSha256 = md.digest(fileBytes)
            otaFirmwareBytes = fileBytes
        }

        tvFileName.text = displayName
        if (sizeText.isNotEmpty()) {
            tvFileSize.text = "文件大小: $sizeText"
            tvFileSize.visibility = View.VISIBLE
        }
        btnStart.visibility = View.VISIBLE
    }

    // ── 在线检查更新 ──

    private fun checkOnlineUpdate() {
        val statusView = view?.findViewById<TextView>(R.id.tv_online_update_status) ?: return
        statusView.text = "检查中..."
        statusView.setTextColor(Color.parseColor("#FFC107"))

        otaScope.launch {
            // 用持久化版本号兜底 (设备状态首次到达前 deviceInfo.firmwareVersion 为 "未知")
            val curVer = if (deviceInfo.firmwareVersion != "未知") deviceInfo.firmwareVersion
                else FileUtils.readSharedPreference("esp_device_fw").ifEmpty { "未知" }
            val result = FirmwareChecker.check("moto_s3", curVer)
            withContext(Dispatchers.Main) {
                if (result.error != null) {
                    statusView.text = "检查失败: ${result.error}"
                    statusView.setTextColor(Color.parseColor("#FF5252"))
                } else if (result.info?.hasUpdate == true) {
                    val info = result.info!!
                    val sizeStr = when {
                        info.fileSize < 1024 -> "${info.fileSize} B"
                        info.fileSize < 1024 * 1024 -> "${info.fileSize / 1024} KB"
                        else -> String.format("%.1f MB", info.fileSize / (1024.0 * 1024.0))
                    }
                    statusView.text = "v${info.latestVersion} ($sizeStr) 可用\n${info.changelogZh}"
                    statusView.setTextColor(Color.parseColor("#4CAF50"))
                    // 变成下载按钮
                    val btnCheck = view?.findViewById<TextView>(R.id.btn_check_update)
                    btnCheck?.text = "下载"
                    btnCheck?.setOnClickListener { downloadAndPrepareUpdate(info) }
                } else {
                    statusView.text = "已是最新版本 (${deviceInfo.firmwareVersion})"
                    statusView.setTextColor(Color.parseColor("#808080"))
                }
            }
        }
    }

    private fun downloadAndPrepareUpdate(info: FirmwareChecker.FirmwareInfo) {
        val statusView = view?.findViewById<TextView>(R.id.tv_online_update_status) ?: return
        val btnCheck = view?.findViewById<TextView>(R.id.btn_check_update) ?: return
        btnCheck.text = "下载中..."
        btnCheck.isEnabled = false
        statusView.text = "下载中 0%"

        otaScope.launch {
            val (file, error) = FirmwareChecker.download(info.downloadUrl, info.sha256)
            withContext(Dispatchers.Main) {
                if (error != null) {
                    statusView.text = "下载失败: $error"
                    statusView.setTextColor(Color.parseColor("#FF5252"))
                    btnCheck.text = "重试"
                    btnCheck.isEnabled = true
                } else if (file != null) {
                    otaFirmwareBytes = file.readBytes()
                    val md = MessageDigest.getInstance("SHA-256")
                    otaFirmwareSha256 = md.digest(otaFirmwareBytes)
                    file.delete()  // 清理临时文件
                    otaNewVersion = info.latestVersion

                    val sizeStr = when {
                        otaFirmwareBytes!!.size < 1024 * 1024 -> "${otaFirmwareBytes!!.size / 1024} KB"
                        else -> String.format("%.1f MB", otaFirmwareBytes!!.size / (1024.0 * 1024.0))
                    }
                    statusView.text = "v${info.latestVersion} ($sizeStr) 已就绪"
                    statusView.setTextColor(Color.parseColor("#4CAF50"))
                    btnCheck.text = "已下载"
                    // 显示推送按钮
                    view?.findViewById<TextView>(R.id.btn_start_update)?.visibility = View.VISIBLE
                }
            }
        }
    }

    // ── BLE OTA 推送 ──

    private sealed class OtaState {
        object Idle : OtaState()
        data class Sending(val progress: Int, val total: Int) : OtaState()
        object Verifying : OtaState()
        object Success : OtaState()
        data class Failed(val reason: String) : OtaState()
    }

    private fun startFirmwareUpdate() {
        val firmwareBytes = otaFirmwareBytes ?: run {
            Toast.makeText(requireContext(), "请先选择固件文件", Toast.LENGTH_SHORT).show()
            return
        }
        val ble = bleService?.bleClient
        val recvFwChar = bleService?.getOtaRecvFwChar()
        val commandChar = bleService?.getOtaCommandChar()
        Log.d(TAG, "[OTA] ble=${ble!=null} recvFw=${recvFwChar!=null} cmd=${commandChar!=null}")
        if (ble == null || recvFwChar == null || commandChar == null) {
            Toast.makeText(requireContext(), "OTA Service 未就绪 (ESP32 固件需更新)", Toast.LENGTH_SHORT).show()
            return
        }

        val btnStart = view?.findViewById<TextView>(R.id.btn_start_update) ?: return
        btnStart.isEnabled = false
        btnStart.text = "推送中 0%"

        otaSender?.abort()
        otaSender = BleOTASender(ble, recvFwChar, commandChar, firmwareBytes,
            object : BleOTASender.Callback {
                override fun onProgress(percent: Int) {
                    btnStart.post { btnStart.text = "推送中 $percent%" }
                }
                override fun onError(code: Int, msg: String) {
                    Log.e(TAG, "[OTA] error: $msg")
                    btnStart.post {
                        btnStart.text = "推送失败"
                        btnStart.setBackgroundColor(Color.parseColor("#FF5252"))
                        btnStart.isEnabled = true
                        Toast.makeText(requireContext(), "OTA 失败: $msg", Toast.LENGTH_LONG).show()
                    }
                }
                override fun onSuccess() {
                    Log.i(TAG, "[OTA] success — ESP32 will restart")
                    btnStart.post {
                        btnStart.text = "推送成功"
                        btnStart.setBackgroundColor(Color.parseColor("#4CAF50"))
                        btnStart.isEnabled = true
                    }
                }
            })
        otaSender?.packetSize = 463  // MTU 500 - 3 bytes header - 2 bytes CRC (末包)
        otaSender?.start()
    }

    override fun onDestroyView() {
        bleService?.setOtaDataCallback(null)
        (bleService?.bleClient as? NordcBleManager)?.onOtaNotify = null  // Nordic OTA 回调清理
        stopRefreshTimer()
        super.onDestroyView()
    }

    override fun onDestroy() {
        otaScope.cancel()
        otaSender?.abort()
        super.onDestroy()
    }
}
