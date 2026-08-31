package com.example.accessoriesmanager.form

import com.example.accessoriesmanager.model.InstalledAccessory
import com.example.accessoriesmanager.viewmodel.InstallationFormViewModel
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.textfield.TextInputEditText

/**
 * Recomputes the installation form's totals (total/paid/unpaid) and payment-status toggle
 * from the current accessory list and increment. Exposes [isAutoToggling] so the form's
 * toggle-group listener can tell a programmatic [autoSetPaymentToggle] check apart from a
 * real user tap.
 */
class InstallationTotalsController(
    private val viewModel: InstallationFormViewModel,
    private val etIncrement: TextInputEditText,
    private val etTotalWorked: TextInputEditText,
    private val etPaid: TextInputEditText,
    private val etUnpaid: TextInputEditText,
    private val tgPayment: MaterialButtonToggleGroup,
    private val btnPaid: MaterialButton,
    private val btnNotPaid: MaterialButton,
    private val btnPartiallyPaid: MaterialButton,
) {
    var isAutoToggling = false
        private set

    /** Runs [updateTotalsUI] and [autoSetPaymentToggle] together, the common case. */
    fun update(accessories: List<InstalledAccessory>) {
        updateTotalsUI(accessories)
        autoSetPaymentToggle(accessories)
    }

    fun updateTotalsUI(accessories: List<InstalledAccessory>) {
        val increment = InstallationTotalsCalculator.parseIncrement(etIncrement.text?.toString().orEmpty())
        val totals = InstallationTotalsCalculator.compute(accessories, increment)

        setTextSafely(etTotalWorked, InstallationTotalsCalculator.formatMoneyDots(totals.total))
        setTextSafely(etPaid, InstallationTotalsCalculator.formatMoneyDots(totals.paid))
        setTextSafely(etUnpaid, InstallationTotalsCalculator.formatMoneyDots(totals.unpaid))
    }

    fun autoSetPaymentToggle(accessories: List<InstalledAccessory>) {
        val increment = InstallationTotalsCalculator.parseIncrement(etIncrement.text?.toString().orEmpty())
        val totals = InstallationTotalsCalculator.compute(accessories, increment)
        val hasBillableAccessories = InstallationTotalsCalculator.billableAccessories(accessories).isNotEmpty()

        val targetId = when {
            !hasBillableAccessories || totals.total <= 0L -> btnNotPaid.id
            totals.paid <= 0L -> btnNotPaid.id
            totals.unpaid <= 0L -> btnPaid.id
            else -> btnPartiallyPaid.id
        }

        if (tgPayment.checkedButtonId == targetId) return

        isAutoToggling = true
        tgPayment.check(targetId)
        isAutoToggling = false

        val state = when (targetId) {
            btnPaid.id -> "PAGADO"
            btnNotPaid.id -> "NO_PAGADO"
            btnPartiallyPaid.id -> "PARCIAL"
            else -> null
        }
        viewModel.setPaymentState(state)
    }

    private fun setTextSafely(et: TextInputEditText, value: String) {
        val current = et.text?.toString().orEmpty()
        if (current == value) return
        et.setText(value)
        et.setSelection(et.text?.length ?: 0)
    }
}
