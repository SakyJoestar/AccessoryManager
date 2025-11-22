    package com.example.accessoriesManager.view.fragment

    import android.os.Bundle
    import android.util.Log
    import android.view.LayoutInflater
    import android.view.View
    import android.view.ViewGroup
    import android.widget.Toast
    import androidx.appcompat.app.AppCompatDelegate
    import androidx.fragment.app.Fragment
    import com.example.accesorymanager.R
    import com.example.accesorymanager.databinding.FragmentLoginBinding
    import com.google.firebase.auth.FirebaseAuth
    import dagger.hilt.android.AndroidEntryPoint
    import com.google.firebase.firestore.FirebaseFirestore
    import com.google.firebase.firestore.FieldValue
    import androidx.navigation.fragment.findNavController
    import android.content.Context

    @AndroidEntryPoint
    class LoginFragment : Fragment() {

        private var _binding: FragmentLoginBinding? = null
        private val binding get() = _binding!!
        private lateinit var firebaseAuth: FirebaseAuth
        private lateinit var firestore: FirebaseFirestore

        override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
        ): View {
            // 1. Aplicar tema guardado ANTES de inflar la vista
            val prefs = requireContext().getSharedPreferences("settings", Context.MODE_PRIVATE)
            val isDark = prefs.getBoolean("dark_mode", false)

            AppCompatDelegate.setDefaultNightMode(
                if (isDark) AppCompatDelegate.MODE_NIGHT_YES
                else AppCompatDelegate.MODE_NIGHT_NO
            )

            // 2. Inflar el layout normalmente
            _binding = FragmentLoginBinding.inflate(inflater, container, false)

            // 3. Configurar el switch (usa la función switchDarkMode que ya combinamos)
            switchDarkMode()

            return binding.root
        }

        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            super.onViewCreated(view, savedInstanceState)
            firebaseAuth = FirebaseAuth.getInstance()
            firestore = FirebaseFirestore.getInstance()

            switchDarkMode()
            setupAuthTabs()
            customizeGoogleButton()
            setupListeners()
        }

        override fun onDestroyView() {
            super.onDestroyView()
            _binding = null
        }

        /* ------------ MODO OSCURO ------------ */

        private fun enableDarkMode() {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        }

        private fun disableDarkMode() {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        }

        private fun switchDarkMode() {
            val swMode = binding.switchCompat

            // 1. SharedPreferences para guardar la preferencia
            val prefs = requireContext().getSharedPreferences("settings", Context.MODE_PRIVATE)

            // 2. Cargar la última preferencia guardada (false = claro por defecto)
            val isDarkSaved = prefs.getBoolean("dark_mode", false)

            // 3. Aplicar el tema guardado
            if (isDarkSaved) {
                enableDarkMode()
            } else {
                disableDarkMode()
            }

            // 4. Reflejarlo en el switch sin disparar el listener
            swMode.isChecked = isDarkSaved

            // 5. Listener del switch (tu lógica original + guardar preferencia)
            swMode.setOnCheckedChangeListener { _, isChecked ->
                // Guardar preferencia
                prefs.edit()
                    .putBoolean("dark_mode", isChecked)
                    .apply()

                // Aplicar tu lógica original
                if (isChecked) {
                    enableDarkMode()
                } else {
                    disableDarkMode()
                }
            }
        }

        /* ------------ TABS LOGIN / SIGNUP ------------ */

        private fun setupAuthTabs() {

            // Seleccionamos por defecto: LOGIN
            binding.authToggleGroup.check(binding.loginTabButton.id)

            // Textos iniciales para login
            updateGoogleTexts(isLogin = true)

            binding.authToggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
                if (!isChecked) return@addOnButtonCheckedListener

                when (checkedId) {

                    binding.loginTabButton.id -> {
                        binding.loginForm.visibility = View.VISIBLE
                        binding.signupForm.visibility = View.GONE
                        updateGoogleTexts(isLogin = true)   // 👈 cambia textos a “Inicia sesión…”
                    }

                    binding.signupTabButton.id -> {
                        binding.loginForm.visibility = View.GONE
                        binding.signupForm.visibility = View.VISIBLE
                        updateGoogleTexts(isLogin = false)  // 👈 cambia textos a “Regístrate…”
                    }
                }
            }
        }

        private fun updateGoogleTexts(isLogin: Boolean) {
            val btnText = if (isLogin) {
                getString(R.string.google_login)
            } else {
                getString(R.string.google_signup)
            }

            val tvText = if (isLogin) {
                getString(R.string.or_login_email)
            } else {
                getString(R.string.or_signup_email)
            }

            // Cambiar el TextView
            binding.orEmailTextView.text = tvText

            // Cambiar el texto del SignInButton
            for (i in 0 until binding.googleSignInButtonId.childCount) {
                val child = binding.googleSignInButtonId.getChildAt(i)
                if (child is android.widget.TextView) {
                    child.text = btnText
                    child.textSize = 14f
                }
            }
        }

        private fun customizeGoogleButton() {
            // Cambiar el texto interno del SignInButton
            for (i in 0 until binding.googleSignInButtonId.childCount) {
                val child = binding.googleSignInButtonId.getChildAt(i)
                if (child is android.widget.TextView) {
                    child.text = "Regístrate con Google"
                    child.textSize = 14f
                }
            }
        }

        /* ------------ LÓGICA FIREBASE ------------ */

        private fun login(email: String, password: String) {
            firebaseAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        Toast.makeText(
                            requireContext(),
                            "Inicio de sesión exitoso",
                            Toast.LENGTH_SHORT
                        ).show()

                        findNavController().navigate(
                            R.id.action_loginFragment_to_accessoriesFragment
                        )

                    } else {
                        Toast.makeText(
                            requireContext(),
                            task.exception?.localizedMessage ?: "Error al iniciar sesión",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
        }

        private fun register(name: String, email: String, password: String) {
            firebaseAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {

                        val user = firebaseAuth.currentUser
                        val uid = user?.uid

                        if (uid != null) {
                            // Objeto a guardar en Firestore
                            val userData = hashMapOf(
                                "name" to name,
                                "email" to email,
                                "createdAt" to FieldValue.serverTimestamp()
                            )

                            firestore.collection("users")
                                .document(uid)
                                .set(userData)
                                .addOnSuccessListener {
                                    Toast.makeText(
                                        requireContext(),
                                        "Registro exitoso",
                                        Toast.LENGTH_SHORT
                                    ).show()

                                    findNavController().navigate(
                                        R.id.action_loginFragment_to_accessoriesFragment
                                    )

                                }
                                .addOnFailureListener { e ->
                                    Toast.makeText(
                                        requireContext(),
                                        "Usuario creado, pero error guardando datos: ${e.localizedMessage}",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }

                        } else {
                            Toast.makeText(
                                requireContext(),
                                "No se pudo obtener el usuario actual",
                                Toast.LENGTH_SHORT
                            ).show()
                        }

                    } else {
                        Toast.makeText(
                            requireContext(),
                            task.exception?.localizedMessage ?: "Error al registrarse",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
        }

        /* ------------ LISTENERS LOGIN / REGISTER ------------ */

        private fun setupListeners() {
            // LOGIN
            binding.loginButton.setOnClickListener {
                val email = binding.emailEditText.text?.toString()?.trim().orEmpty()
                val password = binding.passwordEditText.text?.toString()?.trim().orEmpty()

                if (email.isBlank() || password.isBlank()) {
                    Toast.makeText(
                        requireContext(),
                        "Ingresa correo y contraseña",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    login(email, password)
                }
            }

            // REGISTER
            binding.signupButton.setOnClickListener {
                val name = binding.nameEditText.text?.toString()?.trim().orEmpty()
                val email = binding.signupEmailEditText.text?.toString()?.trim().orEmpty()
                val password = binding.signupPasswordEditText.text?.toString()?.trim().orEmpty()
                val confirm = binding.confirmPasswordEditText.text?.toString()?.trim().orEmpty()

                if (name.isBlank() || email.isBlank() || password.isBlank() || confirm.isBlank()) {
                    Toast.makeText(
                        requireContext(),
                        "Completa todos los campos",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@setOnClickListener
                }

                if (password.length < 6) {
                    Toast.makeText(
                        requireContext(),
                        "La contraseña debe tener al menos 6 caracteres",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@setOnClickListener
                }

                if (password != confirm) {
                    Toast.makeText(
                        requireContext(),
                        "Las contraseñas no coinciden",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@setOnClickListener
                }

                // 👇 ahora pasamos también el nombre
                register(name, email, password)
            }
        }
    }
