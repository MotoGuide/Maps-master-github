package org.zarroboogs.maps.ui.maps;

import android.app.Activity;
import android.app.ProgressDialog;
import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.ScanResult;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import org.zarroboogs.maps.hub.NavigationHubManager;
import org.zarroboogs.maps.utils.FileLogger;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnKeyListener;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Calendar;
import java.util.UUID;

import org.zarroboogs.maps.network.CameraCloudSync;
import org.zarroboogs.maps.ble.BleDeviceListDialog;
import org.zarroboogs.maps.ble.BleDisconnectDialog;
import org.zarroboogs.maps.ble.BleService;
import org.zarroboogs.maps.ble.BleServiceListener;
import org.zarroboogs.maps.ble.BleWriter;
import org.zarroboogs.maps.ui.device.DeviceDetailBottomSheet;

import androidx.fragment.app.Fragment;
import androidx.core.content.ContextCompat;

import com.amap.api.maps.AMap;
import com.amap.api.maps.CameraUpdateFactory;
import com.amap.api.maps.MapView;
import com.amap.api.maps.model.BitmapDescriptorFactory;
import com.amap.api.maps.model.LatLng;
import com.amap.api.maps.model.Marker;
import com.amap.api.maps.model.Poi;
import com.amap.api.maps.model.MarkerOptions;
import com.amap.api.services.core.AMapException;
import com.amap.api.services.core.LatLonPoint;
import com.amap.api.services.core.PoiItem;
import com.amap.api.services.geocoder.GeocodeResult;
import com.amap.api.services.geocoder.GeocodeSearch;
import com.amap.api.services.geocoder.RegeocodeAddress;
import com.amap.api.services.geocoder.RegeocodeQuery;
import com.amap.api.services.geocoder.RegeocodeResult;
import com.amap.api.services.help.Inputtips;
import com.amap.api.services.help.Tip;

import org.zarroboogs.maps.DrawerStateListener;
import org.zarroboogs.maps.MapsApplication;
import org.zarroboogs.maps.data.local.entity.Bookmark;
import org.zarroboogs.maps.data.local.entity.Camera;
import org.zarroboogs.maps.beans.PoiSearchTip;
import org.zarroboogs.maps.cruise.CruiseModeManager;
import org.zarroboogs.maps.module.CameraVoiceManager;
import org.zarroboogs.maps.ble.BleWriter;
import org.zarroboogs.maps.ui.anim.AnimEndListener;
import org.zarroboogs.maps.ui.anim.ViewAnimUtils;
import org.zarroboogs.maps.ui.poi.PoiSearchAdapter;
import org.zarroboogs.maps.R;
import org.zarroboogs.maps.ui.navi.NaviRouteActivity;
import org.zarroboogs.maps.presenters.iviews.ISearchMapsView;
import org.zarroboogs.maps.ui.MapsModule;
import org.zarroboogs.maps.presenters.SearchMapsPresenter;
import org.zarroboogs.maps.utils.FileUtils;
import org.zarroboogs.maps.utils.SettingUtils;
import org.zarroboogs.maps.utils.ToastUtil;
import org.zarroboogs.maps.data.local.AppDatabase;
import org.zarroboogs.maps.data.local.entity.SearchHistory;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;


