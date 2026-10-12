package org.zarroboogs.maps.ui.maps

import com.amap.api.maps.AMap
import com.amap.api.maps.model.BitmapDescriptor
import com.amap.api.maps.model.BitmapDescriptorFactory
import com.amap.api.maps.model.LatLng
import com.amap.api.maps.model.Marker
import com.amap.api.maps.model.MarkerOptions
import com.amap.api.services.core.LatLonPoint
import com.amap.api.services.core.PoiItem
import com.amap.api.services.poisearch.PoiResult
import com.amap.api.services.poisearch.PoiSearch
import org.zarroboogs.maps.MapsApplication
import org.zarroboogs.maps.R

class GasStationOverlayManager private constructor() : PoiSearch.OnPoiSearchListener {

    var isShowing: Boolean = false
        private set

    private val markers = ArrayList<Marker>()
    private var map: AMap? = null
    private var currentLocation: LatLng? = null
    private var poiSearch: PoiSearch? = null
    private val gasStationIcon: BitmapDescriptor =
        BitmapDescriptorFactory.fromResource(R.drawable.jiayouzhanbiaoji)

    var listener: OnGasStationOverlayChangeListener? = null

    interface OnGasStationOverlayChangeListener {
        fun onGasStationOverlayChanged(showing: Boolean)
    }

    companion object {
        @Volatile
        private var instance: GasStationOverlayManager? = null

        @JvmStatic
        @Synchronized
        fun getInstance(): GasStationOverlayManager =
            instance ?: GasStationOverlayManager().also { instance = it }
    }

    fun show(map: AMap, currentLocation: LatLng) {
        isShowing = true
        this.map = map
        this.currentLocation = currentLocation
        searchAndLoadMarkers()
        listener?.onGasStationOverlayChanged(true)
    }

    fun hide() {
        isShowing = false
        removeAllMarkers()
        map = null
        listener?.onGasStationOverlayChanged(false)
    }

    fun rebindMap(newMap: AMap) {
        if (map === newMap) return
        removeAllMarkers()
        map = newMap
        if (isShowing && map != null && currentLocation != null) {
            searchAndLoadMarkers()
        }
    }

    fun detachMap() {
        removeAllMarkers()
        map = null
    }

    fun updateLocation(location: LatLng) {
        currentLocation = location
        if (isShowing && map != null) {
            searchAndLoadMarkers()
        }
    }

    fun destroy() {
        hide()
        try {
            gasStationIcon.recycle()
        } catch (_: Exception) {
        }
    }

    private fun searchAndLoadMarkers() {
        if (this.map == null) return
        val currentLocation = this.currentLocation ?: return
        removeAllMarkers()

        try {
            val query = PoiSearch.Query("加油站", "", "")
            query.pageSize = 20
            query.pageNum = 1
            val center = LatLonPoint(currentLocation.latitude, currentLocation.longitude)
            val bound = PoiSearch.SearchBound(center, 2000)
            poiSearch = PoiSearch(MapsApplication.getAppContext(), query)
            poiSearch?.bound = bound
            poiSearch?.setOnPoiSearchListener(this)
            poiSearch?.searchPOIAsyn()
        } catch (e: com.amap.api.services.core.AMapException) {
            e.printStackTrace()
        }
    }

    private fun loadMarkers(poiItems: List<PoiItem>?) {
        val map = this.map ?: return
        if (poiItems.isNullOrEmpty()) return

        val options = ArrayList<MarkerOptions>()
        for (poi in poiItems) {
            val latLng = LatLng(
                poi.latLonPoint.latitude,
                poi.latLonPoint.longitude
            )
            val title = "[加油站]|" + poi.title
            val snippet = poi.snippet ?: ""
            options.add(
                MarkerOptions()
                    .position(latLng)
                    .icon(gasStationIcon)
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

    override fun onPoiSearched(poiResult: PoiResult?, rCode: Int) {
        if (rCode == 1000 && poiResult != null) {
            loadMarkers(poiResult.pois)
        }
    }

    override fun onPoiItemSearched(poiItem: PoiItem?, i: Int) {
        // not used
    }
}
