package org.zarroboogs.maps.ble;

import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import org.zarroboogs.maps.R;
import org.zarroboogs.maps.hub.BleToggle;
import org.zarroboogs.maps.hub.BleClientProvider;

import java.util.ArrayList;
import java.util.List;

/**
 * BLE 设备列表对话框（共享组件）。
 * MapsFragment 和 NaviCustomActivity 共用，UI 统一。
 */
public class BleDeviceListDialog {

    private static final int PAGE_SIZE = 10;

    private final Context mContext;
    private final BleService mBleService;

    private AlertDialog mDialog;
    private ListView mListView;
    private TextView mPageIndicator;
    private Button mBtnPrev, mBtnNext;
    private Button mTabHistory, mTabNew;
    private CheckBox mLegacyModeCheckbox;
    private int mTabActive;       // 0=history, 1=new
    private int mPage;
    private boolean mScanning;

    public BleDeviceListDialog(Context context, BleService service) {
        mContext = context;
        mBleService = service;
    }

    public void show() {
        if (mBleService == null) return;

        int totalDevices = getDeviceCount();
        int totalPages = Math.max(1, (totalDevices + PAGE_SIZE - 1) / PAGE_SIZE);
        if (mPage >= totalPages) mPage = 0;

        int start = mPage * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, totalDevices);

        List<String> pageItems = buildPageItems(start, end);

        String title = (mTabActive == 0 ? "历史设备" : "新设备") + " (" + totalDevices + "台)";

        // Already showing: update content
        if (mDialog != null && mDialog.isShowing()) {
            mListView.setAdapter(new ArrayAdapter<>(mContext, R.layout.item_ble_device, pageItems));
            mDialog.setTitle(title);
            updatePagination(totalPages);
            updateTabButtons();
            return;
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(mContext, R.layout.item_ble_device, pageItems);
        View view = LayoutInflater.from(mContext).inflate(R.layout.dialog_ble_device_list, null);
        mListView = view.findViewById(R.id.ble_device_list);
        mListView.setAdapter(adapter);
        mPageIndicator = view.findViewById(R.id.ble_page_indicator);
        mBtnPrev = view.findViewById(R.id.ble_btn_prev);
        mBtnNext = view.findViewById(R.id.ble_btn_next);
        mTabHistory = view.findViewById(R.id.ble_tab_history);
        mTabNew = view.findViewById(R.id.ble_tab_new);
        mLegacyModeCheckbox = view.findViewById(R.id.ble_legacy_mode_checkbox);
        // 回退开关：读取当前状态并设置监听器
        mLegacyModeCheckbox.setChecked(!BleToggle.INSTANCE.useNordic());
        mLegacyModeCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            BleToggle.INSTANCE.setUseNordic(!isChecked);
            BleClientProvider.INSTANCE.reload();
            Toast.makeText(mContext, "已切换，重新连接后生效", Toast.LENGTH_SHORT).show();
        });

        updatePagination(totalPages);
        updateTabButtons();

        AlertDialog.Builder builder = new AlertDialog.Builder(mContext);
        builder.setTitle(title);
        builder.setView(view);
        builder.setNegativeButton("关闭", null);
        mDialog = builder.create();

        // Item click → connect
        mListView.setOnItemClickListener((parent, itemView, position, id) -> {
            int realIndex = mPage * PAGE_SIZE + position;
            dismiss();
            if (mTabActive == 0) {
                List<String[]> history = mBleService.loadHistoryDevices();
                if (realIndex >= 0 && realIndex < history.size()) {
                    String mac = history.get(realIndex)[1];
                    try {
                        BluetoothManager bm = (BluetoothManager) mContext.getSystemService(Context.BLUETOOTH_SERVICE);
                        BluetoothAdapter btAdapter = bm.getAdapter();
                        if (btAdapter != null) {
                            BluetoothDevice dev = btAdapter.getRemoteDevice(mac);
                            mBleService.connect(dev);
                        }
                    } catch (Exception e) {
                        Toast.makeText(mContext, "无法连接该设备", Toast.LENGTH_SHORT).show();
                    }
                }
            } else {
                List<ScanResult> results = mBleService.getScanResults();
                if (realIndex >= 0 && realIndex < results.size()) {
                    mBleService.connect(results.get(realIndex).getDevice());
                }
            }
        });

        // Tab switching
        mTabHistory.setOnClickListener(v -> {
            if (mTabActive != 0) { mTabActive = 0; mPage = 0; show(); }
        });
        mTabNew.setOnClickListener(v -> {
            if (mTabActive != 1) { mTabActive = 1; mPage = 0; show(); }
        });

        // Pagination
        mBtnPrev.setOnClickListener(v -> {
            if (mPage > 0) { mPage--; show(); }
        });
        mBtnNext.setOnClickListener(v -> {
            int curTotalPages = Math.max(1, (getDeviceCount() + PAGE_SIZE - 1) / PAGE_SIZE);
            if (mPage < curTotalPages - 1) { mPage++; show(); }
        });

        mDialog.setOnDismissListener(d -> {
            if (mBleService != null) mBleService.stopScan();
            mDialog = null;
            mListView = null;
            mPageIndicator = null;
            mBtnPrev = null;
            mBtnNext = null;
            mTabHistory = null;
            mTabNew = null;
            mLegacyModeCheckbox = null;
        });
        mDialog.show();
        // 深色圆角背景 + 白色标题
        if (mDialog.getWindow() != null) {
            mDialog.getWindow().setBackgroundDrawableResource(R.drawable.rounded_dialog_dark_background);
        }
        // 标题颜色
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

    public void setScanning(boolean scanning) {
        mScanning = scanning;
    }

    // ---- private helpers ----

    private int getDeviceCount() {
        if (mBleService == null) return 0;
        if (mTabActive == 0) return mBleService.loadHistoryDevices().size();
        return mBleService.getScanResults().size();
    }

    private List<String> buildPageItems(int start, int end) {
        List<String> items = new ArrayList<>();
        if (mBleService == null) {
            items.add("服务未初始化");
            return items;
        }
        if (mTabActive == 0) {
            List<String[]> history = mBleService.loadHistoryDevices();
            for (int i = start; i < end && i < history.size(); i++) {
                String[] entry = history.get(i);
                items.add(entry[0] + "\n" + entry[1]);
            }
        } else {
            List<ScanResult> results = mBleService.getScanResults();
            if (results.isEmpty() && mScanning) {
                items.add("扫描中...");
            } else if (results.isEmpty()) {
                items.add("未发现设备");
            } else {
                for (int i = start; i < end && i < results.size(); i++) {
                    ScanResult sr = results.get(i);
                    String name = null;
                    try {
                        if (sr.getScanRecord() != null) name = sr.getScanRecord().getDeviceName();
                        if (name == null || name.isEmpty()) name = sr.getDevice().getName();
                    } catch (SecurityException e) { name = null; }
                    if (name == null || name.isEmpty()) name = sr.getDevice().getAddress();
                    items.add(name + "\n" + sr.getDevice().getAddress());
                }
            }
        }
        return items;
    }

    private void updatePagination(int totalPages) {
        mPageIndicator.setText("第 " + (mPage + 1) + " / " + totalPages + " 页");
        mBtnPrev.setEnabled(mPage > 0);
        mBtnNext.setEnabled(mPage < totalPages - 1);
    }

    private void updateTabButtons() {
        if (mTabHistory != null) mTabHistory.setEnabled(mTabActive != 0);
        if (mTabNew != null) mTabNew.setEnabled(mTabActive != 1);
    }
}
