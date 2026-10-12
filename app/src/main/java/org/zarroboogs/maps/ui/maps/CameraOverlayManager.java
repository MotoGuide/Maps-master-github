package org.zarroboogs.maps.ui.maps;

import com.amap.api.maps.AMap;
import com.amap.api.maps.model.BitmapDescriptor;
import com.amap.api.maps.model.BitmapDescriptorFactory;
import com.amap.api.maps.model.LatLng;
import com.amap.api.maps.model.Marker;
import com.amap.api.maps.model.MarkerOptions;

import org.zarroboogs.maps.MapsApplication;
import org.zarroboogs.maps.R;
import org.zarroboogs.maps.data.local.entity.Camera;
import org.zarroboogs.maps.beans.CameraNoteType;
import org.zarroboogs.maps.utils.SettingUtils;

import android.content.res.Resources;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 摄像头 Overlay 统一管理器。
 * 所有页面的摄像头 marker 显示/隐藏/过滤均通过此单例操作，
 * 不再各自维护开关状态。
 */
public class CameraOverlayManager {

    private static CameraOverlayManager sInstance;

    private boolean mShowing;
    private final Set<String> mActiveNoteTypes = new LinkedHashSet<>();
    private final List<Marker> mMarkers = new ArrayList<>();
    private AMap mMap;

    // 图标缓存（同 MarkerInteractorImpl 模式）
    private final Resources mResources = MapsApplication.getAppContext().getResources();
    private final String mPkg = MapsApplication.getAppContext().getPackageName();
    private final BitmapDescriptor mDefaultIcon = BitmapDescriptorFactory.fromResource(R.drawable.icon_camera_location);
    private final java.util.Map<String, BitmapDescriptor> mIconCache = new java.util.HashMap<>();

    private OnCameraOverlayChangeListener mListener;

    public interface OnCameraOverlayChangeListener {
        void onCameraOverlayChanged(boolean showing);
    }

    private CameraOverlayManager() {
        // 从 SharedPreferences 恢复过滤状态
        String saved = SettingUtils.loadCameraNoteFilter();
        if (saved != null && !saved.isEmpty()) {
            mActiveNoteTypes.addAll(Arrays.asList(saved.split(",")));
        } else {
            // 默认全部 13 种启用
            mActiveNoteTypes.addAll(Arrays.asList(CameraNoteType.STANDARD_VALUES));
        }
    }

    public static synchronized CameraOverlayManager getInstance() {
        if (sInstance == null) {
            sInstance = new CameraOverlayManager();
        }
        return sInstance;
    }

    public boolean isShowing() { return mShowing; }
    public Set<String> getActiveNoteTypes() { return mActiveNoteTypes; }

    public void setOnChangeListener(OnCameraOverlayChangeListener listener) {
        mListener = listener;
    }

    public void show(AMap map) {
        mShowing = true;
        mMap = map;
        loadMarkers();
        notifyListener();
    }

    public void hide() {
        mShowing = false;
        removeAllMarkers();
        mMap = null;
        notifyListener();
    }

    /** 将 markers 切换到另一张地图（不改变 show/hide 状态） */
    public void rebindMap(AMap newMap) {
        if (mMap == newMap) return;
        removeAllMarkers();
        mMap = newMap;
        if (mShowing && mMap != null) loadMarkers();
    }

    /** 清空当前 map 标记但不改变 show/hide 状态 */
    public void detachMap() {
        removeAllMarkers();
        mMap = null;
    }

    public void refresh() {
        if (mShowing && mMap != null) {
            removeAllMarkers();
            loadMarkers();
        }
    }

    /**
     * 设置某个 note 类型的过滤状态。仅当 showing 时即时刷新。
     */
    public void setFilter(String note, boolean enabled) {
        if (enabled) {
            mActiveNoteTypes.add(note);
        } else {
            mActiveNoteTypes.remove(note);
        }
        persistFilters();
        refresh();
    }

    /**
     * 全选或取消全选所有 note 类型。
     */
    public void setAllFilters(boolean enabled) {
        mActiveNoteTypes.clear();
        if (enabled) {
            mActiveNoteTypes.addAll(Arrays.asList(CameraNoteType.STANDARD_VALUES));
        }
        persistFilters();
        refresh();
    }

    /**
     * 释放所有 marker 和图标资源。外部在适当时机调用。
     */
    public void destroy() {
        hide();
        for (BitmapDescriptor icon : mIconCache.values()) {
            if (icon != null && icon != mDefaultIcon) {
                icon.recycle();
            }
        }
        mIconCache.clear();
    }

    // ---------- private ----------

    private void loadMarkers() {
        if (mMap == null) return;
        List<Camera> all = MapsApplication.getDatabase().cameraDao().loadAll();
        ArrayList<MarkerOptions> options = new ArrayList<>();
        for (Camera cam : all) {
            String note = cam.getNote() != null ? cam.getNote() : "";
            // 标准化 note（处理历史值），再检查是否在 active 集合中
            String normalized = CameraNoteType.normalize(note);
            if (!mActiveNoteTypes.contains(normalized)) {
                continue;
            }
            LatLng latLng = new LatLng(cam.getLatitude(), cam.getLongtitude());
            String title = buildCameraMarkerTitle(cam.getId(), cam.getName());
            StringBuilder snippet = new StringBuilder();
            if (cam.getAddress() != null && !cam.getAddress().isEmpty()) {
                snippet.append(cam.getAddress());
            }
            if (!normalized.isEmpty()) {
                if (snippet.length() > 0) snippet.append("\n");
                snippet.append(normalized);
            }
            MarkerOptions mo = new MarkerOptions()
                    .position(latLng)
                    .draggable(true)
                    .icon(getIconForNote(normalized))
                    .title(title)
                    .snippet(snippet.toString());
            options.add(mo);
        }
        // 优先当前城市（城市字段排序）
        // 简化处理：直接 addMarkers
        mMarkers.addAll(mMap.addMarkers(options, false));
    }

    private void removeAllMarkers() {
        for (Marker m : mMarkers) {
            try {
                m.remove();
                m.destroy();
            } catch (Exception ignored) {}
        }
        mMarkers.clear();
    }

    private BitmapDescriptor getIconForNote(String note) {
        String pinyin = CameraNoteType.toIconPinyin(note);
        if (pinyin == null) return mDefaultIcon;
        BitmapDescriptor cached = mIconCache.get(pinyin);
        if (cached != null) return cached;
        int resId = mResources.getIdentifier(pinyin, "drawable", mPkg);
        if (resId == 0) {
            mIconCache.put(pinyin, mDefaultIcon);
            return mDefaultIcon;
        }
        BitmapDescriptor icon = BitmapDescriptorFactory.fromResource(resId);
        mIconCache.put(pinyin, icon);
        return icon;
    }

    private String buildCameraMarkerTitle(Long id, String name) {
        String safeName = name == null ? "" : name.trim();
        String safeId = id == null ? "-1" : String.valueOf(id);
        return "[摄像头]|" + safeId + "|" + safeName;
    }

    private void persistFilters() {
        StringBuilder sb = new StringBuilder();
        for (String note : mActiveNoteTypes) {
            if (sb.length() > 0) sb.append(",");
            sb.append(note);
        }
        SettingUtils.saveCameraNoteFilter(sb.toString());
    }

    private void notifyListener() {
        if (mListener != null) {
            mListener.onCameraOverlayChanged(mShowing);
        }
    }
}
