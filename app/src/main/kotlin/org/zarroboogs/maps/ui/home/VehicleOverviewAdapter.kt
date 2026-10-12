package org.zarroboogs.maps.ui.home

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import org.zarroboogs.maps.R
import org.zarroboogs.maps.data.model.Profile

class VehicleOverviewAdapter(
    private val onItemClick: (Profile) -> Unit,
    private val onImageClick: () -> Unit,
    private val onAddClick: () -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_ITEM = 1
        private const val TYPE_FOOTER = 2
    }

    private var profiles = listOf<Profile>()
    private var selectedImageUri: Uri? = null
    private var vehicleCount: Int = 0
    private var totalSpent: Double = 0.0

    fun submitList(list: List<Profile>, imageUri: Uri?, count: Int, spent: Double) {
        profiles = list
        selectedImageUri = imageUri
        vehicleCount = count
        totalSpent = spent
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int = when {
        position == 0 -> TYPE_HEADER
        position <= profiles.size -> TYPE_ITEM
        else -> TYPE_FOOTER
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_HEADER -> {
                val view = inflater.inflate(R.layout.item_overview_header, parent, false)
                HeaderVH(view)
            }
            TYPE_ITEM -> {
                val view = inflater.inflate(R.layout.item_vehicle_card, parent, false)
                ItemVH(view)
            }
            TYPE_FOOTER -> {
                val view = inflater.inflate(R.layout.item_overview_footer, parent, false)
                FooterVH(view)
            }
            else -> throw IllegalArgumentException("Unknown viewType: $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is HeaderVH -> {
                if (selectedImageUri != null) {
                    try {
                        holder.ivVehicle.setImageURI(selectedImageUri)
                    } catch (_: Exception) {
                        holder.ivVehicle.setImageResource(R.mipmap.ic_profile_default_image_foreground)
                    }
                } else {
                    holder.ivVehicle.setImageResource(R.mipmap.ic_profile_default_image_foreground)
                }
                holder.ivVehicle.setOnClickListener { onImageClick() }
                holder.tvVehicleCount.text = vehicleCount.toString()
                holder.tvTotalSpent.text = if (totalSpent > 0)
                    String.format("%.0f", totalSpent) else "--"
            }
            is ItemVH -> {
                val p = profiles[position - 1]
                holder.tvName.text = p.name
                holder.tvType.text = "${p.type} · ${p.subType.label}"
                holder.tvPlate.text = p.plate.ifBlank { "${p.displacement}cc" }
                if (p.imageUri != null) {
                    try {
                        holder.ivThumb.setImageURI(p.imageUri)
                    } catch (_: Exception) {
                        holder.ivThumb.setImageResource(R.mipmap.ic_profile_default_image_foreground)
                    }
                } else {
                    holder.ivThumb.setImageResource(R.mipmap.ic_profile_default_image_foreground)
                }
                holder.itemView.setOnClickListener { onItemClick(p) }
            }
            is FooterVH -> {
                holder.btnAdd.setOnClickListener { onAddClick() }
            }
        }
    }

    override fun getItemCount(): Int = 1 + profiles.size + 1   // header + items + footer

    class HeaderVH(view: View) : RecyclerView.ViewHolder(view) {
        val ivVehicle: ImageView = view.findViewById(R.id.iv_overview_vehicle)
        val tvVehicleCount: TextView = view.findViewById(R.id.tv_vehicle_count)
        val tvTotalSpent: TextView = view.findViewById(R.id.tv_total_spent)
    }

    class ItemVH(view: View) : RecyclerView.ViewHolder(view) {
        val ivThumb: ImageView = view.findViewById(R.id.iv_thumb)
        val tvName: TextView = view.findViewById(R.id.tv_name)
        val tvType: TextView = view.findViewById(R.id.tv_type)
        val tvPlate: TextView = view.findViewById(R.id.tv_plate)
    }

    class FooterVH(view: View) : RecyclerView.ViewHolder(view) {
        val btnAdd: Button = view.findViewById(R.id.btn_add_profile)
    }
}
