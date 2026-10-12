package org.zarroboogs.maps.presenters;

import com.amap.api.maps.model.MarkerOptions;

import org.zarroboogs.maps.data.local.entity.Camera;

import java.util.ArrayList;

/**
 * Created by andforce on 15/7/19.
 */
public interface MarkerInteractor {

    interface OnMarkerCreatedListener {
        void onMarkerCreated(ArrayList<MarkerOptions> markerOptions);
    }


    interface OnReadCamerasListener {
        void onReadCameras(ArrayList<Camera> cameraBeans);
    }

    void createMarkers(OnMarkerCreatedListener listener);

    /** 按城市过滤创建 markers（city 为 null 时加载所有） */
    void createMarkers(OnMarkerCreatedListener listener, String city);

    void readCameras(OnReadCamerasListener listener);

}
