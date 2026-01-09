package com.example.accessoriesManager.view.fragment

import com.example.accessoriesManager.viewmodel.HeadquarterViewModel
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.example.accesorymanager.databinding.FragmentHeadquartersBinding
import com.example.accessoriesManager.adapter.HeadquarterAdapter
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class HeadquartersFragment : Fragment() {

    private var _binding: FragmentHeadquartersBinding? = null
    private val binding get() = _binding!!

    private val viewModel: HeadquarterViewModel by viewModels()
    private lateinit var adapter: HeadquarterAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHeadquartersBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = HeadquarterAdapter(
            onView = { _ -> },
            onEdit = { _ -> },
            onDelete = { hq -> hq.id?.let { viewModel.delete(it) } }
        )

        binding.recyclerHeadquarters.adapter = adapter
        binding.recyclerHeadquarters.setHasFixedSize(true)

        viewModel.startListening()

        viewLifecycleOwner.lifecycleScope.launchWhenStarted {
            viewModel.items.collect { list ->
                adapter.submitList(list)
            }
        }
    }

    override fun onDestroyView() {
        viewModel.stopListening()
        _binding = null
        super.onDestroyView()
    }
}
