package org.zarroboogs.maps.ui.maps

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.widget.NestedScrollView
import org.zarroboogs.maps.MapsApplication
import org.zarroboogs.maps.R
import org.zarroboogs.maps.data.local.entity.SearchHistory

interface OnSearchItemClickListener {
    fun onItemClick(name: String, address: String?, lat: Double, lng: Double)
}

interface OnQuickAccessConfigListener {
    fun onConfig(item: QuickAccessItem)
}

interface OnQuickAccessActionListener {
    fun onAdd()
    fun onClearHistory()
}

class SearchPanelView(private val rootView: View) {

    private val panel: NestedScrollView = rootView.findViewById(R.id.search_panel_root)
    private val quickAccessContainer: LinearLayout = rootView.findViewById(R.id.quick_access_container)
    private val historyContainer: LinearLayout = rootView.findViewById(R.id.history_container)
    private val historyEmpty: TextView = rootView.findViewById(R.id.history_empty)
    private val bookmarkContainer: LinearLayout = rootView.findViewById(R.id.bookmark_container)
    private val bookmarkEmpty: TextView = rootView.findViewById(R.id.bookmark_empty)
    private val clearHistoryBtn: TextView = rootView.findViewById(R.id.clear_history_btn)

    var onItemClick: OnSearchItemClickListener? = null
    var onQuickAccessConfig: OnQuickAccessConfigListener? = null
    var onQuickAccessAction: OnQuickAccessActionListener? = null

    fun show() {
        refresh()
        panel.visibility = View.VISIBLE
    }

    fun hide() {
        panel.visibility = View.GONE
    }

    fun refresh() {
        val ctx = rootView.context
        buildQuickAccess(ctx)
        buildHistory(ctx)
        buildBookmarks(ctx)
    }

    private fun buildQuickAccess(ctx: Context) {
        quickAccessContainer.removeAllViews()
        val items = QuickAccessStorage.loadAll(ctx)

        items.forEach { item ->
            val iconView = LayoutInflater.from(ctx)
                .inflate(R.layout.item_quick_access, quickAccessContainer, false)
            iconView.findViewById<TextView>(R.id.quick_icon).text = item.icon
            iconView.findViewById<TextView>(R.id.quick_name).text = item.name
            iconView.setOnClickListener {
                if (item.isConfigured()) {
                    onItemClick?.onItemClick(item.name, item.address, item.lat, item.lng)
                } else {
                    onQuickAccessConfig?.onConfig(item)
                }
            }
            iconView.setOnLongClickListener {
                onQuickAccessConfig?.onConfig(item)
                true
            }
            quickAccessContainer.addView(iconView)
        }

        val addView = LayoutInflater.from(ctx)
            .inflate(R.layout.item_quick_access, quickAccessContainer, false)
        addView.findViewById<TextView>(R.id.quick_icon).text = "＋"
        addView.findViewById<TextView>(R.id.quick_name).text = "添加"
        addView.setOnClickListener { onQuickAccessAction?.onAdd() }
        quickAccessContainer.addView(addView)
    }

    private fun buildHistory(ctx: Context) {
        historyContainer.removeAllViews()
        val db = MapsApplication.getDatabase()
        val records = db.searchHistoryDao().loadRecent()

        if (records.isEmpty()) {
            historyEmpty.visibility = View.VISIBLE
            clearHistoryBtn.visibility = View.GONE
        } else {
            historyEmpty.visibility = View.GONE
            clearHistoryBtn.visibility = View.VISIBLE
            records.forEach { record ->
                historyContainer.addView(createRecordItem(ctx, record.name, record.address,
                    record.latitude, record.longitude))
            }
        }

        clearHistoryBtn.setOnClickListener {
            db.searchHistoryDao().deleteAll()
            buildHistory(ctx)
            onQuickAccessAction?.onClearHistory()
        }
    }

    private fun buildBookmarks(ctx: Context) {
        bookmarkContainer.removeAllViews()
        val db = MapsApplication.getDatabase()
        val bookmarks = db.bookmarkDao().loadAll()

        if (bookmarks.isEmpty()) {
            bookmarkEmpty.visibility = View.VISIBLE
        } else {
            bookmarkEmpty.visibility = View.GONE
            bookmarks.forEach { bm ->
                bookmarkContainer.addView(createRecordItem(ctx, bm.name, bm.address,
                    bm.latitude, bm.longitude))
            }
        }
    }

    private fun createRecordItem(ctx: Context, name: String, address: String?,
                                 lat: Double, lng: Double): View {
        val item = LayoutInflater.from(ctx)
            .inflate(R.layout.item_search_record, historyContainer, false)
        item.findViewById<TextView>(R.id.record_name).text = name
        val addrTv = item.findViewById<TextView>(R.id.record_address)
        if (!address.isNullOrBlank()) {
            addrTv.text = address
            addrTv.visibility = View.VISIBLE
        }
        item.setOnClickListener {
            onItemClick?.onItemClick(name, address, lat, lng)
        }
        return item
    }
}
