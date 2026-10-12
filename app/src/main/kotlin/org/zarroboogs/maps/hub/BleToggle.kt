package org.zarroboogs.maps.hub

import org.zarroboogs.maps.utils.FileUtils

/**
 * BLE 模式切换开关：Nordic（默认） / Legacy（手写 BleWriter）。
 *
 * 用户可在设备列表弹窗中勾选"兼容 BLE 模式"切为 Legacy，
 * 下次连接生效（不主动断开当前连接）。
 */
object BleToggle {
    private const val KEY = "ble_use_nordic"
    /** 默认走 Nordic，出问题时用户可手动切 Legacy */
    private const val DEFAULT = true

    fun useNordic(): Boolean {
        val raw = try { FileUtils.readSharedPreference(KEY) } catch (_: Exception) { null }
        // 防御：SharedPreferences 可能返回空字符串而非 null，空串 toBoolean()=false 会导致误判
        return when (raw) {
            "true" -> true
            "false" -> false
            else -> DEFAULT  // null / "" / 其他异常值 → 走默认
        }
    }

    fun setUseNordic(use: Boolean) = FileUtils.writeSharedPreference(KEY, use.toString())
}
