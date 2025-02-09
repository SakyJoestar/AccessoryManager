package com.example.accessoriesManager.view.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.accesorymanager.databinding.FragmentVehiclesBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class HeadquartersFragment:Fragment() {
    private lateinit var binding: FragmentVehiclesBinding

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
       binding = FragmentVehiclesBinding.inflate(layoutInflater)

        return binding.root
    }
}