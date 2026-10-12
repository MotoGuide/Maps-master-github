package org.zarroboogs.maps;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;

import com.amap.api.maps.MapsInitializer;
import com.amap.api.location.AMapLocationClient;
import com.amap.api.navi.NaviSetting;

import com.tencent.tencentmap.mapsdk.maps.TencentMapInitializer;
import com.tencent.navix.api.NavigatorZygote;
import com.tencent.navix.api.NavigatorConfig;

import org.zarroboogs.maps.data.local.AppDatabase;
import org.zarroboogs.maps.data.local.dao.CameraDao;
import org.zarroboogs.maps.data.local.entity.Camera;
import org.zarroboogs.maps.network.CameraCloudSync;
import org.zarroboogs.maps.utils.Constants;
import org.zarroboogs.maps.utils.CrashHandler;
import org.zarroboogs.maps.utils.FileLogger;
import org.zarroboogs.maps.utils.FileUtils;
import org.zarroboogs.maps.utils.JsonUtils;

import java.util.ArrayList;

public class MapsApplication extends Application {

    private static Context sContext;
    private static AppDatabase sDatabase;

    // 高德通知渠道ID
    public static final String AMAP_CHANNEL_ID = "amap_channel";

    @Override
    public void onCreate() {
        super.onCreate();
        sContext = this;

        // 创建通知渠道
        createAmapNotificationChannel();

        // ========== 核心修复：高德地图+定位 隐私合规（必须加！）==========
        try {
            // 地图SDK隐私协议
            MapsInitializer.updatePrivacyShow(this, true, true);
            MapsInitializer.updatePrivacyAgree(this, true);

            // 导航SDK隐私协议（不加=AMapNaviView不渲染路线）
            NaviSetting.updatePrivacyShow(this, true, true);
            NaviSetting.updatePrivacyAgree(this, true);

            // 定位SDK隐私协议（不加=华为手机地图不显示）
            AMapLocationClient.updatePrivacyShow(this, true, true);
            AMapLocationClient.updatePrivacyAgree(this, true);
        } catch (Exception e) {
            e.printStackTrace();
        }

        // 文件日志初始化（路测后回溯分析）
        FileLogger.init(this);

        // 崩溃捕获初始化
        CrashHandler.getInstance().init(this);

        // ========== 腾讯地图导航SDK 初始化 ==========
        try {
            TencentMapInitializer.setAgreePrivacy(getApplicationContext(), true);
            TencentMapInitializer.start(getApplicationContext());
            NavigatorZygote.with(getApplicationContext()).init(
                NavigatorConfig.builder()
                    .setUserAgreedPrivacy(true)
                    .setDeviceId("maps_motorcycle")
                    .build()
            );
            NavigatorZygote.with(getApplicationContext()).start();
        } catch (Exception e) {
            FileLogger.e("MapsApplication", "Tencent SDK init failed", e);
        }

        // 摄像头数据初始化（异步：云端同步 → 本地缓存 → assets 兜底）
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    boolean synced = CameraCloudSync.getInstance().syncFromCloud();
                    if (synced) {
                        FileUtils.writeBooleanToSharedPreference(Constants.PreferenceKeys.KEY_INIT, true);
                        return;
                    }
                    // cloud unavailable, check if local DB has data
                    long count = getDatabase().cameraDao().count();
                    if (count > 0) {
                        // keep existing local data
                        FileUtils.writeBooleanToSharedPreference(Constants.PreferenceKeys.KEY_INIT, true);
                        return;
                    }
                    // first install with no network: fall back to assets JSON
                    getDatabase().cameraDao().deleteAll();
                    String json = FileUtils.readStringFromAsset(MapsApplication.getAppContext(), "motorcycle_cameras.json");
                    ArrayList<Camera> cameraBeans = JsonUtils.prasePaperCameras(json);
                    if (cameraBeans != null) {
                        for (Camera camera : cameraBeans) {
                            try {
                                getDatabase().cameraDao().insertOrReplace(camera);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                        FileUtils.writeBooleanToSharedPreference(Constants.PreferenceKeys.KEY_INIT, true);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();

    }

    /**
     * 创建通知渠道，修复通知报错
     */
    private void createAmapNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    AMAP_CHANNEL_ID,
                    "高德地图通知",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    public static Context getAppContext() {
        return sContext;
    }

    public static AppDatabase getDatabase() {
        if (sDatabase == null) {
            sDatabase = AppDatabase.Companion.getInstance(sContext);
        }
        return sDatabase;
    }
}
