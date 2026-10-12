package org.zarroboogs.maps.beans;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;

import com.amap.api.maps.model.LatLng;


/**
 * Created by wangdiyuan on 15-7-17.
 */
public class GeoFenceInfo {
    private LatLng latLng;
    private long mId = 0;

    public static final String GEOFENCE_BROADCAST_ACTION = "org.zarroboogs.maps.geofence_broadcast";
    private static final String EXTRA_GEOFENCE_ID = "geofence_id";

    private IntentFilter filter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);

    private PendingIntent mPendingIntent;

    public GeoFenceInfo(Context context, LatLng ll, long id) {
        this.mId = id;
        this.latLng = ll;
        filter.addAction(GEOFENCE_BROADCAST_ACTION);  // 所有围栏共用同一 action

        Intent intent = new Intent(GEOFENCE_BROADCAST_ACTION);
        intent.putExtra(EXTRA_GEOFENCE_ID, mId);       // mId 放 extras 里
        mPendingIntent = PendingIntent.getBroadcast(context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    public String getAction() {
        return GEOFENCE_BROADCAST_ACTION;
    }

    public long getId() {
        return mId;
    }

    public LatLng getLatLng() {
        return latLng;
    }

    public PendingIntent getPendingIntent() {
        return mPendingIntent;
    }

    public IntentFilter getFilter() {
        return filter;
    }
}
