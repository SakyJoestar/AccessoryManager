package com.example.accessoriesManager.form

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import com.example.accesorymanager.R
import com.example.accesorymanager.databinding.FragmentFormBaseBinding
import com.example.accessoriesManager.model.Headquarter
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.firestore.FirebaseFirestore

class HeadquarterFormFragment : Fragment(R.layout.fragment_form_base) {

    private val firestore by lazy { FirebaseFirestore.getInstance() }
    private lateinit var binding: FragmentFormBaseBinding

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding = FragmentFormBaseBinding.bind(view)

        binding.tvFormTitle.text = "Nueva sede"

        // Inflar campos específicos del formulario
        layoutInflater.inflate(
            R.layout.form_headquarter_fields,
            binding.formFieldsContainer,
            true
        )

        val etName =
            binding.formFieldsContainer.findViewById<TextInputEditText>(R.id.etName)
        val etIncrement =
            binding.formFieldsContainer.findViewById<TextInputEditText>(R.id.etIncrement)

        // Valor por defecto
        etIncrement.setText("0")

        binding.btnSave.setOnClickListener {

            etName.error = null

            val name = etName.text?.toString()?.trim().orEmpty()
            val incrementText = etIncrement.text?.toString()?.trim()
            val increment = if (incrementText.isNullOrEmpty()) 0 else incrementText.toInt()

            if (name.isEmpty()) {
                etName.error = "Obligatorio"
                return@setOnClickListener
            }

            // Deshabilitar botón
            binding.btnSave.isEnabled = false
            val oldText = binding.btnSave.text
            binding.btnSave.text = "Verificando..."

            // 🔍 Verificar si ya existe una sede con ese nombre
            firestore.collection("headquarters")
                .whereEqualTo("name", name)
                .limit(1)
                .get()
                .addOnSuccessListener { querySnapshot ->

                    if (!querySnapshot.isEmpty) {
                        // ❌ Ya existe
                        etName.error = "Ya existe una sede con ese nombre"
                        binding.btnSave.isEnabled = true
                        binding.btnSave.text = oldText
                        return@addOnSuccessListener
                    }

                    // ✅ No existe → guardar
                    binding.btnSave.text = "Guardando..."

                    val headquarter = Headquarter(
                        name = name,
                        increment = increment
                    )

                    firestore.collection("headquarters")
                        .add(headquarter)
                        .addOnSuccessListener {
                            Snackbar.make(view, "Sede guardada correctamente ✅", Snackbar.LENGTH_SHORT).show()

                            etName.setText("")
                            etIncrement.setText("0")
                            etName.requestFocus()

                            binding.btnSave.isEnabled = true
                            binding.btnSave.text = oldText
                        }
                        .addOnFailureListener {
                            Snackbar.make(view, "Error al guardar 😢", Snackbar.LENGTH_LONG).show()
                            binding.btnSave.isEnabled = true
                            binding.btnSave.text = oldText
                        }
                }
                .addOnFailureListener {
                    Snackbar.make(view, "Error al verificar duplicados 😢", Snackbar.LENGTH_LONG).show()
                    binding.btnSave.isEnabled = true
                    binding.btnSave.text = oldText
                }
        }
    }
}
