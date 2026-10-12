package org.zarroboogs.maps.ui.navi;

import com.amap.api.navi.AMapNaviViewListener;
import com.amap.api.navi.AmapPageType;

/**
 * 基础导航视图监听器，实现所有 AMapNaviViewListener 接口的方法
 */
public abstract class BaseNaviViewListener implements AMapNaviViewListener {
    @Override
    public void onNaviSetting() {
        // 空实现
    }

    @Override
    public void onNaviCancel() {
        // 空实现
    }

    @Override
    public void onNaviMapMode(int i) {
        // 空实现
    }

    @Override
    public void onNaviTurnClick() {
        // 空实现
    }

    @Override
    public void onNextRoadClick() {
        // 空实现
    }

    @Override
    public void onScanViewButtonClick() {
        // 空实现
    }

    @Override
    public void onNaviViewLoaded() {
        // 空实现
    }

    @Override
    public boolean onNaviBackClick() {
        return false;
    }

    @Override
    public void onViewTypeChanged(AmapPageType amapPageType) {
        // 空实现
    }

    @Override
    public void onNaviRouteHighlightChange(long l, int i) {
        // 空实现
    }

    @Override
    public void onEagleChanged(boolean b) {
        // 空实现
    }

    @Override
    public void onControlMusicVolumeModeChanged(int i) {
        // 空实现
    }

    @Override
    public void onListenToVoiceDuringCallChanged(boolean b) {
        // 空实现
    }

    @Override
    public void onScaleAutoChanged(boolean b) {
        // 空实现
    }

    @Override
    public void onDayAndNightModeChanged(int i) {
        // 空实现
    }

    @Override
    public void onBroadcastModeChanged(int i) {
        // 空实现
    }

    @Override
    public void onStrategyChanged(int i) {
        // 空实现
    }

    @Override
    public void onAMapNaviViewExit() {
        // 空实现
    }

    @Override
    public void onLockMap(boolean isLock) {
        // 空实现
    }

    @Override
    public void onMapTypeChanged(int mapType) {
        // 空实现
    }

    @Override
    public void onStopSpeaking() {
        // 空实现
    }
}
