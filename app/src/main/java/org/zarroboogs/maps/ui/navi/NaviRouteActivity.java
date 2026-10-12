package org.zarroboogs.maps.ui.navi;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import org.zarroboogs.maps.utils.FileLogger;
import android.util.SparseArray;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.widget.Button;

import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.PopupMenu;
import android.widget.Toast;

import com.amap.api.maps.AMap;
import com.amap.api.maps.CameraUpdateFactory;
import com.amap.api.maps.MapView;

import com.amap.api.maps.model.LatLng;

import com.amap.api.navi.AMapNavi;
import com.amap.api.navi.AMapNaviListener;
import com.amap.api.navi.enums.TransportType;
import com.amap.api.navi.model.AMapCalcRouteResult;
import com.amap.api.navi.model.AMapCarInfo;
import com.amap.api.navi.model.AMapLaneInfo;
import com.amap.api.navi.model.AMapModelCross;
import com.amap.api.navi.model.AMapNaviCameraInfo;
import com.amap.api.navi.model.AMapNaviCross;
import com.amap.api.navi.model.AMapNaviLocation;
import com.amap.api.navi.model.AMapNaviPath;
import com.amap.api.navi.model.AMapNaviRouteNotifyData;
import com.amap.api.navi.model.AMapNaviTrafficFacilityInfo;
import com.amap.api.navi.model.AMapServiceAreaInfo;
import com.amap.api.navi.model.AMapTravelInfo;
import com.amap.api.navi.model.AimLessModeCongestionInfo;
import com.amap.api.navi.model.AimLessModeStat;
import com.amap.api.navi.model.NaviInfo;
import com.amap.api.navi.model.NaviLatLng;
import com.amap.api.navi.view.RouteOverLay;

import org.zarroboogs.maps.R;
import org.zarroboogs.maps.cruise.CruiseModeManager;
import org.zarroboogs.maps.data.model.Profile;
import org.zarroboogs.maps.data.model.ProfileRepository;
import org.zarroboogs.maps.ui.BaseActivity;
import org.zarroboogs.maps.ui.maps.CameraOverlayManager;
import org.zarroboogs.maps.ui.maps.MapsMainActivity;
import org.zarroboogs.maps.ui.tencent.MotorProfilePickerDialog;
import org.zarroboogs.maps.ui.tencent.TencentMotorRouteActivity;
import org.zarroboogs.maps.utils.SettingUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * 路线规划页面 — 对标官方demo RestRouteShowActivity。
 * 接收明确的起点和终点坐标进行算路。
 * 路线规划成功后启动 NaviCustomActivity 进行GPS实时导航。
 */
public class NaviRouteActivity extends BaseActivity implements AMapNaviListener {
    private static final String TAG = "NaviRouteActivity";
    public static final String EXTRA_START_LAT = "start_lat";
    public static final String EXTRA_START_LNG = "start_lng";
    public static final String EXTRA_END_LAT = "end_lat";
    public static final String EXTRA_END_LNG = "end_lng";

    // 导航模式常量
    public static final int MODE_DRIVE = 0;
    public static final int MODE_MOTOR = 1;
    public static final int MODE_RIDE = 2;
    public static final int MODE_WALK = 3;
    public static final String EXTRA_TRAVEL_MODE = "travel_mode";

    private ImageButton startBtn;
    private Button startEmulatorBtn;
    private ImageButton btnLocate, btnRefresh, btnPreference;
    private ImageView backView;
    private MapView mapView;
    private TextView distanceView, timeView, costView;
    private LinearLayout costColumn;
    private TextView navModeSelector;
    private AMap map;
    private AMapNavi navi;

    private int currentNaviMode = MODE_DRIVE;

    private ArrayList<NaviLatLng> startList;
    private ArrayList<NaviLatLng> endList;
    private SparseArray<RouteOverLay> routeOverlays = new SparseArray<>();
    private boolean calculateSuccess;
    private boolean mNaviListenerRemoved;  // 标记是否在启动导航前主动注销了 listener
    private boolean mapLoaded;

    // 偏好设置 — 对标demo RestRouteShowActivity 第55行
    private boolean congestion, avoidhightspeed, cost, hightspeed;

