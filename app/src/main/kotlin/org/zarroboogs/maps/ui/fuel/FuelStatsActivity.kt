package org.zarroboogs.maps.ui.fuel

import android.os.Bundle
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import org.zarroboogs.maps.R
import org.zarroboogs.maps.data.local.AppDatabase
import org.zarroboogs.maps.data.local.FuelCalculator
import org.zarroboogs.maps.ui.BaseActivity
import kotlin.concurrent.thread

class FuelStatsActivity : BaseActivity() {

    private lateinit var profileName: String
    private lateinit var dao: org.zarroboogs.maps.data.local.dao.FuelRecordDao

    private lateinit var tvAvgFuel: TextView
    private lateinit var tvAvgCost: TextView
    private lateinit var tvTotalSpent: TextView
    private lateinit var rv: RecyclerView
    private lateinit var fab: FloatingActionButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_fuel_stats)

        profileName = intent.getStringExtra(EXTRA_PROFILE_NAME) ?: ""
        dao = AppDatabase.getInstance(this).fuelRecordDao()

        tvAvgFuel = findViewById(R.id.tv_avg_fuel)
        tvAvgCost = findViewById(R.id.tv_avg_cost)
        tvTotalSpent = findViewById(R.id.tv_total_spent)
        rv = findViewById(R.id.rv_records)
        fab = findViewById(R.id.fab_add)

        findViewById<android.widget.ImageButton>(R.id.btn_back).setOnClickListener { finish() }

        rv.layoutManager = LinearLayoutManager(this)

        fab.setOnClickListener {
            DialogAddFuel.newInstance(profileName) { record ->
                thread {
                    dao.insert(record)
                    runOnUiThread { refresh() }
                }
            }.show(supportFragmentManager, "DialogAddFuel")
        }

        refresh()
    }

    private fun refresh() {
        thread {
            val all = dao.loadByProfile(profileName)
            val asc = dao.loadByProfileAsc(profileName)
            runOnUiThread {
                // 摘要
                val fuelPer100km = FuelCalculator.latestFuelPer100km(asc)
                val costPerKm = FuelCalculator.calcAvgCostPerKm(asc)
                val spent = FuelCalculator.totalSpent(all)

                tvAvgFuel.text = FuelCalculator.formatFuelPer100km(fuelPer100km)
                tvAvgCost.text = FuelCalculator.formatCostPerKm(costPerKm)
                tvTotalSpent.text = FuelCalculator.formatTotalSpent(spent)

                // 列表（DESC: 最新在前）
                rv.adapter = FuelRecordAdapter(all)
            }
        }
    }

    companion object {
        const val EXTRA_PROFILE_NAME = "profile_name"
    }
}
