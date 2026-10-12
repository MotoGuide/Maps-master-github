package org.zarroboogs.maps.data.local

import org.zarroboogs.maps.data.local.entity.FuelRecord
import kotlin.math.roundToInt

object FuelCalculator {

    /** 优惠单价 = unitPrice - discount */
    fun effectivePrice(unitPrice: Double, discount: Double): Double =
        (unitPrice - discount).coerceAtLeast(0.01)

    /** 加油量(L) = totalPaid / (unitPrice - discount) */
    fun calcLiters(totalPaid: Double, unitPrice: Double, discount: Double): Double {
        val price = effectivePrice(unitPrice, discount)
        return totalPaid / price
    }

    /** 百公里油耗 = liters / 里程差 × 100。需至少 2 笔记录（本次+上次） */
    fun calcFuelPer100km(current: FuelRecord, previous: FuelRecord): Double? {
        val diff = current.mileage - previous.mileage
        if (diff <= 0) return null
        return current.liters / diff * 100
    }

    /** 平均每公里单价 = (总实付 - 最近一次实付) / (最新里程 - 首次里程) */
    fun calcAvgCostPerKm(recordsAsc: List<FuelRecord>): Double? {
        if (recordsAsc.size < 2) return null
        val first = recordsAsc.first()
        val latest = recordsAsc.last()
        val totalPaidExcludingLatest = recordsAsc.sumOf { it.totalPaid } - latest.totalPaid
        val range = latest.mileage - first.mileage
        if (range <= 0) return null
        return totalPaidExcludingLatest / range
    }

    /** 总消费金额 */
    fun totalSpent(records: List<FuelRecord>): Double =
        records.sumOf { it.totalPaid }

    /** 最新一次可计算的百公里油耗（倒序查找，第一对相邻记录） */
    fun latestFuelPer100km(recordsAsc: List<FuelRecord>): Double? {
        if (recordsAsc.size < 2) return null
        for (i in recordsAsc.size - 1 downTo 1) {
            val result = calcFuelPer100km(recordsAsc[i], recordsAsc[i - 1])
            if (result != null) return result
        }
        return null
    }

    /** 格式化百公里油耗（完整带单位） */
    fun formatFuelPer100km(value: Double?): String =
        if (value != null) String.format("%.1f L/100KM", value) else "-- L/100KM"

    /** 百公里油耗纯数值 */
    fun formatFuelPer100kmValue(value: Double?): String =
        if (value != null) String.format("%.1f", value) else "--"

    /** 格式化每公里单价（完整带单位） */
    fun formatCostPerKm(value: Double?): String =
        if (value != null) String.format("%.2f 元/KM", value) else "-- 元/KM"

    /** 每公里单价纯数值 */
    fun formatCostPerKmValue(value: Double?): String =
        if (value != null) String.format("%.2f", value) else "--"

    /** 格式化总消费（完整带单位） */
    fun formatTotalSpent(value: Double): String =
        String.format("%.0f 元", value)

    /** 总消费纯数值 */
    fun formatTotalSpentValue(value: Double): String =
        String.format("%.0f", value)
}
