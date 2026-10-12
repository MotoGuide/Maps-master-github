package org.zarroboogs.maps.ui.maps

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import org.zarroboogs.maps.MapsApplication
import org.zarroboogs.maps.R

interface OnQuickAccessSavedListener {
    fun onSaved(item: QuickAccessItem)
}

class QuickAccessConfigSheet(
    private val item: QuickAccessItem,
    private val onSavedListener: OnQuickAccessSavedListener
) : BottomSheetDialogFragment() {

    private var bookmarkListExpanded = false

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return BottomSheetDialog(requireContext(), R.style.DarkBottomSheetDialogTheme)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.sheet_quick_access_config, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<TextView>(R.id.config_icon).text = item.icon
        view.findViewById<TextView>(R.id.config_title).text = "设置「${item.name}」地址"

        val optionsTop = view.findViewById<LinearLayout>(R.id.config_options_top)
        val optionsBottom = view.findViewById<LinearLayout>(R.id.config_options_bottom)
        val bookmarkList = view.findViewById<LinearLayout>(R.id.config_bookmark_list)

        val configured = item.isConfigured()

        if (configured) {
            view.findViewById<TextView>(R.id.config_subtitle).visibility = View.GONE
            val addressCard = view.findViewById<LinearLayout>(R.id.config_address_card)
            addressCard.visibility = View.VISIBLE
            view.findViewById<TextView>(R.id.config_current_address).text = item.address

            optionsTop.addView(buildOptionCard("⭐", "更换地址（从收藏选）") {
                toggleBookmarkList(bookmarkList)
            })
            optionsBottom.addView(buildOptionCard("🔍", "更换地址（搜索）") {
                dismiss()
                onSavedListener.onSaved(item)
            })

            view.findViewById<TextView>(R.id.config_clear_btn).visibility = View.VISIBLE
            view.findViewById<TextView>(R.id.config_clear_btn).setOnClickListener {
                val cleared = item.copy(address = "", lat = 0.0, lng = 0.0)
                QuickAccessStorage.save(requireContext(), cleared)
                onSavedListener.onSaved(cleared)
                dismiss()
            }
        } else {
            optionsTop.addView(buildOptionCard("⭐", "从收藏地址中选择") {
                toggleBookmarkList(bookmarkList)
            })
            optionsBottom.addView(buildOptionCard("🔍", "搜索地址") {
                dismiss()
                onSavedListener.onSaved(item)
            })
        }
    }

    private fun toggleBookmarkList(container: LinearLayout) {
        if (bookmarkListExpanded) {
            container.visibility = View.GONE
            container.removeAllViews()
            bookmarkListExpanded = false
        } else {
            val bookmarks = MapsApplication.getDatabase().bookmarkDao().loadAll()
            if (bookmarks.isEmpty()) {
                Toast.makeText(context, "暂无收藏地址，请先添加收藏", Toast.LENGTH_SHORT).show()
                return
            }
            container.removeAllViews()
            bookmarks.forEach { bm ->
                container.addView(buildBookmarkItem(bm))
            }
            container.visibility = View.VISIBLE
            bookmarkListExpanded = true
        }
    }

    private fun buildBookmarkItem(bm: org.zarroboogs.maps.data.local.entity.Bookmark): View {
        val itemView = LayoutInflater.from(context)
            .inflate(R.layout.item_config_sub_option, null)
        itemView.findViewById<TextView>(R.id.sub_option_icon).text = "📍"
        val textView = itemView.findViewById<TextView>(R.id.sub_option_text)
        textView.text = "${bm.name}\n${bm.address ?: ""}"
        itemView.setOnClickListener {
            val updated = item.copy(
                address = bm.address ?: bm.name,
                lat = bm.latitude,
                lng = bm.longitude
            )
            QuickAccessStorage.save(requireContext(), updated)
            onSavedListener.onSaved(updated)
            dismiss()
        }
        return itemView
    }

    private fun buildOptionCard(icon: String, text: String, onClick: () -> Unit): View {
        val card = LayoutInflater.from(context)
            .inflate(R.layout.item_config_option, null)
        card.findViewById<TextView>(R.id.option_icon).text = icon
        card.findViewById<TextView>(R.id.option_text).text = text
        card.setOnClickListener { onClick() }
        return card
    }
}
