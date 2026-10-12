package org.zarroboogs.maps.ui.fuel

import android.app.DatePickerDialog
import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import org.zarroboogs.maps.R
import org.zarroboogs.maps.data.local.FuelCalculator
import org.zarroboogs.maps.data.local.entity.FuelRecord
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class DialogAddFuel : DialogFragment() {

    private var profileName: String = ""
    private var onSaved: ((FuelRecord) -> Unit)? = null

    private val cal = Calendar.getInstance()
    private val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    companion object {
        fun newInstance(profileName: String, onSaved: (FuelRecord) -> Unit): DialogAddFuel {
            val d = DialogAddFuel()
            d.profileName = profileName
            d.onSaved = onSaved
            return d
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val view = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_add_fuel, null, false)

        val tvDate = view.findViewById<TextView>(R.id.tv_fuel_date)
        val btnPickDate = view.findViewById<View>(R.id.btn_pick_date)
        val etMileage = view.findViewById<EditText>(R.id.et_mileage)
        val etUnitPrice = view.findViewById<EditText>(R.id.et_unit_price)
        val etDiscount = view.findViewById<EditText>(R.id.et_discount)
        val etTotalPaid = view.findViewById<EditText>(R.id.et_total_paid)
        val etNote = view.findViewById<EditText>(R.id.et_note)

        tvDate.text = dateFmt.format(cal.time)

        btnPickDate.setOnClickListener {
            DatePickerDialog(
                requireContext(),
                R.style.DarkDialogTheme,
                { _, y, m, d ->
                    cal.set(y, m, d)
                    tvDate.text = dateFmt.format(cal.time)
                },
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH)
            ).apply {
                datePicker.maxDate = System.currentTimeMillis()
            }.show()
        }

        return AlertDialog.Builder(requireContext(), R.style.DarkDialogTheme)
            .setTitle("记一笔")
            .setView(view)
            .setPositiveButton("保存") { _, _ ->
                val mileageStr = etMileage.text.toString().trim()
                val unitPriceStr = etUnitPrice.text.toString().trim()
                val discountStr = etDiscount.text.toString().trim()
                val totalPaidStr = etTotalPaid.text.toString().trim()

                if (mileageStr.isEmpty() || unitPriceStr.isEmpty() || totalPaidStr.isEmpty()) {
                    Toast.makeText(requireContext(), "请填写里程、单价和实付金额", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val mileage = mileageStr.toDoubleOrNull()
                val unitPrice = unitPriceStr.toDoubleOrNull()
                val discount = discountStr.toDoubleOrNull() ?: 0.0
                val totalPaid = totalPaidStr.toDoubleOrNull()

                if (mileage == null || unitPrice == null || totalPaid == null ||
                    mileage <= 0 || unitPrice <= 0 || totalPaid <= 0) {
                    Toast.makeText(requireContext(), "请输入有效的数字", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                if (discount >= unitPrice) {
                    Toast.makeText(requireContext(), "优惠金额不能大于等于单价", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val liters = FuelCalculator.calcLiters(totalPaid, unitPrice, discount)

                val record = FuelRecord(
                    profileName = profileName,
                    date = cal.timeInMillis,
                    mileage = mileage,
                    unitPrice = unitPrice,
                    discount = discount,
                    totalPaid = totalPaid,
                    liters = liters,
                    note = etNote.text.toString().trim().ifEmpty { null }
                )

                onSaved?.invoke(record)
            }
            .setNegativeButton("取消", null)
            .create()
    }
}
