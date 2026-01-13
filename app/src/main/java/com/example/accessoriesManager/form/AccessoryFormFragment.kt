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
import com.example.accessoriesManager.ui.ThousandsSeparatorTextWatcher
import com.example.accessoriesManager.ui.showSnack
import com.example.accessoriesManager.viewmodel.AccessoryFormViewModel
import com.google.android.material.textfield.TextInputEditText
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class AccessoryFormFragment : Fragment(R.layout.fragment_form_base) {

    private var _binding: FragmentFormBaseBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AccessoryFormViewModel by viewModels()

    private var editId: String? = null

    companion object {
        private const val ARG_ID = "accessoryId"
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        _binding = FragmentFormBaseBinding.bind(view)

        // Inflar campos específicos
        layoutInflater.inflate(
            R.layout.form_accessory_fields,
            binding.formFieldsContainer,
            true
        )

        val etName =
            binding.formFieldsContainer.findViewById<TextInputEditText>(R.id.etName)
        val etPrice =
            binding.formFieldsContainer.findViewById<TextInputEditText>(R.id.etPrice)

        // ✅ Formatear precio con puntos de miles mientras escribe
        etPrice.addTextChangedListener(ThousandsSeparatorTextWatcher(etPrice))

        // Defaults
        etPrice.setText("0")

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
            etName.error = null
            etPrice.error = null

            val name = etName.text?.toString().orEmpty()

            // ✅ quitar puntos para mandar el número limpio al VM
            val priceRaw = etPrice.text
                ?.toString()
                ?.replace(".", "")

            viewModel.save(
                id = editId,
                nameRaw = name,
                priceRaw = priceRaw
            )
        }

        // Estado VM (crear + editar)
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    when (state) {
                        is AccessoryFormViewModel.UiState.Idle -> {
                            binding.btnSave.isEnabled = true
                            binding.btnSave.text = normalText
                        }

                        is AccessoryFormViewModel.UiState.Checking -> {
                            binding.btnSave.isEnabled = false
                            binding.btnSave.text = "Verificando..."
                        }

                        is AccessoryFormViewModel.UiState.Saving -> {
                            binding.btnSave.isEnabled = false
                            binding.btnSave.text =
                                if (isEditMode) "Actualizando..." else "Guardando..."
                        }

                        is AccessoryFormViewModel.UiState.NameError -> {
                            etName.error = state.msg
                            binding.btnSave.isEnabled = true
                            binding.btnSave.text = normalText
                        }

                        is AccessoryFormViewModel.UiState.PriceError -> {
                            etPrice.error = state.msg
                            binding.btnSave.isEnabled = true
                            binding.btnSave.text = normalText
                        }

                        is AccessoryFormViewModel.UiState.Success -> {
                            showSnack(state.msg)
                            hideKeyboard()

                            // Solo limpiar si era creación
                            if (!isEditMode) {
                                etName.setText("")
                                etPrice.setText("0")
                                etName.requestFocus()
                            }

                            binding.btnSave.isEnabled = true
                            binding.btnSave.text = normalText
                        }

                        is AccessoryFormViewModel.UiState.Error -> {
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
                viewModel.form.collect { accessory ->
                    accessory?.let {
                        etName.setText(it.name)
                        // ✅ setea el precio y el watcher lo formatea solo
                        etPrice.setText(it.price.toString())
                    }
                }
            }
        }
    }

    private fun setTitles(isEdit: Boolean) {
        binding.tvFormTitle.text = if (isEdit) "Editar accesorio" else "Nuevo accesorio"
        (requireActivity() as? AppCompatActivity)?.supportActionBar?.title =
            if (isEdit) "Editar accesorio" else "Nuevo accesorio"
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
