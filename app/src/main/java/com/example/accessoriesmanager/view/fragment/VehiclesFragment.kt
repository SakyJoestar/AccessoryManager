package com.example.accessoriesmanager.view.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.accessoriesmanager.R
import com.example.accessoriesmanager.databinding.FragmentVehiclesBinding
import com.example.accessoriesmanager.adapter.VehicleAdapter
import com.example.accessoriesmanager.ui.showSnack
import com.example.accessoriesmanager.viewmodel.VehicleViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class VehiclesFragment : Fragment() {

    private var _binding: FragmentVehiclesBinding? = null
    private val binding get() = _binding!!

    private val viewModel: VehicleViewModel by viewModels()
    private lateinit var adapter: VehicleAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentVehiclesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = VehicleAdapter(
            onView = { _ -> },
            onEdit = { vehicle ->
                val bundle = Bundle().apply {
                    putString("vehicleId", vehicle.id)
                }
                findNavController().navigate(
                    R.id.action_vehiclesFragment_to_vehicleFormFragment,
                    bundle
                )
            },
            onDelete = { vehicle ->
                vehicle.id?.let { id ->
                    showDeleteDialog(id)
                }
            }
        )

        binding.recyclerVehicles.adapter = adapter
        binding.recyclerVehicles.setHasFixedSize(true)

        viewModel.startListening()

        viewLifecycleOwner.lifecycleScope.launchWhenStarted {
            viewModel.items.collect { list ->
                adapter.submitList(list)
            }
        }

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
            .setTitle("Eliminar vehículo")
            .setMessage("¿Quieres eliminar el vehículo?")
            .setPositiveButton("Sí") { _, _ ->
                viewModel.delete(id)
                showDeletedMessage()
            }
            .setNegativeButton("No", null)
            .show()
    }

    private fun showDeletedMessage() {
        showSnack("Vehículo eliminado ✅")
    }
}
