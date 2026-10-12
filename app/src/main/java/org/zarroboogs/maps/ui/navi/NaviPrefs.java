package org.zarroboogs.maps.ui.navi;

import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import org.zarroboogs.maps.MapsApplication;

public class NaviPrefs {

    public static final String SETTING_PREF_NAVI_NIGHT = "setting_pref_navi_night";
    public static final String SETTING_PREF_NAVI_FIX_PATH = "setting_pref_navi_fix_path";
    public static final String SETTING_PREF_NAVI_JAM = "setting_pref_navi_jam";
    public static final String SETTING_PREF_NAVI_TRAFFIC = "setting_pref_navi_traffic";
    public static final String SETTING_PREF_NAVI_CAMERA = "setting_pref_navi_camera";
    public static final String SETTING_PREF_NAVI_SCREEN_ON = "setting_pref_navi_screen_on";
    public static final String SETTING_PREF_NAVI_BEIJNG_CAMERA = "setting_pref_navi_beijng_camera";

    // 新增：导航设置页面相关配置项
    public static final String SETTING_PREF_BROADCAST_MODE = "setting_pref_broadcast_mode";     // 播报模式：0-详细，1-简洁，2-静音
    public static final String SETTING_PREF_NAVI_VIEW_MODE = "setting_pref_navi_view_mode";     // 导航视角：0-3D车头朝上，1-2D北朝上
    public static final String SETTING_PREF_DAY_NIGHT_MODE = "setting_pref_day_night_mode";     // 日夜模式：0-自动，1-白天，2-夜间
    public static final String SETTING_PREF_SCALE_AUTO_ZOOM = "setting_pref_scale_auto_zoom";   // 比例尺智能缩放：true/false

    // 路线偏好
    public static final String SETTING_PREF_AVOID_CONGESTION = "setting_pref_avoid_congestion";   // 避免拥堵
    public static final String SETTING_PREF_AVOID_HIGHSPEED = "setting_pref_avoid_highspeed";     // 不走高速
    public static final String SETTING_PREF_AVOID_COST = "setting_pref_avoid_cost";               // 避免收费
    public static final String SETTING_PREF_HIGHSPEED_PRIORITY = "setting_pref_highspeed_priority"; // 高速优先

    private static SharedPreferences getPrefs() {
        return PreferenceManager.getDefaultSharedPreferences(MapsApplication.getAppContext());
    }

    public static boolean getNaviNight() {
        return getPrefs().getBoolean(SETTING_PREF_NAVI_NIGHT, false);
    }

    public static boolean getReCalculateRouteForYaw() {
        return getPrefs().getBoolean(SETTING_PREF_NAVI_FIX_PATH, true);
    }

    public static boolean getReCalculateRouteForTrafficJam() {
        return getPrefs().getBoolean(SETTING_PREF_NAVI_JAM, true);
    }

    public static boolean getTrafficInfoUpdateEnabled() {
        return getPrefs().getBoolean(SETTING_PREF_NAVI_TRAFFIC, true);
    }

    public static boolean getCameraInfoUpdateEnabled() {
        return getPrefs().getBoolean(SETTING_PREF_NAVI_CAMERA, true);
    }

    public static boolean getScreenAlwaysBright() {
        return getPrefs().getBoolean(SETTING_PREF_NAVI_SCREEN_ON, true);
    }

    public static boolean getBeijingCamera() {
        return getPrefs().getBoolean(SETTING_PREF_NAVI_BEIJNG_CAMERA, true);
    }

    // ==================== 新增：导航设置页面相关方法 ====================

    /**
     * 获取播报模式
     * @return 0-详细播报，1-简洁播报，2-静音
     */
    public static int getBroadcastMode() {
        return getPrefs().getInt(SETTING_PREF_BROADCAST_MODE, 0); // 默认详细播报
    }

    /**
     * 设置播报模式
     * @param mode 0-详细播报，1-简洁播报，2-静音
     */
    public static void setBroadcastMode(int mode) {
        getPrefs().edit().putInt(SETTING_PREF_BROADCAST_MODE, mode).apply();
    }

    /**
     * 获取导航视角模式
     * @return 0-3D车头朝上，1-2D北朝上
     */
    public static int getNaviViewMode() {
        return getPrefs().getInt(SETTING_PREF_NAVI_VIEW_MODE, 0);
    }

    /**
     * 获取导航视角模式（指定默认值）
     * @param defaultMode 未设置时的默认值
     * @return 0-3D车头朝上，1-2D北朝上
     */
    public static int getNaviViewMode(int defaultMode) {
        return getPrefs().getInt(SETTING_PREF_NAVI_VIEW_MODE, defaultMode);
    }

    /**
     * 设置导航视角模式
     * @param mode 0-3D车头朝上，1-2D北朝上
     */
    public static void setNaviViewMode(int mode) {
        getPrefs().edit().putInt(SETTING_PREF_NAVI_VIEW_MODE, mode).apply();
    }

    /**
     * 获取日夜模式
     * @return 0-自动，1-白天，2-夜间
     */
    public static int getDayNightMode() {
        return getPrefs().getInt(SETTING_PREF_DAY_NIGHT_MODE, 0); // 默认自动
    }

    /**
     * 设置日夜模式
     * @param mode 0-自动，1-白天，2-夜间
     */
    public static void setDayNightMode(int mode) {
        getPrefs().edit().putInt(SETTING_PREF_DAY_NIGHT_MODE, mode).apply();
    }

    /**
     * 获取比例尺智能缩放状态
     * @return true-开启，false-关闭
     */
    public static boolean getScaleAutoZoom() {
        return getPrefs().getBoolean(SETTING_PREF_SCALE_AUTO_ZOOM, true); // 默认开启
    }

    /**
     * 设置比例尺智能缩放状态
     * @param enabled true-开启，false-关闭
     */
    public static void setScaleAutoZoom(boolean enabled) {
        getPrefs().edit().putBoolean(SETTING_PREF_SCALE_AUTO_ZOOM, enabled).apply();
    }

    // ==================== 路线偏好 ====================

    public static boolean getAvoidCongestion() {
        return getPrefs().getBoolean(SETTING_PREF_AVOID_CONGESTION, false);
    }

    public static void setAvoidCongestion(boolean enabled) {
        getPrefs().edit().putBoolean(SETTING_PREF_AVOID_CONGESTION, enabled).apply();
    }

    public static boolean getAvoidHighspeed() {
        return getPrefs().getBoolean(SETTING_PREF_AVOID_HIGHSPEED, false);
    }

    public static void setAvoidHighspeed(boolean enabled) {
        getPrefs().edit().putBoolean(SETTING_PREF_AVOID_HIGHSPEED, enabled).apply();
    }

    public static boolean getAvoidCost() {
        return getPrefs().getBoolean(SETTING_PREF_AVOID_COST, false);
    }

    public static void setAvoidCost(boolean enabled) {
        getPrefs().edit().putBoolean(SETTING_PREF_AVOID_COST, enabled).apply();
    }

    public static boolean getHighspeedPriority() {
        return getPrefs().getBoolean(SETTING_PREF_HIGHSPEED_PRIORITY, false);
    }

    public static void setHighspeedPriority(boolean enabled) {
        getPrefs().edit().putBoolean(SETTING_PREF_HIGHSPEED_PRIORITY, enabled).apply();
    }
}
