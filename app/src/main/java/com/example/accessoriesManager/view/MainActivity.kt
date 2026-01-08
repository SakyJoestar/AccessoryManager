package com.example.accessoriesManager.view

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.activity.addCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.findNavController
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.example.accesorymanager.R
import com.example.accesorymanager.databinding.AppBarMainBinding
import com.example.accessoriesManager.ui.common.showQuickMenu
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private lateinit var binding: AppBarMainBinding
    private lateinit var auth: FirebaseAuth

    private var showLogoutMenu = true
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
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        setSupportActionBar(binding.toolbar)

        // 👇 Esto apunta a las vistas del activity_main.xml incluido
        val content = binding.contentMain

        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController

        val navGraph = navController.navInflater.inflate(R.navigation.nav_graph)
        navGraph.setStartDestination(
            if (auth.currentUser != null) R.id.accessoriesFragment else R.id.loginFragment
        )
        navController.graph = navGraph

        setupActionBarWithNavController(navController)

        content.bottomNav.setupWithNavController(navController)

        content.fabAdd.setOnClickListener {
            if (quickMenuDialog?.isShowing == true) {
                quickMenuDialog?.dismiss()
                return@setOnClickListener
            }

            val nc = findNavController(R.id.nav_host_fragment)
            val bottomBarHeight = content.bottomAppBar.height

            toggleFabToClose(content, true)

            quickMenuDialog = showQuickMenu(
                activity = this,
                bottomBarHeightPx = bottomBarHeight,
                onSedeClick = { nc.navigate(R.id.headquarterFormFragment) }
            ).apply {
                setOnDismissListener {
                    toggleFabToClose(content, false)
                    quickMenuDialog = null
                }
            }
        }

        navController.addOnDestinationChangedListener { _, destination, _ ->

            val isAuthScreen = destination.id == R.id.loginFragment
            val isHeadquarterForm = destination.id == R.id.headquarterFormFragment

            quickMenuDialog?.dismiss()

            when {
                isAuthScreen -> {
                    content.bottomNav.visibility = View.GONE
                    binding.toolbar.visibility = View.GONE
                    content.fabAdd.visibility = View.GONE
                    content.bottomAppBar.visibility = View.GONE

                    showLogoutMenu = false
                    supportActionBar?.setDisplayHomeAsUpEnabled(false)
                }

                isHeadquarterForm -> {
                    binding.toolbar.visibility = View.VISIBLE
                    content.bottomNav.visibility = View.GONE
                    content.bottomAppBar.visibility = View.GONE
                    content.fabAdd.visibility = View.GONE

                    showLogoutMenu = true
                    supportActionBar?.title = "Nueva Sede"
                    supportActionBar?.setDisplayHomeAsUpEnabled(true)
                }

                else -> {
                    binding.toolbar.visibility = View.VISIBLE
                    content.bottomNav.visibility = View.VISIBLE
                    content.bottomAppBar.visibility = View.VISIBLE
                    content.fabAdd.visibility = View.VISIBLE

                    showLogoutMenu = true
                    supportActionBar?.title = "CFT"
                    supportActionBar?.setDisplayHomeAsUpEnabled(destination.id !in rootDestinations)
                }
            }

            invalidateOptionsMenu()
        }

        onBackPressedDispatcher.addCallback(this) {
            val currentDestId = navController.currentDestination?.id
            when {
                currentDestId == R.id.loginFragment -> {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
                currentDestId in rootDestinations -> showExitDialog()
                else -> navController.navigateUp()
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        if (!showLogoutMenu) return false
        menuInflater.inflate(R.menu.logout, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_logout -> {
                auth.signOut()
                val navController = (supportFragmentManager
                    .findFragmentById(R.id.nav_host_fragment) as NavHostFragment).navController
                navController.navigate(R.id.loginFragment)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = (supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment).navController
        return navController.navigateUp() || super.onSupportNavigateUp()
    }

    private fun showExitDialog() {
        AlertDialog.Builder(this)
            .setTitle("Salir de la aplicación")
            .setMessage("¿Deseas cerrar la app?")
            .setPositiveButton("Sí") { _, _ -> finish() }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun toggleFabToClose(content: com.example.accesorymanager.databinding.ActivityMainBinding, isOpen: Boolean) {
        content.fabAdd.setImageResource(if (isOpen) R.drawable.ic_close else R.drawable.ic_add_24)
    }
}