/**
 * A simple {@link Fragment} subclass.
 * Activities that contain this fragment must implement the
 * {@link MapsFragment.OnFragmentInteractionListener} interface
 * to handle interaction events.
 * Use the {@link MapsFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class MapsFragment extends Fragment implements View.OnClickListener, DrawerStateListener, ISearchMapsView, OnKeyListener, GeocodeSearch.OnGeocodeSearchListener, CruiseModeManager.CruiseModeListener {

    private static final boolean DEBUG = false;
    private static final String CAMERA_MARKER_PREFIX = "[摄像头]";
    private static final String CAMERA_MARKER_SEPARATOR = "|";


    // TODO: Rename parameter arguments, choose names that match
    // the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
    private static final String ARG_PARAM1 = "param1";
    private static final String ARG_PARAM2 = "param2";

    // TODO: Rename and change types of parameters
    private String mParam1;
    private String mParam2;

    private boolean mLandscape = false;
    private float mOri = 0;

    private AMap aMap;
    private MapView mapView;
    private MapsModule mMapsModule;

    private SensorManager mSensorManager;
    private MySensorEventListener mEventListener = new MySensorEventListener();
    private float mDevicesDirection = 0f;
    private ImageButton mCompass;

    private ImageButton mMyLocation;
    private EditText mSearchEditText;

    private SearchMapsPresenter mSearchMapsPresenter;

    private OnFragmentInteractionListener mListener;

    private ListView mListView;
    private PoiSearchAdapter mPoiSearchAdapter;
    private SearchViewHelper mSearchViewHelper;
    private SearchPanelView mSearchPanelView;
    private QuickAccessItem mPendingQuickAccess;


    // float window
    private RelativeLayout mSearchFloatWindow;
    private TextView mSearchPoiTitle;
    private TextView mSearchPoiSummary;
    private TextView mSearchPoiTel;
    private ImageButton mLineBtn;
    private ImageButton mCameraBtn;
    private ImageButton mAddBtn;
    private ImageButton mCameraToggleBtn;
    private ImageButton mCruiseToggleBtn;
    private ImageButton mDarkModeToggleBtn;
    private ImageButton mGeofenceButton;
    private ImageButton mCruiseVoiceButton;
    private CameraVoiceManager mVoiceManager;
    private ImageButton mToiletToggleBtn;
    private ImageButton mBookmarkToggleBtn;
    private ImageButton mGasStationToggleBtn;
    private ImageButton mFavoriteBtn;

    // Filter window views
    private View mCameraFilterWindow;
    private android.widget.GridLayout mFilterCheckboxContainer;
    private java.util.List<android.widget.TextView> mFilterCheckBoxes;

    // 添加摄像头 dialog 预填数据（来自逆地理编码）
    private String mPendingCameraAddress;
    private String mPendingCameraCity;

    // progress dialog
    private ProgressDialog mSearchProgressDialog;

    // BLE bluetooth UI
    private ImageButton mBleScanButton;
    private ImageView mBleConnectionStatus;
    private RelativeLayout mBleButtonContainer;
    private ImageView mIconBleDevice;
    private TextView mTvBleConnectStatus;

    // BLE Service binding
    private BleService mBleService;
    private boolean mBound;
    private Handler mHandler;

    // BLE 设备列表对话框（共享组件）
    private BleDeviceListDialog mBleDeviceListDialog;
    private BleDisconnectDialog mBleDisconnectDialog;
    private boolean mPendingBleScanAfterPermission;
    private static final int REQUEST_ENABLE_BT = 102;

    // BLE 扫描 progress dialog（UI 层保留）
    private android.app.AlertDialog mBleScanProgressDialog;

    // 巡航模式
    private CruiseModeManager mCruiseModeManager;

    // 地图跟随（巡航模式下，触摸取消跟随，5s后自动恢复）
    private boolean mIsNeedFollow = true;
    private Timer mNeedFollowTimer;



    private PoiItem mPoiItem;
    private Marker mSearchMarker;
    private final List<PoiSearchTip> mLastSearchTips = new ArrayList<>();
    private LatLng mSelectedLatLng;
    private GeocodeSearch mGeocodeSearch;
    private String mSelectedPoiTitle;
    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @param param1 Parameter 1.
     * @param param2 Parameter 2.
     * @return A new instance of fragment MapsFragment.
     */
    // TODO: Rename and change types and number of parameters
    public static MapsFragment newInstance(String param1, String param2) {
        MapsFragment fragment = new MapsFragment();
        Bundle args = new Bundle();
        args.putString(ARG_PARAM1, param1);
        args.putString(ARG_PARAM2, param2);
        fragment.setArguments(args);
        return fragment;
    }

    public MapsFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            mParam1 = getArguments().getString(ARG_PARAM1);
            mParam2 = getArguments().getString(ARG_PARAM2);
        }

        mLandscape = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;

        mSensorManager = (SensorManager) getActivity().getApplicationContext().getSystemService(Context.SENSOR_SERVICE);
        mSearchMapsPresenter = new SearchMapsPresenter(this);

        mPoiSearchAdapter = new PoiSearchAdapter(getActivity().getApplicationContext());

        mHandler = new Handler();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_maps, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mCompass = (ImageButton) view.findViewById(R.id.ori_compass);
        mMyLocation = (ImageButton) view.findViewById(R.id.my_location_btn);

        mapView = (MapView) view.findViewById(R.id.map);
        mapView.onCreate(savedInstanceState);// 此方法必须重写
        init();


        FileLogger.w("MotoNav", "MapsFragment init: creating MapsModule...");
        mMapsModule = new MapsModule(this, aMap);
        mMapsModule.init();
        FileLogger.w("MotoNav", "MapsFragment init: MapsModule created, creating CruiseModeManager...");

        // 初始化巡航模式管理器 — 改为纯手动开启，不再自动启动
        // 用户通过首页巡航按钮 (cruise_toggle_btn) 手动控制开关
        mCruiseModeManager = CruiseModeManager.getInstance(getActivity());
        mCruiseModeManager.setListener(this);
        FileLogger.w("MotoNav", "MapsFragment init: CruiseModeManager created (manual only)");

        aMap.setMyLocationType(AMap.LOCATION_TYPE_LOCATE);
        aMap.setOnMapClickListener(new AMap.OnMapClickListener() {
            @Override
            public void onMapClick(LatLng latLng) {
                hideKeyboard(mSearchEditText);
                mMapsModule.disableAutoLocation();
                mSelectedLatLng = latLng;
                mSelectedPoiTitle = null;
                showSearchMarker(latLng, "地图选点");
                reverseGeocodeSelectedPoint(latLng);
            }
        });
        aMap.setOnPOIClickListener(new AMap.OnPOIClickListener() {
            @Override
            public void onPOIClick(Poi poi) {
                if (poi == null) {
                    return;
                }
                hideKeyboard(mSearchEditText);
                mMapsModule.disableAutoLocation();
                mSelectedLatLng = poi.getCoordinate();
                mSelectedPoiTitle = poi.getName();
                showSearchMarker(mSelectedLatLng, mSelectedPoiTitle);
                reverseGeocodeSelectedPoint(mSelectedLatLng);
            }
        });

        // 全局 Marker 点击监听器（处理厕所等自定义标记）
        aMap.setOnMarkerClickListener(new AMap.OnMarkerClickListener() {
            @Override
            public boolean onMarkerClick(com.amap.api.maps.model.Marker marker) {
                String title = marker.getTitle();
                if (title != null) {
                    // 处理厕所标记点击
                    if (title.startsWith("[厕所]")) {
                        hideKeyboard(mSearchEditText);
                        mMapsModule.disableAutoLocation();
                        mSelectedLatLng = marker.getPosition();
                        String toiletTitle = title.replace("[厕所]|", "");
                        String snippet = marker.getSnippet() != null ? marker.getSnippet() : "";
                        showToiletFloatWindow(toiletTitle, snippet);
                        return true;
                    }
                    if (title.startsWith("[摄像头]")) {
                        hideKeyboard(mSearchEditText);
                        mMapsModule.disableAutoLocation();
                        mSelectedLatLng = marker.getPosition();
                        String[] parts = title.split("\\|", 3);
                        String cameraTitle = parts.length == 3 ? parts[2].trim() : title.substring(5).trim();
                        String snippet = marker.getSnippet() != null ? marker.getSnippet() : "";
                        showCameraFloatWindow(cameraTitle, snippet, marker);
                        return true;
                    }
                    if (title.startsWith("[加油站]")) {
                        hideKeyboard(mSearchEditText);
                        mMapsModule.disableAutoLocation();
                        mSelectedLatLng = marker.getPosition();
                        String gasTitle = title.replace("[加油站]|", "").trim();
                        String snippet = marker.getSnippet() != null ? marker.getSnippet() : "";
                        showGasStationFloatWindow(gasTitle, snippet);
                        return true;
                    }
                    if (title.startsWith("[收藏]")) {
                        hideKeyboard(mSearchEditText);
                        mMapsModule.disableAutoLocation();
                        mSelectedLatLng = marker.getPosition();
                        String bookmarkTitle = title.replace("[收藏]|", "").trim();
                        String snippet = marker.getSnippet() != null ? marker.getSnippet() : "";
                        showBookmarkFloatWindow(bookmarkTitle, snippet, marker);
                        return true;
                    }
                }
                return false;
            }
        });

        ImageButton mDrawerSwitch = (ImageButton) view.findViewById(R.id.left_drawer_switch);
        mDrawerSwitch.setOnClickListener(this);
        ImageButton mSearchBtn = (ImageButton) view.findViewById(R.id.cancel_search);
        mSearchBtn.setOnClickListener(this);
        mSearchEditText = (EditText) view.findViewById(R.id.poi_search_in_maps);
        mSearchEditText.setOnClickListener(this);
        mSearchEditText.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus && !mSearchViewHelper.isInSearch()
                    && mSearchFloatWindow.getVisibility() != View.VISIBLE) {
                mSearchMapsPresenter.enterSearch();
            }
        });

        mSearchViewHelper = new SearchViewHelper(view);
        try {
            mGeocodeSearch = new GeocodeSearch(getActivity().getApplicationContext());
            mGeocodeSearch.setOnGeocodeSearchListener(this);
        } catch (Exception e) {
            e.printStackTrace();
        }

        mSearchEditText.setOnKeyListener(this);
        mSearchEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                try {
                    String newText = charSequence.toString().trim();
                    if (!TextUtils.isEmpty(newText)) {
                        Inputtips inputTips = new Inputtips(getActivity().getApplicationContext(),
                                new Inputtips.InputtipsListener() {

                                    @Override
                                    public void onGetInputtips(List<Tip> tipList, int rCode) {
                                        if (rCode == 1000 && tipList != null) {// 正确返回
                                            List<PoiSearchTip> tips = new ArrayList<>();

                                            for (Tip tip : tipList) {
                                                PoiSearchTip mtip = new PoiSearchTip(tip.getName(), tip.getDistrict(), tip.getAdcode());
                                                tips.add(mtip);
                                            }

                                            mLastSearchTips.clear();
                                            mLastSearchTips.addAll(tips);
                                            mPoiSearchAdapter.addResultTips(tips);

                                            if (mListView.getVisibility() == View.GONE){
                                                mListView.setVisibility(View.VISIBLE);
                                            }
                                        }
                                    }
                                });
                        inputTips.requestInputtips(newText, "北京");// 第一个参数表示提示关键字，第二个参数默认代表全国，也可以为城市区号
                        // 有文字时显示建议列表，隐藏搜索面板
                        if (mSearchPanelView != null) mSearchPanelView.hide();
                        if (mListView.getVisibility() == View.GONE) {
                            mListView.setVisibility(View.VISIBLE);
                        }
                    } else {
                        // 文字清空时显示搜索面板，隐藏建议列表（仅搜索模式下）
                        mListView.setVisibility(View.GONE);
                        if (mSearchPanelView != null && mSearchViewHelper.isInSearch()) {
                            mSearchPanelView.show();
                        }
                    }
                } catch (Throwable e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void afterTextChanged(Editable editable) {

            }
        });

        mListView = (ListView) view.findViewById(R.id.search_result_list_view);
        mListView.setAdapter(mPoiSearchAdapter);
        mListView.requestFocus();
        mListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                hideKeyboard(mSearchEditText);

                // stop follow mode
                mMapsModule.disableAutoLocation();

                PoiSearchAdapter adapter = (PoiSearchAdapter) parent.getAdapter();
                PoiSearchTip tip = (PoiSearchTip) adapter.getItem(position);
                mSearchEditText.setText(tip.getName());
                mSearchEditText.setSelection(mSearchEditText.getText().toString().length());

                mSearchViewHelper.showSelectedPoi();
                mSearchMapsPresenter.searchPoi(getActivity(), tip.getName(), "");
            }
        });

        // 初始化搜索面板（快捷图标 + 历史 + 收藏）
        mSearchPanelView = new SearchPanelView(view);
        mSearchPanelView.setOnItemClick(new OnSearchItemClickListener() {
            @Override
            public void onItemClick(String name, String address, double lat, double lng) {
                if (getActivity() == null) return;
                getActivity().runOnUiThread(() -> {
                    hideKeyboard(mSearchEditText);
                    mMapsModule.disableAutoLocation();
                    mSearchViewHelper.showSelectedPoi();
                    mSelectedLatLng = new LatLng(lat, lng);
                    showSearchMarker(mSelectedLatLng, name, address);
                    recordSearchHistory(name, address, lat, lng);
                });
            }
        });
        mSearchPanelView.setOnQuickAccessConfig(item -> {
            if (getActivity() == null) return;
            QuickAccessConfigSheet sheet = new QuickAccessConfigSheet(item, new OnQuickAccessSavedListener() {
                @Override
                public void onSaved(QuickAccessItem updated) {
                    mPendingQuickAccess = null;
                    if (updated.getLat() != 0.0 && updated.getLng() != 0.0) {
                        ToastUtil.show(getActivity(), "已设置「" + updated.getName() + "」地址");
                    } else {
                        mPendingQuickAccess = updated;
                        mSearchViewHelper.enterSearch();
                    }
                }
            });
            sheet.show(getActivity().getSupportFragmentManager(), "quick_access_config");
        });
        mSearchPanelView.setOnQuickAccessAction(new OnQuickAccessActionListener() {
            @Override
            public void onAdd() {
                String customId = "custom_" + System.currentTimeMillis();
                QuickAccessItem newItem = new QuickAccessItem(customId, "自定义", "📍", "", 0.0, 0.0);
                QuickAccessStorage.INSTANCE.addCustom(getActivity().getApplicationContext(), newItem);
                mSearchPanelView.refresh();
            }
            @Override
            public void onClearHistory() {
                ToastUtil.show(getActivity(), "搜索历史已清空");
            }
        });

        mSearchFloatWindow = (RelativeLayout) view.findViewById(R.id.search_float_rl);
        mSearchPoiTitle = (TextView) view.findViewById(R.id.search_result_title);
        mSearchPoiSummary = (TextView) view.findViewById(R.id.search_poi_summary);
        mLineBtn = (ImageButton) view.findViewById(R.id.maps_drive_line_btn);
        mCameraBtn = (ImageButton) view.findViewById(R.id.maps_camera_btn);
        mAddBtn = (ImageButton) view.findViewById(R.id.maps_add_btn);
        mCameraToggleBtn = (ImageButton) view.findViewById(R.id.camera_toggle_btn);
        mGeofenceButton = (ImageButton) view.findViewById(R.id.motobaojing_button);
        mCruiseToggleBtn = (ImageButton) view.findViewById(R.id.cruise_toggle_btn);
        mDarkModeToggleBtn = (ImageButton) view.findViewById(R.id.dark_mode_toggle_btn);
        mCruiseVoiceButton = (ImageButton) view.findViewById(R.id.cruise_voice_button);
        mToiletToggleBtn = (ImageButton) view.findViewById(R.id.toilet_toggle_btn);
        mBookmarkToggleBtn = (ImageButton) view.findViewById(R.id.bookmark_toggle_btn);
        mGasStationToggleBtn = (ImageButton) view.findViewById(R.id.gas_station_toggle_btn);
        mFavoriteBtn = (ImageButton) view.findViewById(R.id.maps_favorite_btn);
        mSearchPoiTel = (TextView) view.findViewById(R.id.search_poi_tel);

        // 右下角巡航切换按钮
        mCruiseToggleBtn.setOnClickListener(v -> {
            CruiseModeManager cruiseMgr = CruiseModeManager.getInstance();
            if (cruiseMgr != null && cruiseMgr.isRunning()) {
                cruiseMgr.stop();
                mCruiseToggleBtn.setImageResource(R.drawable.xunhang);
                SettingUtils.writeCurrentCruiseState(SettingUtils.SWITCH_OFF);
            } else {
                boolean ok = cruiseMgr.start();
                if (ok) {
                    mCruiseToggleBtn.setImageResource(R.drawable.xunhang_active);
                    SettingUtils.writeCurrentCruiseState(SettingUtils.SWITCH_ON);
                }
            }
        });

        // 右下角摄像头切换按钮（统一通过 CameraOverlayManager）
        mCameraToggleBtn.setOnClickListener(v -> {
            final CameraOverlayManager mgr = CameraOverlayManager.getInstance();
            if (!mgr.isShowing()) {
                mCameraToggleBtn.setImageResource(R.drawable.camera_deposit_active);
                mgr.show(aMap);
                mCameraFilterWindow.setVisibility(View.VISIBLE);
            } else {
                mCameraToggleBtn.setImageResource(R.drawable.camera_deposit);
                mgr.hide();
                mCameraFilterWindow.setVisibility(View.GONE);
            }
        });

        // 左下角厕所切换按钮（统一通过 ToiletOverlayManager）
        mToiletToggleBtn.setOnClickListener(v -> {
            final ToiletOverlayManager mgr = ToiletOverlayManager.getInstance();
            if (!mgr.isShowing()) {
                // 获取当前定位
                com.amap.api.location.AMapLocation myLoc = mMapsModule.getMyLocation();
                if (myLoc == null) {
                    Toast.makeText(getActivity(), "正在定位，请稍后再试", Toast.LENGTH_SHORT).show();
                    return;
                }
                LatLng currentLoc = new LatLng(myLoc.getLatitude(), myLoc.getLongitude());
                mToiletToggleBtn.setImageResource(R.drawable.lashi);
                mgr.show(aMap, currentLoc);
            } else {
                mToiletToggleBtn.setImageResource(R.drawable.zhaocesuo);
                mgr.hide();
            }
        });

        // 左下角收藏点位切换按钮（统一通过 BookmarkOverlayManager）
        mBookmarkToggleBtn.setOnClickListener(v -> {
            final BookmarkOverlayManager mgr = BookmarkOverlayManager.getInstance();
            if (!mgr.isShowing()) {
                mBookmarkToggleBtn.setImageResource(R.drawable.shoucang_active);
                mgr.show(aMap);
            } else {
                mBookmarkToggleBtn.setImageResource(R.drawable.shoucang);
                mgr.hide();
            }
        });

        // 左下角加油站切换按钮（统一通过 GasStationOverlayManager）
        mGasStationToggleBtn.setOnClickListener(v -> {
            final GasStationOverlayManager mgr = GasStationOverlayManager.getInstance();
            if (!mgr.isShowing()) {
                com.amap.api.location.AMapLocation myLoc = mMapsModule.getMyLocation();
                if (myLoc == null) {
                    Toast.makeText(getActivity(), "正在定位，请稍后再试", Toast.LENGTH_SHORT).show();
                    return;
                }
                LatLng currentLoc = new LatLng(myLoc.getLatitude(), myLoc.getLongitude());
                mGasStationToggleBtn.setImageResource(R.drawable.jiayouzhan_active);
                mgr.show(aMap, currentLoc);
            } else {
                mGasStationToggleBtn.setImageResource(R.drawable.jiayouzhan);
                mgr.hide();
            }
        });

        // 右下角地图暗黑模式切换按钮（三态：自动/白天/夜间）
        int dayNightMode = SettingUtils.readDarkMapMode();
        updateDayNightButtonUI(dayNightMode);
        // 初始化时应用地图样式
        applyDayNightModeToMap(dayNightMode);
        
        mDarkModeToggleBtn.setOnClickListener(v -> {
            int currentMode = SettingUtils.readDarkMapMode();
            // 循环切换：自动 → 白天 → 夜间 → 自动
            int newMode;
            switch (currentMode) {
                case SettingUtils.DAY_NIGHT_MODE_AUTO:
                    newMode = SettingUtils.DAY_NIGHT_MODE_DAY;
                    break;
                case SettingUtils.DAY_NIGHT_MODE_DAY:
                    newMode = SettingUtils.DAY_NIGHT_MODE_NIGHT;
                    break;
                case SettingUtils.DAY_NIGHT_MODE_NIGHT:
                default:
                    newMode = SettingUtils.DAY_NIGHT_MODE_AUTO;
                    break;
            }
            SettingUtils.writeDarkMapMode(newMode);
            applyDayNightModeToMap(newMode);
            updateDayNightButtonUI(newMode);
        });

        // 自定义巡航语音播报按钮
        mVoiceManager = CameraVoiceManager.getInstance(getActivity());
        mVoiceManager.init();
        mCruiseVoiceButton.setOnClickListener(v -> {
            mVoiceManager.toggleMute();
            updateCruiseVoiceButtonIcon();
        });
        updateCruiseVoiceButtonIcon();

        // 电子围栏报警开关按钮
        updateGeofenceButtonIcon();
        mGeofenceButton.setOnClickListener(v -> {
            boolean enabled = !SettingUtils.isGeofenceEnabled();
            if (enabled) {
                startGeofence();
            } else {
                stopGeofence();
            }
        });

        // 摄像头过滤窗口初始化
        initCameraFilterWindow(view);

        // BLE bluetooth button initialization
        mBleScanButton = (ImageButton) view.findViewById(R.id.ble_scan_button);
        mBleConnectionStatus = (ImageView) view.findViewById(R.id.ble_connection_status);
        mBleButtonContainer = (RelativeLayout) view.findViewById(R.id.ble_button_container);
        mIconBleDevice = (ImageView) view.findViewById(R.id.icon_ble_device);
        mTvBleConnectStatus = (TextView) view.findViewById(R.id.tv_ble_connect_status);

        // 左上角蓝牙连接状态组件点击 → 设备详情 BottomSheet
        View bleConnectionLayout = view.findViewById(R.id.layout_ble_connection);
        bleConnectionLayout.setOnClickListener(v -> {
            boolean connected = mBleService != null && mBleService.isConnected();
            String deviceName = connected ? mBleService.getConnectedDeviceName() : null;
            String mac = connected ? mBleService.getConnectedDeviceAddress() : null;
            long connectedTime = connected ? mBleService.getConnectedTime() : 0L;
            DeviceDetailBottomSheet sheet = DeviceDetailBottomSheet.newInstance(
                    connected, deviceName, mac, connectedTime);
            sheet.setBleService(mBleService);
            sheet.show(getChildFragmentManager(), "DeviceDetailBottomSheet");
        });

        mBleScanButton.setOnClickListener(v -> {
            if (mBleService != null && mBleService.isConnected()) {
                if (mBleDisconnectDialog == null) {
                    mBleDisconnectDialog = new BleDisconnectDialog(getActivity(), mBleService);
                }
                mBleDisconnectDialog.show();
            } else if (mBleService != null) {
                if (!checkAndRequestPermissionsForBleScan()) return;
                BluetoothAdapter adapter = ((android.bluetooth.BluetoothManager)
                        getActivity().getSystemService(Context.BLUETOOTH_SERVICE)).getAdapter();
                if (adapter == null || !adapter.isEnabled()) {
                    Intent enableBt = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                    startActivityForResult(enableBt, REQUEST_ENABLE_BT);
                    return;
                }
                openBleDeviceListAndScan();
            }
        });

        // Bind BleService via ApplicationContext
        bindBleService();

        // 地图跟随：触摸取消跟随，5s 后自动恢复
        setupMapFollow();
    }
    // ---- BleService binding ----

    private final ServiceConnection mBleServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mBleService = ((BleService.LocalBinder) service).getService();
            mBleService.registerListener(mBleServiceListener);
            mBound = true;
            updateBleConnectionUI(mBleService.isConnected());
            FileLogger.d("MotoNav", "MapsFragment bound to BleService");
            // 恢复电子围栏运行状态（app 重启后按钮状态保持但围栏已停止）
            updateGeofenceButtonUi();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mBleService = null;
            mBound = false;
        }
    };

    private final BleServiceListener mBleServiceListener = new BleServiceListener() {
        @Override
        public void onScanResult(List<BleDeviceEntry> devices) {
            if (!isAdded()) return;
            getActivity().runOnUiThread(() -> {
                if (mBleDeviceListDialog != null && mBleDeviceListDialog.isShowing()) {
                    mBleDeviceListDialog.show();
                }
            });
        }

        @Override
        public void onRssiRead(int rssi) {
            if (!isAdded()) return;
            getActivity().runOnUiThread(() -> {
                DeviceDetailBottomSheet sheet = (DeviceDetailBottomSheet)
                        getChildFragmentManager().findFragmentByTag("DeviceDetailBottomSheet");
                if (sheet != null) sheet.updateRssi(rssi);
            });
        }

        @Override
        public void onDeviceStatus(int batteryPercent, String firmwareVersion) {
            if (!isAdded()) return;
            getActivity().runOnUiThread(() -> {
                DeviceDetailBottomSheet sheet = (DeviceDetailBottomSheet)
                        getChildFragmentManager().findFragmentByTag("DeviceDetailBottomSheet");
                if (sheet != null) sheet.updateDeviceStatus(batteryPercent, firmwareVersion);
            });
        }

        @Override
        public void onDeviceTelemetry(int batteryPercent, String firmwareVersion, int heapKb, int fps) {
            NavigationHubManager.INSTANCE.onDeviceTelemetry(batteryPercent, firmwareVersion, heapKb, fps);
        }

        @Override
        public void onConnectionStateChanged(boolean connected, String deviceName) {
            if (!isAdded()) return;
            getActivity().runOnUiThread(() -> updateBleConnectionUI(connected));
        }

        @Override
        public void onScanStarted() {}

        @Override
        public void onScanStopped() {
            if (!isAdded()) return;
            getActivity().runOnUiThread(() -> {
                if (mBleDeviceListDialog != null && mBleDeviceListDialog.isShowing()) {
                    mBleDeviceListDialog.setScanning(false);
                    mBleDeviceListDialog.show();
                }
            });
        }

        @Override
        public void onError(String message) {
            if (!isAdded()) return;
            getActivity().runOnUiThread(() ->
                Toast.makeText(getActivity(), message, Toast.LENGTH_SHORT).show());
        }
    };

    private void bindBleService() {
        Context ctx = getActivity().getApplicationContext();
        Intent intent = new Intent(ctx, BleService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ctx.startForegroundService(intent);
        } else {
            ctx.startService(intent);
        }
        ctx.bindService(intent, mBleServiceConnection, Context.BIND_AUTO_CREATE);
    }

    private void unbindBleService() {
        if (mBound) {
            Context ctx = getActivity().getApplicationContext();
            ctx.unbindService(mBleServiceConnection);
            mBound = false;
        }
    }

    public AMap getGaoDeMap() {
        return aMap;
    }

    public ImageButton getMyLocationBtn() {
        return mMyLocation;
    }

    public float getDevicesDirection() {
        return mDevicesDirection;
    }

    /**
     * 初始化地图
     */
    public void init() {
        if (aMap == null) {
            aMap = mapView.getMap();
            // 设置地图初始位置为北京市
            aMap.moveCamera(com.amap.api.maps.CameraUpdateFactory.newLatLngZoom(new com.amap.api.maps.model.LatLng(39.9042, 116.4074), 12));
        }
    }

    // 重新启动定位
    public void startLocation() {
        if (mMapsModule != null) {
            mMapsModule.startLocation();
        }
    }

    // ======================== 巡航模式 ========================

    private void startCruiseMode() {
        if (mCruiseModeManager != null && !mCruiseModeManager.isRunning()) {
            boolean ok = mCruiseModeManager.start();
            if (ok) {
                FileLogger.d("MotoNav", "Cruise mode started");
                mCruiseToggleBtn.setImageResource(R.drawable.xunhang_active);
            } else {
                FileLogger.e("MotoNav", "Failed to start cruise mode");
            }
        }
    }

    private void stopCruiseMode() {
        if (mCruiseModeManager != null && mCruiseModeManager.isRunning()) {
            mCruiseModeManager.stop();
            mCruiseToggleBtn.setImageResource(R.drawable.xunhang);
            FileLogger.d("MotoNav", "Cruise mode stopped");
        }
    }

    // ======================== 地图跟随 ========================

    private void setupMapFollow() {
        aMap.setOnMapTouchListener(new AMap.OnMapTouchListener() {
            @Override
            public void onTouch(MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        clearFollowTimer();
                        mIsNeedFollow = false;
                        break;
                    case MotionEvent.ACTION_UP:
                        clearFollowTimer();
                        mNeedFollowTimer = new Timer();
                        mNeedFollowTimer.schedule(new TimerTask() {
                            @Override
                            public void run() {
                                mIsNeedFollow = true;
                            }
                        }, 5000);
                        break;
                }
            }
        });
    }

    private void clearFollowTimer() {
        if (mNeedFollowTimer != null) {
            mNeedFollowTimer.cancel();
            mNeedFollowTimer = null;
        }
    }

    // ======================== 电子围栏报警 ========================

    private void startGeofence() {
        SettingUtils.writeGeofenceEnabled(true);
        updateGeofenceButtonIcon();
        org.zarroboogs.maps.hub.NavigationHubManager.INSTANCE.startCruise();
        // 注入 GeoFenceManager 引用供 BleService 后台 GPS 喂给围栏
        if (mBleService != null && mMapsModule != null) {
            org.zarroboogs.maps.presenters.MapsPresenter presenter = mMapsModule.getMapsPresenter();
            if (presenter instanceof org.zarroboogs.maps.presenters.MapsPresenterImpl) {
                org.zarroboogs.maps.module.GeoFenceManager gfm = ((org.zarroboogs.maps.presenters.MapsPresenterImpl) presenter).getGeoFenceManager();
                if (gfm != null) {
                    mBleService.setGeoFenceManager(gfm);
                    mBleService.startGpsForGeofence();
                    gfm.setActive(true);
                    FileLogger.i("MotoNav", "Geofence started (BLE GPS)");
                }
            }
        }
    }

    private void stopGeofence() {
        SettingUtils.writeGeofenceEnabled(false);
        updateGeofenceButtonIcon();
        org.zarroboogs.maps.hub.NavigationHubManager.INSTANCE.stopCruise();
        if (mBleService != null) {
            mBleService.stopGpsForGeofence();
        }
        org.zarroboogs.maps.presenters.MapsPresenter presenter = mMapsModule != null ? mMapsModule.getMapsPresenter() : null;
        if (presenter instanceof org.zarroboogs.maps.presenters.MapsPresenterImpl) {
            org.zarroboogs.maps.module.GeoFenceManager gfm = ((org.zarroboogs.maps.presenters.MapsPresenterImpl) presenter).getGeoFenceManager();
            if (gfm != null) gfm.setActive(false);
        }
        FileLogger.i("MotoNav", "Geofence stopped");
    }

    void updateGeofenceButtonUi() {
        boolean enabled = SettingUtils.isGeofenceEnabled();
        if (mGeofenceButton != null) {
            mGeofenceButton.setImageResource(enabled ? R.drawable.motobaojing_active : R.drawable.motobaojing);
        }
        if (enabled) {
            // 恢复围栏运行（app 重启后按钮状态还在但围栏未启动）
            startGeofence();
        }
    }

    private void updateGeofenceButtonIcon() {
        if (mGeofenceButton == null) return;
        mGeofenceButton.setImageResource(
            SettingUtils.isGeofenceEnabled() ? R.drawable.motobaojing_active : R.drawable.motobaojing);
    }

    // ======================== CruiseModeListener ========================

    @Override
    public void onCruiseStarted() {
        if (mCruiseToggleBtn != null) {
            mCruiseToggleBtn.setImageResource(R.drawable.xunhang_active);
        }
    }

    @Override
    public void onCruiseStopped() {
        if (mCruiseToggleBtn != null) {
            mCruiseToggleBtn.setImageResource(R.drawable.xunhang);
        }
    }

    @Override public void onCameraDetected(Camera camera, int distanceM) {}
    @Override
    public void onLocationChanged(LatLng latLng, float bearing, float speed) {
        if (mIsNeedFollow && aMap != null && latLng != null) {
            aMap.animateCamera(CameraUpdateFactory.changeLatLng(latLng));
        }
    }
    @Override
    public void onCruiseStatisticsUpdated(int distanceM, int timeS) {
        // SDK 检测到移动（连续 5 点 > 15km/h）→ 自动点亮巡航按钮
        if (mCruiseToggleBtn != null && mCruiseModeManager != null && mCruiseModeManager.isRunning()) {
            mCruiseToggleBtn.setImageResource(R.drawable.xunhang_active);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        mapView.onResume();
        Sensor mSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ORIENTATION);
        Sensor mPSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

        mSensorManager.registerListener(mEventListener, mSensor, SensorManager.SENSOR_DELAY_UI);
        mSensorManager.registerListener(mEventListener,mPSensor, SensorManager.SENSOR_DELAY_UI);

        // 巡航改为纯手动模式，不再自动重启（由用户点击按钮手动控制）

        // 从导航返回时恢复摄像头 markers 到首页地图
        if (aMap != null) {
            CameraOverlayManager.getInstance().rebindMap(aMap);
            // 恢复厕所标记
            ToiletOverlayManager.getInstance().rebindMap(aMap);

            // 恢复地图暗黑模式状态
            int dayNightMode = SettingUtils.readDarkMapMode();
            if (dayNightMode == SettingUtils.DAY_NIGHT_MODE_NIGHT || 
                (dayNightMode == SettingUtils.DAY_NIGHT_MODE_AUTO && SettingUtils.isNightTime())) {
                aMap.setMapType(AMap.MAP_TYPE_NIGHT);
            } else {
                aMap.setMapType(SettingUtils.readCurrentMapsStyle());
            }
            if (mDarkModeToggleBtn != null) {
                updateDayNightButtonUI(dayNightMode);
            }
        }

        // 从导航返回时重新注册 BLE listener（可能被 NaviCustomActivity 覆盖）
        if (mBleService != null) {
            mBleService.registerListener(mBleServiceListener);
            updateBleConnectionUI(mBleService.isConnected());
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        mapView.onSaveInstanceState(outState);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        clearFollowTimer();
        // 停止巡航模式
        if (mCruiseModeManager != null) {
            mCruiseModeManager.destroy();
            mCruiseModeManager = null;
        }
        unbindBleService();
        mMapsModule.onDestroy();
        mapView.onDestroy();
        if (mHandler != null) {
            mHandler.removeCallbacksAndMessages(null);
            mHandler = null;
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        mapView.onPause();
        mSensorManager.unregisterListener(mEventListener);
        // Stop BLE scan when leaving the page
        if (mBleService != null && mBleService.isScanning()) {
            mBleService.stopScan();
        }
        if (mBleDeviceListDialog != null) mBleDeviceListDialog.dismiss();
        if (mBleDisconnectDialog != null) mBleDisconnectDialog.dismiss();

        // 停止巡航模式（SDK 监测 + 按钮恢复）
        stopCruiseMode();
        if (mCruiseToggleBtn != null) {
            mCruiseToggleBtn.setImageResource(R.drawable.xunhang);
        }
    }

    // TODO: Rename method, update argument and hook method into UI event
    public void onButtonPressed(Uri uri) {
        if (mListener != null) {
            mListener.onFragmentInteraction(uri);
        }
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        try {
            mListener = (OnFragmentInteractionListener) activity;
        } catch (ClassCastException e) {
            throw new ClassCastException(activity.toString()
                    + " must implement OnFragmentInteractionListener");
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mListener = null;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_ENABLE_BT) {
            if (resultCode == Activity.RESULT_OK) {
                openBleDeviceListAndScan();
            } else {
                Toast.makeText(getActivity(), "需要开启蓝牙才能扫描设备", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_LOCATION_PERMISSION || requestCode == REQUEST_BLUETOOTH_PERMISSION) {
            boolean allGranted = grantResults.length > 0;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (allGranted && mPendingBleScanAfterPermission) {
                mPendingBleScanAfterPermission = false;
                openBleDeviceListAndScan();
            } else if (!allGranted) {
                mPendingBleScanAfterPermission = false;
                if (requestCode == REQUEST_LOCATION_PERMISSION) {
                    Toast.makeText(getActivity().getApplicationContext(), "需要位置权限才能扫描蓝牙设备", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(getActivity().getApplicationContext(), "需要蓝牙权限才能扫描蓝牙设备", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    public void onLeftDrawerViewClick(int id){
        if (id == R.id.left_drawer_satellite){
            int currentStyle = SettingUtils.readCurrentMapsStyle();

            int newStyle = AMap.MAP_TYPE_NORMAL;

            if (currentStyle == AMap.MAP_TYPE_NORMAL){
                newStyle = AMap.MAP_TYPE_SATELLITE;
            } else{
                newStyle = AMap.MAP_TYPE_NORMAL;
            }

            SettingUtils.writeCurrentMapsStyle(newStyle);

            mMapsModule.changeMapStyle(newStyle);

        }
    }
    @Override
    public void onClick(View view) {
        int id = view.getId();
        if (id == R.id.left_drawer_switch) {
            if (mSearchViewHelper.isInSearch()) {
                mSearchMapsPresenter.exitSearch();
            } else {
                mSearchMapsPresenter.openDrawer();
            }

        } else if (id == R.id.cancel_search) {
            if (mSearchFloatWindow.getVisibility() == View.VISIBLE){
                mSearchMapsPresenter.exitSearch();
            } else{
                if (mSearchViewHelper.isInSearch()) {
                    String mSearchText = mSearchEditText.getText().toString();
                    if (TextUtils.isEmpty(mSearchText)){
                        Toast.makeText(getActivity().getApplicationContext(),R.string.search_no_text, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    mSearchMapsPresenter.searchPoi(getActivity().getApplicationContext(),mSearchText , "");
                } else {
                    mSearchMapsPresenter.enterSearch();
                }
            }


        } else if (id == R.id.poi_search_in_maps) {
            if (mSearchFloatWindow.getVisibility() == View.VISIBLE){
                mSearchMapsPresenter.enterSearch();
            } else{
                if (!mSearchViewHelper.isInSearch) {
                    mSearchMapsPresenter.enterSearch();
                }
            }

        }
    }

    public boolean isInSearch(){
        return mSearchViewHelper.isInSearch();
    }

    // DrawerLayout state
    @Override
    public void onDrawerSlide(View drawerView, float slideOffset) {

    }

    @Override
    public void onDrawerOpened(View drawerView) {

    }

    @Override
    public void onDrawerClosed(View drawerView) {

    }

    @Override
    public void onDrawerStateChanged(int newState) {

    }

    @Override
    public void openDrawer() {
        ((MapsMainActivity) getActivity()).openLeftDrawer();
    }

    @Override
    public void closeDrawer() {
        ((MapsMainActivity) getActivity()).closeLeftDrawer();
    }

    @Override
    public boolean onKey(View v, int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_ENTER) {
            hideKeyboard(mSearchEditText);
            mSearchMapsPresenter.searchPoi(getActivity().getApplicationContext(), mSearchEditText.getText().toString(), "");
            return true;
        }

        return false;
    }

    class SearchViewHelper {
        View compassView;
        View myLocationView;
        View searchMaskView;
        ImageButton drawerSwitch;
        EditText searchEditText;
        ListView listView;
        private View floatWindow;
        private boolean isInSearch = false;

        public boolean isInSearch() {
            return isInSearch;
        }

        public SearchViewHelper(View rootView) {
            compassView = rootView.findViewById(R.id.ori_compass);
            myLocationView = rootView.findViewById(R.id.my_location_btn);
            searchMaskView = rootView.findViewById(R.id.search_mask);
            drawerSwitch = (ImageButton) rootView.findViewById(R.id.left_drawer_switch);
            searchEditText = (EditText) rootView.findViewById(R.id.poi_search_in_maps);
            listView = (ListView) rootView.findViewById(R.id.search_result_list_view);
            floatWindow =  rootView.findViewById(R.id.search_float_rl);
        }

        public void enterSearch() {
            isInSearch = true;
            floatWindow.setVisibility(View.GONE);

            compassView.setVisibility(View.GONE);
            myLocationView.setVisibility(View.GONE);
            mCameraToggleBtn.setVisibility(View.GONE);
            mCruiseToggleBtn.setVisibility(View.GONE);
            mDarkModeToggleBtn.setVisibility(View.GONE);
            mGeofenceButton.setVisibility(View.GONE);
            mCruiseVoiceButton.setVisibility(View.GONE);
            mToiletToggleBtn.setVisibility(View.GONE);
            mBookmarkToggleBtn.setVisibility(View.GONE);
            mGasStationToggleBtn.setVisibility(View.GONE);

            String currentText = searchEditText.getText().toString().trim();
            if (TextUtils.isEmpty(currentText)) {
                // 未输入文字：显示搜索面板（快捷图标 + 历史 + 收藏）
                listView.setVisibility(View.GONE);
                if (mSearchPanelView != null) mSearchPanelView.show();
            } else if (!mLastSearchTips.isEmpty()) {
                mPoiSearchAdapter.addResultTips(mLastSearchTips);
                listView.setVisibility(View.VISIBLE);
            } else if (listView.getAdapter().getCount() == 0) {
                listView.setVisibility(View.GONE);
            }

            ViewAnimUtils.popupinWithInterpolator(searchMaskView, new AnimEndListener() {
                @Override
                public void onAnimEnd() {
                    searchMaskView.setVisibility(View.VISIBLE);
                }
            });

            if (listView.getAdapter().getCount() > 0 && listView.getVisibility() == View.VISIBLE){
                ViewAnimUtils.popupinWithInterpolator(listView, new AnimEndListener() {
                    @Override
                    public void onAnimEnd() {
                        listView.setVisibility(View.VISIBLE);
                    }
                });
            }


            drawerSwitch.setImageResource(R.drawable.ic_qu_appbar_back);
            drawerSwitch.setVisibility(View.VISIBLE);
            searchEditText.setCursorVisible(true);

            showKeyboard(searchEditText);
        }

        public void exitSearch() {
            isInSearch = false;
            floatWindow.setVisibility(View.GONE);

            // 隐藏搜索面板
            if (mSearchPanelView != null) mSearchPanelView.hide();
            mPendingQuickAccess = null;

            if (searchMaskView.getVisibility() == View.VISIBLE){
                ViewAnimUtils.popupoutWithInterpolator(searchMaskView, new AnimEndListener() {
                    @Override
                    public void onAnimEnd() {
                        searchMaskView.setVisibility(View.GONE);
                    }
                });
            }


            if (listView.getVisibility() == View.VISIBLE){
                ViewAnimUtils.popupoutWithInterpolator(listView, new AnimEndListener() {
                    @Override
                    public void onAnimEnd() {
                        listView.setVisibility(View.GONE);
                        compassView.setVisibility(View.VISIBLE);
                        myLocationView.setVisibility(View.VISIBLE);
                        mCameraToggleBtn.setVisibility(View.VISIBLE);
                        mCruiseToggleBtn.setVisibility(View.VISIBLE);
                        mDarkModeToggleBtn.setVisibility(View.VISIBLE);
                        mGeofenceButton.setVisibility(View.VISIBLE);
                        mGeofenceButton.setVisibility(View.VISIBLE);
                mCruiseVoiceButton.setVisibility(View.VISIBLE);
                        mToiletToggleBtn.setVisibility(View.VISIBLE);
                        mBookmarkToggleBtn.setVisibility(View.VISIBLE);
                        mGasStationToggleBtn.setVisibility(View.VISIBLE);
                    }
                });
            } else{
                compassView.setVisibility(View.VISIBLE);
                myLocationView.setVisibility(View.VISIBLE);
                mCameraToggleBtn.setVisibility(View.VISIBLE);
                mCruiseToggleBtn.setVisibility(View.VISIBLE);
                mDarkModeToggleBtn.setVisibility(View.VISIBLE);
                mGeofenceButton.setVisibility(View.VISIBLE);
                mCruiseVoiceButton.setVisibility(View.VISIBLE);
                mToiletToggleBtn.setVisibility(View.VISIBLE);
                mBookmarkToggleBtn.setVisibility(View.VISIBLE);
                mGasStationToggleBtn.setVisibility(View.VISIBLE);
            }


            drawerSwitch.setImageResource(R.drawable.ic_qu_menu_grabber);
            drawerSwitch.setVisibility(View.GONE);
            searchEditText.setText("");
            searchEditText.setCursorVisible(false);

            hideKeyboard(searchEditText);


        }

        public void showSuggestTips() {
            isInSearch = true;
            if (mSearchPanelView != null) mSearchPanelView.hide();
            floatWindow.setVisibility(View.GONE);
            listView.setVisibility(View.GONE);
            compassView.setVisibility(View.GONE);
            myLocationView.setVisibility(View.GONE);
            mCameraToggleBtn.setVisibility(View.GONE);
            mCruiseToggleBtn.setVisibility(View.GONE);
            mDarkModeToggleBtn.setVisibility(View.GONE);
            mGeofenceButton.setVisibility(View.GONE);
            mCruiseVoiceButton.setVisibility(View.GONE);
            searchEditText.setCursorVisible(false);
            searchMaskView.setVisibility(View.GONE);
            drawerSwitch.setImageResource(R.drawable.ic_qu_appbar_back);
            drawerSwitch.setVisibility(View.VISIBLE);

        }

        public void showSelectedPoi() {
            isInSearch = true;
            if (mSearchPanelView != null) mSearchPanelView.hide();
            floatWindow.setVisibility(View.VISIBLE);
            listView.setVisibility(View.GONE);
            compassView.setVisibility(View.GONE);
            myLocationView.setVisibility(View.GONE);
            mCameraToggleBtn.setVisibility(View.GONE);
            mCruiseToggleBtn.setVisibility(View.GONE);
            mDarkModeToggleBtn.setVisibility(View.GONE);
            mGeofenceButton.setVisibility(View.GONE);
            mCruiseVoiceButton.setVisibility(View.GONE);
            mToiletToggleBtn.setVisibility(View.GONE);
            mBookmarkToggleBtn.setVisibility(View.GONE);
            mGasStationToggleBtn.setVisibility(View.GONE);
            searchEditText.setCursorVisible(false);
            searchMaskView.setVisibility(View.GONE);
            drawerSwitch.setImageResource(R.drawable.ic_qu_appbar_back);
        }
    }

    private void hideKeyboard(View view) {
        InputMethodManager manager = (InputMethodManager) getActivity().getApplicationContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        manager.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }

    private void showKeyboard(View view) {
        InputMethodManager manager = (InputMethodManager) getActivity().getApplicationContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        manager.showSoftInput(view, InputMethodManager.SHOW_FORCED);
    }

    private LatLng parseLatLngFromTip(PoiSearchTip tip) {
        try {
            if (tip == null || TextUtils.isEmpty(tip.getAdcode())) {
                return null;
            }
            String adCode = tip.getAdcode();
            if (!adCode.contains(",")) {
                return null;
            }
            String[] arr = adCode.split(",");
            if (arr.length < 2) {
                return null;
            }
            double lat = Double.parseDouble(arr[0]);
            double lon = Double.parseDouble(arr[1]);
            return new LatLng(lat, lon);
        } catch (Exception e) {
            return null;
        }
    }

    private void showSearchMarker(LatLng targetLatLng, String title) {
        showSearchMarker(targetLatLng, title, null);
    }

    private void showSearchMarker(LatLng targetLatLng, String title, String address) {
        if (mSearchMarker != null) {
            mSearchMarker.remove();
        }
        mSearchMarker = aMap.addMarker(new MarkerOptions()
                .position(targetLatLng)
                .title(title)
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)));
        aMap.animateCamera(CameraUpdateFactory.newCameraPosition(new com.amap.api.maps.model.CameraPosition(targetLatLng, 16, 0, 0)));
        // 填充 POI 浮窗
        mSearchPoiTitle.setText(title != null ? title : "");
        mSearchPoiSummary.setText(address != null ? address : "");
        mSearchPoiTel.setText("");
        bindNaviButton();
    }

    private void showMapClickPoiFloatWindow(String title, String summary) {
        mSearchFloatWindow.setVisibility(View.VISIBLE);
        mSearchViewHelper.showSelectedPoi();
        mSearchPoiTitle.setText(title);
        mSearchPoiSummary.setText(summary);
        mSearchPoiTel.setText("");
        bindNaviButton();
    }

    // 显示摄像头详细信息窗口
    public void showCameraFloatWindow(String title, String summary, com.amap.api.maps.model.Marker marker) {
        mSearchFloatWindow.setVisibility(View.VISIBLE);
        mSearchViewHelper.showSelectedPoi();
        mSearchPoiTitle.setText(title);
        mSearchPoiSummary.setText(summary);
        mSearchPoiTel.setText("");
        bindCameraButton(marker);
    }

    // 显示厕所详细信息窗口（只有导航按钮）
    private void showToiletFloatWindow(String title, String summary) {
        mSearchFloatWindow.setVisibility(View.VISIBLE);
        mSearchViewHelper.showSelectedPoi();
        mSearchPoiTitle.setText(title);
        mSearchPoiSummary.setText(summary);
        mSearchPoiTel.setText("");
        bindToiletButton();
    }

    // 显示加油站详细信息窗口（导航+收藏按钮）
    private void showGasStationFloatWindow(String title, String summary) {
        mSearchFloatWindow.setVisibility(View.VISIBLE);
        mSearchViewHelper.showSelectedPoi();
        mSearchPoiTitle.setText(title);
        mSearchPoiSummary.setText(summary);
        mSearchPoiTel.setText("");
        bindGasStationButton();
    }

    // 显示收藏点位详细信息窗口（导航+取消收藏按钮）
    private void showBookmarkFloatWindow(String title, String summary, com.amap.api.maps.model.Marker marker) {
        mSearchFloatWindow.setVisibility(View.VISIBLE);
        mSearchViewHelper.showSelectedPoi();
        mSearchPoiTitle.setText(title);
        mSearchPoiSummary.setText(summary);
        mSearchPoiTel.setText("");
        bindBookmarkButton(marker);
    }

    private void bindNaviButton() {
        // 导航按钮
        mLineBtn.setImageResource(R.drawable.ic_navigation_arrow);
        mLineBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    if (mSelectedLatLng == null) {
                        return;
                    }
                    com.amap.api.location.AMapLocation myLoc = mMapsModule.getMyLocation();
                    if (myLoc == null) {
                        android.widget.Toast.makeText(getActivity(), "正在定位，请稍后再试", android.widget.Toast.LENGTH_SHORT).show();
                        return;
                    }

                    Intent intent = new Intent(getActivity(), NaviRouteActivity.class);
                    intent.putExtra(NaviRouteActivity.EXTRA_START_LAT, myLoc.getLatitude());
                    intent.putExtra(NaviRouteActivity.EXTRA_START_LNG, myLoc.getLongitude());
                    intent.putExtra(NaviRouteActivity.EXTRA_END_LAT, mSelectedLatLng.latitude);
                    intent.putExtra(NaviRouteActivity.EXTRA_END_LNG, mSelectedLatLng.longitude);
                    getActivity().startActivity(intent);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });

        // 新增按钮 - 显示，用于添加点位
        mAddBtn.setVisibility(View.VISIBLE);
        mAddBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mSelectedLatLng == null) {
                    return;
                }
                showAddCameraDialog();
            }
        });

        // 删除按钮 - 搜索POI时隐藏（无已有marker可删除）
        mCameraBtn.setVisibility(View.GONE);

        // 收藏按钮
        setupFavoriteButton(true);
    }

    // 绑定摄像头窗口的按钮事件
    private void bindCameraButton(final com.amap.api.maps.model.Marker marker) {
        // 导航按钮
        mLineBtn.setImageResource(R.drawable.ic_navigation_arrow);
        mLineBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    if (marker == null) {
                        return;
                    }
                    com.amap.api.location.AMapLocation myLoc = mMapsModule.getMyLocation();
                    if (myLoc == null) {
                        android.widget.Toast.makeText(getActivity(), "正在定位，请稍后再试", android.widget.Toast.LENGTH_SHORT).show();
                        return;
                    }

                    Intent intent = new Intent(getActivity(), NaviRouteActivity.class);
                    intent.putExtra(NaviRouteActivity.EXTRA_START_LAT, myLoc.getLatitude());
                    intent.putExtra(NaviRouteActivity.EXTRA_START_LNG, myLoc.getLongitude());
                    intent.putExtra(NaviRouteActivity.EXTRA_END_LAT, marker.getPosition().latitude);
                    intent.putExtra(NaviRouteActivity.EXTRA_END_LNG, marker.getPosition().longitude);
                    getActivity().startActivity(intent);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });

        // 删除按钮 - 已屏蔽（用户不需要删除摄像头）
        // mCameraBtn.setVisibility(View.VISIBLE);
        // mCameraBtn.setImageResource(R.drawable.ic_qu_appbar_close);
        // mCameraBtn.setOnClickListener(new View.OnClickListener() {
        //     @Override
        //     public void onClick(View v) {
        //         if (marker == null) {
        //             return;
        //         }
        //         new android.app.AlertDialog.Builder(getActivity(), R.style.DarkDialogTheme)
        //                 .setTitle("删除确认")
        //                 .setMessage("确定要删除这个摩托车抓拍摄像头吗？")
        //                 .setPositiveButton("确定", new android.content.DialogInterface.OnClickListener() {
        //                     @Override
        //                     public void onClick(android.content.DialogInterface dialog, int which) {
        //                         org.zarroboogs.maps.data.local.entity.Camera targetCamera = findCameraByMarker(marker);
        //                         if (targetCamera == null) {
        //                             android.widget.Toast.makeText(getActivity().getApplicationContext(), "未找到对应摄像头，无法删除", android.widget.Toast.LENGTH_SHORT).show();
        //                             return;
        //                         }
        //                         org.zarroboogs.maps.MapsApplication.getDatabase().cameraDao().delete(targetCamera);
        //                         CameraOverlayManager.getInstance().refresh();
        //                         final Long delId = targetCamera.getId();
        //                         if (delId != null) {
        //                             new Thread(new Runnable() {
        //                                 @Override
        //                                 public void run() {
        //                                     CameraCloudSync.getInstance().deleteCamera(delId);
        //                                 }
        //                             }).start();
        //                         }
        //                         marker.hideInfoWindow();
        //                         mSearchFloatWindow.setVisibility(View.GONE);
        //                         android.widget.Toast.makeText(getActivity().getApplicationContext(), "删除成功！", android.widget.Toast.LENGTH_SHORT).show();
        //                     }
        //                 })
        //                 .setNegativeButton("取消", null)
        //                 .show();
        //     }
        // });
        mCameraBtn.setVisibility(View.GONE);

        // 新增按钮 - 点击已有摄像头时隐藏
        mAddBtn.setVisibility(View.GONE);

        // 收藏按钮
        setupFavoriteButton(true);
    }

    // 绑定厕所窗口的按钮事件（只有导航按钮）
    private void bindToiletButton() {
        // 导航按钮
        mLineBtn.setImageResource(R.drawable.ic_navigation_arrow);
        mLineBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    if (mSelectedLatLng == null) {
                        return;
                    }
                    com.amap.api.location.AMapLocation myLoc = mMapsModule.getMyLocation();
                    if (myLoc == null) {
                        android.widget.Toast.makeText(getActivity(), "正在定位，请稍后再试", android.widget.Toast.LENGTH_SHORT).show();
                        return;
                    }

                    Intent intent = new Intent(getActivity(), NaviRouteActivity.class);
                    intent.putExtra(NaviRouteActivity.EXTRA_START_LAT, myLoc.getLatitude());
                    intent.putExtra(NaviRouteActivity.EXTRA_START_LNG, myLoc.getLongitude());
                    intent.putExtra(NaviRouteActivity.EXTRA_END_LAT, mSelectedLatLng.latitude);
                    intent.putExtra(NaviRouteActivity.EXTRA_END_LNG, mSelectedLatLng.longitude);
                    getActivity().startActivity(intent);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });

        // 隐藏新增按钮
        mAddBtn.setVisibility(View.GONE);

        // 隐藏删除按钮
        mCameraBtn.setVisibility(View.GONE);

        // 收藏按钮
        setupFavoriteButton(true);
    }

    // 收藏按钮通用设置
    private void setupFavoriteButton(boolean visible) {
        if (!visible) {
            mFavoriteBtn.setVisibility(View.GONE);
            return;
        }
        mFavoriteBtn.setVisibility(View.VISIBLE);
        final boolean isBookmarked = mSelectedLatLng != null
                && BookmarkOverlayManager.getInstance()
                        .isBookmarked(mSelectedLatLng.latitude, mSelectedLatLng.longitude);
        updateFavoriteIcon(isBookmarked);

        mFavoriteBtn.setOnClickListener(v -> {
            if (mSelectedLatLng == null) return;
            BookmarkOverlayManager mgr = BookmarkOverlayManager.getInstance();
            double lat = mSelectedLatLng.latitude;
            double lng = mSelectedLatLng.longitude;
            if (mgr.isBookmarked(lat, lng)) {
                new android.app.AlertDialog.Builder(getActivity(), R.style.DarkDialogTheme)
                        .setTitle("取消收藏")
                        .setMessage("确定要取消收藏此地点吗？")
                        .setPositiveButton("确定", (dialog, which) -> {
                            mgr.remove(lat, lng);
                            updateFavoriteIcon(false);
                            Toast.makeText(getActivity(), "已取消收藏", Toast.LENGTH_SHORT).show();
                        })
                        .setNegativeButton("取消", null)
                        .show();
            } else {
                String name = mSearchPoiTitle.getText() != null ? mSearchPoiTitle.getText().toString() : "";
                String address = mSearchPoiSummary.getText() != null ? mSearchPoiSummary.getText().toString() : "";
                String poiType = "search";
                mgr.add(new org.zarroboogs.maps.data.local.entity.Bookmark(
                        0L, name, address, lat, lng, poiType, System.currentTimeMillis()));
                updateFavoriteIcon(true);
                Toast.makeText(getActivity(), "已收藏", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void updateFavoriteIcon(boolean bookmarked) {
        mFavoriteBtn.setImageResource(bookmarked
                ? R.drawable.shoucang_active
                : R.drawable.shoucangzengjia);
    }

    // 绑定加油站窗口的按钮事件（导航+收藏按钮）
    private void bindGasStationButton() {
        mLineBtn.setImageResource(R.drawable.ic_navigation_arrow);
        mLineBtn.setOnClickListener(v -> {
            if (mSelectedLatLng == null) return;
            com.amap.api.location.AMapLocation myLoc = mMapsModule.getMyLocation();
            if (myLoc == null) {
                Toast.makeText(getActivity(), "正在定位，请稍后再试", Toast.LENGTH_SHORT).show();
                return;
            }
            Intent intent = new Intent(getActivity(), NaviRouteActivity.class);
            intent.putExtra(NaviRouteActivity.EXTRA_START_LAT, myLoc.getLatitude());
            intent.putExtra(NaviRouteActivity.EXTRA_START_LNG, myLoc.getLongitude());
            intent.putExtra(NaviRouteActivity.EXTRA_END_LAT, mSelectedLatLng.latitude);
            intent.putExtra(NaviRouteActivity.EXTRA_END_LNG, mSelectedLatLng.longitude);
            getActivity().startActivity(intent);
        });

        mAddBtn.setVisibility(View.GONE);
        mCameraBtn.setVisibility(View.GONE);
        setupFavoriteButton(true);
    }

    // 绑定收藏点位窗口的按钮事件（导航+取消收藏按钮）
    private void bindBookmarkButton(final com.amap.api.maps.model.Marker marker) {
        mLineBtn.setImageResource(R.drawable.ic_navigation_arrow);
        mLineBtn.setOnClickListener(v -> {
            if (marker == null) return;
            com.amap.api.location.AMapLocation myLoc = mMapsModule.getMyLocation();
            if (myLoc == null) {
                Toast.makeText(getActivity(), "正在定位，请稍后再试", Toast.LENGTH_SHORT).show();
                return;
            }
            Intent intent = new Intent(getActivity(), NaviRouteActivity.class);
            intent.putExtra(NaviRouteActivity.EXTRA_START_LAT, myLoc.getLatitude());
            intent.putExtra(NaviRouteActivity.EXTRA_START_LNG, myLoc.getLongitude());
            intent.putExtra(NaviRouteActivity.EXTRA_END_LAT, marker.getPosition().latitude);
            intent.putExtra(NaviRouteActivity.EXTRA_END_LNG, marker.getPosition().longitude);
            getActivity().startActivity(intent);
        });

        mAddBtn.setVisibility(View.GONE);

        // 取消收藏按钮（复用 mCameraBtn 位置）
        mCameraBtn.setVisibility(View.VISIBLE);
        mCameraBtn.setImageResource(R.drawable.ic_qu_appbar_close);
        mCameraBtn.setOnClickListener(v -> {
            if (marker == null) return;
            new android.app.AlertDialog.Builder(getActivity(), R.style.DarkDialogTheme)
                    .setTitle("取消收藏")
                    .setMessage("确定要取消收藏此地点吗？")
                    .setPositiveButton("确定", (dialog, which) -> {
                        BookmarkOverlayManager.getInstance().remove(
                                marker.getPosition().latitude, marker.getPosition().longitude);
                        mSearchFloatWindow.setVisibility(View.GONE);
                        Toast.makeText(getActivity(), "已取消收藏", Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton("取消", null)
                    .show();
        });

        // 收藏点位本身不显示"添加收藏"按钮
        mFavoriteBtn.setVisibility(View.GONE);
    }

    private void initCameraFilterWindow(View rootView) {
        mCameraFilterWindow = rootView.findViewById(R.id.camera_filter_rl);
        mFilterCheckboxContainer = (android.widget.GridLayout) rootView.findViewById(R.id.camera_filter_checkbox_container);
        mFilterCheckBoxes = new java.util.ArrayList<>();

        // 创建 13 个按钮（替代复选框）
        String[] notes = org.zarroboogs.maps.beans.CameraNoteType.STANDARD_VALUES;
        java.util.Set<String> activeNotes = CameraOverlayManager.getInstance().getActiveNoteTypes();
        for (String note : notes) {
            android.widget.TextView btn = new android.widget.TextView(getActivity());
            btn.setText(note);
            btn.setTextSize(14);
            btn.setTextColor(android.graphics.Color.WHITE);
            btn.setGravity(android.view.Gravity.CENTER);
            btn.setPadding(10, 12, 10, 12);
            btn.setBackgroundResource(activeNotes.contains(note) ? R.drawable.route_card_selected : R.drawable.route_card_normal);
            btn.setTextColor(activeNotes.contains(note) ? android.graphics.Color.BLACK : android.graphics.Color.WHITE);
            btn.setClickable(true);
            btn.setFocusable(true);
            
            final String finalNote = note;
            btn.setOnClickListener(v -> {
                boolean isChecked = !activeNotes.contains(finalNote);
                CameraOverlayManager.getInstance().setFilter(finalNote, isChecked);
                if (isChecked) {
                    btn.setBackgroundResource(R.drawable.route_card_selected);
                    btn.setTextColor(android.graphics.Color.BLACK);
                    activeNotes.add(finalNote);
                } else {
                    btn.setBackgroundResource(R.drawable.route_card_normal);
                    btn.setTextColor(android.graphics.Color.WHITE);
                    activeNotes.remove(finalNote);
                }
            });
            
            android.widget.GridLayout.LayoutParams params = new android.widget.GridLayout.LayoutParams();
            params.width = 0;
            params.height = android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
            params.rowSpec = android.widget.GridLayout.spec(android.widget.GridLayout.UNDEFINED, 1f);
            params.columnSpec = android.widget.GridLayout.spec(android.widget.GridLayout.UNDEFINED, 1f);
            params.setMargins(4, 4, 4, 4);
            mFilterCheckboxContainer.addView(btn, params);
            mFilterCheckBoxes.add(btn);
        }

        // 全选 / 取消全选
        android.widget.Button btnSelectAll = (android.widget.Button) rootView.findViewById(R.id.camera_filter_select_all);
        android.widget.Button btnDeselectAll = (android.widget.Button) rootView.findViewById(R.id.camera_filter_deselect_all);
        btnSelectAll.setOnClickListener(v -> {
            CameraOverlayManager.getInstance().setAllFilters(true);
            activeNotes.clear();
            activeNotes.addAll(java.util.Arrays.asList(org.zarroboogs.maps.beans.CameraNoteType.STANDARD_VALUES));
            for (int i = 0; i < mFilterCheckBoxes.size(); i++) {
                mFilterCheckBoxes.get(i).setBackgroundResource(R.drawable.route_card_selected);
                mFilterCheckBoxes.get(i).setTextColor(android.graphics.Color.BLACK);
            }
        });
        btnDeselectAll.setOnClickListener(v -> {
            CameraOverlayManager.getInstance().setAllFilters(false);
            activeNotes.clear();
            for (int i = 0; i < mFilterCheckBoxes.size(); i++) {
                mFilterCheckBoxes.get(i).setBackgroundResource(R.drawable.route_card_normal);
                mFilterCheckBoxes.get(i).setTextColor(android.graphics.Color.WHITE);
            }
        });

        // 关闭按钮
        rootView.findViewById(R.id.camera_filter_close).setOnClickListener(v -> hideCameraFilterWindow());
    }

    public void hideCameraFilterWindow() {
        mCameraFilterWindow.setVisibility(View.GONE);
    }

    public boolean isFilterWindowShowing() {
        return mCameraFilterWindow != null && mCameraFilterWindow.getVisibility() == View.VISIBLE;
    }

    private void showAddCameraDialog() {
        final android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(getActivity(), R.style.DarkDialogTheme);
        View view = getActivity().getLayoutInflater().inflate(R.layout.dialog_add_camera, null);
        builder.setView(view);

        final android.app.AlertDialog dialog = builder.create();
        dialog.show();

        final android.widget.EditText etName = view.findViewById(R.id.et_camera_name);
        final android.widget.EditText etAddress = view.findViewById(R.id.et_camera_address);
        final android.widget.EditText etDirection = view.findViewById(R.id.et_camera_direction);
        final android.widget.Spinner spinnerNote = view.findViewById(R.id.spinner_camera_note);

        // 地址预填（来自逆地理编码）
        if (!android.text.TextUtils.isEmpty(mPendingCameraAddress)) {
            etAddress.setText(mPendingCameraAddress);
        }

        // note 下拉选择（13 种标准值，白字深色背景）
        String[] noteValues = org.zarroboogs.maps.beans.CameraNoteType.STANDARD_VALUES;
        android.widget.ArrayAdapter<String> noteAdapter = new android.widget.ArrayAdapter<>(
                getActivity(), R.layout.spinner_item_white, noteValues);
        noteAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item_white);
        spinnerNote.setAdapter(noteAdapter);

        android.widget.Button btnCancel = view.findViewById(R.id.btn_cancel);
        android.widget.Button btnConfirm = view.findViewById(R.id.btn_confirm);

        btnCancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });

        btnConfirm.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String name = etName.getText().toString().trim();
                String address = etAddress.getText().toString().trim();
                String direction = etDirection.getText().toString().trim();
                String note = (String) spinnerNote.getSelectedItem();

                org.zarroboogs.maps.data.local.entity.Camera camera = new org.zarroboogs.maps.data.local.entity.Camera();
                camera.setName(!android.text.TextUtils.isEmpty(name) ? name : "未命名摄像头");
                camera.setAddress(address);
                camera.setDirection(direction);
                camera.setNote(note);
                camera.setLatitude(mSelectedLatLng.latitude);
                camera.setLongtitude(mSelectedLatLng.longitude);
                // city 从逆地理编码结果自动填入
                camera.setCity(mPendingCameraCity);

                org.zarroboogs.maps.MapsApplication.getDatabase().cameraDao().insert(camera);

                if (CameraOverlayManager.getInstance().isShowing()) {
                    CameraOverlayManager.getInstance().refresh();
                }

                dialog.dismiss();
                android.widget.Toast.makeText(getActivity().getApplicationContext(), "添加成功！", android.widget.Toast.LENGTH_SHORT).show();

                // 异步上传到云端
                final org.zarroboogs.maps.data.local.entity.Camera uploadedCamera = camera;
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        CameraCloudSync.getInstance().uploadCamera(uploadedCamera);
                    }
                }).start();
            }
        });
    }

    private void reverseGeocodeSelectedPoint(LatLng latLng) {
        if (mGeocodeSearch == null) {
            showMapClickPoiFloatWindow("地图选点", "纬度:" + latLng.latitude + " 经度:" + latLng.longitude);
            return;
        }
        LatLonPoint point = new LatLonPoint(latLng.latitude, latLng.longitude);
        RegeocodeQuery query = new RegeocodeQuery(point, 200, GeocodeSearch.AMAP);
        mGeocodeSearch.getFromLocationAsyn(query);
    }

    @Override
    public void onRegeocodeSearched(RegeocodeResult result, int rCode) {
        if (mSelectedLatLng == null) {
            return;
        }
        String summary = "";
        mPendingCameraAddress = null;
        mPendingCameraCity = null;
        if (rCode == 1000 && result != null && result.getRegeocodeAddress() != null) {
            RegeocodeAddress addr = result.getRegeocodeAddress();
            String formatAddress = addr.getFormatAddress();
            mPendingCameraAddress = formatAddress;
            // 提取城市名（高德逆地理编码返回的 city 字段，如"济南市"）
            mPendingCameraCity = addr.getCity();
            String title = !TextUtils.isEmpty(mSelectedPoiTitle) ? mSelectedPoiTitle : (TextUtils.isEmpty(formatAddress) ? "地图选点" : formatAddress);
            summary = TextUtils.isEmpty(formatAddress) ? "暂无详细地址" : formatAddress;
            showMapClickPoiFloatWindow(title, summary);
        } else {
            String title = TextUtils.isEmpty(mSelectedPoiTitle) ? "地图选点" : mSelectedPoiTitle;
            showMapClickPoiFloatWindow(title, "暂无详细地址");
        }
    }

    @Override
    public void onGeocodeSearched(GeocodeResult geocodeResult, int rCode) {
    }

    private String normalizeCameraTitle(String title) {
        if (title == null) {
            return "";
        }
        String normalized = title.trim();
        if (!normalized.startsWith(CAMERA_MARKER_PREFIX)) {
            return normalized;
        }
        String[] parts = normalized.split("\\|", 3);
        if (parts.length == 3) {
            return parts[2].trim();
        }
        return normalized.substring(CAMERA_MARKER_PREFIX.length()).trim();
    }

    private org.zarroboogs.maps.data.local.entity.Camera findCameraByMarker(com.amap.api.maps.model.Marker marker) {
        if (marker == null) {
            return null;
        }
        Long cameraId = parseCameraIdFromMarkerTitle(marker.getTitle());
        if (cameraId != null && cameraId > 0) {
            org.zarroboogs.maps.data.local.entity.Camera byId =
                    org.zarroboogs.maps.MapsApplication.getDatabase().cameraDao().loadById(cameraId);
            if (byId != null) {
                return byId;
            }
        }
        java.util.List<org.zarroboogs.maps.data.local.entity.Camera> cameras =
                org.zarroboogs.maps.MapsApplication.getDatabase().cameraDao().loadAll();
        if (cameras == null || cameras.isEmpty()) {
            return null;
        }

        final double markerLat = marker.getPosition().latitude;
        final double markerLng = marker.getPosition().longitude;
        final String markerTitle = normalizeCameraTitle(marker.getTitle());

        org.zarroboogs.maps.data.local.entity.Camera bestMatch = null;
        double bestDistance = Double.MAX_VALUE;
        final double maxDistance = 0.001; // 约100米，兜底容差

        for (org.zarroboogs.maps.data.local.entity.Camera camera : cameras) {
            if (camera == null || camera.getLatitude() == null || camera.getLongtitude() == null) {
                continue;
            }
            double dLat = camera.getLatitude() - markerLat;
            double dLng = camera.getLongtitude() - markerLng;
            double distance = dLat * dLat + dLng * dLng;
            if (distance > maxDistance * maxDistance) {
                continue;
            }
            if (distance < bestDistance) {
                bestDistance = distance;
                bestMatch = camera;
            } else if (Math.abs(distance - bestDistance) < 1e-12 && bestMatch != null) {
                // 同距离时，优先标题一致的点位
                String cameraTitle = normalizeCameraTitle(camera.getName());
                String currentBestTitle = normalizeCameraTitle(bestMatch.getName());
                if (!markerTitle.isEmpty() && cameraTitle.equals(markerTitle) && !currentBestTitle.equals(markerTitle)) {
                    bestMatch = camera;
                }
            }
        }
        return bestMatch;
    }

    private Long parseCameraIdFromMarkerTitle(String title) {
        if (title == null) {
            return null;
        }
        String normalized = title.trim();
        if (!normalized.startsWith(CAMERA_MARKER_PREFIX + CAMERA_MARKER_SEPARATOR)) {
            return null;
        }
        String[] parts = normalized.split("\\|", 3);
        if (parts.length < 2) {
            return null;
        }
        try {
            long parsed = Long.parseLong(parts[1]);
            return parsed > 0 ? parsed : null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }


    @Override
    public void enterSearch() {
        mSearchViewHelper.enterSearch();
    }

    @Override
    public void exitSearch() {
        if (false) { // 新版本 API 不支持 PoiOverlay，暂时注释
            // mPoiOverlay.removeFromMap();
            // mPoiOverlay = null;
        }
        if (mSearchMarker != null) {
            mSearchMarker.remove();
            mSearchMarker = null;
        }
        mSelectedLatLng = null;
        mSelectedPoiTitle = null;
        mPoiItem = null;
        mSearchViewHelper.exitSearch();

    }

    // 新版本 API 不支持 PoiOverlay，暂时注释
    // private PoiOverlay mPoiOverlay;

    @Override
    public void showSearchResult(List<PoiItem> poiItems) {

        if (poiItems == null || poiItems.isEmpty()){
            if (mPoiItem == null) {
                ToastUtil.show(getActivity().getApplicationContext(), R.string.no_poi_search);
            }
        } else {
            mSearchViewHelper.showSelectedPoi();
            PoiItem firstItem = poiItems.get(0);
            LatLng latLng = new LatLng(firstItem.getLatLonPoint().getLatitude(), firstItem.getLatLonPoint().getLongitude());
            mSelectedLatLng = latLng;
            showSearchMarker(latLng, firstItem.getTitle());
            mSearchMapsPresenter.showPoiFloatWindow(firstItem);
            mPoiItem = firstItem;
        }

    }

    @Override
    public void showSearchProgress() {
        if (mSearchProgressDialog == null){
            mSearchProgressDialog = new ProgressDialog(getActivity());
            mSearchProgressDialog.setMessage(getResources().getString(R.string.search_message));
        }
        mSearchProgressDialog.show();

    }

    @Override
    public void hideSearchProgress() {
        mSearchProgressDialog.hide();
    }

    @Override
    public void showPoiFloatWindow(PoiItem poiItem) {
        mSearchFloatWindow.setVisibility(View.VISIBLE);
        mSearchViewHelper.showSelectedPoi();

        PoiItem item = poiItem;

        if (DEBUG){
            FileLogger.d("MotoNav", "showPoiFloatWindow: " + poiItem.getTitle() + " (" + poiItem.getDistance() + "m)");
        }


        
        mSearchPoiTitle.setText(poiItem.getTitle());
        String sum = "";
        if (!TextUtils.isEmpty(poiItem.getProvinceName())){
            sum += poiItem.getProvinceName();
        }

        if (!TextUtils.isEmpty(poiItem.getCityName())){
            sum += "-" + poiItem.getCityName();
        }

        if (!TextUtils.isEmpty(poiItem.getAdName())){
            sum += "-" + poiItem.getAdName();
        }

        if (!TextUtils.isEmpty(poiItem.getSnippet())){
            sum += "-" + poiItem.getSnippet();
        }

        mSearchPoiSummary.setText(sum);
        mSearchPoiTel.setText(poiItem.getTel());
        bindNaviButton();

        // 记录搜索历史
        LatLonPoint point = poiItem.getLatLonPoint();
        if (point != null) {
            recordSearchHistory(poiItem.getTitle(), sum, point.getLatitude(), point.getLongitude());
        }
    }

    private void recordSearchHistory(String name, String address, double lat, double lng) {
        try {
            AppDatabase db = MapsApplication.getDatabase();
            SearchHistory existing = null;
            for (SearchHistory h : db.searchHistoryDao().loadAll()) {
                if (h.getLatitude() == lat && h.getLongitude() == lng) {
                    existing = h;
                    break;
                }
            }
            if (existing != null) {
                db.searchHistoryDao().delete(existing);
            }
            SearchHistory record = new SearchHistory();
            record.setName(name != null ? name : "");
            record.setAddress(address != null ? address : "");
            record.setLatitude(lat);
            record.setLongitude(lng);
            record.setSearchedAt(System.currentTimeMillis());
            db.searchHistoryDao().insert(record);
            db.searchHistoryDao().trimTo20();

            // 处理快捷图标配置：如果有 pending quick access item，保存到此
            if (mPendingQuickAccess != null) {
                QuickAccessItem updated = new QuickAccessItem(
                    mPendingQuickAccess.getId(),
                    mPendingQuickAccess.getName(),
                    mPendingQuickAccess.getIcon(),
                    address != null ? address : name,
                    lat, lng
                );
                QuickAccessStorage.INSTANCE.save(
                    MapsFragment.this.getActivity().getApplicationContext(), updated);
                ToastUtil.show(getActivity(), "已设置「" + mPendingQuickAccess.getName() + "」地址");
                mPendingQuickAccess = null;
                if (mSearchPanelView != null) mSearchPanelView.refresh();
            }
        } catch (Exception e) {
            FileLogger.e("MotoNav", "recordSearchHistory failed: " + e.getMessage());
        }
    }

    // DrawerLayout state

    /**
     * This interface must be implemented by activities that contain this
     * fragment to allow an interaction in this fragment to be communicated
     * to the activity and potentially other fragments contained in that
     * activity.
     * <p/>
     * See the Android Training lesson <a href=
     * "http://developer.android.com/training/basics/fragments/communicating.html"
     * >Communicating with Other Fragments</a> for more information.
     */
    public interface OnFragmentInteractionListener {
        // TODO: Update argument type and name
        public void onFragmentInteraction(Uri uri);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        mLandscape = (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE);
    }

    // ======================== BLE Bluetooth ========================

    private static final int REQUEST_LOCATION_PERMISSION = 100;
    private static final int REQUEST_BLUETOOTH_PERMISSION = 101;
    private static final String PERMISSION_BLUETOOTH_SCAN = "android.permission.BLUETOOTH_SCAN";
    private static final String PERMISSION_BLUETOOTH_CONNECT = "android.permission.BLUETOOTH_CONNECT";

    // ---- Permission helpers ----

    private boolean hasPermission(String permission) {
        return ContextCompat.checkSelfPermission(requireContext(), permission) == PackageManager.PERMISSION_GRANTED;
    }

    private boolean checkAndRequestPermissionsForBleScan() {
        if (!isAdded()) return false;
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            boolean hasScan = hasPermission(PERMISSION_BLUETOOTH_SCAN);
            boolean hasConnect = hasPermission(PERMISSION_BLUETOOTH_CONNECT);
            if (!hasScan || !hasConnect) {
                mPendingBleScanAfterPermission = true;
                requestBluetoothPermissions();
                return false;
            }
        } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            if (!hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
                mPendingBleScanAfterPermission = true;
                requestBluetoothPermissions();
                return false;
            }
        }
        return true;
    }

    private boolean ensureBluetoothConnectPermission() {
        if (android.os.Build.VERSION.SDK_INT < 31) return true;
        if (hasPermission(PERMISSION_BLUETOOTH_CONNECT)) return true;
        requestBluetoothPermissions();
        Toast.makeText(getActivity(), "需要蓝牙连接权限", Toast.LENGTH_SHORT).show();
        return false;
    }

    private void requestBluetoothPermissions() {
        if (!isAdded()) return;
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            requestPermissions(new String[]{
                    PERMISSION_BLUETOOTH_SCAN,
                    PERMISSION_BLUETOOTH_CONNECT
            }, REQUEST_BLUETOOTH_PERMISSION);
        } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_LOCATION_PERMISSION);
        }
    }

    // Device list dialog moved to BleDeviceListDialog (shared with NaviCustomActivity)
    // Auto-reconnect now handled by BleService

    // ---- BLE UI helpers ----

    private void updateBleConnectionUI(boolean connected) {
        if (connected) {
            mBleConnectionStatus.setVisibility(View.GONE);
            mBleScanButton.setImageResource(R.drawable.ble_active);
            mBleScanButton.clearColorFilter();
            mIconBleDevice.setImageResource(R.drawable.icon_device_connected);
            mTvBleConnectStatus.setText("已连接");
        } else {
            mBleConnectionStatus.setVisibility(View.GONE);
            mBleScanButton.setImageResource(R.drawable.ble);
            mBleScanButton.clearColorFilter();
            mIconBleDevice.setImageResource(R.drawable.icon_device_disconnected);
            mTvBleConnectStatus.setText("未连接");
        }
    }

    private void dismissBleScanDialog() {
        if (mBleScanProgressDialog != null && mBleScanProgressDialog.isShowing()) {
            mBleScanProgressDialog.dismiss();
        }
    }

    private void openBleDeviceListAndScan() {
        if (mBleService == null) return;
        if (mBleDeviceListDialog == null) {
            mBleDeviceListDialog = new BleDeviceListDialog(getActivity(), mBleService);
        }
        mBleDeviceListDialog.setScanning(true);
        mBleDeviceListDialog.show();
        mBleService.startScan();
    }

    class MySensorEventListener implements SensorEventListener {

        @Override
        public void onSensorChanged(SensorEvent sensorEvent) {
            if (sensorEvent.sensor.getType() == Sensor.TYPE_ORIENTATION) {
                float x = sensorEvent.values[SensorManager.DATA_X];

                float fixedX = x;
                if (mLandscape ){
                    if (mOri < 0) {
                        fixedX -= 90;
                    } else {
                        fixedX += 90;
                    }
                }

                if (Math.abs(fixedX - mDevicesDirection) > 2) {
                    if (SettingUtils.readCurrentMyLocationMode() == AMap.LOCATION_TYPE_MAP_ROTATE){
                        mCompass.setRotation(-fixedX);
                    } else{
                        mCompass.setRotation(0);
                    }

                    mDevicesDirection = fixedX;

                    mMapsModule.onOrientationChanged(mDevicesDirection);
                }
            } else if (sensorEvent.sensor.getType() == Sensor.TYPE_ACCELEROMETER){
                float x = sensorEvent.values[SensorManager.DATA_X];
                float y = sensorEvent.values[SensorManager.DATA_Y];
                float z = sensorEvent.values[SensorManager.DATA_Z];
                mOri = x;
//                FileLogger.d("PSensor-onSensorChanged","" + x + "   Ori :" + x);
            }


        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int i) {

        }
    }

    // ======================== Day Night Mode Helpers ========================

    /**
     * 更新日夜模式按钮图标
     */
    private void updateDayNightButtonUI(int mode) {
        if (mDarkModeToggleBtn == null) return;
        switch (mode) {
            case SettingUtils.DAY_NIGHT_MODE_AUTO:
                mDarkModeToggleBtn.setImageResource(R.drawable.auto);
                break;
            case SettingUtils.DAY_NIGHT_MODE_DAY:
                mDarkModeToggleBtn.setImageResource(R.drawable.sun);
                break;
            case SettingUtils.DAY_NIGHT_MODE_NIGHT:
                mDarkModeToggleBtn.setImageResource(R.drawable.moon);
                break;
        }
    }

    private void updateCruiseVoiceButtonIcon() {
        if (mCruiseVoiceButton == null) return;
        if (mVoiceManager != null && mVoiceManager.isMuted()) {
            mCruiseVoiceButton.setImageResource(R.drawable.jingyin);
        } else {
            mCruiseVoiceButton.setImageResource(R.drawable.yuyinbobao);
        }
    }

    /**
     * 根据日夜模式设置地图样式
     */
    private void applyDayNightModeToMap(int mode) {
        if (aMap == null) return;
        switch (mode) {
            case SettingUtils.DAY_NIGHT_MODE_AUTO:
                // 自动模式：根据时间判断
                if (SettingUtils.isNightTime()) {
                    aMap.setMapType(AMap.MAP_TYPE_NIGHT);
                } else {
                    aMap.setMapType(SettingUtils.readCurrentMapsStyle());
                }
                break;
            case SettingUtils.DAY_NIGHT_MODE_DAY:
                aMap.setMapType(SettingUtils.readCurrentMapsStyle());
                break;
            case SettingUtils.DAY_NIGHT_MODE_NIGHT:
                aMap.setMapType(AMap.MAP_TYPE_NIGHT);
                break;
        }
    }

}
