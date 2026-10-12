package org.zarroboogs.maps.ble;

import android.app.AlertDialog;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import org.zarroboogs.maps.R;

/**
 * BLE 断开连接确认对话框（共享组件）。
 * MapsFragment 和 NaviCustomActivity 共用，UI 统一。
 */
public class BleDisconnectDialog {

    private final Context mContext;
    private final BleService mBleService;
    private AlertDialog mDialog;

    public BleDisconnectDialog(Context context, BleService service) {
        mContext = context;
        mBleService = service;
    }

    public void show() {
        if (mBleService == null) return;

        String deviceName = mBleService.getConnectedDeviceName();
        if (deviceName == null || deviceName.isEmpty()) deviceName = "未知设备";

        View view = LayoutInflater.from(mContext).inflate(R.layout.dialog_ble_disconnect, null);
        TextView nameView = view.findViewById(R.id.ble_disconnect_device_name);
        nameView.setText("当前已连接: " + deviceName);

        mDialog = new AlertDialog.Builder(mContext)
                .setTitle("蓝牙连接")
                .setView(view)
                .setPositiveButton("断开连接", (d, w) -> {
                    if (mBleService != null) mBleService.disconnect();
                })
                .setNegativeButton("取消", null)
                .create();

        mDialog.setOnDismissListener(d -> mDialog = null);
        mDialog.show();
        // 深色圆角背景 + 白色标题/按钮
        if (mDialog.getWindow() != null) {
            mDialog.getWindow().setBackgroundDrawableResource(R.drawable.rounded_dialog_dark_background);
        }
        int titleId = mContext.getResources().getIdentifier("alertTitle", "id", "android");
        if (titleId > 0) {
            TextView titleView = mDialog.findViewById(titleId);
            if (titleView != null) titleView.setTextColor(0xFFFFFFFF);
        }
    }

    public void dismiss() {
        if (mDialog != null && mDialog.isShowing()) {
            mDialog.dismiss();
        }
        mDialog = null;
    }

    public boolean isShowing() {
        return mDialog != null && mDialog.isShowing();
    }
}
