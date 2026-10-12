package org.zarroboogs.maps.presenters;

import android.content.res.Resources;

import com.amap.api.maps.model.BitmapDescriptor;
import com.amap.api.maps.model.BitmapDescriptorFactory;
import com.amap.api.maps.model.LatLng;
import com.amap.api.maps.model.MarkerOptions;

import org.zarroboogs.maps.MapsApplication;
import org.zarroboogs.maps.R;
import org.zarroboogs.maps.data.local.entity.Camera;
import org.zarroboogs.maps.beans.CameraNoteType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MarkerInteractorImpl implements MarkerInteractor {
    private static final String CAMERA_MARKER_PREFIX = "[摄像头]";
    private static final String CAMERA_MARKER_SEPARATOR = "|";

    private final Resources mResources = MapsApplication.getAppContext().getResources();
    private final String mPkg = MapsApplication.getAppContext().getPackageName();
    private final BitmapDescriptor mDefaultIcon = BitmapDescriptorFactory.fromResource(R.drawable.icon_camera_location);
    private final Map<String, BitmapDescriptor> mIconCache = new HashMap<>();

    @Override
    public void createMarkers(OnMarkerCreatedListener listener) {
        if (null != listener){
            listener.onMarkerCreated(createMarkerOptions(readCameras()));
        }
    }

    @Override
    public void createMarkers(OnMarkerCreatedListener listener, String city) {
        if (null != listener) {
            listener.onMarkerCreated(createMarkerOptions(readCamerasByCity(city)));
        }
    }

    @Override
    public void readCameras(OnReadCamerasListener listener) {
        if (listener != null) {
            listener.onReadCameras(readCameras());
        }
    }

    private ArrayList<Camera> readCamerasByCity(String city) {
        List<Camera> all = MapsApplication.getDatabase().cameraDao().loadAll();
        if (city == null || city.isEmpty()) {
            return new ArrayList<>(all);
        }
        // 当前城市优先：匹配 city 的排在前面，其余排在后面
        ArrayList<Camera> priority = new ArrayList<>();
        ArrayList<Camera> others = new ArrayList<>();
        for (Camera cam : all) {
            if (city.equals(cam.getCity())) {
                priority.add(cam);
            } else {
                others.add(cam);
            }
        }
        priority.addAll(others);
        return priority;
    }

    private ArrayList<MarkerOptions> createMarkerOptions(ArrayList<Camera> cameraBeans){
        ArrayList<MarkerOptions> markerOptions = new ArrayList<>();
        for (Camera cameraBean : cameraBeans) {
            LatLng latLng = new LatLng(cameraBean.getLatitude(), cameraBean.getLongtitude());
            String title = cameraBean.getName();
            StringBuilder snippet = new StringBuilder();
            if (cameraBean.getAddress() != null && !cameraBean.getAddress().isEmpty()) {
                snippet.append("地址: " + cameraBean.getAddress());
            }
            if (cameraBean.getNote() != null && !cameraBean.getNote().isEmpty()) {
                if (snippet.length() > 0) {
                    snippet.append("\n");
                }
                snippet.append("类型: " + cameraBean.getNote());
            }
            String cameraTitle = buildCameraMarkerTitle(cameraBean.getId(), title);
            BitmapDescriptor icon = getIconForNote(cameraBean.getNote());
            MarkerOptions mo = new MarkerOptions()
                    .position(latLng)
                    .draggable(true)
                    .icon(icon)
                    .title(cameraTitle)
                    .snippet(snippet.toString());
            markerOptions.add(mo);
        }
        return markerOptions;
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

    private ArrayList<Camera> readCameras(){
        List<Camera> list = MapsApplication.getDatabase().cameraDao().loadAll();
        return new ArrayList<>(list);
    }

    private String buildCameraMarkerTitle(Long id, String name) {
        String safeName = name == null ? "" : name.trim();
        String safeId = id == null ? "-1" : String.valueOf(id);
        return CAMERA_MARKER_PREFIX + CAMERA_MARKER_SEPARATOR + safeId + CAMERA_MARKER_SEPARATOR + safeName;
    }

    /** 释放图标资源，外部在适当时机调用（如 activity destroy） */
    public void recycleIcons() {
        for (BitmapDescriptor icon : mIconCache.values()) {
            if (icon != null && icon != mDefaultIcon) {
                icon.recycle();
            }
        }
        mIconCache.clear();
    }
}
