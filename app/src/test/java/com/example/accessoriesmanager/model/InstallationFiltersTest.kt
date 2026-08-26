package com.example.accessoriesmanager.model

import com.google.firebase.Timestamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date

private fun tsOf(date: LocalDate): Timestamp =
    Timestamp(Date.from(date.atStartOfDay(ZoneId.systemDefault()).toInstant()))

private fun installation(
    order: Int? = null,
    plate: String? = null,
    serie: String? = null,
    headquarter: Any? = null,
    vehicle: Any? = null,
    accessories: List<InstalledAccessory>? = null,
    state: String? = null,
    date: LocalDate? = LocalDate.of(2026, 3, 10)
) = Installation(
    order = order,
    plate = plate,
    serie = serie,
    headquarter = headquarter,
    vehicle = vehicle,
    accessories = accessories,
    state = state,
    date = date?.let { tsOf(it) }
)

class InstallationFiltersTest {

    // -------------------- matchesQuery --------------------

    @Test
    fun `matchesQuery with blank query always matches`() {
        assertTrue(installation().matchesQuery("   "))
    }

    @Test
    fun `matchesQuery matches by order plate or serie`() {
        val inst = installation(order = 1234, plate = "ABC123", serie = "SR001")

        assertTrue(inst.matchesQuery("1234"))
        assertTrue(inst.matchesQuery("abc123"))
        assertTrue(inst.matchesQuery("sr001"))
        assertFalse(inst.matchesQuery("no-match"))
    }

    @Test
    fun `matchesQuery matches by legacy headquarter and vehicle maps`() {
        val inst = installation(
            headquarter = mapOf("name" to "Sede Norte"),
            vehicle = mapOf("make" to "Toyota", "model" to "Hilux")
        )

        assertTrue(inst.matchesQuery("sede norte"))
        assertTrue(inst.matchesQuery("hilux"))
    }

    @Test
    fun `matchesQuery matches by accessory name or id`() {
        val inst = installation(
            accessories = listOf(InstalledAccessory(accessoryId = "acc-1", name = "Alarma"))
        )

        assertTrue(inst.matchesQuery("alarma"))
        assertTrue(inst.matchesQuery("acc-1"))
    }

    // -------------------- matchesStatus --------------------

    @Test
    fun `matchesStatus Todos always matches`() {
        assertTrue(installation(state = "PAGADO").matchesStatus("Todos"))
        assertTrue(installation(state = null).matchesStatus("Todos"))
    }

    @Test
    fun `matchesStatus recognizes legacy state spellings`() {
        assertTrue(installation(state = "PAID").matchesStatus("Pagado"))
        assertTrue(installation(state = "UNPAID").matchesStatus("No Pagado"))
        assertTrue(installation(state = "INCOMPLETE").matchesStatus("Parcial"))
        assertFalse(installation(state = "PAGADO").matchesStatus("Parcial"))
    }

    // -------------------- matchesDates --------------------

    @Test
    fun `matchesDates with no filters always matches`() {
        assertTrue(installation().matchesDates(null, null, null))
    }

    @Test
    fun `matchesDates exact filter requires the same day`() {
        val inst = installation(date = LocalDate.of(2026, 3, 10))

        assertTrue(inst.matchesDates(LocalDate.of(2026, 3, 10), null, null))
        assertFalse(inst.matchesDates(LocalDate.of(2026, 3, 11), null, null))
    }

    @Test
    fun `matchesDates range filter is inclusive on both ends`() {
        val inst = installation(date = LocalDate.of(2026, 3, 10))

        assertTrue(inst.matchesDates(null, LocalDate.of(2026, 3, 10), LocalDate.of(2026, 3, 10)))
        assertTrue(inst.matchesDates(null, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31)))
        assertFalse(inst.matchesDates(null, LocalDate.of(2026, 3, 11), null))
        assertFalse(inst.matchesDates(null, null, LocalDate.of(2026, 3, 9)))
    }

    @Test
    fun `matchesDates with a null installation date never matches an active filter`() {
        val inst = installation(date = null)
        assertFalse(inst.matchesDates(LocalDate.of(2026, 3, 10), null, null))
    }

    // -------------------- paid state helpers --------------------

    @Test
    fun `isPaidState isUnpaidState isPartialState recognize legacy spellings`() {
        assertTrue(installation(state = "paid").isPaidState())
        assertTrue(installation(state = "unpaid").isUnpaidState())
        assertTrue(installation(state = "incomplete").isPartialState())

        assertFalse(installation(state = "PARCIAL").isPaidState())
    }

    // -------------------- toLocalDate --------------------

    @Test
    fun `Timestamp toLocalDate round trips through the same zone`() {
        val date = LocalDate.of(2026, 8, 26)
        assertEquals(date, tsOf(date).toLocalDate())
    }

    @Test
    fun `null Timestamp toLocalDate returns null`() {
        val nullTimestamp: Timestamp? = null
        assertEquals(null, nullTimestamp.toLocalDate())
    }
}
