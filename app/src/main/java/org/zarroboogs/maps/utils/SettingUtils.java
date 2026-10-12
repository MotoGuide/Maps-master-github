package org.zarroboogs.maps.utils;

import android.content.Context;
import android.content.SharedPreferences;

import com.amap.api.maps.AMap;

import org.zarroboogs.maps.MapsApplication;

/**
 * Created by andforce on 15/8/1.
 */
public class SettingUtils {

    private static final String MYLOCATION_KEY = "location_mode";
    private static final String MAPS_STYLE_KEY = "maps_style_key";
    private static final String JING_CAMERA = "jing_camera";
    public static int SWITCH_ON = 1;
    public static int SWITCH_OFF = 0;

    public static int readCurrentMyLocationMode() {
        return FileUtils.readIntFromSharedPreference(MYLOCATION_KEY, AMap.LOCATION_TYPE_LOCATE);
    }

    public static void writeCurrentMyLocationMode(int mode) {
        FileUtils.writeIntToSharedPreference(MYLOCATION_KEY, mode);
    }

    public static int readCurrentMapsStyle() {
        return FileUtils.readIntFromSharedPreference(MAPS_STYLE_KEY, AMap.MAP_TYPE_NORMAL);
    }

    public static void writeCurrentMapsStyle(int style) {
        FileUtils.writeIntToSharedPreference(MAPS_STYLE_KEY, style);
    }

    public static int readCurrentCameraState() {
        return FileUtils.readIntFromSharedPreference(JING_CAMERA, SWITCH_OFF);
    }

    public static void writeCurrentCameraState(int state) {
        FileUtils.writeIntToSharedPreference(JING_CAMERA, state);
    }

    private static SharedPreferences sPref = MapsApplication.getAppContext().getSharedPreferences(MapsApplication.getAppContext().getPackageName() + "_preferences", Context.MODE_PRIVATE);
    public static final String SETTING_PREF_JING_CAMERA = "setting_pref_jing_camera";
    public static final String SETTING_PREF_JING_CAMERA_ALERT = "setting_pref_jing_camera_alert";

    public static boolean isEnableBeijingCamera(){
        return sPref.getBoolean(SETTING_PREF_JING_CAMERA, true);
    }

    public static boolean isEnableBeijingCameraAlert(){
        return sPref.getBoolean(SETTING_PREF_JING_CAMERA_ALERT, true);
    }

    // 摄像头 note 类型过滤（逗号分隔字符串）
    private static final String CAMERA_NOTE_FILTER = "camera_note_filter";

    public static String loadCameraNoteFilter() {
        return sPref.getString(CAMERA_NOTE_FILTER, null);
    }

    public static void saveCameraNoteFilter(String filter) {
        sPref.edit().putString(CAMERA_NOTE_FILTER, filter).apply();
    }

    // 电子围栏预警距离（米），巡航模式下触发预警的距离阈值
    private static final String GEOFENCE_DISTANCE_KEY = "geofence_distance";

    // 电子围栏报警独立开关（独立于巡航模式）
    private static final String GEOFENCE_ENABLED_KEY = "setting_pref_geofence_enabled";

    public static boolean isGeofenceEnabled() {
        return sPref.getBoolean(GEOFENCE_ENABLED_KEY, false);
    }

    public static void writeGeofenceEnabled(boolean enabled) {
        sPref.edit().putBoolean(GEOFENCE_ENABLED_KEY, enabled).apply();
    }
    public static final int GEOFENCE_DISTANCE_MIN = 10;
    public static final int GEOFENCE_DISTANCE_MAX = 2000;
    public static final int GEOFENCE_DISTANCE_DEFAULT = 300;

    public static int readGeofenceDistance() {
        int dist = FileUtils.readIntFromSharedPreference(GEOFENCE_DISTANCE_KEY, GEOFENCE_DISTANCE_DEFAULT);
        if (dist < GEOFENCE_DISTANCE_MIN) dist = GEOFENCE_DISTANCE_DEFAULT;
        if (dist > GEOFENCE_DISTANCE_MAX) dist = GEOFENCE_DISTANCE_MAX;
        return dist;
    }

    public static void writeGeofenceDistance(int distance) {
        if (distance < GEOFENCE_DISTANCE_MIN) distance = GEOFENCE_DISTANCE_MIN;
        if (distance > GEOFENCE_DISTANCE_MAX) distance = GEOFENCE_DISTANCE_MAX;
        FileUtils.writeIntToSharedPreference(GEOFENCE_DISTANCE_KEY, distance);
    }

    // 巡航模式
    private static final String CRUISE_KEY = "cruise_mode";
    public static final String SETTING_PREF_CRUISE_MODE = "setting_pref_cruise_mode";

    public static int readCurrentCruiseState() {
        return FileUtils.readIntFromSharedPreference(CRUISE_KEY, SWITCH_OFF);
    }

    public static void writeCurrentCruiseState(int state) {
        FileUtils.writeIntToSharedPreference(CRUISE_KEY, state);
    }

    // 地图暗黑模式（全局控制：首页、路线规划、导航）
    // 0=自动模式（根据时间）, 1=白天模式, 2=夜间模式
    private static final String DARK_MAP_KEY = "dark_map_mode";
    public static final int DAY_NIGHT_MODE_AUTO = 0;
    public static final int DAY_NIGHT_MODE_DAY = 1;
    public static final int DAY_NIGHT_MODE_NIGHT = 2;

    public static int readDarkMapMode() {
        return sPref.getInt(DARK_MAP_KEY, DAY_NIGHT_MODE_AUTO);
    }

    public static void writeDarkMapMode(int mode) {
        sPref.edit().putInt(DARK_MAP_KEY, mode).apply();
    }

    /**
     * 判断是否为夜间时间（用于自动日夜模式）
     * 夜间时间：18:00 - 06:00
     */
    public static boolean isNightTime() {
        java.util.Calendar c = java.util.Calendar.getInstance();
        int hour = c.get(java.util.Calendar.HOUR_OF_DAY);
        return hour < 6 || hour >= 18;
    }


}
