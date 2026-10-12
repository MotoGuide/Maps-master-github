package org.zarroboogs.maps.ui.maps

import com.amap.api.maps.AMap
import com.amap.api.maps.model.BitmapDescriptor
import com.amap.api.maps.model.BitmapDescriptorFactory
import com.amap.api.maps.model.LatLng
import com.amap.api.maps.model.Marker
import com.amap.api.maps.model.MarkerOptions
import org.zarroboogs.maps.MapsApplication
import org.zarroboogs.maps.R
import org.zarroboogs.maps.data.local.entity.Bookmark

class BookmarkOverlayManager private constructor() {

    var isShowing: Boolean = false
        private set

    private val markers = ArrayList<Marker>()
    private var map: AMap? = null
    private val bookmarkIcon: BitmapDescriptor =
        BitmapDescriptorFactory.fromResource(R.drawable.shoucangbiaoji)

    var listener: OnBookmarkOverlayChangeListener? = null

    interface OnBookmarkOverlayChangeListener {
        fun onBookmarkOverlayChanged(showing: Boolean)
    }

    companion object {
        @Volatile
        private var instance: BookmarkOverlayManager? = null

        @JvmStatic
        @Synchronized
        fun getInstance(): BookmarkOverlayManager =
            instance ?: BookmarkOverlayManager().also { instance = it }
    }

    fun show(map: AMap) {
        isShowing = true
        this.map = map
        loadMarkers()
        listener?.onBookmarkOverlayChanged(true)
    }

    fun hide() {
        isShowing = false
        removeAllMarkers()
        this.map = null
        listener?.onBookmarkOverlayChanged(false)
    }

    fun rebindMap(newMap: AMap) {
        if (map === newMap) return
        removeAllMarkers()
        map = newMap
        if (isShowing && map != null) loadMarkers()
    }

    fun detachMap() {
        removeAllMarkers()
        map = null
    }

    fun refresh() {
        if (isShowing && map != null) {
            removeAllMarkers()
            loadMarkers()
        }
    }

    fun add(poi: Bookmark) {
        MapsApplication.getDatabase().bookmarkDao().insert(poi)
        if (isShowing) refresh()
    }

    fun remove(lat: Double, lng: Double) {
        val dao = MapsApplication.getDatabase().bookmarkDao()
        val all = dao.loadAll()
        for (poi in all) {
            if (poi.latitude == lat && poi.longitude == lng) {
                dao.delete(poi)
                break
            }
        }
        if (isShowing) refresh()
    }

    fun isBookmarked(lat: Double, lng: Double): Boolean {
        val all = MapsApplication.getDatabase().bookmarkDao().loadAll()
        return all.any { it.latitude == lat && it.longitude == lng }
    }

    fun destroy() {
        hide()
        try {
            bookmarkIcon.recycle()
        } catch (_: Exception) {
        }
    }

    private fun loadMarkers() {
        val map = this.map ?: return
        val all = try {
            MapsApplication.getDatabase().bookmarkDao().loadAll()
        } catch (e: Exception) {
            emptyList()
        }
        val options = ArrayList<MarkerOptions>()
        for (poi in all) {
            val latLng = LatLng(poi.latitude, poi.longitude)
            val title = "[收藏]|" + (poi.name ?: "")
            val snippet = poi.address ?: ""
            options.add(
                MarkerOptions()
                    .position(latLng)
                    .icon(bookmarkIcon)
                    .title(title)
                    .snippet(snippet)
            )
        }
        markers.addAll(map.addMarkers(options, false))
    }

    private fun removeAllMarkers() {
        for (m in markers) {
            try {
                m.remove()
                m.destroy()
            } catch (_: Exception) {
            }
        }
        markers.clear()
    }
}
