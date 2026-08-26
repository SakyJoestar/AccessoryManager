package com.example.accessoriesmanager.model

/**
 * Retrocompatible: headquarter/vehicle solían guardarse como Map (objeto embebido) y ahora
 * se guardan como texto libre. Un mismo documento antiguo en Firestore puede traer cualquiera
 * de las dos formas, así que todo lo que muestra estas etiquetas pasa por aquí.
 */
@Suppress("UNCHECKED_CAST")
fun headquarterLabelFromAny(value: Any?): String {
    if (value == null) return ""
    return when (value) {
        is String -> value.trim()
        is Map<*, *> -> ((value["name"] as? String).orEmpty()).trim()
        else -> value.toString().trim()
    }
}

@Suppress("UNCHECKED_CAST")
fun vehicleLabelFromAny(value: Any?): String {
    if (value == null) return ""
    return when (value) {
        is String -> value.trim()
        is Map<*, *> -> {
            val make = (value["make"] as? String).orEmpty().trim()
            val model = (value["model"] as? String).orEmpty().trim()
            listOf(make, model).filter { it.isNotBlank() }.joinToString(" - ").trim()
        }
        else -> value.toString().trim()
    }
}
