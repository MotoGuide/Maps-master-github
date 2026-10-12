package org.zarroboogs.maps.ble

import java.io.File

/**
 * 【开源版精简壳】OTA 固件检查/下载。
 *
 * 说明：固件分发服务对接实现未包含在本开源版本中。
 * 此处仅保留公开接口签名（供 UI 层无改动调用），调用将返回不可用提示。
 */
object FirmwareChecker {

    data class FirmwareInfo(
        val hasUpdate: Boolean = false,
        val latestVersion: String = "",
        val fileSize: Long = 0,
        val sha256: String = "",
        val downloadUrl: String = "",
        val changelogZh: String = "",
        val minAppVersion: String = ""
    )

    data class CheckResult(val info: FirmwareInfo?, val error: String?)

    /** 检查固件更新（开源版：不可用） */
    suspend fun check(deviceType: String, currentVersion: String): CheckResult =
        CheckResult(null, "开源版本不含 OTA 固件服务")

    /** 下载固件（开源版：不可用） */
    suspend fun download(url: String, expectedSha256: String): Pair<File?, String?> =
        Pair(null, "开源版本不含 OTA 固件服务")
}
