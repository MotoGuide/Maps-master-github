package org.zarroboogs.maps.ui.fuel

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import org.zarroboogs.maps.R
import org.zarroboogs.maps.data.local.AppDatabase
import org.zarroboogs.maps.data.local.FuelCalculator
import kotlin.concurrent.thread

class FuelRecordBottomSheet : BottomSheetDialogFragment() {

    companion object {
        private const val ARG_PROFILE_NAME = "profile_name"

        fun newInstance(profileName: String) = FuelRecordBottomSheet().apply {
            arguments = Bundle().apply { putString(ARG_PROFILE_NAME, profileName) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.DarkBottomSheetDialogTheme)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.bottom_sheet_fuel_records, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val profileName = arguments?.getString(ARG_PROFILE_NAME) ?: return

        val tvAvgFuel = view.findViewById<TextView>(R.id.tv_avg_fuel)
        val tvAvgCost = view.findViewById<TextView>(R.id.tv_avg_cost)
        val tvTotalSpent = view.findViewById<TextView>(R.id.tv_total_spent)
        val rv = view.findViewById<RecyclerView>(R.id.rv_records)

        rv.layoutManager = LinearLayoutManager(requireContext())

        thread {
            val dao = AppDatabase.getInstance(requireContext()).fuelRecordDao()
            val all = dao.loadByProfile(profileName)
            val asc = dao.loadByProfileAsc(profileName)
            activity?.runOnUiThread {
                tvAvgFuel.text = FuelCalculator.formatFuelPer100kmValue(
                    FuelCalculator.latestFuelPer100km(asc))
                tvAvgCost.text = FuelCalculator.formatCostPerKmValue(
                    FuelCalculator.calcAvgCostPerKm(asc))
                tvTotalSpent.text = FuelCalculator.formatTotalSpentValue(
                    FuelCalculator.totalSpent(all))
                rv.adapter = FuelRecordAdapter(all)
            }
        }
    }
}
