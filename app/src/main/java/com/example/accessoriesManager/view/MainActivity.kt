package com.example.accessoriesManager.view

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.activity.addCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.navigation.findNavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.example.accesorymanager.R
import com.example.accessoriesManager.ui.common.showQuickMenu
import com.google.android.material.bottomappbar.BottomAppBar
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private lateinit var bottomNav: BottomNavigationView
    private lateinit var fab: FloatingActionButton
    private lateinit var bottomAppBar: BottomAppBar
    private lateinit var toolbar: Toolbar
    private lateinit var auth: FirebaseAuth   // 👈 auth global

    // Flag para saber si mostramos el menú (logout) o no
    private var showLogoutMenu: Boolean = true
    private var quickMenuDialog: AlertDialog? = null

    // Destinos raíz (los de la bottom bar, donde NO queremos flecha)
    private val rootDestinations = setOf(
        R.id.accessoriesFragment,
        R.id.headquartersFragment,
        R.id.recordsFragment,
        R.id.vehiclesFragment
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Layout con AppBar + include de activity_main
        setContentView(R.layout.app_bar_main)

        auth = FirebaseAuth.getInstance()   // 👈 inicializamos FirebaseAuth

        toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)

        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController

        // 👇 Configuramos el startDestination según si hay usuario logeado o no
        val navGraph = navController.navInflater.inflate(R.navigation.nav_graph)
        val currentUser = auth.currentUser

        navGraph.setStartDestination(
            if (currentUser != null) {
                // Ya está logeado → ir directo a Accessories
                R.id.accessoriesFragment
            } else {
                // No hay sesión → ir al login
                R.id.loginFragment
            }
        )

        navController.graph = navGraph

        setupActionBarWithNavController(navController)

        bottomNav = findViewById(R.id.bottom_nav)
        bottomNav.setupWithNavController(navController)

        val fab = findViewById<FloatingActionButton>(R.id.fab_add)

        fab.setOnClickListener {

            // si ya está abierto, ciérralo
            if (quickMenuDialog?.isShowing == true) {
                quickMenuDialog?.dismiss()
                return@setOnClickListener
            }

            val navController = findNavController(R.id.nav_host_fragment)

            val bottomBar = findViewById<View>(R.id.bottom_app_bar) // o tu BottomNavigationView id
            val bottomBarHeight = bottomBar.height

            toggleFabToClose(true)

            quickMenuDialog = showQuickMenu(
                activity = this,
                bottomBarHeightPx = bottomBarHeight,
                onSedeClick = { navController.navigate(R.id.headquarterFormFragment) }
            ).apply {
                setOnDismissListener {
                    toggleFabToClose(false)
                    quickMenuDialog = null
                }
            }
        }

        bottomAppBar = findViewById(R.id.bottom_app_bar)

        // Mostrar / ocultar Toolbar, BottomNav, menú y flecha según destino
        navController.addOnDestinationChangedListener { _, destination, _ ->

            val isAuthScreen = destination.id == R.id.loginFragment

            if (isAuthScreen) {
                // Login: sin toolbar, sin bottomNav, sin menú, sin flecha
                bottomNav.visibility = View.GONE
                toolbar.visibility = View.GONE
                fab.visibility = View.GONE
                bottomAppBar.visibility = View.GONE
                showLogoutMenu = false
                supportActionBar?.setDisplayHomeAsUpEnabled(false)
            } else {
                // Resto de pantallas: toolbar y bottomNav visibles
                bottomNav.visibility = View.VISIBLE
                toolbar.visibility = View.VISIBLE
                fab.visibility = View.VISIBLE
                bottomAppBar.visibility = View.VISIBLE
                showLogoutMenu = true

                navController.addOnDestinationChangedListener { _, destination, _ ->

                    val isAuthScreen = destination.id == R.id.loginFragment
                    val isHeadquarterForm = destination.id == R.id.headquarterFormFragment

                    // Si cambias de pantalla, cierra el quick menu si está abierto
                    quickMenuDialog?.dismiss()

                    if (isAuthScreen) {
                        bottomNav.visibility = View.GONE
                        toolbar.visibility = View.GONE
                        fab.visibility = View.GONE
                        bottomAppBar.visibility = View.GONE
                        showLogoutMenu = false
                        supportActionBar?.setDisplayHomeAsUpEnabled(false)

                    } else {
                        toolbar.visibility = View.VISIBLE
                        showLogoutMenu = true

                        when {
                            isHeadquarterForm -> {
                                // ✅ FORM SEDE
                                bottomNav.visibility = View.GONE
                                bottomAppBar.visibility = View.GONE
                                fab.visibility = View.GONE

                                supportActionBar?.title = "Nueva Sede"
                                supportActionBar?.setDisplayHomeAsUpEnabled(true)
                            }

                            else -> {
                                // ✅ Pantallas normales (tabs y otras)
                                bottomNav.visibility = View.VISIBLE
                                bottomAppBar.visibility = View.VISIBLE
                                fab.visibility = View.VISIBLE

                                supportActionBar?.title = "CFT"

                                if (destination.id in rootDestinations) {
                                    supportActionBar?.setDisplayHomeAsUpEnabled(false)
                                } else {
                                    supportActionBar?.setDisplayHomeAsUpEnabled(true)
                                }
                            }
                        }
                    }

                    invalidateOptionsMenu()
                }

                // Si es un destino raíz (tabs): NO flecha
                if (destination.id in rootDestinations) {
                    supportActionBar?.setDisplayHomeAsUpEnabled(false)
                } else {
                    // Pantallas internas: SÍ flecha
                    supportActionBar?.setDisplayHomeAsUpEnabled(true)
                }
            }

            // Forzar recreación del menú cuando cambia de destino
            invalidateOptionsMenu()
        }

        // 🔹 Manejo GLOBAL del botón atrás
        onBackPressedDispatcher.addCallback(this) {
            val currentDestId = navController.currentDestination?.id

            when {
                currentDestId == R.id.loginFragment -> {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
                currentDestId in rootDestinations -> {
                    showExitDialog()
                }
                else -> {
                    navController.navigateUp()
                }
            }
        }
    }

    // Inflar el menú SOLO cuando showLogoutMenu es true
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        if (!showLogoutMenu) return false
        menuInflater.inflate(R.menu.logout, menu)
        return true
    }

    // Manejar clicks del menú
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_logout -> {
                // Cerrar sesión en Firebase
                auth.signOut()

                // Navegar al loginFragment
                val navHostFragment =
                    supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
                val navController = navHostFragment.navController

                navController.navigate(R.id.loginFragment)

                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController
        return navController.navigateUp() || super.onSupportNavigateUp()
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

    private fun toggleFabToClose(isOpen: Boolean) {
        val fab = findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(R.id.fab_add)
        fab.setImageResource(if (isOpen) R.drawable.ic_close else R.drawable.ic_add_24)
    }

}
