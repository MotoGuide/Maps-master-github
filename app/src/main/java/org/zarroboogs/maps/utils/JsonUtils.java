
package org.zarroboogs.maps.utils;

import org.zarroboogs.maps.utils.FileLogger;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.zarroboogs.maps.data.local.entity.Camera;

import java.util.ArrayList;
import java.util.Iterator;

public class JsonUtils {

    // {"code":0,"data":{"Notes_1434352586132.png":1},"errInfo":[]}
    @SuppressWarnings("unchecked")
    public static ArrayList<String> filterNeedUploadFiles(String jsonStr) {
        ArrayList<String> needUpload = new ArrayList<String>();

        JSONObject responseJsonObject;
        try {
            responseJsonObject = new JSONObject(jsonStr);
            JSONObject data = responseJsonObject.getJSONObject("data");
            Iterator<String> keys = data.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                int value = data.getInt(key);
                if (value == 0) {
                }
            }

            return needUpload;
        } catch (JSONException e) {
            e.printStackTrace();
        }

        return null;
    }

    public static ArrayList<Camera> prasePaperCameras(String json){
        ArrayList<Camera> cameraBeans = new ArrayList<>();

        JSONObject responseJsonObject;
        try {
            responseJsonObject = new JSONObject(json);
            JSONArray data = responseJsonObject.getJSONArray("data");
            for (int i = 0, j = data.length(); i < j; i++) {
                JSONObject jsonObject = data.getJSONObject(i);
                double lat = jsonObject.getDouble("latitude");
                double lon = jsonObject.getDouble("longtitude");
                String name = jsonObject.getString("name");
                int id = jsonObject.getInt("id");
                String direction = jsonObject.getString("direction");
                String address = jsonObject.getString("address");
                String note = jsonObject.optString("note", "");
                String city = jsonObject.optString("city", null);

                FileLogger.d("JsonUtils", "lat:" + lat + "lon:" + lat);

                Camera paperCameraBean = new Camera();
                paperCameraBean.setId((long) id);
                paperCameraBean.setDirection(direction);
                paperCameraBean.setLatitude(lat);
                paperCameraBean.setLongtitude(lon);
                paperCameraBean.setName(name);
                paperCameraBean.setAddress(address);
                paperCameraBean.setNote(org.zarroboogs.maps.beans.CameraNoteType.normalize(note));
                paperCameraBean.setCity(city);

                cameraBeans.add(paperCameraBean);
            }
            return cameraBeans;
        } catch (JSONException e) {
            e.printStackTrace();
        }

        return null;
    }
}
