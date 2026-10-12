package org.zarroboogs.maps.ble;

import android.app.Service;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.le.ScanResult;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;

import org.zarroboogs.maps.module.GeoFenceManager;

import java.util.ArrayList;
import java.util.List;

/**
 * 【开源版精简壳】BLE 连接/推送服务。
 *
 * 说明：BLE 连接、扫描、推送协议等底层实现未包含在本开源版本中。
 * 本类仅保留原有公开接口签名（供 UI 层无改动调用），所有实现均为空操作，
 * 开源版中连接设备/扫描/推送等操作不会实际生效。
 */
public class BleService extends Service {

    /** 供 UI 层绑定的 Binder（保留原签名） */
    public class LocalBinder extends Binder {
        public BleService getService() {
            return BleService.this;
        }
    }

    /** OTA 数据回调接口（保留类型定义，UI 层以 SAM 方式使用） */
    public interface OtaDataCallback {
        void onOtaData(byte[] data, int charId);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return new LocalBinder();
    }

    // ---- 以下均为保留签名的空实现（开源版不含 BLE 底层）----

    public void setGeoFenceManager(GeoFenceManager gfm) {}

    public void registerListener(BleServiceListener listener) {}

    public void unregisterListener() {}

    public boolean isConnected() { return false; }

    public BleClient getBleClient() { return null; }

    public void setOtaDataCallback(OtaDataCallback cb) {}

    public BluetoothGatt getGatt() { return null; }

    public BluetoothGattCharacteristic getOtaRecvFwChar() { return null; }

    public BluetoothGattCharacteristic getOtaCommandChar() { return null; }

    public boolean isOtaReady() { return false; }

    public String getConnectedDeviceName() { return null; }

    public String getConnectedDeviceAddress() { return null; }

    public long getConnectedTime() { return 0; }

    public void readRemoteRssi() {}

    public boolean isScanning() { return false; }

    public List<ScanResult> getScanResults() { return new ArrayList<>(); }

    public void setNavActive(boolean active) {}

    public void startGpsInjection() {}

    public void stopGpsInjection() {}

    public void startGpsForGeofence() {}

    public void stopGpsForGeofence() {}

    public boolean isGpsInjecting() { return false; }

    public void startScan() {}

    public void stopScan() {}

    public void connect(BluetoothDevice device) {}

    public void disconnect() {}

    public void updateNotificationText(String text) {}

    public List<String[]> loadHistoryDevices() { return new ArrayList<>(); }
}
