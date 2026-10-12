package org.zarroboogs.maps.utils

import android.content.Context
import android.util.Log
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 双写日志：logcat + 内部存储文件。
 * 每天一个文件 navi_yyyy-MM-dd.log，自动清理 7 天以前的日志。
 */
object FileLogger {
    private var logDir: File? = null
    private var writer: BufferedWriter? = null
    private var currentDate: String = ""
    private val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val timeFmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    @JvmStatic
    fun init(context: Context) {
        val ctx = context.applicationContext ?: context
        logDir = File(ctx.filesDir, "logs")
        logDir!!.mkdirs()
        rotateLogs()
        i("FileLogger", "Initialized: ${logDir!!.absolutePath}")
    }

    private fun openLogFile() {
        val today = dateFmt.format(Date())
        if (today == currentDate && writer != null) return
        try { writer?.close() } catch (_: Exception) {}
        currentDate = today
        writer = try {
            BufferedWriter(FileWriter(File(logDir, "navi_$today.log"), true))
        } catch (e: Exception) {
            Log.e("FileLogger", "Failed to open log file", e)
            null
        }
    }

    private fun rotateLogs() {
        val cutoff = System.currentTimeMillis() - 7L * 24 * 3600_000L
        logDir?.listFiles()?.forEach { file ->
            if (file.isFile && file.lastModified() < cutoff) file.delete()
        }
    }

    @JvmStatic fun d(tag: String, msg: String) { Log.d(tag, msg); write("D", tag, msg) }
    @JvmStatic fun w(tag: String, msg: String) { Log.w(tag, msg); write("W", tag, msg) }
    @JvmStatic fun w(tag: String, msg: String, tr: Throwable) { Log.w(tag, msg, tr); write("W", tag, "$msg ${tr.message}") }
    @JvmStatic fun e(tag: String, msg: String) { Log.e(tag, msg); write("E", tag, msg) }
    @JvmStatic fun e(tag: String, msg: String, tr: Throwable) { Log.e(tag, msg, tr); write("E", tag, "$msg ${tr.message}") }
    @JvmStatic fun i(tag: String, msg: String) { Log.i(tag, msg); write("I", tag, msg) }

    private fun write(level: String, tag: String, msg: String) {
        try {
            openLogFile()
            val time = timeFmt.format(Date())
            writer?.write("$time [$level] $tag: $msg\n")
            writer?.flush()
        } catch (_: Exception) {}
    }

    /** 返回所有日志文件（供导出/分享） */
    @JvmStatic
    fun getLogFiles(): Array<File> {
        return logDir?.listFiles { f -> f.isFile && f.name.endsWith(".log") } ?: emptyArray()
    }

    /** 今天日志文件的路径 */
    @JvmStatic
    fun getTodayLogFile(): File? {
        val today = dateFmt.format(Date())
        return File(logDir, "navi_$today.log")
    }
}
