package com.example.accessoriesManager.form

import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.example.accesorymanager.R
import com.example.accessoriesManager.model.Headquarter
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText

class HeadquarterFormFragment : Fragment(R.layout.fragment_form_base) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<TextView>(R.id.tvFormTitle).text = "Nueva sede"

        val container = view.findViewById<LinearLayout>(R.id.formFieldsContainer)
        layoutInflater.inflate(R.layout.form_headquarter_fields, container, true)

        view.findViewById<MaterialButton>(R.id.btnSave).setOnClickListener {
            val name = view.findViewById<TextInputEditText>(R.id.etName).text.toString().trim()
            val incText = view.findViewById<TextInputEditText>(R.id.etIncrement).text.toString().trim()

            if (name.isEmpty()) {
                view.findViewById<TextInputEditText>(R.id.etName).error = "Obligatorio"
                return@setOnClickListener
            }
            if (incText.isEmpty()) {
                view.findViewById<TextInputEditText>(R.id.etIncrement).error = "Obligatorio"
                return@setOnClickListener
            }

            val headquarter = Headquarter(name = name, increment = incText.toInt())
            // guardar a Firestore...
        }
    }
}
