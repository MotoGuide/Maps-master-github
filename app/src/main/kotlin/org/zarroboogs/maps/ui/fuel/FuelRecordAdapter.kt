package org.zarroboogs.maps.ui.fuel

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import org.zarroboogs.maps.R
import org.zarroboogs.maps.data.local.FuelCalculator
import org.zarroboogs.maps.data.local.entity.FuelRecord
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FuelRecordAdapter(
    private val records: List<FuelRecord>
) : RecyclerView.Adapter<FuelRecordAdapter.VH>() {

    private val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val expanded = mutableSetOf<Int>()

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvDate: TextView = view.findViewById(R.id.tv_date)
        val tvMileage: TextView = view.findViewById(R.id.tv_mileage)
        val tvPriceInfo: TextView = view.findViewById(R.id.tv_price_info)
        val tvAmount: TextView = view.findViewById(R.id.tv_amount)
        val llExpand: View = view.findViewById(R.id.ll_expand)
        val tvLiters: TextView = view.findViewById(R.id.tv_liters)
        val tvFuelPer100km: TextView = view.findViewById(R.id.tv_fuel_per_100km)
        val tvNote: TextView = view.findViewById(R.id.tv_note)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_fuel_record, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val r = records[position]

        holder.tvDate.text = dateFmt.format(Date(r.date))
        holder.tvMileage.text = String.format("%.0f km", r.mileage)
        holder.tvPriceInfo.text = String.format("单价 %.2f  优惠 %.2f", r.unitPrice, r.discount)
        holder.tvAmount.text = String.format("%.0f 元", r.totalPaid)

        // 查找上一条记录用于计算
        val prev = if (position + 1 < records.size) records[position + 1] else null
        val fuelPer100km = if (prev != null) FuelCalculator.calcFuelPer100km(r, prev) else null

        val isExpanded = expanded.contains(position)
        holder.llExpand.visibility = if (isExpanded) View.VISIBLE else View.GONE

        if (isExpanded) {
            holder.tvLiters.text = String.format("加油量: %.2f L", r.liters)
            if (fuelPer100km != null) {
                holder.tvFuelPer100km.text = String.format("百公里油耗: %.1f L/100KM", fuelPer100km)
                holder.tvFuelPer100km.visibility = View.VISIBLE
            } else {
                holder.tvFuelPer100km.visibility = View.GONE
            }
            if (!r.note.isNullOrBlank()) {
                holder.tvNote.text = r.note
                holder.tvNote.visibility = View.VISIBLE
            } else {
                holder.tvNote.visibility = View.GONE
            }
        }

        holder.itemView.setOnClickListener {
            if (expanded.contains(position)) {
                expanded.remove(position)
            } else {
                expanded.add(position)
            }
            notifyItemChanged(position)
        }
    }

    override fun getItemCount(): Int = records.size
}
