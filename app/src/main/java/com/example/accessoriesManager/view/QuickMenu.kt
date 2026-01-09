package com.example.accessoriesManager.ui.common

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import com.example.accesorymanager.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder

data class ActionItem(
    val title: String,
    val iconRes: Int,
    val circleColor: Int
)

fun showQuickMenu(
    activity: Activity,
    bottomBarHeightPx: Int,
    onSedeClick: () -> Unit = {}
): androidx.appcompat.app.AlertDialog {

    val items = listOf(
        ActionItem("Instalación", R.drawable.instalacion, 0xFF7E57C2.toInt()),
        ActionItem("Accesorio", R.drawable.accesorio, 0xFF42A5F5.toInt()),
        ActionItem("Vehiculo", R.drawable.car, 0xFF66BB6A.toInt()),
        ActionItem("Sede", R.drawable.sede, 0xFF8D6E63.toInt())
    )

    val adapter = object : ArrayAdapter<ActionItem>(activity, R.layout.item_action, items) {
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: LayoutInflater.from(context)
                .inflate(R.layout.item_action, parent, false)

            val item = items[position]
            view.findViewById<TextView>(R.id.title).text = item.title
            view.findViewById<ImageView>(R.id.icon).setImageResource(item.iconRes)

            val circle = (view as ViewGroup).getChildAt(0) as FrameLayout
            (circle.background as? GradientDrawable)?.setColor(item.circleColor)

            return view
        }
    }

    val content = LayoutInflater.from(activity).inflate(R.layout.dialog_actions, null, false)
    val list = content.findViewById<android.widget.ListView>(R.id.actionsList)
    list.adapter = adapter

    val dialog = MaterialAlertDialogBuilder(activity)
        .setView(content)
        .create()

    list.setOnItemClickListener { _, _, which, _ ->
        dialog.dismiss()
        when (which) {
            3 -> onSedeClick()
        }
    }

    dialog.setOnShowListener {
        dialog.window?.setLayout(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT
        )
        dialog.window?.setGravity(Gravity.CENTER)
    }

    dialog.show()
    return dialog
}
