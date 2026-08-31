package com.example.accessoriesmanager.form

import android.content.Context
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import androidx.fragment.app.Fragment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * Wires an [AutoCompleteTextView] to a reactive catalog: keeps its dropdown options in sync
 * with [Flow]<List<T>> updates, and applies [onSelected] (plus the usual dismiss-keyboard
 * housekeeping) when the user picks an entry. Used for the installation form's Sede/Vehículo
 * pickers; not a fit for a fixed, non-reactive list like Condición.
 */
class CatalogDropdownController<T>(
    private val fragment: Fragment,
    private val dropdown: AutoCompleteTextView,
    private val labelOf: (T) -> String,
    private val onSelected: (T) -> Unit,
) {
    private var current: List<T> = emptyList()

    fun observe(scope: CoroutineScope, items: Flow<List<T>>) {
        scope.launch {
            items.collect { list ->
                current = list
                dropdown.setAdapter(
                    ArrayAdapter(fragment.requireContext(), android.R.layout.simple_list_item_1, list.map(labelOf))
                )
            }
        }

        dropdown.setOnItemClickListener { _, _, idx, _ ->
            current.getOrNull(idx)?.let(onSelected)
            hideKeyboard()
            dropdown.clearFocus()
        }
    }

    private fun hideKeyboard() {
        val imm = fragment.requireContext()
            .getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(dropdown.windowToken, 0)
    }
}
