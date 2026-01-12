package com.example.accessoriesManager.ui

import android.R
import android.app.Activity
import android.view.View
import androidx.annotation.IdRes
import androidx.fragment.app.Fragment
import com.google.android.material.snackbar.Snackbar

/**
 * Muestra un Snackbar anclado (por defecto) a un view del Activity (ej: BottomAppBar),
 * para que NO empuje el FAB.
 */
fun Fragment.showSnack(
    message: String,
    duration: Int = Snackbar.LENGTH_SHORT,
    @IdRes anchorId: Int? = null,
    fallbackAnchorId: Int? = null
) {
    val activity = requireActivity()
    val root = activity.findViewById<View>(R.id.content)

    // 1) Anchor principal (si lo mandas)
    val anchor: View? = anchorId?.let { activity.findViewById(it) }
    // 2) Anchor por defecto (si no mandas anchorId)
        ?: fallbackAnchorId?.let { activity.findViewById(it) }

    Snackbar.make(root, message, duration).apply {
        anchor?.let { setAnchorView(it) }
    }.show()
}

/**
 * Variante para llamarla desde cualquier Activity también.
 */
fun Activity.showSnack(
    message: String,
    duration: Int = Snackbar.LENGTH_SHORT,
    @IdRes anchorId: Int? = null
) {
    val root = findViewById<View>(R.id.content)
    val anchor = anchorId?.let { findViewById<View>(it) }

    Snackbar.make(root, message, duration).apply {
        anchor?.let { setAnchorView(it) }
    }.show()
}
