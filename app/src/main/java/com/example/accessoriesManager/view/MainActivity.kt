package com.example.accessoriesManager.view

import android.os.Bundle
import android.view.View
import androidx.activity.addCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.example.accesorymanager.R
import com.google.android.material.bottomnavigation.BottomNavigationView
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private lateinit var bottomNav: BottomNavigationView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController

        bottomNav = findViewById(R.id.bottom_nav)
        bottomNav.setupWithNavController(navController)

        // Mostrar / ocultar bottom nav según destino
        navController.addOnDestinationChangedListener { _, destination, _ ->
            when (destination.id) {
                R.id.loginFragment -> bottomNav.visibility = View.GONE // ocultar Bottom Nav en login
                else -> bottomNav.visibility = View.VISIBLE            // mostrar en otros fragments
            }
        }

        // 🔹 Manejo GLOBAL del botón atrás
        onBackPressedDispatcher.addCallback(this) {
            val currentDestId = navController.currentDestination?.id

            // Fragments raíz: los de la bottom bar
            val rootDestinations = setOf(
                R.id.accessoriesFragment,
                R.id.headquartersFragment,
                R.id.recordsFragment,
                R.id.vehiclesFragment
            )

            when {
                // En login: dejar comportamiento normal (cerrar activity/app)
                currentDestId == R.id.loginFragment -> {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }

                // En uno de los tabs principales: preguntar si quiere salir
                currentDestId in rootDestinations -> {
                    showExitDialog()
                }

                // En cualquier otro fragment: navegar hacia atrás normal
                else -> {
                    navController.navigateUp()
                }
            }
        }
    }

    private fun showExitDialog() {
        AlertDialog.Builder(this)
            .setTitle("Salir de la aplicación")
            .setMessage("¿Deseas cerrar la app?")
            .setPositiveButton("Sí") { _, _ ->
                finish()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
}