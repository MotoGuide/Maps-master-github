package org.zarroboogs.maps.service

import android.app.Notification
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 一次性工具：抓取高德地图/腾讯地图的导航系统通知，分析通知数据的完整度。
 * 日志写到 files/logs/notify_yyyy-MM-dd.log，JSON 行格式，一行一条通知。
 */
class NavNotificationListener : NotificationListenerService() {

    companion object {
        private val TARGET_PACKAGES = setOf(
            "com.autonavi.minimap",  // 高德地图
            "com.tencent.map"        // 腾讯地图
        )

        @JvmStatic
        fun isEnabled(context: Context): Boolean {
            val flat = Settings.Secure.getString(
                context.contentResolver,
                "enabled_notification_listeners"
            )
            return flat?.contains(context.packageName) == true
        }

        @JvmStatic
        fun openSettings(context: Context) {
            context.startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
        }
    }

    private var logFile: File? = null
    private var writer: FileWriter? = null
    private var currentDate: String = ""
    private val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val timeFmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    override fun onCreate() {
        super.onCreate()
        val logDir = File(filesDir, "logs")
        logDir.mkdirs()
        openLogFile()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName !in TARGET_PACKAGES) return

        try {
            openLogFile()
            val notif = sbn.notification
            val extras = notif.extras
            val line = buildString {
                append("{")
                append("\"ts\":${System.currentTimeMillis()},")
                append("\"pkg\":\"${sbn.packageName}\",")
                append("\"title\":\"${extras.getString(Notification.EXTRA_TITLE, "")}\",")
                append("\"text\":\"${extras.getString(Notification.EXTRA_TEXT, "")}\",")
                append("\"subText\":\"${extras.getString(Notification.EXTRA_SUB_TEXT, "")}\",")
                append("\"bigText\":\"${extras.getCharSequence(Notification.EXTRA_BIG_TEXT, "")}\",")
                append("\"summaryText\":\"${extras.getString(Notification.EXTRA_SUMMARY_TEXT, "")}\",")
                append("\"postTime\":${sbn.postTime}")
                append("}\n")
            }
            writer?.write(line)
            writer?.flush()
        } catch (_: Exception) {}
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {}

    private fun openLogFile() {
        val today = dateFmt.format(Date())
        if (today == currentDate && writer != null) return
        try { writer?.close() } catch (_: Exception) {}
        currentDate = today
        logFile = File(File(filesDir, "logs"), "notify_$today.log")
        writer = try {
            FileWriter(logFile, true)
        } catch (_: Exception) {
            null
        }
    }

    override fun onDestroy() {
        try { writer?.close() } catch (_: Exception) {}
        super.onDestroy()
    }
}
