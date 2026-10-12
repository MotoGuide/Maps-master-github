package org.zarroboogs.maps.hub

import org.zarroboogs.maps.ble.BleClient

/**
 * 【开源版精简壳】BLE 客户端提供者。
 *
 * 说明：BLE 底层实现未包含在本开源版本中，本类仅保留既有接口签名。
 */
object BleClientProvider {
    @Volatile
    var client: BleClient? = null
        private set

    /** 开关变更后调用，下次连接生效（开源版：空操作） */
    fun reload() {}
}
