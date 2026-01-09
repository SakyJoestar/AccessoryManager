package com.example.accessoriesManager.form

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.accesorymanager.R
import com.example.accesorymanager.databinding.FragmentFormBaseBinding
import com.example.accessoriesManager.viewmodel.HeadquarterFormViewModel
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class HeadquarterFormFragment : Fragment(R.layout.fragment_form_base) {

    private lateinit var binding: FragmentFormBaseBinding
    private val viewModel: HeadquarterFormViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding = FragmentFormBaseBinding.bind(view)

        binding.tvFormTitle.text = "Nueva sede"

        layoutInflater.inflate(
            R.layout.form_headquarter_fields,
            binding.formFieldsContainer,
            true
        )

        val etName = binding.formFieldsContainer.findViewById<TextInputEditText>(R.id.etName)
        val etIncrement = binding.formFieldsContainer.findViewById<TextInputEditText>(R.id.etIncrement)

        etIncrement.setText("0")

        val normalText = binding.btnSave.text

        binding.btnSave.setOnClickListener {
            etName.error = null
            viewModel.save(
                nameRaw = etName.text?.toString().orEmpty(),
                incrementRaw = etIncrement.text?.toString()
            )
        }

        // ✅ Observar estado del VM y actualizar UI
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
                            Snackbar.make(view, state.msg, Snackbar.LENGTH_SHORT).show()
                            etName.setText("")
                            etIncrement.setText("0")
                            etName.requestFocus()

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
    }
}
