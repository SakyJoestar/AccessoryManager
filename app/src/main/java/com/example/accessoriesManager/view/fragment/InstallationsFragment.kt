package com.example.accessoriesManager.view.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.accesorymanager.databinding.FragmentInstallationsBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class InstallationsFragment:Fragment() {
    private lateinit var binding: FragmentInstallationsBinding

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentInstallationsBinding.inflate(layoutInflater)

        return binding.root
    }

}