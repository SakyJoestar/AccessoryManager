package com.example.accessoriesManager.view.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.accesorymanager.R
import com.example.accesorymanager.databinding.FragmentAccessoriesBinding
import com.example.accessoriesManager.adapter.AccessoryAdapter
import com.example.accessoriesManager.ui.showSnack
import com.example.accessoriesManager.viewmodel.AccessoryViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class AccessoriesFragment : Fragment() {

    private var _binding: FragmentAccessoriesBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AccessoryViewModel by viewModels()
    private lateinit var adapter: AccessoryAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAccessoriesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = AccessoryAdapter(
            onView = { _ -> },
            onEdit = { accessory ->
                val bundle = Bundle().apply {
                    putString("accessoryId", accessory.id)
                }
                findNavController().navigate(
                    R.id.action_accessoriesFragment_to_accessoryFormFragment,
                    bundle
                )
            },
            onDelete = { accessory ->
                accessory.id.let { id ->
                    if (id.isNotBlank()) showDeleteDialog(id)
                }
            }
        )

        binding.recyclerAccessories.adapter = adapter
        binding.recyclerAccessories.setHasFixedSize(true)

        viewModel.startListening()

        viewLifecycleOwner.lifecycleScope.launchWhenStarted {
            viewModel.items.collect { list ->
                adapter.submitList(list)
            }
        }

        // (Opcional pero recomendado) mostrar errores
        viewLifecycleOwner.lifecycleScope.launchWhenStarted {
            viewModel.error.collect { msg ->
                msg?.let { showSnack(it) }
            }
        }
    }

    override fun onDestroyView() {
        viewModel.stopListening()
        _binding = null
        super.onDestroyView()
    }

    private fun showDeleteDialog(id: String) {
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Eliminar accesorio")
            .setMessage("¿Quieres eliminar el accesorio?")
            .setPositiveButton("Sí") { _, _ ->
                viewModel.delete(id)
                showDeletedMessage()
            }
            .setNegativeButton("No", null)
            .show()
    }

    private fun showDeletedMessage() {
        showSnack("Accesorio eliminado ✅")
    }
}
