package org.zarroboogs.maps.ble;

import java.util.List;

/** BleService 向 UI 层报告事件的回调接口 */
public interface BleServiceListener {

    void onScanResult(List<BleDeviceEntry> devices);
    void onConnectionStateChanged(boolean connected, String deviceName);
    void onScanStarted();
    void onScanStopped();
    void onError(String message);
    default void onRssiRead(int rssi) {}
    default void onDeviceStatus(int batteryPercent, String firmwareVersion) {}
    /** 设备遥测扩展：含 heap_free_kb + fps */
    default void onDeviceTelemetry(int batteryPercent, String firmwareVersion, int heapKb, int fps) {}

    /** OTA 通知原始数据 */
    default void onOtaNotify(byte[] data) {}

    /** 简单的 BLE 设备项 DTO */
    class BleDeviceEntry {
        public final String name;
        public final String mac;
        public final int rssi;

        public BleDeviceEntry(String name, String mac, int rssi) {
            this.name = name;
            this.mac = mac;
            this.rssi = rssi;
        }
    }
}
