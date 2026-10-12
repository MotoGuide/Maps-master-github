package org.zarroboogs.maps.ble

import android.bluetooth.BluetoothGattCharacteristic

/**
 * 【开源版精简壳】ESP BLE OTA 发送端。
 *
 * 说明：OTA 协议实现未包含在本开源版本中。
 * 本类仅保留原有构造与回调签名（供 UI 层无改动调用），实现均为空操作。
 */
class BleOTASender(
    bleClient: BleClient,
    recvFwChar: BluetoothGattCharacteristic,
    commandChar: BluetoothGattCharacteristic,
    bin: ByteArray,
    callback: Callback
) {
    interface Callback {
        fun onProgress(percent: Int)
        fun onError(code: Int, msg: String)
        fun onSuccess()
    }

    /** 分片大小（MTU 协商后由调用方更新） */
    var packetSize = 20

    fun start() {}

    fun abort() {}

    /** 处理 ESP32 发来的扇区 ACK（开源版：空操作） */
    fun onSectorAckReceived(data: ByteArray) {}

    /** 处理 ESP32 发来的命令 ACK（开源版：空操作） */
    fun onCommandAckReceived(data: ByteArray) {}
}
