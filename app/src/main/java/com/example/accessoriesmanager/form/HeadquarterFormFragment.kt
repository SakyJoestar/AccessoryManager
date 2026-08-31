package com.example.accessoriesmanager.form

import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.accessoriesmanager.R
import com.example.accessoriesmanager.databinding.FragmentFormBaseBinding
import com.example.accessoriesmanager.ui.FormUiState
import com.example.accessoriesmanager.ui.ThousandsSeparatorTextWatcher
import com.example.accessoriesmanager.ui.showSnack
import com.example.accessoriesmanager.viewmodel.HeadquarterFormViewModel
import com.google.android.material.textfield.TextInputEditText
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

        val etName =
            binding.formFieldsContainer.findViewById<TextInputEditText>(R.id.etName)
        val etIncrement =
            binding.formFieldsContainer.findViewById<TextInputEditText>(R.id.etIncrement)

        // ✅ Formatear incremento con puntos de miles mientras escribe (igual que precio)
        etIncrement.addTextChangedListener(ThousandsSeparatorTextWatcher(etIncrement))

        // Defaults
        etIncrement.setText("0")

        // ¿Edición?
        editId = arguments?.getString(ARG_ID)
        val isEditMode = !editId.isNullOrBlank()

        setTitles(isEditMode)
        binding.btnSave.text = if (isEditMode) "Actualizar" else "Guardar"

        // Cargar si es edición (desde VM)
        if (isEditMode) {
            viewModel.loadById(editId!!)
        }

        val normalText = binding.btnSave.text

        // Guardar / Actualizar (mismo flujo)
        binding.btnSave.setOnClickListener {
            etName.error = null
            etIncrement.error = null

            val name = etName.text?.toString().orEmpty()

            // ✅ quitar puntos para mandar el número limpio al VM
            val incRaw = etIncrement.text
                ?.toString()
                ?.replace(".", "")

            viewModel.save(
                id = editId,
                nameRaw = name,
                incrementRaw = incRaw
            )
        }

        // Estado VM (crear + editar)
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    when (state) {
                        is FormUiState.Idle -> {
                            binding.btnSave.isEnabled = true
                            binding.btnSave.text = normalText
                        }

                        is FormUiState.Checking -> {
                            binding.btnSave.isEnabled = false
                            binding.btnSave.text = "Verificando..."
                        }

                        is FormUiState.Saving -> {
                            binding.btnSave.isEnabled = false
                            binding.btnSave.text = if (isEditMode) "Actualizando..." else "Guardando..."
                        }

                        is FormUiState.FieldError -> {
                            when (state.field) {
                                "name" -> etName.error = state.msg
                                "increment" -> etIncrement.error = state.msg
                            }
                            binding.btnSave.isEnabled = true
                            binding.btnSave.text = normalText
                        }

                        is FormUiState.Success -> {
                            showSnack(state.msg)
                            hideKeyboard()

                            // Solo limpiar si era creación
                            if (!isEditMode) {
                                etName.setText("")
                                etIncrement.setText("0")
                                etName.requestFocus()
                            }

                            binding.btnSave.isEnabled = true
                            binding.btnSave.text = normalText
                        }

                        is FormUiState.Error -> {
                            showSnack(state.msg)
                            binding.btnSave.isEnabled = true
                            binding.btnSave.text = normalText
                        }
                    }
                }
            }
        }

        // Cuando cargue el form (modo edición), rellena inputs
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.form.collect { hq ->
                    hq?.let {
                        etName.setText(it.name)
                        // ✅ setea el incremento y el watcher lo formatea solo
                        etIncrement.setText(it.increment.toString())
                    }
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.closeScreen.collect {
                    findNavController().popBackStack()
                }
            }
        }
    }

    private fun setTitles(isEdit: Boolean) {
        binding.tvFormTitle.text = if (isEdit) "Editar sede" else "Nueva sede"
        (requireActivity() as? AppCompatActivity)?.supportActionBar?.title =
            if (isEdit) "Editar sede" else "Nueva sede"
    }

    private fun hideKeyboard() {
        val imm =
            requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(requireView().windowToken, 0)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
