package com.example.accessoriesManager.ui

import android.app.Activity
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.example.accesorymanager.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder

object QuickMenu {
    data class ActionItem(
        val title: String,
        val iconRes: Int,
        val circleColor: Int
    )

    fun showQuickMenu(
        activity: Activity,
        bottomBarHeightPx: Int,
        onSedeClick: () -> Unit = {},
        onVehicleClick: () -> Unit = {},
        onAccessoryClick: () -> Unit = {},
        onInstallationClick: () -> Unit = {}
    ): AlertDialog {

        val items = listOf(
            ActionItem("Instalación", R.drawable.instalacion, 0xFF7E57C2.toInt()),
            ActionItem("Accesorio", R.drawable.accesorio, 0xFF42A5F5.toInt()),
            ActionItem("Vehiculo", R.drawable.car, 0xFF66BB6A.toInt()),
            ActionItem("Sede", R.drawable.sede, 0xFF8D6E63.toInt())
        )

        // 👇 Necesitamos el dialog visible dentro del adapter
        lateinit var dialog: AlertDialog

        val adapter = object : ArrayAdapter<ActionItem>(
            activity,
            R.layout.item_action,
            items
        ) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {

                val view = convertView ?: LayoutInflater.from(context)
                    .inflate(R.layout.item_action, parent, false)

                val item = items[position]

                val title = view.findViewById<TextView>(R.id.title)
                val icon = view.findViewById<ImageView>(R.id.icon)
                val circle = (view as ViewGroup).getChildAt(0) as FrameLayout

                title.text = item.title
                icon.setImageResource(item.iconRes)
                (circle.background as? GradientDrawable)?.setColor(item.circleColor)

                // ✅ CLICK EN TODA LA FILA
                view.setOnClickListener {

                    // ⏱️ delay corto para que se vea el ripple
                    view.postDelayed({
                        dialog.dismiss()

                        when (position) {
                            3 -> onSedeClick()
                            2 -> onVehicleClick()
                            1 -> onAccessoryClick()
                            0 -> onInstallationClick()
                        }
                    }, 180)
                }

                return view
            }
        }

        val content = LayoutInflater.from(activity)
            .inflate(R.layout.dialog_actions, null, false)

        val list = content.findViewById<ListView>(R.id.actionsList)
        list.adapter = adapter

        dialog = MaterialAlertDialogBuilder(activity)
            .setView(content)
            .create()

        dialog.setOnShowListener {
            val width = (activity.resources.displayMetrics.widthPixels * 0.75f).toInt()
            dialog.window?.setLayout(
                width,
                WindowManager.LayoutParams.WRAP_CONTENT
            )
            dialog.window?.setGravity(Gravity.CENTER)
        }

        dialog.show()
        return dialog
    }
}