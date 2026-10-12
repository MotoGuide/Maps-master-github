package org.zarroboogs.maps;

import android.location.Location;
import android.os.Bundle;

import com.amap.api.location.AMapLocation;
import com.amap.api.location.AMapLocationListener;

/**
 * Created by andforce on 15/8/1.
 */
public abstract class OnLocationChangedListener implements AMapLocationListener {

    public abstract void onGaodeLocationChanged(AMapLocation aMapLocation);

    @Override
    public void onLocationChanged(AMapLocation aMapLocation) {
        onGaodeLocationChanged(aMapLocation);
    }
}
