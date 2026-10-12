package org.zarroboogs.maps.ble

/**
 * 【开源版精简壳】Nordic BLE 管理器。
 *
 * 说明：BLE 连接/OTA 底层实现未包含在本开源版本中，
 * 此处仅保留 UI 层引用到的类型与回调节点。
 */
class NordcBleManager : BleClient {

    /** OTA notify 回调（UI 层赋值/清理） */
    fun interface OnOtaNotifyCallback {
        fun onOtaNotify(data: ByteArray, charId: Int)
    }

    var onOtaNotify: OnOtaNotifyCallback? = null
}