    // 路线选择
    private HorizontalScrollView routeSelectorScroll;
    private LinearLayout routeSelectorContainer;
    private LinearLayout routeActionButtons;
    private int[] currentRouteIds;
    private int selectedRouteIndex;
    private int zindex = 1;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_route);

        // 从基本类型 double 重建 NaviLatLng，避免 Parcelable 跨进程序列化问题
        double startLat = getIntent().getDoubleExtra(EXTRA_START_LAT, 0);
        double startLng = getIntent().getDoubleExtra(EXTRA_START_LNG, 0);
        double endLat = getIntent().getDoubleExtra(EXTRA_END_LAT, 0);
        double endLng = getIntent().getDoubleExtra(EXTRA_END_LNG, 0);
        startList = new ArrayList<>();
        startList.add(new NaviLatLng(startLat, startLng));
        endList = new ArrayList<>();
        endList.add(new NaviLatLng(endLat, endLng));

        FileLogger.d(TAG, "onCreate: start=(" + startLat + "," + startLng
                + "), end=(" + endLat + "," + endLng + ")");
        // 从 NaviPrefs 读取路线偏好默认值
        congestion = NaviPrefs.getAvoidCongestion();
        avoidhightspeed = NaviPrefs.getAvoidHighspeed();
        cost = NaviPrefs.getAvoidCost();
        hightspeed = NaviPrefs.getHighspeedPriority();

        initView(state);
        initNavi();
        loadCameraMarkers();
    }

    private void initView(Bundle state) {
        startBtn = findViewById(R.id.routestartnavi);
        backView = findViewById(R.id.route_back_view);
        distanceView = findViewById(R.id.navi_route_distance);
        timeView = findViewById(R.id.navi_route_time);
        costView = findViewById(R.id.navi_route_cost);
        costColumn = findViewById(R.id.route_cost_column);
        navModeSelector = findViewById(R.id.nav_mode_selector);
        routeSelectorScroll = findViewById(R.id.route_selector_scroll);
        routeSelectorContainer = findViewById(R.id.route_selector_container);
        routeActionButtons = findViewById(R.id.route_action_buttons);
        btnLocate = findViewById(R.id.btn_locate);
        btnRefresh = findViewById(R.id.btn_refresh);
        btnPreference = findViewById(R.id.btn_preference);
        mapView = findViewById(R.id.routemap);
        mapView.onCreate(state);
        map = mapView.getMap();
        // 地图暗黑模式（全局控制）
        int dayNightMode = SettingUtils.readDarkMapMode();
        if (dayNightMode == SettingUtils.DAY_NIGHT_MODE_NIGHT
                || (dayNightMode == SettingUtils.DAY_NIGHT_MODE_AUTO && SettingUtils.isNightTime())) {
            map.setMapType(AMap.MAP_TYPE_NIGHT);
        }
        map.setOnMapLoadedListener(() -> {
            mapLoaded = true;
            zoomToRouteSpan();
        });

        startBtn.setEnabled(false);
        startBtn.setOnClickListener(v -> startRealtimeNavi());
        startEmulatorBtn = findViewById(R.id.routestartemulator);
        startEmulatorBtn.setEnabled(false);
        startEmulatorBtn.setOnClickListener(v -> startEmulatorNavi());
        backView.setOnClickListener(v -> finish());
        btnLocate.setOnClickListener(v -> animateToStart());
        btnRefresh.setOnClickListener(v -> calculateRoute());
        btnPreference.setOnClickListener(v -> showPreferenceDialog());
        navModeSelector.setOnClickListener(v -> showNavModePopup());
    }

    /**
     * 初始化 AMapNavi 单例 — 对标官方demo RestRouteShowActivity.onCreate
     */
    private void initNavi() {
        try {
            navi = AMapNavi.getInstance(getApplicationContext());
            navi.addAMapNaviListener(this);
            calculateRoute();
        } catch (Exception e) {
            FileLogger.e(TAG, "initNavi failed", e);
        }
    }

    /**
     * 显示导航模式选择弹出菜单
     */
    private void showNavModePopup() {
        PopupMenu popup = new PopupMenu(this, navModeSelector);
        popup.getMenu().add(0, MODE_DRIVE, 0, R.string.nav_mode_drive);
        popup.getMenu().add(0, MODE_MOTOR, 1, R.string.nav_mode_motor);
        popup.getMenu().add(0, MODE_RIDE, 2, R.string.nav_mode_ride);
        popup.getMenu().add(0, MODE_WALK, 3, R.string.nav_mode_walk);
        popup.setOnMenuItemClickListener(item -> {
            int newMode = item.getItemId();
            if (newMode != currentNaviMode) {
                currentNaviMode = newMode;
                navModeSelector.setText(item.getTitle());
                onNavModeChanged();
            }
            return true;
        });
        popup.show();
    }

    /**
     * 导航模式切换时：配置SDK状态、更新UI、重新算路
     */
    private void onNavModeChanged() {
        if (navi == null) return;
        clearRoute();

        switch (currentNaviMode) {
            case MODE_DRIVE:
                // 不在路线规划页调用 setIsNaviTravelView，只由 NaviCustomActivity 负责
                AMapCarInfo defaultCar = new AMapCarInfo();
                navi.setCarInfo(defaultCar);
                btnPreference.setVisibility(View.VISIBLE);
                break;
            case MODE_RIDE:
                navi.setIsNaviTravelView(true);
                navi.setTravelInfo(new AMapTravelInfo(TransportType.Ride));
                btnPreference.setVisibility(View.GONE);
                break;
            case MODE_MOTOR:
                // 腾讯摩托车导航 — 立即弹配置选择并跳转
                btnPreference.setVisibility(View.GONE);
                showMotorProfilePicker();
                // 恢复回默认模式，避免重复弹窗
                currentNaviMode = MODE_DRIVE;
                navModeSelector.setText(R.string.nav_mode_drive);
                return;
            case MODE_WALK:
                navi.setIsNaviTravelView(true);
                navi.setTravelInfo(new AMapTravelInfo(TransportType.Walk));
                btnPreference.setVisibility(View.GONE);
                break;
        }

        calculateRoute();
    }

    /**
     * 算路 — 根据当前导航模式调用对应SDK接口
     */
    private void calculateRoute() {
        // 摩托车模式不调用 AMap API
        if (currentNaviMode == MODE_MOTOR) return;

        if (navi == null || startList == null || startList.isEmpty()
                || endList == null || endList.isEmpty()) {
            FileLogger.e(TAG, "calculateRoute failed: navi=" + (navi != null)
                    + ", startList=" + (startList != null ? startList.size() : "null")
                    + ", endList=" + (endList != null ? endList.size() : "null"));
            handleRouteFailure();
            return;
        }
        calculateSuccess = false;
        startBtn.setEnabled(false);
        startEmulatorBtn.setEnabled(false);
        clearRoute();

        NaviLatLng from = startList.get(0);
        NaviLatLng to = endList.get(0);

        switch (currentNaviMode) {
            case MODE_RIDE:
                navi.calculateRideRoute(from, to);
                FileLogger.d(TAG, "calculateRoute: RIDE mode");
                break;
            case MODE_WALK:
                navi.calculateWalkRoute(from, to);
                FileLogger.d(TAG, "calculateRoute: WALK mode");
                break;
            case MODE_DRIVE:
            default:
                // 冲突检测 — 对标demo
                if (avoidhightspeed && hightspeed) {
                    Toast.makeText(this, "不走高速与高速优先不能同时为true", Toast.LENGTH_SHORT).show();
                }
                if (cost && hightspeed) {
                    Toast.makeText(this, "高速优先与避免收费不能同时为true", Toast.LENGTH_SHORT).show();
                }
                int strategy = 0;
                try {
                    strategy = navi.strategyConvert(congestion, avoidhightspeed, cost, hightspeed, true);
                } catch (Exception e) {
                    FileLogger.e(TAG, "strategyConvert failed", e);
                }
                navi.calculateDriveRoute(startList, endList, null, strategy);
                FileLogger.d(TAG, "calculateRoute: DRIVE strategy=" + strategy);
                break;
        }
    }

    /**
     * 显示偏好设置弹窗 — 深色 HUD 风格，点击即生效
     * 互斥规则：
     * - "高速优先" 与 "不走高速"、"避免收费" 互斥
     * - "不走高速" 和 "避免收费" 可以同时选中
     */
    private void showPreferenceDialog() {
        if (currentNaviMode == MODE_RIDE || currentNaviMode == MODE_WALK || currentNaviMode == MODE_MOTOR) {
            Toast.makeText(this, "当前模式不支持路线偏好设置", Toast.LENGTH_SHORT).show();
            return;
        }
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_route_preference, null);
        
        TextView btnCongestion = dialogView.findViewById(R.id.btn_congestion);
        TextView btnAvoidHightspeed = dialogView.findViewById(R.id.btn_avoidhightspeed);
        TextView btnCost = dialogView.findViewById(R.id.btn_cost);
        TextView btnHightspeed = dialogView.findViewById(R.id.btn_hightspeed);

        // 设置初始状态
        updateButtonBackground(btnCongestion, congestion);
        updateButtonBackground(btnAvoidHightspeed, avoidhightspeed);
        updateButtonBackground(btnCost, cost);
        updateButtonBackground(btnHightspeed, hightspeed);

        // 点击切换状态
        btnCongestion.setOnClickListener(v -> {
            congestion = !congestion;
            updateButtonBackground(btnCongestion, congestion);
            calculateRoute(); // 立即生效
        });

        btnAvoidHightspeed.setOnClickListener(v -> {
            avoidhightspeed = !avoidhightspeed;
            if (avoidhightspeed && hightspeed) {
                hightspeed = false;
                updateButtonBackground(btnHightspeed, false);
            }
            updateButtonBackground(btnAvoidHightspeed, avoidhightspeed);
            calculateRoute(); // 立即生效
        });

        btnCost.setOnClickListener(v -> {
            cost = !cost;
            if (cost && hightspeed) {
                hightspeed = false;
                updateButtonBackground(btnHightspeed, false);
            }
            updateButtonBackground(btnCost, cost);
            calculateRoute(); // 立即生效
        });

        btnHightspeed.setOnClickListener(v -> {
            hightspeed = !hightspeed;
            if (hightspeed) {
                avoidhightspeed = false;
                cost = false;
                updateButtonBackground(btnAvoidHightspeed, false);
                updateButtonBackground(btnCost, false);
            }
            updateButtonBackground(btnHightspeed, hightspeed);
            calculateRoute(); // 立即生效
        });

        new AlertDialog.Builder(this)
                .setView(dialogView)
                .show();
    }

    /**
     * 根据选中状态更新按钮背景
     * 选中：黄色背景 route_card_selected + 黑色文字
     * 未选中：深灰色背景 route_card_normal + 白色文字
     */
    private void updateButtonBackground(TextView button, boolean isSelected) {
        if (isSelected) {
            button.setBackgroundResource(R.drawable.route_card_selected);
            button.setTextColor(0xFF000000); // 黄色背景下用黑色文字
        } else {
            button.setBackgroundResource(R.drawable.route_card_normal);
            button.setTextColor(0xFFFFFFFF); // 深灰背景下用白色文字
        }
    }

    /**
     * 定位到起点 — 移动地图到起点位置
     */
    private void animateToStart() {
        if (startList != null && !startList.isEmpty()) {
            NaviLatLng start = startList.get(0);
            map.animateCamera(CameraUpdateFactory.newLatLngZoom(
                    new LatLng(start.getLatitude(), start.getLongitude()), 15));
        }
    }

    /**
     * 绘制单条路线 — 对标官方demo RestRouteShowActivity.drawRoutes
     */
    private void drawRoute(int routeId, AMapNaviPath path) {
        calculateSuccess = true;
        map.moveCamera(CameraUpdateFactory.changeTilt(0));
        RouteOverLay routeOverLay = new RouteOverLay(map, path, this);
        routeOverLay.setTrafficLine(false);
        routeOverLay.addToMap();
        routeOverlays.put(routeId, routeOverLay);
    }

    /**
     * 清除地图上所有路线 — 对标官方demo RestRouteShowActivity.clearRoute
     */
    private void clearRoute() {
        for (int i = 0; i < routeOverlays.size(); i++) {
            routeOverlays.valueAt(i).removeFromMap();
        }
        routeOverlays.clear();
        currentRouteIds = null;
        selectedRouteIndex = 0;
        if (routeSelectorContainer != null) {
            routeSelectorContainer.removeAllViews();
        }
        if (routeSelectorScroll != null) {
            routeSelectorScroll.setVisibility(View.GONE);
        }
        updateActionButtonsPosition(false);
    }

    /**
     * 缩放地图以显示完整路线
     */
    private void zoomToRouteSpan() {
        if (routeOverlays.size() > 0) {
            routeOverlays.valueAt(0).zoomToSpan();
        }
    }

    /**
     * 构建路线选择卡片 — 多条路线时显示选择UI，单路线时隐藏。
     */
    private void buildRouteCards(int[] routeIds, HashMap<Integer, AMapNaviPath> paths) {
        currentRouteIds = routeIds;
        routeSelectorContainer.removeAllViews();

        if (routeIds.length <= 1) {
            routeSelectorScroll.setVisibility(View.GONE);
            updateActionButtonsPosition(false);
            // 单路线：直接更新UI，不调用selectRouteId（避免在onCalculateRouteSuccess回调栈中调用）
            if (routeIds.length == 1) {
                selectedRouteIndex = 0;
                updateRouteInfoUI(routeIds[0]);
            }
            return;
        }

        // 多路线：显示选择条并构建卡片
        routeSelectorScroll.setVisibility(View.VISIBLE);
        updateActionButtonsPosition(true);
        LayoutInflater inflater = LayoutInflater.from(this);

        for (int i = 0; i < routeIds.length; i++) {
            AMapNaviPath path = paths.get(routeIds[i]);
            if (path == null) continue;

            View card = inflater.inflate(R.layout.item_route_card, routeSelectorContainer, false);
            TextView labelView = card.findViewById(R.id.route_card_label);
            TextView timeView = card.findViewById(R.id.route_card_time);
            TextView distView = card.findViewById(R.id.route_card_dist);

            // 路线标签
            String labels = path.getLabels();
            if (labels != null && !labels.isEmpty()) {
                labelView.setText(labels);
            } else {
                labelView.setText("路线" + (i + 1));
            }

            // 时间
            int minutes = (path.getAllTime() + 59) / 60;
            timeView.setText(minutes + "分钟");

            // 距离 + 收费
            double km = ((int) (path.getAllLength() / 100.0)) / 10.0;
            int toll = path.getTollCost();
            if (toll > 0) {
                distView.setText(km + "km · ¥" + toll);
            } else {
                distView.setText(km + "km · 免费");
            }

            final int index = i;
            card.setOnClickListener(v -> onRouteCardClicked(index));
            routeSelectorContainer.addView(card);
        }

        // 默认高亮第一条路线（不调用selectRouteId，避免在onCalculateRouteSuccess回调栈中调用）
        highlightRouteUI(0);
    }

    /**
     * 高亮指定路线并更新UI（不调用selectRouteId）。
     * 用于onCalculateRouteSuccess回调中的默认选中，避免在SDK回调栈中调用selectRouteId。
     */
    private void highlightRouteUI(int index) {
        if (currentRouteIds == null || index < 0 || index >= currentRouteIds.length) return;
        selectedRouteIndex = index;
        int routeId = currentRouteIds[index];

        // 1. 地图路线高亮
        for (int i = 0; i < routeOverlays.size(); i++) {
            routeOverlays.valueAt(i).setTransparency(0.4f);
        }
        RouteOverLay selected = routeOverlays.get(routeId);
        if (selected != null) {
            selected.setTransparency(1);
            selected.setZindex(zindex++);
        }

        // 2. 更新底部信息
        updateRouteInfoUI(routeId);

        // 3. 更新卡片选中态
        for (int i = 0; i < routeSelectorContainer.getChildCount(); i++) {
            View card = routeSelectorContainer.getChildAt(i);
            if (i == index) {
                card.setBackgroundResource(R.drawable.route_card_selected);
                ((TextView) card.findViewById(R.id.route_card_label)).setTextColor(0xFF000000);
                ((TextView) card.findViewById(R.id.route_card_time)).setTextColor(0xFF333333);
                ((TextView) card.findViewById(R.id.route_card_dist)).setTextColor(0xFF555555);
            } else {
                card.setBackgroundResource(R.drawable.route_card_normal);
                ((TextView) card.findViewById(R.id.route_card_label)).setTextColor(0xFFFFFFFF);
                ((TextView) card.findViewById(R.id.route_card_time)).setTextColor(0xFFB0B0B0);
                ((TextView) card.findViewById(R.id.route_card_dist)).setTextColor(0xFF808080);
            }
        }
    }

    /**
     * 路线卡片点击 — 对标官方demo RestRouteShowActivity.changeRoute()：
     * 高亮选中路线、更新底部信息、通知SDK。
     * 仅在用户手动点击路线卡片时调用（onClick事件），不在SDK回调中调用。
     */
    private void onRouteCardClicked(int index) {
        highlightRouteUI(index);
        // 用户手动点击路线卡片时通知SDK选中路线
        if (currentRouteIds != null && index >= 0 && index < currentRouteIds.length) {
            navi.selectRouteId(currentRouteIds[index]);
        }
    }

    /**
     * 更新底部路线信息UI
     */
    private void updateRouteInfoUI(int routeId) {
        if (navi == null) {
            handleRouteFailure();
            return;
        }
        AMapNaviPath path = navi.getNaviPaths().get(routeId);
        if (path == null) {
            handleRouteFailure();
            return;
        }
        calculateSuccess = true;
        distanceView.setText(String.valueOf(((int) (path.getAllLength() / 100.0)) / 10.0));
        timeView.setText(String.valueOf((path.getAllTime() + 59) / 60));

        // 骑行/步行无收费信息，隐藏收费列
        if (currentNaviMode == MODE_RIDE || currentNaviMode == MODE_WALK) {
            costColumn.setVisibility(View.GONE);
        } else {
            costColumn.setVisibility(View.VISIBLE);
            costView.setText(String.valueOf(path.getTollCost()));
        }
        startBtn.setEnabled(true);
        startEmulatorBtn.setEnabled(true);

        if (mapLoaded) {
            zoomToRouteSpan();
        }
    }

    private void handleRouteFailure() {
        calculateSuccess = false;
        startBtn.setEnabled(false);
        startEmulatorBtn.setEnabled(false);
        Toast.makeText(this, "规划失败", Toast.LENGTH_SHORT).show();
    }

    private void updateActionButtonsPosition(boolean selectorVisible) {
        if (routeActionButtons == null) return;
        RelativeLayout.LayoutParams lp = (RelativeLayout.LayoutParams) routeActionButtons.getLayoutParams();
        lp.removeRule(RelativeLayout.ABOVE);
        if (selectorVisible) {
            lp.addRule(RelativeLayout.ABOVE, R.id.route_selector_scroll);
        } else {
            lp.addRule(RelativeLayout.ABOVE, R.id.navi_cast_root_rl);
        }
        routeActionButtons.setLayoutParams(lp);
    }

    /**
     * 启动GPS实时导航 — 严格对标官方demo RestRouteShowActivity 第342-344行。
     * demo在启动导航前对AMapNavi单例零操作：不调selectRouteId、不移除监听器。
     * selectRouteId已在onRouteCardClicked中由用户交互触发（单路线时SDK自动选择）。
     */
    private void startRealtimeNavi() {
        if (currentNaviMode == MODE_MOTOR) {
            showMotorProfilePicker();
            return;
        }
        if (!calculateSuccess) {
            FileLogger.e(TAG, "startRealtimeNavi failed: route not calculated");
            return;
        }
        // 启动导航前注销 AMapNaviListener，避免与 NaviCustomActivity 双重注册
        // onResume 中会重新注册
        if (navi != null) {
            navi.removeAMapNaviListener(this);
            mNaviListenerRemoved = true;
            FileLogger.d(TAG, "NaviListener removed before startRealNavi");
        }
        stopCruiseIfRunning();
        FileLogger.d(TAG, "startRealtimeNavi, mode=" + currentNaviMode);
        Intent intent = new Intent(this, NaviCustomActivity.class);
        intent.putExtra("gps", true);
        intent.putExtra(EXTRA_TRAVEL_MODE, currentNaviMode);
        startActivity(intent);
    }

    private void startEmulatorNavi() {
        if (currentNaviMode == MODE_MOTOR) {
            startRealtimeNavi(); // 摩托车模式只用真实导航
            return;
        }
        if (!calculateSuccess) {
            FileLogger.e(TAG, "startEmulatorNavi failed: route not calculated");
            return;
        }
        // 启动导航前注销 AMapNaviListener，避免与 NaviCustomActivity 双重注册
        if (navi != null) {
            navi.removeAMapNaviListener(this);
            mNaviListenerRemoved = true;
            FileLogger.d(TAG, "NaviListener removed before startEmulatorNavi");
        }
        FileLogger.d(TAG, "startEmulatorNavi, mode=" + currentNaviMode);
        Intent intent = new Intent(this, NaviCustomActivity.class);
        intent.putExtra("gps", false);
        intent.putExtra(EXTRA_TRAVEL_MODE, currentNaviMode);
        startActivity(intent);
    }

    private void loadCameraMarkers() {
        if (map == null) return;
        CameraOverlayManager mgr = CameraOverlayManager.getInstance();
        if (mgr.isShowing()) {
            mgr.show(map);
        }
    }

    private void stopCruiseIfRunning() {
        CruiseModeManager cruiseMgr = CruiseModeManager.getInstance();
        if (cruiseMgr != null && cruiseMgr.isRunning()) {
            cruiseMgr.stop();
            SettingUtils.writeCurrentCruiseState(SettingUtils.SWITCH_OFF);
            FileLogger.i(TAG, "Cruise mode auto-cancelled before navigation");
        }
    }

    // ======================== 生命周期 ========================

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            Intent intent = new Intent(this, MapsMainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            startActivity(intent);
            finish();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        mapView.onSaveInstanceState(outState);
    }

    @Override
    protected void onResume() {
        super.onResume();
        mapView.onResume();
        // 从 NaviCustomActivity 返回后重新注册 AMapNaviListener（启动导航前已注销）
        if (mNaviListenerRemoved && navi != null) {
            navi.addAMapNaviListener(this);
            mNaviListenerRemoved = false;
            FileLogger.d(TAG, "NaviListener re-added in onResume");
        }
        if (navi != null) {
            navi.setIsNaviTravelView(currentNaviMode == MODE_RIDE || currentNaviMode == MODE_WALK);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        mapView.onPause();
    }

    /**
     * 对标官方demo RestRouteShowActivity.onDestroy（第172-188行）：
     * 清空数据 + removeAMapNaviListener + destroy() 释放AMapNavi单例。
     *
     * 关键：只在 isFinishing() 时调用 destroy()。
     * 如果是系统回收Activity（非用户主动退出），不销毁AMapNavi单例，
     * 避免导航页面NaviCustomActivity正在使用时路线数据丢失。
     * 对标demo RouteNaviActivity的注释：
     * "当前页面不销毁AMapNavi对象，因为可能会返回再次进行路线选择"
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (startList != null) startList.clear();
        if (endList != null) endList.clear();
        clearRoute();
        mapView.onDestroy();
        if (navi != null) {
            navi.removeAMapNaviListener(this);
            // 只在用户主动退出（finish）时销毁AMapNavi单例
            // 系统回收时不销毁，保护正在导航的NaviCustomActivity
            if (isFinishing()) {
                navi.destroy();
            }
        }
    }

    // ======================== AMapNaviListener 回调 ========================

    @Override public void onInitNaviFailure() { handleRouteFailure(); }
    @Override public void onInitNaviSuccess() {}
    @Override public void onStartNavi(int type) {}
    @Override public void onTrafficStatusUpdate() {}
    @Override public void onLocationChange(AMapNaviLocation location) {}
    @Override public void onGetNavigationText(int type, String text) {}
    @Override public void onGetNavigationText(String s) {}
    @Override public void onEndEmulatorNavi() {}
    @Override public void onArriveDestination() {}
    @Override public void onReCalculateRouteForYaw() {}
    @Override public void onReCalculateRouteForTrafficJam() {}
    @Override public void onArrivedWayPoint(int wayID) {}
    @Override public void onGpsOpenStatus(boolean enabled) {}
    @Override public void onNaviInfoUpdate(NaviInfo naviinfo) {}
    @Override public void onServiceAreaUpdate(AMapServiceAreaInfo[] serviceAreaInfos) {}
    @Override public void updateCameraInfo(AMapNaviCameraInfo[] cameraInfos) {}
    @Override public void updateIntervalCameraInfo(AMapNaviCameraInfo a, AMapNaviCameraInfo b, int i) {}
    @Override public void onPlayRing(int playRing) {}
    @Override public void showCross(AMapNaviCross cross) {}
    @Override public void hideCross() {}
    @Override public void showLaneInfo(AMapLaneInfo laneInfo) {}
    @Override public void showLaneInfo(AMapLaneInfo[] laneInfos, byte[] bg, byte[] rec) {}
    @Override public void hideLaneInfo() {}
    @Override public void showModeCross(AMapModelCross modelCross) {}
    @Override public void hideModeCross() {}
    @Override public void notifyParallelRoad(int parallelRoadStatus) {}
    @Override public void OnUpdateTrafficFacility(AMapNaviTrafficFacilityInfo info) {}
    @Override public void OnUpdateTrafficFacility(AMapNaviTrafficFacilityInfo[] infos) {}
    @Override public void updateAimlessModeStatistics(AimLessModeStat stat) {}
    @Override public void updateAimlessModeCongestionInfo(AimLessModeCongestionInfo info) {}

    /**
     * int[]版本回调 — 骑行/步行模式可能通过此回调返回结果。
     * 驾车模式保持留空，使用AMapCalcRouteResult版本。
     */
    @Override
    public void onCalculateRouteSuccess(int[] ints) {
        if ((currentNaviMode == MODE_RIDE || currentNaviMode == MODE_WALK) && ints != null && ints.length > 0) {
            FileLogger.d(TAG, "onCalculateRouteSuccess(int[]) for travel mode, routeCount=" + ints.length);
            clearRoute();
            HashMap<Integer, AMapNaviPath> paths = navi.getNaviPaths();
            for (int routeId : ints) {
                AMapNaviPath path = paths.get(routeId);
                if (path != null) {
                    drawRoute(routeId, path);
                }
            }
            buildRouteCards(ints, paths);
        }
    }

    /**
     * 对标demo RestRouteShowActivity第550-562行：
     * 清空旧路线 → 绘制新路线 → 构建选择卡片
     */
    @Override
    public void onCalculateRouteSuccess(AMapCalcRouteResult result) {
        FileLogger.d(TAG, "onCalculateRouteSuccess(AMapCalcRouteResult)");
        if (result == null || navi == null) return;

        // 对标demo第552行：清空上次计算的路径列表
        clearRoute();

        int[] routeIds = result.getRouteid();
        if (routeIds == null || routeIds.length == 0) return;

        HashMap<Integer, AMapNaviPath> paths = navi.getNaviPaths();
        for (int routeId : routeIds) {
            AMapNaviPath path = paths.get(routeId);
            if (path != null) {
                drawRoute(routeId, path);
            }
        }
        Toast.makeText(this, "路线规划成功", Toast.LENGTH_SHORT).show();
        buildRouteCards(routeIds, paths);
    }

    @Override
    public void onCalculateRouteFailure(int errorCode) {
        FileLogger.e(TAG, "onCalculateRouteFailure errorCode=" + errorCode);
        handleRouteFailure();
    }

    @Override
    public void onCalculateRouteFailure(AMapCalcRouteResult result) {
        FileLogger.e(TAG, "onCalculateRouteFailure result=" + (result == null ? -1 : result.getErrorCode()));
        handleRouteFailure();
    }

    @Override public void onNaviRouteNotify(AMapNaviRouteNotifyData routeNotifyData) {}
    @Override public void onGpsSignalWeak(boolean isWeak) {}

    // ========== 摩托车导航 — 配置选择 ==========

    private void showMotorProfilePicker() {
        List<Profile> allProfiles = ProfileRepository.INSTANCE.loadProfiles(this);
        List<Profile> motorProfiles = new java.util.ArrayList<>();
        for (Profile p : allProfiles) {
            if ("摩托车".equals(p.getType())) {
                motorProfiles.add(p);
            }
        }

        MotorProfilePickerDialog dialog = MotorProfilePickerDialog.newInstance(motorProfiles);
        dialog.setOnProfileSelectedListener(new MotorProfilePickerDialog.OnProfileSelectedListener() {
            @Override
            public void onProfileSelected(int index) {
                if (index >= 0 && index < motorProfiles.size()) {
                    launchTencentRoute(motorProfiles.get(index));
                }
            }

            @Override
            public void onNoConfigSelected() {
                launchTencentRoute(null);
            }
        });
        dialog.show(getSupportFragmentManager(), "motor_profile_picker");
    }

    private void launchTencentRoute(Profile profile) {
        if (startList == null || startList.isEmpty() || endList == null || endList.isEmpty()) {
            Toast.makeText(this, "缺少起终点信息", Toast.LENGTH_SHORT).show();
            return;
        }
        NaviLatLng start = startList.get(0);
        NaviLatLng end = endList.get(0);

        Intent intent = new Intent(this, TencentMotorRouteActivity.class);
        intent.putExtra("start_lat", start.getLatitude());
        intent.putExtra("start_lng", start.getLongitude());
        intent.putExtra("end_lat", end.getLatitude());
        intent.putExtra("end_lng", end.getLongitude());
        if (profile != null) {
            intent.putExtra("motor_displacement", profile.getDisplacement());
            intent.putExtra("motor_energy_type", "GAS");
        }
        startActivity(intent);
    }
}
