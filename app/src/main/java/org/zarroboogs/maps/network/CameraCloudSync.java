package org.zarroboogs.maps.network;

import org.zarroboogs.maps.utils.FileLogger;
import org.zarroboogs.maps.utils.FileUtils;

import org.zarroboogs.maps.MapsApplication;
import org.zarroboogs.maps.data.local.dao.CameraDao;
import org.zarroboogs.maps.data.local.entity.Camera;
import org.zarroboogs.maps.utils.Constants;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * 摄像头云端同步。单例，使用 OkHttp3。
 * GET /api/cameras → 全量下载 → Room upsert
 * POST /api/cameras/batch → 上传单条
 */
public class CameraCloudSync {
    private static final String TAG = "CameraCloudSync";
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    private static CameraCloudSync sInstance;
    private final OkHttpClient client;
    private final String baseUrl;
    private final String apiKey;

    private CameraCloudSync() {
        client = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .build();
        // 优先读 SharedPreferences（用户配置），没有则用 Constants 默认值
        String spUrl = FileUtils.readSharedPreference("camera_server_url");
        String spKey = FileUtils.readSharedPreference("camera_server_key");
        baseUrl = (spUrl != null && !spUrl.isEmpty()) ? spUrl : Constants.CAMERA_API_BASE_URL;
        apiKey = (spKey != null && !spKey.isEmpty()) ? spKey : Constants.CAMERA_API_KEY;
    }

    public static synchronized CameraCloudSync getInstance() {
        if (sInstance == null) {
            sInstance = new CameraCloudSync();
        }
        return sInstance;
    }

    /** 重置单例，下次调用 getInstance() 会用新的 SP 配置重建 */
    public static synchronized void resetInstance() {
        sInstance = null;
    }

    /**
     * 从云端全量同步摄像头数据到本地 Room。
     * @return true 成功，false 失败（网络不通等）
     */
    public boolean syncFromCloud() {
        try {
            Request request = new Request.Builder()
                    .url(baseUrl + "/api/cameras")
                    .header("X-Api-Key", apiKey)
                    .header("User-Agent", "okhttp/MotoNav")
                    .get()
                    .build();

            Response response = client.newCall(request).execute();
            if (!response.isSuccessful()) {
                FileLogger.w(TAG, "syncFromCloud HTTP " + response.code());
                response.close();
                return false;
            }

            String body = response.body().string();
            response.close();

            JSONObject json = new JSONObject(body);
            if (json.getInt("code") != 0) {
                FileLogger.w(TAG, "syncFromCloud server error: " + json.optString("msg"));
                return false;
            }

            JSONArray data = json.getJSONArray("data");
            CameraDao dao = MapsApplication.getDatabase().cameraDao();
            ArrayList<Camera> existingList = new ArrayList<>(dao.loadAll());
            java.util.Set<Long> cloudIds = new java.util.HashSet<>();

            for (int i = 0; i < data.length(); i++) {
                JSONObject item = data.getJSONObject(i);
                Camera c = new Camera();
                c.setId(item.getLong("id"));
                c.setName(item.getString("name"));
                c.setAddress(item.optString("address", ""));
                c.setLatitude(item.optDouble("latitude", 0));
                c.setLongtitude(item.optDouble("longtitude", 0));
                c.setDirection(item.optString("direction", ""));
                c.setNote(item.optString("note", ""));
                c.setCity(item.optString("city", null));
                cloudIds.add(c.getId());
                dao.insertOrReplace(c);
            }

            // 移除云端已不存在的本地记录（只移除 id>0 且不在云端列表中的）
            for (Camera local : existingList) {
                long localId = local.getId();
                if (localId > 0 && !cloudIds.contains(localId)) {
                    dao.deleteById(localId);
                }
            }

            FileLogger.i(TAG, "syncFromCloud done, " + data.length() + " cameras");
            return true;
        } catch (IOException | org.json.JSONException e) {
            FileLogger.e(TAG, "syncFromCloud failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * 上传一条摄像头到云端（异步调用，在后台线程执行）。
     * 成功后会用服务器返回的 id 更新本地记录，保持主键一致。
     */
    public boolean uploadCamera(Camera camera) {
        try {
            JSONObject item = new JSONObject();
            // 不传本地 Room 自增 ID，让服务端分配新 ID
            item.put("name", camera.getName());
            item.put("address", camera.getAddress() != null ? camera.getAddress() : "");
            item.put("latitude", camera.getLatitude() != null ? camera.getLatitude() : 0);
            item.put("longtitude", camera.getLongtitude() != null ? camera.getLongtitude() : 0);
            item.put("direction", camera.getDirection() != null ? camera.getDirection() : "");
            item.put("note", camera.getNote() != null ? camera.getNote() : "");
            item.put("city", camera.getCity() != null ? camera.getCity() : "");

            JSONArray batch = new JSONArray();
            batch.put(item);

            RequestBody requestBody = RequestBody.create(batch.toString(), JSON);
            Request request = new Request.Builder()
                    .url(baseUrl + "/api/cameras/batch")
                    .header("X-Api-Key", apiKey)
                    .header("User-Agent", "okhttp/MotoNav")
                    .post(requestBody)
                    .build();

            Response response = client.newCall(request).execute();
            if (!response.isSuccessful()) {
                FileLogger.w(TAG, "uploadCamera HTTP " + response.code());
                response.close();
                return false;
            }

            response.close();

            // 重新全量同步，避免本地 ID 与服务端 ID 不一致导致重复
            syncFromCloud();

            FileLogger.i(TAG, "uploadCamera done, name=" + camera.getName());
            return true;
        } catch (IOException | org.json.JSONException e) {
            FileLogger.e(TAG, "uploadCamera failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * 从云端删除一条摄像头。
     */
    public boolean deleteCamera(long cameraId) {
        try {
            Request request = new Request.Builder()
                    .url(baseUrl + "/api/cameras/" + cameraId)
                    .header("X-Api-Key", apiKey)
                    .header("User-Agent", "okhttp/MotoNav")
                    .delete()
                    .build();

            Response response = client.newCall(request).execute();
            response.close();

            if (response.isSuccessful()) {
                FileLogger.i(TAG, "deleteCamera done, id=" + cameraId);
                return true;
            }
            FileLogger.w(TAG, "deleteCamera HTTP " + response.code());
            return false;
        } catch (IOException e) {
            FileLogger.e(TAG, "deleteCamera failed: " + e.getMessage());
            return false;
        }
    }
}
