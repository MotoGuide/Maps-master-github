package org.zarroboogs.maps.ui.tencent

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color
import android.util.AttributeSet
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import com.tencent.navix.api.map.MapApi
import com.tencent.navix.api.model.NavDriveRoute
import com.tencent.navix.api.model.NavNonMotorRoute
import com.tencent.navix.api.model.NavRoute
import com.tencent.navix.api.model.NavRoutePlan
import com.tencent.navix.core.NavigatorContext
import com.tencent.tencentmap.mapsdk.maps.CameraUpdateFactory
import com.tencent.tencentmap.mapsdk.maps.model.*
import org.zarroboogs.maps.R
import java.util.Locale

// — 对齐 Demo RouteView + RouteItemView —

class TencentRouteView(context: Context, attrs: AttributeSet?) : LinearLayout(context, attrs) {

    private val itemView1: TencentRouteItemView
    private val itemView2: TencentRouteItemView
    private val itemView3: TencentRouteItemView

    private var selectIndex = 0
    private lateinit var tencentMap: MapApi
    private var plan: NavRoutePlan<*>? = null

    private val polylineMap = mutableMapOf<Int, Polyline>()
    private var startMarker: Marker? = null
    private var endMarker: Marker? = null

    init {
        LayoutInflater.from(context).inflate(R.layout.view_route, this)
        findViewById<LinearLayout>(R.id.route_item_container).apply {
            itemView1 = TencentRouteItemView(context, attrs)
            itemView2 = TencentRouteItemView(context, attrs)
            itemView3 = TencentRouteItemView(context, attrs)
            addView(itemView1, 0, ViewGroup.LayoutParams.MATCH_PARENT)
            addView(itemView2, 0, ViewGroup.LayoutParams.MATCH_PARENT)
            addView(itemView3, 0, ViewGroup.LayoutParams.MATCH_PARENT)
            (itemView1.layoutParams as LayoutParams).weight = 1f
            (itemView2.layoutParams as LayoutParams).weight = 1f
            (itemView3.layoutParams as LayoutParams).weight = 1f
        }
    }

    fun injectMap(map: MapApi?) { tencentMap = map!! }

    fun currentIndex(): Int = selectIndex

    fun updateRoutePlan(routePlan: NavRoutePlan<*>?) {
        routePlan?.apply {
            routeDatas?.apply {
                if (routeDatas!!.size > 2) {
                    itemView3.visibility = View.VISIBLE
                    itemView3.update(routeDatas!![2], "${routeDatas!![2].tag}")
                    itemView3.setOnClickListener { selectIndex = 2; itemView3.setSelect(true); itemView2.setSelect(false); itemView1.setSelect(false); drawRoute() }
                } else {
                    itemView3.visibility = View.INVISIBLE; itemView3.setOnClickListener(null)
                }
                if (routeDatas!!.size > 1) {
                    itemView2.visibility = View.VISIBLE
                    itemView2.update(routeDatas!![1], "${routeDatas!![1].tag}")
                    itemView2.setOnClickListener { selectIndex = 1; itemView3.setSelect(false); itemView2.setSelect(true); itemView1.setSelect(false); drawRoute() }
                } else {
                    itemView2.visibility = View.INVISIBLE; itemView2.setOnClickListener(null)
                }
                if (routeDatas!!.isNotEmpty()) {
                    itemView1.visibility = View.VISIBLE
                    itemView1.update(routeDatas!![0], "${routeDatas!![0].tag}")
                    itemView1.setOnClickListener { selectIndex = 0; itemView3.setSelect(false); itemView2.setSelect(false); itemView1.setSelect(true); drawRoute() }
                } else {
                    itemView1.visibility = View.INVISIBLE; itemView1.setOnClickListener(null)
                }
            }
        }
        plan = routePlan
        selectIndex = 0
        itemView1.setSelect(true)
        itemView2.setSelect(false)
        itemView3.setSelect(false)
        drawRoute()
    }

    fun clear() {
        polylineMap.values.forEach { it.remove() }
        polylineMap.clear()
        startMarker?.remove(); startMarker = null
        endMarker?.remove(); endMarker = null
    }

    private fun drawRoute() {
        clear()
        plan?.apply {
            startMarker = tencentMap.addMarker(MarkerOptions(LatLng(startPoi.latitude, startPoi.longitude))
                .icon(BitmapDescriptorFactory.fromBitmap(BitmapFactory.decodeResource(resources, R.drawable.app_icon_start_point))))
            endMarker = tencentMap.addMarker(MarkerOptions(LatLng(endPoi.latitude, endPoi.longitude))
                .icon(BitmapDescriptorFactory.fromBitmap(BitmapFactory.decodeResource(resources, R.drawable.app_icon_end_point))))
        }
        val builder = LatLngBounds.Builder()
        plan?.routeDatas?.forEachIndexed { index, routeData ->
            val pts = routeData.routePoints ?: return@forEachIndexed
            if (pts.isEmpty()) return@forEachIndexed

            var zIndex = 100
            val indexes = intArrayOf(0, pts.size)
            var main = if (index == selectIndex) 0xFF00CC66.toInt() else 0xFFAFDBC7.toInt()
            var stroke = if (index == selectIndex) 0xFF009449.toInt() else 0xFF8BB8A3.toInt()
            var colors = intArrayOf(main, main)
            var borderColors = intArrayOf(stroke, stroke)
            if (index == selectIndex) {
                zIndex = 200
            }
            builder.include(pts)
            polylineMap[index] = tencentMap.addPolyline(
                PolylineOptions().addAll(pts).arrow(true).width(32f).borderWidth(4f)
                    .zIndex(zIndex).color(Color.GREEN)
                    .colors(colors, indexes).borderColors(borderColors)
                    .level(OverlayLevel.OverlayLevelAboveBuildings).clickable(true)
            )
        }
        val dp20 = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 20f,
            NavigatorContext.share().applicationContext.resources.displayMetrics).toInt()
        tencentMap.moveCamera(CameraUpdateFactory.newLatLngBoundsRect(builder.build(), dp20, dp20, dp20 + itemView1.height, dp20))
    }
}

// — 对齐 Demo RouteItemView —

class TencentRouteItemView(context: Context, attrs: AttributeSet?) : LinearLayout(context, attrs) {

    private val index: android.widget.TextView
    private val name: android.widget.TextView

    init {
        LayoutInflater.from(context).inflate(R.layout.view_route_item, this)
        index = findViewById(R.id.route_index)
        name = findViewById(R.id.route_name)
    }

    fun update(data: NavRoute, tag: String) {
        index.text = tag
        if (data is NavDriveRoute) {
            name.text = "全长 ${distanceToString(data.distance)}"
        } else if (data is NavNonMotorRoute) {
            name.text = "全长 ${distanceToString(data.distance)}"
        }
    }

    fun setSelect(selected: Boolean) {
        if (selected) {
            findViewById<View>(R.id.route_group).setBackgroundResource(R.drawable.app_bg_focus_r18)
            index.setTextColor(Color.parseColor("#FFFFFF"))
            name.setTextColor(Color.parseColor("#FFFFFF"))
        } else {
            findViewById<View>(R.id.route_group).background = null
            index.setTextColor(Color.parseColor("#333333"))
            name.setTextColor(Color.parseColor("#333333"))
        }
    }

    private fun distanceToString(distance: Int): String {
        if (distance < 1000) return "${distance}米"
        var disStr = String.format(Locale.getDefault(), "%.1f", distance / 1000.0)
        if (disStr.endsWith(".0") || distance > 1000000) disStr = disStr.substring(0, disStr.length - 2)
        return "${disStr}公里"
    }
}
