package org.zarroboogs.maps.ui.maps

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

data class QuickAccessItem(
    val id: String,
    val name: String,
    val icon: String,       // emoji or drawable name
    val address: String = "",
    val lat: Double = 0.0,
    val lng: Double = 0.0
) {
    fun isConfigured(): Boolean = lat != 0.0 && lng != 0.0 && address.isNotBlank()
}

object QuickAccessStorage {

    private const val PREFS_NAME = "quick_access_prefs"
    private const val KEY_ITEMS = "items"

    private val defaults = listOf(
        QuickAccessItem(id = "home", name = "家", icon = "🏠"),
        QuickAccessItem(id = "work", name = "公司", icon = "💼")
    )

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun loadAll(ctx: Context): List<QuickAccessItem> {
        val json = prefs(ctx).getString(KEY_ITEMS, null) ?: return defaults
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                QuickAccessItem(
                    id = obj.getString("id"),
                    name = obj.getString("name"),
                    icon = obj.optString("icon", "📍"),
                    address = obj.optString("address", ""),
                    lat = obj.optDouble("lat", 0.0),
                    lng = obj.optDouble("lng", 0.0)
                )
            }
        } catch (_: Exception) {
            defaults
        }
    }

    fun save(ctx: Context, item: QuickAccessItem) {
        val items = loadAll(ctx).toMutableList()
        val idx = items.indexOfFirst { it.id == item.id }
        if (idx >= 0) items[idx] = item else items.add(item)
        writeAll(ctx, items)
    }

    fun addCustom(ctx: Context, item: QuickAccessItem) {
        val items = loadAll(ctx).toMutableList()
        items.add(item)
        writeAll(ctx, items)
    }

    fun remove(ctx: Context, id: String) {
        val items = loadAll(ctx).filter { it.id != id }
        writeAll(ctx, items)
    }

    private fun writeAll(ctx: Context, items: List<QuickAccessItem>) {
        val arr = JSONArray()
        items.forEach { item ->
            arr.put(JSONObject().apply {
                put("id", item.id)
                put("name", item.name)
                put("icon", item.icon)
                put("address", item.address)
                put("lat", item.lat)
                put("lng", item.lng)
            })
        }
        prefs(ctx).edit().putString(KEY_ITEMS, arr.toString()).apply()
    }
}
