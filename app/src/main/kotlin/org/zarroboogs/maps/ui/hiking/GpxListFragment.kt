package org.zarroboogs.maps.ui.hiking

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.zarroboogs.maps.R
import org.zarroboogs.maps.hiking.gpx.GpxRepository
import org.zarroboogs.maps.hiking.model.GpxTrack
import org.zarroboogs.maps.hiking.room.GpxDatabase

/**
 * GPX/KML 轨迹列表 Fragment。
 *
 * @param onTrackNavigate 用户选择"导航"后的回调
 * @param onTrackSimulate 用户选择"模拟导航"后的回调
 */
class GpxListFragment(
    private val onTrackNavigate: (GpxTrack) -> Unit = {},
    private val onTrackSimulate: (GpxTrack) -> Unit = {}
) : Fragment() {

    companion object { const val TAG = "Hiking" }

    private lateinit var recyclerView: RecyclerView
    private lateinit var repository: GpxRepository
    private var tracks: List<GpxTrack> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_gpx_list, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d(TAG, "[LIST] onViewCreated")
        recyclerView = view.findViewById(R.id.gpx_list_recycler)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        val db = GpxDatabase.getInstance(requireContext())
        repository = GpxRepository(db.gpxDao())

        lifecycleScope.launch {
            db.gpxDao().getAllTracks().collect { entities ->
                Log.d(TAG, "[LIST] DB updated: ${entities.size} tracks")
                loadTracks(entities)
            }
        }

        view.findViewById<View>(R.id.gpx_btn_import)?.setOnClickListener {
            Log.i(TAG, "[LIST] import button clicked")
            startActivity(Intent(requireContext(), GpxImportActivity::class.java))
        }
    }

    private fun loadTracks(entities: List<org.zarroboogs.maps.hiking.room.GpxTrackEntity>) {
        // Room 数据库操作必须在后台线程
        lifecycleScope.launch(Dispatchers.IO) {
            tracks = entities.mapNotNull { entity ->
                try {
                    repository.loadFullTrack(entity.id)
                } catch (e: Exception) {
                    Log.w(TAG, "[LIST] loadFullTrack failed for ${entity.id}: ${e.message}")
                    null
                }
            }
            Log.d(TAG, "[LIST] loaded ${tracks.size} full tracks")
            // Adapter 更新必须在主线程
            withContext(Dispatchers.Main) {
                recyclerView.adapter = GpxTrackAdapter(tracks) { track ->
                    showTrackActions(track)
                }
            }
        }
    }

    private fun showTrackActions(track: GpxTrack) {
        Log.i(TAG, "[LIST] trackActions: name=${track.name}, id=${track.id}")
        val options = arrayOf("导航", "模拟导航", "删除")
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle(track.name)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        Log.i(TAG, "[LIST] navigate selected: ${track.name}")
                        onTrackNavigate(track)
                    }
                    1 -> {
                        Log.i(TAG, "[LIST] simulate selected: ${track.name}")
                        onTrackSimulate(track)
                    }
                    2 -> {
                        Log.i(TAG, "[LIST] delete selected: ${track.name}")
                        // Room 阻塞调用必须在后台线程
                        lifecycleScope.launch(Dispatchers.IO) {
                            repository.deleteTrack(track.id)
                            Log.d(TAG, "[LIST] deleted")
                            withContext(Dispatchers.Main) {
                                Toast.makeText(requireContext(), "已删除: ${track.name}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            }
            .show()
    }
}

/**
 * GPX 轨迹列表 Adapter（内联简化版）。
 */
class GpxTrackAdapter(
    private val tracks: List<GpxTrack>,
    private val onClick: (GpxTrack) -> Unit
) : RecyclerView.Adapter<GpxTrackAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name: android.widget.TextView = view.findViewById(R.id.item_track_name)
        val info: android.widget.TextView = view.findViewById(R.id.item_track_info)
        val distance: android.widget.TextView = view.findViewById(R.id.item_track_distance)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_gpx_track, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val track = tracks[position]
        holder.name.text = track.name
        holder.info.text = "${track.waypointCount}点 · ${if (track.source == org.zarroboogs.maps.hiking.model.TrackSource.IMPORTED) "导入" else "录制"}"
        holder.distance.text = "%.1f km".format(track.totalDistanceM / 1000)
        holder.itemView.setOnClickListener { onClick(track) }
    }

    override fun getItemCount(): Int = tracks.size
}
