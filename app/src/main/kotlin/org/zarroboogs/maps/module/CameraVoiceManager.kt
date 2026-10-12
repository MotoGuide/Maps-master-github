package org.zarroboogs.maps.module

import android.content.Context
import android.speech.tts.TextToSpeech
import org.zarroboogs.maps.utils.FileLogger
import org.zarroboogs.maps.MapsApplication
import java.util.Locale

class CameraVoiceManager private constructor(context: Context) {

    companion object {
        private const val TAG = "MotoNav"
        private const val COOLDOWN_MS = 5000L

        @Volatile
        private var instance: CameraVoiceManager? = null

        @JvmStatic
        fun getInstance(context: Context): CameraVoiceManager {
            return instance ?: synchronized(this) {
                instance ?: CameraVoiceManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private var tts: TextToSpeech? = null
    private var ttsReady: Boolean = false
    private var mMuted: Boolean = false
    private var mLastAlarm: Int = 0
    private var mLastAnnounceTime: Long = 0
    private var mLastAnnouncedDistanceM: Int = -1
    private val pendingSpeaks = mutableListOf<() -> Unit>()

    private val alarmTemplateMap = mapOf(
        0x01 to "前方%d米有闯红灯抓拍摄像头",
        0x02 to "前方%d米有靠右行驶抓拍摄像头",
        0x03 to "前方%d米有公交车道抓拍摄像头",
        0x04 to "前方%d米有非机动车道抓拍摄像头",
        0x05 to "前方%d米有限行抓拍摄像头",
        0x06 to "前方%d米有测速摄像头，请控制车速",
        0x07 to "前方%d米有违规变道抓拍摄像头",
        0x08 to "前方%d米有逆行抓拍摄像头",
        0x09 to "前方%d米有不戴头盔抓拍摄像头",
        0x0A to "前方%d米有应急车道抓拍摄像头",
        0x0B to "前方%d米有噪音超标抓拍摄像头",
        0x0C to "前方%d米有压线抓拍摄像头",
        0x0D to "前方%d米有违规用灯抓拍摄像头",
    )

    fun init() {
        if (tts != null && ttsReady) return
        if (tts == null) {
            tts = TextToSpeech(MapsApplication.getAppContext()) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    tts?.language = Locale.CHINESE
                    ttsReady = true
                    FileLogger.d(TAG, "TTS initialized: Chinese")
                    flushPendingSpeaks()
                } else {
                    FileLogger.e(TAG, "TTS init failed, status=$status")
                }
            }
        }
    }

    /** 每次导航启动调用，恢复默认播报状态 */
    fun reset() {
        mMuted = false
        mLastAlarm = 0
        mLastAnnounceTime = 0
        mLastAnnouncedDistanceM = -1
    }

    /** 导航模式：摄像头告警播报，同一摄像头距离每变化50m重新播报 */
    fun onAlarmChanged(alarm: Int, distanceM: Int) {
        if (alarm == 0x00) {
            mLastAlarm = 0
            mLastAnnouncedDistanceM = -1
            return
        }
        if (mMuted) return

        val now = System.currentTimeMillis()

        // 新摄像头 → 立即播报
        if (alarm != mLastAlarm) {
            val template = alarmTemplateMap[alarm] ?: return
            if (now - mLastAnnounceTime < COOLDOWN_MS && mLastAlarm != 0) return
            mLastAlarm = alarm
            mLastAnnounceTime = now
            val rounded = (distanceM / 50) * 50
            mLastAnnouncedDistanceM = rounded
            val text = if (rounded > 0) String.format(template, rounded)
                       else template.replace("%d米", "")
            FileLogger.d(TAG, "speak(new cam): $text")
            speakOrQueue(text, "cam_" + Integer.toHexString(alarm))
            return
        }

        // 同一摄像头：距离每变化 ≥50m 重新播报
        if (now - mLastAnnounceTime < COOLDOWN_MS) return
        val rounded = (distanceM / 50) * 50
        if (rounded <= 0) return
        if (rounded == mLastAnnouncedDistanceM) return

        val template = alarmTemplateMap[alarm] ?: return
        mLastAnnounceTime = now
        mLastAnnouncedDistanceM = rounded
        val text = String.format(template, rounded)
        FileLogger.d(TAG, "speak(progressive): $text")
        speakOrQueue(text, "cam_" + Integer.toHexString(alarm))
    }

    /** 巡航模式：地理围栏告警播报 */
    fun playCruiseAlert(radiusM: Int) {
        if (mMuted) {
            FileLogger.w(TAG, "[VOICE] cruise alert BLOCKED (muted)")
            return
        }
        val text = "请注意，${radiusM}米范围内有摩托车抓拍摄像头"
        FileLogger.w(TAG, "[VOICE] cruise alert: $text")
        FileLogger.d(TAG, "speak cruise: $text")
        speakOrQueue(text, "cruise_alert")
    }

    fun toggleMute() {
        mMuted = !mMuted
        if (!mMuted) {
            mLastAlarm = 0
            mLastAnnounceTime = 0
        }
        FileLogger.d(TAG, "mute toggled: $mMuted")
    }

    fun isMuted(): Boolean = mMuted

    private fun speakOrQueue(text: String, utteranceId: String) {
        if (ttsReady) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        } else {
            FileLogger.d(TAG, "TTS not ready, queueing: $text")
            pendingSpeaks.add { tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId) }
        }
    }

    private fun flushPendingSpeaks() {
        if (pendingSpeaks.isEmpty()) return
        FileLogger.d(TAG, "Flushing ${pendingSpeaks.size} pending speaks")
        val batch = pendingSpeaks.toList()
        pendingSpeaks.clear()
        for (speak in batch) {
            speak.invoke()
        }
    }

    fun destroy() {
        pendingSpeaks.clear()
        tts?.stop()
        tts?.shutdown()
        tts = null
        ttsReady = false
    }
}
