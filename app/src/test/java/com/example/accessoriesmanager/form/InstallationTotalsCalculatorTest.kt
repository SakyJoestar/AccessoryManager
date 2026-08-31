package com.example.accessoriesmanager.form

import com.example.accessoriesmanager.model.InstalledAccessory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InstallationTotalsCalculatorTest {

    @Test
    fun `parseIncrement strips thousands dots`() {
        assertEquals(12000L, InstallationTotalsCalculator.parseIncrement("12.000"))
    }

    @Test
    fun `parseIncrement blank or non numeric text returns zero`() {
        assertEquals(0L, InstallationTotalsCalculator.parseIncrement(""))
        assertEquals(0L, InstallationTotalsCalculator.parseIncrement("abc"))
    }

    @Test
    fun `billableAccessories keeps rows with an id or name and a positive price`() {
        val accessories = listOf(
            InstalledAccessory(accessoryId = "a1", name = "Alarma", price = 5000L),
            InstalledAccessory(accessoryId = null, name = "Sin id pero con nombre", price = 3000L),
            InstalledAccessory(accessoryId = "a2", name = null, price = 0L), // sin precio -> fuera
            InstalledAccessory(accessoryId = null, name = null, price = 1000L) // fila vacía -> fuera
        )

        val billable = InstallationTotalsCalculator.billableAccessories(accessories)

        assertEquals(2, billable.size)
        assertTrue(billable.any { it.name == "Alarma" })
        assertTrue(billable.any { it.name == "Sin id pero con nombre" })
    }

    @Test
    fun `compute sums price plus increment per accessory and splits paid unpaid`() {
        val accessories = listOf(
            InstalledAccessory(accessoryId = "a1", name = "Alarma", price = 5000L, isPaid = true),
            InstalledAccessory(accessoryId = "a2", name = "Polarizado", price = 3000L, isPaid = false)
        )

        val totals = InstallationTotalsCalculator.compute(accessories, increment = 1000L)

        // total = (5000+1000) + (3000+1000) = 10000
        assertEquals(10000L, totals.total)
        // paid = solo Alarma: 5000+1000
        assertEquals(6000L, totals.paid)
        // unpaid = total - paid
        assertEquals(4000L, totals.unpaid)
    }

    @Test
    fun `compute with no billable accessories returns all zeros`() {
        val totals = InstallationTotalsCalculator.compute(emptyList(), increment = 500L)

        assertEquals(0L, totals.total)
        assertEquals(0L, totals.paid)
        assertEquals(0L, totals.unpaid)
    }

    @Test
    fun `formatMoneyDots inserts a dot every three digits from the right`() {
        assertEquals("0", InstallationTotalsCalculator.formatMoneyDots(0L))
        assertEquals("999", InstallationTotalsCalculator.formatMoneyDots(999L))
        assertEquals("1.000", InstallationTotalsCalculator.formatMoneyDots(1000L))
        assertEquals("1.234.567", InstallationTotalsCalculator.formatMoneyDots(1234567L))
    }
}
