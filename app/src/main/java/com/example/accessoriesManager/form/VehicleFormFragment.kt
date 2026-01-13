package com.example.accessoriesManager.form

import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.accesorymanager.R
import com.example.accesorymanager.databinding.FragmentFormBaseBinding
import com.example.accessoriesManager.ui.showSnack
import com.example.accessoriesManager.viewmodel.VehicleFormViewModel
import com.google.android.material.textfield.TextInputEditText
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class VehicleFormFragment : Fragment(R.layout.fragment_form_base) {

    private var _binding: FragmentFormBaseBinding? = null
    private val binding get() = _binding!!

    private val viewModel: VehicleFormViewModel by viewModels()

    private var editId: String? = null

    companion object {
        private const val ARG_ID = "vehicleId"
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        _binding = FragmentFormBaseBinding.bind(view)

        // Inflar campos específicos
        layoutInflater.inflate(
            R.layout.form_vehicle_fields,
            binding.formFieldsContainer,
            true
        )

        val etMake =
            binding.formFieldsContainer.findViewById<TextInputEditText>(R.id.etMake)
        val etModel =
            binding.formFieldsContainer.findViewById<TextInputEditText>(R.id.etModel)

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

        // Guardar / Actualizar
        binding.btnSave.setOnClickListener {
            etMake.error = null
            etModel.error = null

            val make = etMake.text?.toString().orEmpty()
            val model = etModel.text?.toString().orEmpty()

            viewModel.save(
                id = editId,
                makeRaw = make,
                modelRaw = model
            )
        }

        // Estado VM (crear + editar)
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    when (state) {
                        is VehicleFormViewModel.UiState.Idle -> {
                            binding.btnSave.isEnabled = true
                            binding.btnSave.text = normalText
                        }

                        is VehicleFormViewModel.UiState.Checking -> {
                            binding.btnSave.isEnabled = false
                            binding.btnSave.text = "Verificando..."
                        }

                        is VehicleFormViewModel.UiState.Saving -> {
                            binding.btnSave.isEnabled = false
                            binding.btnSave.text =
                                if (isEditMode) "Actualizando..." else "Guardando..."
                        }

                        is VehicleFormViewModel.UiState.MakeError -> {
                            etMake.error = state.msg
                            binding.btnSave.isEnabled = true
                            binding.btnSave.text = normalText
                        }

                        is VehicleFormViewModel.UiState.ModelError -> {
                            etModel.error = state.msg
                            binding.btnSave.isEnabled = true
                            binding.btnSave.text = normalText
                        }

                        is VehicleFormViewModel.UiState.Success -> {
                            showSnack(state.msg)
                            hideKeyboard()

                            // Solo limpiar si era creación
                            if (!isEditMode) {
                                etMake.setText("")
                                etModel.setText("")
                                etMake.requestFocus()
                            }

                            binding.btnSave.isEnabled = true
                            binding.btnSave.text = normalText
                        }

                        is VehicleFormViewModel.UiState.Error -> {
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
                viewModel.form.collect { vehicle ->
                    vehicle?.let {
                        etMake.setText(it.make)
                        etModel.setText(it.model)
                    }
                }
            }
        }
    }

    private fun setTitles(isEdit: Boolean) {
        binding.tvFormTitle.text = if (isEdit) "Editar vehículo" else "Nuevo vehículo"
        (requireActivity() as? AppCompatActivity)?.supportActionBar?.title =
            if (isEdit) "Editar vehículo" else "Nuevo vehículo"
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
