package com.example.accessoriesmanager.form

import com.example.accessoriesmanager.model.InstalledAccessory

data class InstallationTotals(val total: Long, val paid: Long, val unpaid: Long)

object InstallationTotalsCalculator {

    fun parseIncrement(rawIncrementText: String): Long =
        rawIncrementText.replace(".", "").trim().toLongOrNull() ?: 0L

    fun billableAccessories(accessories: List<InstalledAccessory>): List<InstalledAccessory> =
        accessories.filter {
            val hasId = !it.accessoryId.isNullOrBlank()
            val hasName = !it.name.isNullOrBlank()
            (hasId || hasName) && it.price > 0L
        }

    fun compute(accessories: List<InstalledAccessory>, increment: Long): InstallationTotals {
        val billable = billableAccessories(accessories)
        val total = billable.sumOf { it.price + increment }
        val paid = billable.filter { it.isPaid }.sumOf { it.price + increment }
        val unpaid = total - paid
        return InstallationTotals(total, paid, unpaid)
    }

    fun formatMoneyDots(value: Long): String {
        val s = value.toString()
        val sb = StringBuilder()
        var count = 0
        for (i in s.length - 1 downTo 0) {
            sb.append(s[i])
            count++
            if (count == 3 && i != 0) {
                sb.append('.')
                count = 0
            }
        }
        return sb.reverse().toString()
    }
}
