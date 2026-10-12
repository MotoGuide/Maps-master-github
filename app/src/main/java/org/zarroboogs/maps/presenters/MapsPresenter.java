package org.zarroboogs.maps.presenters;

/**
 * Created by andforce on 15/7/19.
 */
public interface MapsPresenter {

    public void loadDefaultCameraMarkers();

    /** 按城市加载摄像头 markers（city 为 null 时加载所有） */
    public void loadDefaultCameraMarkers(String city);

    public void enableDefaultGeoFences();

    public void disableDefaultGeoFences();

    public void changeMyLocationMode();

    public void stopFollowMode();

}
