package com.example.accessoriesManager.ui

import android.text.Editable
import android.text.TextWatcher
import com.google.android.material.textfield.TextInputEditText
import java.text.NumberFormat
import java.util.Locale

class ThousandsSeparatorTextWatcher(
    private val editText: TextInputEditText
) : TextWatcher {

    private var current = ""

    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

    override fun afterTextChanged(s: Editable?) {
        if (s.toString() == current) return

        editText.removeTextChangedListener(this)

        val cleanString = s.toString().replace(".", "")

        if (cleanString.isNotEmpty()) {
            val parsed = cleanString.toLongOrNull()
            if (parsed != null) {
                val formatted = NumberFormat
                    .getInstance(Locale("es", "CO"))
                    .format(parsed)

                current = formatted
                editText.setText(formatted)
                editText.setSelection(formatted.length)
            }
        } else {
            current = ""
        }

        editText.addTextChangedListener(this)
    }
}
