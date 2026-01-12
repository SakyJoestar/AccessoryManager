package com.example.accessoriesManager.form

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.accesorymanager.R
import com.example.accesorymanager.databinding.FragmentFormBaseBinding
import com.example.accessoriesManager.viewmodel.HeadquarterFormViewModel
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class HeadquarterFormFragment : Fragment(R.layout.fragment_form_base) {

    private var _binding: FragmentFormBaseBinding? = null
    private val binding get() = _binding!!

    private val viewModel: HeadquarterFormViewModel by viewModels()

    private var editId: String? = null

    companion object {
        private const val ARG_ID = "hqId"
        private const val COLLECTION = "headquarters"
        private const val FIELD_NAME = "name"
        private const val FIELD_INCREMENT = "increment"
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        _binding = FragmentFormBaseBinding.bind(view)

        // Inflar campos específicos
        layoutInflater.inflate(
            R.layout.form_headquarter_fields,
            binding.formFieldsContainer,
            true
        )

        val etName = binding.formFieldsContainer.findViewById<TextInputEditText>(R.id.etName)
        val etIncrement = binding.formFieldsContainer.findViewById<TextInputEditText>(R.id.etIncrement)

        // Defaults
        etIncrement.setText("0")

        // ¿Edición?
        editId = arguments?.getString(ARG_ID)
        val isEditMode = !editId.isNullOrBlank()

        setTitles(isEditMode)

        if (isEditMode) {
            loadFromFirestore(editId!!, etName, etIncrement)
            binding.btnSave.text = "Actualizar"
        } else {
            binding.btnSave.text = "Guardar"
        }

        val normalText = binding.btnSave.text

        binding.btnSave.setOnClickListener {
            etName.error = null

            // Si estás editando, idealmente deberías tener viewModel.update(...)
            // Como tu VM ya tiene save(...), aquí te dejo dos caminos:
            // 1) Si tu VM soporta actualizar por id, úsalo.
            // 2) Si no, actualizamos directo con Firestore (simple y funciona).

            val name = etName.text?.toString().orEmpty()
            val incRaw = etIncrement.text?.toString()

            if (isEditMode) {
                updateInFirestore(
                    id = editId!!,
                    nameRaw = name,
                    incrementRaw = incRaw,
                    onOk = {
                        hideKeyboard()
                        Snackbar.make(view, "Sede actualizada correctamente ✅", Snackbar.LENGTH_SHORT).show()
                    },
                    onFail = { msg ->
                        Snackbar.make(view, msg, Snackbar.LENGTH_LONG).show()
                    }
                )
            } else {
                viewModel.save(
                    nameRaw = name,
                    incrementRaw = incRaw
                )
            }
        }

        // Estado VM (solo afecta el flujo de CREAR)
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    when (state) {
                        is HeadquarterFormViewModel.UiState.Idle -> {
                            binding.btnSave.isEnabled = true
                            binding.btnSave.text = normalText
                        }

                        is HeadquarterFormViewModel.UiState.Checking -> {
                            binding.btnSave.isEnabled = false
                            binding.btnSave.text = "Verificando..."
                        }

                        is HeadquarterFormViewModel.UiState.Saving -> {
                            binding.btnSave.isEnabled = false
                            binding.btnSave.text = "Guardando..."
                        }

                        is HeadquarterFormViewModel.UiState.NameError -> {
                            etName.error = state.msg
                            binding.btnSave.isEnabled = true
                            binding.btnSave.text = normalText
                        }

                        is HeadquarterFormViewModel.UiState.Success -> {
                            // Solo limpiar si era creación
                            if (!isEditMode) {
                                Snackbar.make(view, state.msg, Snackbar.LENGTH_SHORT).show()
                                hideKeyboard()

                                etName.setText("")
                                etIncrement.setText("0")
                                etName.requestFocus()
                            }

                            binding.btnSave.isEnabled = true
                            binding.btnSave.text = normalText
                        }

                        is HeadquarterFormViewModel.UiState.Error -> {
                            Snackbar.make(view, state.msg, Snackbar.LENGTH_LONG).show()
                            binding.btnSave.isEnabled = true
                            binding.btnSave.text = normalText
                        }
                    }
                }
            }
        }

        // Si tu VM ya expone form (opcional), lo dejamos por si lo usas
        viewLifecycleOwner.lifecycleScope.launchWhenStarted {
            viewModel.form.collect { hq ->
                hq?.let {
                    etName.setText(it.name)
                    etIncrement.setText(it.increment.toString())
                }
            }
        }
    }

    private fun setTitles(isEdit: Boolean) {
        binding.tvFormTitle.text = if (isEdit) "Editar sede" else "Nueva sede"
        (requireActivity() as? AppCompatActivity)?.supportActionBar?.title =
            if (isEdit) "Editar sede" else "Nueva sede"
    }

    private fun loadFromFirestore(
        id: String,
        etName: TextInputEditText,
        etIncrement: TextInputEditText
    ) {
        FirebaseFirestore.getInstance()
            .collection(COLLECTION)
            .document(id)
            .get()
            .addOnSuccessListener { doc ->
                if (!doc.exists()) {
                    Snackbar.make(requireView(), "No se encontró la sede", Snackbar.LENGTH_LONG).show()
                    return@addOnSuccessListener
                }

                val name = doc.getString(FIELD_NAME).orEmpty()
                val inc = (doc.getLong(FIELD_INCREMENT) ?: 0L).toString()

                etName.setText(name)
                etIncrement.setText(inc)
            }
            .addOnFailureListener {
                Snackbar.make(requireView(), "Error cargando sede", Snackbar.LENGTH_LONG).show()
            }
    }

    private fun updateInFirestore(
        id: String,
        nameRaw: String,
        incrementRaw: String?,
        onOk: () -> Unit,
        onFail: (String) -> Unit
    ) {
        val name = nameRaw.trim()
        if (name.isEmpty()) {
            onFail("El nombre no puede estar vacío")
            return
        }

        val inc = incrementRaw?.trim().takeUnless { it.isNullOrEmpty() } ?: "0"
        val incInt = inc.toIntOrNull() ?: 0

        binding.btnSave.isEnabled = false
        binding.btnSave.text = "Actualizando..."

        FirebaseFirestore.getInstance()
            .collection(COLLECTION)
            .document(id)
            .update(
                mapOf(
                    FIELD_NAME to name,
                    FIELD_INCREMENT to incInt
                )
            )
            .addOnSuccessListener {
                binding.btnSave.isEnabled = true
                binding.btnSave.text = "Actualizar"
                onOk()
            }
            .addOnFailureListener { e ->
                binding.btnSave.isEnabled = true
                binding.btnSave.text = "Actualizar"
                onFail("No se pudo actualizar: ${e.message ?: "error"}")
            }
    }

    private fun hideKeyboard() {
        val imm =
            requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        imm.hideSoftInputFromWindow(requireView().windowToken, 0)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
