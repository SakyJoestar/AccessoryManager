package com.example.accessoriesmanager.model

import org.junit.Assert.assertEquals
import org.junit.Test

class InstallationLabelsTest {

    // -------------------- headquarterLabelFromAny --------------------

    @Test
    fun `headquarterLabelFromAny with null returns empty string`() {
        assertEquals("", headquarterLabelFromAny(null))
    }

    @Test
    fun `headquarterLabelFromAny with plain string trims it`() {
        assertEquals("Sede Norte", headquarterLabelFromAny("  Sede Norte  "))
    }

    @Test
    fun `headquarterLabelFromAny with legacy map reads the name field`() {
        val legacy = mapOf("name" to " Sede Sur ", "increment" to 1000L)
        assertEquals("Sede Sur", headquarterLabelFromAny(legacy))
    }

    @Test
    fun `headquarterLabelFromAny with map missing name returns empty string`() {
        assertEquals("", headquarterLabelFromAny(mapOf("increment" to 1000L)))
    }

    // -------------------- vehicleLabelFromAny --------------------

    @Test
    fun `vehicleLabelFromAny with null returns empty string`() {
        assertEquals("", vehicleLabelFromAny(null))
    }

    @Test
    fun `vehicleLabelFromAny with plain string trims it`() {
        assertEquals("Toyota Hilux", vehicleLabelFromAny("  Toyota Hilux  "))
    }

    @Test
    fun `vehicleLabelFromAny with legacy map joins make and model`() {
        val legacy = mapOf("make" to " Toyota ", "model" to " Hilux ")
        assertEquals("Toyota - Hilux", vehicleLabelFromAny(legacy))
    }

    @Test
    fun `vehicleLabelFromAny with only make in map skips the separator`() {
        val legacy = mapOf("make" to "Toyota", "model" to null)
        assertEquals("Toyota", vehicleLabelFromAny(legacy))
    }

    @Test
    fun `vehicleLabelFromAny with empty map returns empty string`() {
        assertEquals("", vehicleLabelFromAny(emptyMap<String, Any?>()))
    }
}
