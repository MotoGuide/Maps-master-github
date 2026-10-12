package org.zarroboogs.maps.data.model

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject

object ProfileRepository {
    private const val PREFS_NAME = "app_navigation_prefs"
    private const val KEY_PROFILES = "profiles_json"
    private const val KEY_SELECTED = "current_profile"
    private const val KEY_OVERVIEW_IMAGE = "overview_image_uri"

    val vehicleTypeMap: LinkedHashMap<String, List<VehicleSubType>> = linkedMapOf(
        "摩托车" to listOf(
            VehicleSubType("巡航", "MOTORCYCLE"),
            VehicleSubType("拉力", "MOTORCYCLE"),
            VehicleSubType("仿赛", "MOTORCYCLE"),
            VehicleSubType("街车", "MOTORCYCLE"),
            VehicleSubType("踏板车", "MOTORCYCLE")
        ),
        "汽车" to listOf(
            VehicleSubType("燃油车", "CAR"),
            VehicleSubType("新能源", "CAR")
        ),
        "自行车" to listOf(
            VehicleSubType("自行车", "BICYCLE")
        ),
        "三轮车" to listOf(
            VehicleSubType("电动三轮车", "TRICYCLE"),
            VehicleSubType("燃油三轮车", "TRICYCLE")
        ),
        "电动车" to listOf(
            VehicleSubType("电动车", "ELECTRIC")
        )
    )

    private fun getPrefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun loadProfiles(context: Context): MutableList<Profile> {
        val profiles = mutableListOf<Profile>()
        val json = getPrefs(context).getString(KEY_PROFILES, null) ?: return profiles
        try {
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val uriStr = obj.optString("imageUri", "")
                val uri = if (uriStr.isNotEmpty()) Uri.parse(uriStr) else null
                profiles.add(
                    Profile(
                        name = obj.getString("name"),
                        type = obj.getString("type"),
                        subType = VehicleSubType(
                            obj.getString("subTypeLabel"),
                            obj.getString("subTypeRouting")
                        ),
                        displacement = obj.getInt("displacement"),
                        plate = obj.optString("plate", ""),
                        imageUri = uri
                    )
                )
            }
        } catch (_: Exception) { }
        return profiles
    }

    fun saveProfiles(context: Context, profiles: List<Profile>) {
        val arr = JSONArray()
        for (p in profiles) {
            val obj = JSONObject()
            obj.put("name", p.name)
            obj.put("type", p.type)
            obj.put("subTypeLabel", p.subType.label)
            obj.put("subTypeRouting", p.subType.routingType)
            obj.put("displacement", p.displacement)
            obj.put("plate", p.plate)
            obj.put("imageUri", p.imageUri?.toString() ?: "")
            arr.put(obj)
        }
        getPrefs(context).edit().putString(KEY_PROFILES, arr.toString()).apply()
    }

    fun loadSelectedIndex(context: Context): Int {
        return getPrefs(context).getInt(KEY_SELECTED, 0)
    }

    fun saveSelectedIndex(context: Context, index: Int) {
        getPrefs(context).edit().putInt(KEY_SELECTED, index).apply()
    }

    fun loadOverviewImageUri(context: Context): Uri? {
        val str = getPrefs(context).getString(KEY_OVERVIEW_IMAGE, null) ?: return null
        return Uri.parse(str)
    }

    fun saveOverviewImageUri(context: Context, uri: Uri?) {
        getPrefs(context).edit().putString(KEY_OVERVIEW_IMAGE, uri?.toString() ?: "").apply()
    }
}
