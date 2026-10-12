package org.zarroboogs.maps.ui.maps;

import com.amap.api.maps.AMap;
import com.amap.api.maps.model.BitmapDescriptor;
import com.amap.api.maps.model.BitmapDescriptorFactory;
import com.amap.api.maps.model.LatLng;
import com.amap.api.maps.model.Marker;
import com.amap.api.maps.model.MarkerOptions;
import com.amap.api.services.core.LatLonPoint;
import com.amap.api.services.core.PoiItem;
import com.amap.api.services.poisearch.PoiResult;
import com.amap.api.services.poisearch.PoiSearch;

import org.zarroboogs.maps.MapsApplication;
import org.zarroboogs.maps.R;

import java.util.ArrayList;
import java.util.List;

/**
 * 厕所 Overlay 统一管理器。
 * 所有页面的厕所 marker 显示/隐藏均通过此单例操作，
 * 不再各自维护开关状态。
 */
public class ToiletOverlayManager implements PoiSearch.OnPoiSearchListener {

    private static ToiletOverlayManager sInstance;

    private boolean mShowing;
    private final List<Marker> mMarkers = new ArrayList<>();
    private AMap mMap;
    private LatLng mCurrentLocation;
    private PoiSearch mPoiSearch;

    // 图标
    private final BitmapDescriptor mToiletIcon = BitmapDescriptorFactory.fromResource(R.drawable.dabianbiaoji);

    private OnToiletOverlayChangeListener mListener;

    public interface OnToiletOverlayChangeListener {
        void onToiletOverlayChanged(boolean showing);
    }

    private ToiletOverlayManager() {
    }

    public static synchronized ToiletOverlayManager getInstance() {
        if (sInstance == null) {
            sInstance = new ToiletOverlayManager();
        }
        return sInstance;
    }

    public boolean isShowing() {
        return mShowing;
    }

    public void setOnChangeListener(OnToiletOverlayChangeListener listener) {
        mListener = listener;
    }

    public void show(AMap map, LatLng currentLocation) {
        mShowing = true;
        mMap = map;
        mCurrentLocation = currentLocation;
        searchAndLoadMarkers();
        notifyListener();
    }

    public void hide() {
        mShowing = false;
        removeAllMarkers();
        mMap = null;
        notifyListener();
    }

    /**
     * 将 markers 切换到另一张地图（不改变 show/hide 状态）
     */
    public void rebindMap(AMap newMap) {
        if (mMap == newMap) return;
        removeAllMarkers();
        mMap = newMap;
        if (mShowing && mMap != null && mCurrentLocation != null) {
            searchAndLoadMarkers();
        }
    }

    /**
     * 清空当前 map 标记但不改变 show/hide 状态
     */
    public void detachMap() {
        removeAllMarkers();
        mMap = null;
    }

    /**
     * 更新当前位置并刷新厕所标记
     */
    public void updateLocation(LatLng location) {
        mCurrentLocation = location;
        if (mShowing && mMap != null) {
            searchAndLoadMarkers();
        }
    }

    /**
     * 释放所有 marker 和图标资源
     */
    public void destroy() {
        hide();
        if (mToiletIcon != null) {
            mToiletIcon.recycle();
        }
    }

    // ---------- private ----------

    private void searchAndLoadMarkers() {
        if (mMap == null || mCurrentLocation == null) return;

        removeAllMarkers();

        // 使用高德 POI 搜索 API 搜索周边厕所
        try {
            PoiSearch.Query query = new PoiSearch.Query("厕所", "", "");
            query.setPageSize(20);
            query.setPageNum(1);

            LatLonPoint center = new LatLonPoint(mCurrentLocation.latitude, mCurrentLocation.longitude);
            PoiSearch.SearchBound bound = new PoiSearch.SearchBound(center, 2000); // 搜索半径2000米

            mPoiSearch = new PoiSearch(MapsApplication.getAppContext(), query);
            mPoiSearch.setBound(bound);
            mPoiSearch.setOnPoiSearchListener(this);
            mPoiSearch.searchPOIAsyn();
        } catch (com.amap.api.services.core.AMapException e) {
            e.printStackTrace();
        }
    }

    private void loadMarkers(List<PoiItem> poiItems) {
        if (mMap == null || poiItems == null || poiItems.isEmpty()) return;

        ArrayList<MarkerOptions> options = new ArrayList<>();
        for (PoiItem poi : poiItems) {
            LatLng latLng = new LatLng(poi.getLatLonPoint().getLatitude(), poi.getLatLonPoint().getLongitude());
            String title = "[厕所]|" + poi.getTitle();
            String snippet = poi.getSnippet() != null ? poi.getSnippet() : "";

            MarkerOptions mo = new MarkerOptions()
                    .position(latLng)
                    .icon(mToiletIcon)
                    .title(title)
                    .snippet(snippet);
            options.add(mo);
        }

        mMarkers.addAll(mMap.addMarkers(options, false));
    }

    private void removeAllMarkers() {
        for (Marker m : mMarkers) {
            try {
                m.remove();
                m.destroy();
            } catch (Exception ignored) {
            }
        }
        mMarkers.clear();
    }

    private void notifyListener() {
        if (mListener != null) {
            mListener.onToiletOverlayChanged(mShowing);
        }
    }

    @Override
    public void onPoiSearched(PoiResult poiResult, int rCode) {
        if (rCode == 1000 && poiResult != null && poiResult.getPois() != null) {
            loadMarkers(poiResult.getPois());
        }
    }

    @Override
    public void onPoiItemSearched(com.amap.api.services.core.PoiItem poiItem, int i) {
        // 不需要处理单个 POI 搜索结果
    }
}