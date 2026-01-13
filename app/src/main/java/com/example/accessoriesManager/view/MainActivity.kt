package com.example.accessoriesManager.view

import android.os.Bundle
import android.view.MenuItem
import android.view.View
import androidx.activity.addCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.example.accesorymanager.R
import com.example.accesorymanager.databinding.AppBarMainBinding
import com.example.accesorymanager.databinding.ActivityMainBinding
import com.example.accessoriesManager.ui.QuickMenu
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.AndroidEntryPoint
import androidx.core.content.edit

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private lateinit var binding: AppBarMainBinding
    private lateinit var content: ActivityMainBinding
    private lateinit var navController: NavController
    private lateinit var auth: FirebaseAuth

    private var quickMenuDialog: AlertDialog? = null

    private val rootDestinations = setOf(
        R.id.accessoriesFragment,
        R.id.headquartersFragment,
        R.id.recordsFragment,
        R.id.vehiclesFragment
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = AppBarMainBinding.inflate(layoutInflater)

        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        val isDark = prefs.getBoolean("dark_mode", false)

        AppCompatDelegate.setDefaultNightMode(
            if (isDark) AppCompatDelegate.MODE_NIGHT_YES
            else AppCompatDelegate.MODE_NIGHT_NO
        )

        setContentView(binding.root)

        content = binding.contentMain
        auth = FirebaseAuth.getInstance()

        setSupportActionBar(binding.toolbar)

        // ---------- NAV CONTROLLER ----------
        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController

        if (savedInstanceState == null) {
            val navGraph = navController.navInflater.inflate(R.navigation.nav_graph)
            navGraph.setStartDestination(
                if (auth.currentUser != null)
                    R.id.accessoriesFragment
                else
                    R.id.loginFragment
            )
            navController.graph = navGraph
        }

        setupActionBarWithNavController(navController)
        content.bottomNav.setupWithNavController(navController)

        // ---------- TOOLBAR ----------
        binding.btnTheme.setOnClickListener { toggleTheme() }

        binding.btnLogout.setOnClickListener {
            showLogoutDialog()
        }

        updateThemeButtonIcon()

        // ---------- FAB ----------
        content.fabAdd.setOnClickListener {

            if (quickMenuDialog?.isShowing == true) {
                quickMenuDialog?.dismiss()
                return@setOnClickListener
            }

            toggleFabToClose(true)

            quickMenuDialog = QuickMenu.showQuickMenu(
                activity = this,
                bottomBarHeightPx = content.bottomAppBar.height,
                onSedeClick = {
                    navController.navigate(R.id.headquarterFormFragment)
                }, onVehicleClick = {
                    navController.navigate(R.id.vehicleFormFragment)
                },
                onAccessoryClick = {
                    navController.navigate(R.id.accessoryFormFragment)
                },
                onInstallationClick = {
//                    navController.navigate(R.id.recordFormFragment)
                }
            ).apply {
                setOnDismissListener {
                    toggleFabToClose(false)
                    quickMenuDialog = null
                }
            }
        }

        // ---------- DESTINATION CHANGES ----------
        navController.addOnDestinationChangedListener { _, destination, _ ->

            quickMenuDialog?.dismiss()

            when (destination.id) {

                R.id.loginFragment -> {
                    hideMainUi()
                    binding.appBar.visibility = View.GONE
                    supportActionBar?.setDisplayHomeAsUpEnabled(false)
                }

                R.id.headquarterFormFragment -> {
                    hideMainUi()
                    binding.appBar.visibility = View.VISIBLE
                    supportActionBar?.title = "Nueva Sede"
                    supportActionBar?.setDisplayHomeAsUpEnabled(true)
                }

                R.id.vehicleFormFragment -> {
                    hideMainUi()
                    binding.appBar.visibility = View.VISIBLE
                    supportActionBar?.title = "Nuevo Vehiculo"
                    supportActionBar?.setDisplayHomeAsUpEnabled(true)
                }

                R.id.accessoryFormFragment -> {
                    hideMainUi()
                    binding.appBar.visibility = View.VISIBLE
                    supportActionBar?.title = "Nuevo Accesorio"
                    supportActionBar?.setDisplayHomeAsUpEnabled(true)
                }


                else -> {
                    binding.appBar.visibility = View.VISIBLE
                    content.bottomNav.visibility = View.VISIBLE
                    content.bottomAppBar.visibility = View.VISIBLE
                    content.fabAdd.visibility = View.VISIBLE

                    supportActionBar?.title = "Car Facility Tracker"
                    supportActionBar?.setDisplayHomeAsUpEnabled(
                        destination.id !in rootDestinations
                    )
                }
            }
        }

        // ---------- BACK FÍSICO ----------
        onBackPressedDispatcher.addCallback(this) {
            when (navController.currentDestination?.id) {
                R.id.loginFragment -> {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
                in rootDestinations -> showExitDialog()
                else -> navController.navigateUp()
            }
        }
    }

    // ---------- FLECHA TOOLBAR ----------
    override fun onSupportNavigateUp(): Boolean {
        return navController.navigateUp() || super.onSupportNavigateUp()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                navController.navigateUp()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    // ---------- THEME ----------
    private fun toggleTheme() {
        val current = AppCompatDelegate.getDefaultNightMode()
        val newMode =
            if (current == AppCompatDelegate.MODE_NIGHT_YES)
                AppCompatDelegate.MODE_NIGHT_NO
            else
                AppCompatDelegate.MODE_NIGHT_YES

        AppCompatDelegate.setDefaultNightMode(newMode)

        // ✅ Guardar preferencia
        getSharedPreferences("settings", MODE_PRIVATE)
            .edit {
                putBoolean("dark_mode", newMode == AppCompatDelegate.MODE_NIGHT_YES)
            }

        updateThemeButtonIcon()
    }


    private fun updateThemeButtonIcon() {
        val isDark = AppCompatDelegate.getDefaultNightMode() == AppCompatDelegate.MODE_NIGHT_YES
        binding.btnTheme.setImageResource(
            if (isDark) R.drawable.ic_dark_mode else R.drawable.ic_light_mode
        )
    }

    // ---------- UI HELPERS ----------
    private fun toggleFabToClose(isOpen: Boolean) {
        content.fabAdd.setImageResource(
            if (isOpen) R.drawable.ic_close else R.drawable.ic_add_24
        )
    }

    private fun hideMainUi() {
        content.bottomNav.visibility = View.GONE
        content.bottomAppBar.visibility = View.GONE
        content.fabAdd.visibility = View.GONE
    }

    private fun showExitDialog() {
        AlertDialog.Builder(this)
            .setTitle("Salir de la aplicación")
            .setMessage("¿Deseas cerrar la app?")
            .setPositiveButton("Sí") { _, _ -> finish() }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun showLogoutDialog() {
        AlertDialog.Builder(this)
            .setTitle("Cerrar sesión")
            .setMessage("¿Quieres cerrar sesión?")
            .setPositiveButton("Sí") { _, _ ->
                auth.signOut()
                navController.navigate(R.id.loginFragment)
            }
            .setNegativeButton("No", null)
            .show()
    }

}
