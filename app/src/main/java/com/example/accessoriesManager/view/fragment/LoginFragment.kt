package com.example.accessoriesManager.view.fragment

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import com.example.accesorymanager.R
import com.example.accesorymanager.databinding.FragmentLoginBinding
import com.example.accessoriesManager.model.AuthStatus
import com.example.accessoriesManager.viewmodel.AuthViewModel
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import androidx.core.content.edit

@AndroidEntryPoint
class LoginFragment : Fragment() {

    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!

    // ✅ MVVM
    private val authViewModel: AuthViewModel by viewModels()

    // 🔹 Google Sign-In (UI)
    private lateinit var googleSignInClient: GoogleSignInClient

    companion object {
        private const val TAG = "LoginFragment"
    }

    private val googleSignInLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                try {
                    val account = task.getResult(ApiException::class.java)
                    val idToken = account.idToken

                    if (idToken != null) {
                        // ✅ Se lo pasamos al ViewModel
                        authViewModel.loginWithGoogleToken(idToken)
                    } else {
                        Toast.makeText(requireContext(), "Token de Google inválido", Toast.LENGTH_SHORT).show()
                    }

                } catch (e: ApiException) {
                    Log.w(TAG, "Google sign in failed", e)
                    Toast.makeText(requireContext(), "Error al iniciar con Google", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(requireContext(), "Inicio con Google cancelado", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // 1) Aplicar tema guardado ANTES de inflar la vista (lo dejamos aquí como pediste)
        val prefs = requireContext().getSharedPreferences("settings", Context.MODE_PRIVATE)
        val isDark = prefs.getBoolean("dark_mode", false)

        AppCompatDelegate.setDefaultNightMode(
            if (isDark) AppCompatDelegate.MODE_NIGHT_YES
            else AppCompatDelegate.MODE_NIGHT_NO
        )

        // 2) Inflar layout
        _binding = FragmentLoginBinding.inflate(inflater, container, false)

        // 3) Configurar switch tema
        switchDarkMode()

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Google client
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()

        googleSignInClient = GoogleSignIn.getClient(requireActivity(), gso)

        customizeGoogleButton()
        switchDarkMode()
        setupAuthTabs()
        setupListeners()
        observeAuthState()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    /* ------------ OBSERVER MVVM ------------ */

    private fun observeAuthState() {
        viewLifecycleOwner.lifecycleScope.launchWhenStarted {
            authViewModel.status.collectLatest { status ->
                when (status) {
                    is AuthStatus.Idle -> Unit

                    is AuthStatus.Loading -> {
                        // opcional: mostrar loading (progress, deshabilitar botones, etc.)
                        // Ejemplo:
                        // binding.loginButton.isEnabled = false
                        // binding.signupButton.isEnabled = false
                    }

                    is AuthStatus.Success -> {
                        Toast.makeText(requireContext(), status.message, Toast.LENGTH_SHORT).show()
                        navigateToHome()
                        authViewModel.resetStatus()
                    }

                    is AuthStatus.Error -> {
                        Toast.makeText(requireContext(), status.message, Toast.LENGTH_LONG).show()
                        // binding.loginButton.isEnabled = true
                        // binding.signupButton.isEnabled = true
                    }
                }
            }
        }
    }

    /* ------------ MODO OSCURO (SE QUEDA EN FRAGMENT) ------------ */

    private fun enableDarkMode() {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
    }

    private fun disableDarkMode() {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
    }

    private fun switchDarkMode() {
        val swMode = binding.switchCompat

        val prefs = requireContext().getSharedPreferences("settings", Context.MODE_PRIVATE)
        val isDarkSaved = prefs.getBoolean("dark_mode", false)

        if (isDarkSaved) enableDarkMode() else disableDarkMode()

        swMode.isChecked = isDarkSaved

        swMode.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit { putBoolean("dark_mode", isChecked) }
            if (isChecked) enableDarkMode() else disableDarkMode()
        }
    }

    /* ------------ TABS LOGIN / SIGNUP ------------ */

    private fun setupAuthTabs() {
        binding.authToggleGroup.check(binding.loginTabButton.id)
        updateGoogleTexts(isLogin = true)

        binding.authToggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener

            when (checkedId) {
                binding.loginTabButton.id -> {
                    binding.loginForm.visibility = View.VISIBLE
                    binding.signupForm.visibility = View.GONE
                    updateGoogleTexts(isLogin = true)
                }

                binding.signupTabButton.id -> {
                    binding.loginForm.visibility = View.GONE
                    binding.signupForm.visibility = View.VISIBLE
                    updateGoogleTexts(isLogin = false)
                }
            }
        }
    }

    private fun updateGoogleTexts(isLogin: Boolean) {
        val btnText = if (isLogin) getString(R.string.google_login) else getString(R.string.google_signup)
        val tvText = if (isLogin) getString(R.string.or_login_email) else getString(R.string.or_signup_email)

        binding.orEmailTextView.text = tvText

        for (i in 0 until binding.googleSignInButtonId.childCount) {
            val child = binding.googleSignInButtonId.getChildAt(i)
            if (child is android.widget.TextView) {
                child.text = btnText
                child.textSize = 14f
            }
        }
    }

    private fun customizeGoogleButton() {
        for (i in 0 until binding.googleSignInButtonId.childCount) {
            val child = binding.googleSignInButtonId.getChildAt(i)
            if (child is android.widget.TextView) {
                child.textSize = 14f
                child.isAllCaps = false
            }
        }
    }

    /* ------------ LISTENERS (LLAMAN AL VIEWMODEL) ------------ */

    private fun signInWithGoogle() {
        googleSignInClient.signOut().addOnCompleteListener {
            googleSignInLauncher.launch(googleSignInClient.signInIntent)
        }
    }

    private fun setupListeners() {

        binding.googleSignInButtonId.setOnClickListener {
            signInWithGoogle()
        }

        binding.loginButton.setOnClickListener {
            val email = binding.emailEditText.text?.toString()?.trim().orEmpty()
            val password = binding.passwordEditText.text?.toString()?.trim().orEmpty()

            if (email.isBlank() || password.isBlank()) {
                Toast.makeText(requireContext(), "Ingresa correo y contraseña", Toast.LENGTH_SHORT).show()
            } else {
                authViewModel.login(email, password)
            }
        }

        binding.signupButton.setOnClickListener {
            val name = binding.nameEditText.text?.toString()?.trim().orEmpty()
            val email = binding.signupEmailEditText.text?.toString()?.trim().orEmpty()
            val password = binding.signupPasswordEditText.text?.toString()?.trim().orEmpty()
            val confirm = binding.confirmPasswordEditText.text?.toString()?.trim().orEmpty()

            if (name.isBlank() || email.isBlank() || password.isBlank() || confirm.isBlank()) {
                Toast.makeText(requireContext(), "Completa todos los campos", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (password.length < 6) {
                Toast.makeText(requireContext(), "La contraseña debe tener al menos 6 caracteres", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (password != confirm) {
                Toast.makeText(requireContext(), "Las contraseñas no coinciden", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            authViewModel.register(name, email, password)
        }
    }

    /* ------------ NAVIGATION ------------ */

    private fun navigateToHome() {
        val navOptions = NavOptions.Builder()
            .setPopUpTo(R.id.loginFragment, true)
            .build()

        // ✅ Ajusta aquí tu destino real
        findNavController().navigate(
            R.id.action_loginFragment_to_RecordFragments,
            null,
            navOptions
        )
    }
}
