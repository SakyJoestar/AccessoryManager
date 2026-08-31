package com.example.accessoriesmanager.viewmodel

import com.example.accessoriesmanager.model.InstalledAccessory
import com.google.firebase.Timestamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InstallationFormValidatorTest {

    private val validDate = Timestamp(0, 0)
    private val validAccessories = listOf(
        InstalledAccessory(accessoryId = "a1", name = "Alarma", price = 5000L)
    )

    private fun validInput(
        order: Int? = 100,
        serie: String = "SR001",
        plate: String = "ABC123",
        warehouse: String = "12",
        date: Timestamp? = validDate,
        headquarterLabel: String? = "Sede Norte",
        vehicleLabel: String? = "Toyota - Hilux",
        accessories: List<InstalledAccessory> = validAccessories
    ) = InstallationFormInput(order, serie, plate, warehouse, date, headquarterLabel, vehicleLabel, accessories)

    private fun errorField(result: InstallationFormValidation): String {
        assertTrue("expected Invalid but was $result", result is InstallationFormValidation.Invalid)
        return (result as InstallationFormValidation.Invalid).error.field
    }

    @Test
    fun `valid input passes and normalizes serie plate and warehouse`() {
        val result = InstallationFormValidator.validate(
            validInput(serie = " sr001 ", plate = " abc123 ", warehouse = " 12 ")
        )

        assertTrue(result is InstallationFormValidation.Valid)
        val form = (result as InstallationFormValidation.Valid).form
        assertEquals("SR001", form.serie)
        assertEquals("ABC123", form.plate)
        assertEquals("12", form.warehouse)
        assertEquals(1, form.accessories.size)
    }

    @Test
    fun `requires at least order serie or plate`() {
        val result = InstallationFormValidator.validate(
            validInput(order = null, serie = "", plate = "")
        )
        assertEquals("order_serie_plate", errorField(result))
    }

    @Test
    fun `requires a date`() {
        val result = InstallationFormValidator.validate(validInput(date = null))
        assertEquals("date", errorField(result))
    }

    @Test
    fun `requires a headquarter`() {
        val result = InstallationFormValidator.validate(validInput(headquarterLabel = null))
        assertEquals("headquarter", errorField(result))
    }

    @Test
    fun `requires a vehicle`() {
        val result = InstallationFormValidator.validate(validInput(vehicleLabel = null))
        assertEquals("vehicle", errorField(result))
    }

    @Test
    fun `requires at least one billable accessory`() {
        val result = InstallationFormValidator.validate(validInput(accessories = emptyList()))
        assertEquals("accessories", errorField(result))
    }

    @Test
    fun `order longer than 7 characters is rejected`() {
        val result = InstallationFormValidator.validate(validInput(order = 123456789))
        assertEquals("order", errorField(result))
    }

    @Test
    fun `serie with invalid characters is rejected`() {
        val result = InstallationFormValidator.validate(validInput(serie = "abc-123"))
        assertEquals("serie", errorField(result))
    }

    @Test
    fun `plate must be exactly 6 alphanumeric characters`() {
        val result = InstallationFormValidator.validate(validInput(plate = "AB12"))
        assertEquals("plate", errorField(result))
    }

    @Test
    fun `warehouse must be numeric`() {
        val result = InstallationFormValidator.validate(validInput(warehouse = "12A"))
        assertEquals("warehouse", errorField(result))
    }

    @Test
    fun `blank warehouse is allowed`() {
        val result = InstallationFormValidator.validate(validInput(warehouse = "  "))
        assertTrue(result is InstallationFormValidation.Valid)
        assertEquals(null, (result as InstallationFormValidation.Valid).form.warehouse)
    }
}
