package org.zarroboogs.maps.ui.hiking

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.zarroboogs.maps.R
import org.zarroboogs.maps.data.local.AppDatabase
import org.zarroboogs.maps.data.local.entity.Bookmark

/**
 * Tab3 收藏地点列表 Fragment。
 *
 * 读取现有 Bookmark 数据库，暗色卡片列表展示。
 * 通过 [onBookmarkClick] 回调通知容器。
 */
class BookmarkListFragment(
    private val onBookmarkClick: ((Bookmark) -> Unit)? = null
) : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyState: ViewGroup

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_bookmark_list, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        recyclerView = view.findViewById(R.id.bookmark_list_recycler)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        emptyState = view.findViewById(R.id.bookmark_empty_state) as ViewGroup

        lifecycleScope.launch(Dispatchers.IO) {
            val bookmarks = try {
                AppDatabase.getInstance(requireContext()).bookmarkDao().loadAll()
            } catch (e: Exception) {
                emptyList()
            }
            withContext(Dispatchers.Main) {
                if (bookmarks.isEmpty()) {
                    emptyState.visibility = View.VISIBLE
                    recyclerView.visibility = View.GONE
                } else {
                    emptyState.visibility = View.GONE
                    recyclerView.visibility = View.VISIBLE
                    recyclerView.adapter = BookmarkAdapter(bookmarks, onBookmarkClick)
                }
            }
        }
    }
}

/**
 * 收藏地点列表 Adapter。
 */
class BookmarkAdapter(
    private val bookmarks: List<Bookmark>,
    private val onClick: ((Bookmark) -> Unit)?
) : RecyclerView.Adapter<BookmarkAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.item_bookmark_name)
        val address: TextView = view.findViewById(R.id.item_bookmark_address)
        val coords: TextView = view.findViewById(R.id.item_bookmark_coords)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_bookmark, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val b = bookmarks[position]
        holder.name.text = b.name.ifEmpty { "未命名" }
        holder.address.text = b.address?.takeIf { it.isNotBlank() } ?: "暂无地址"
        holder.coords.text = "%.6f\n%.6f".format(b.latitude, b.longitude)
        holder.itemView.setOnClickListener { onClick?.invoke(b) }
    }

    override fun getItemCount(): Int = bookmarks.size
}
