package com.example.accessoriesManager.utils

import com.example.accessoriesManager.model.Installation
import kotlin.collections.get

@Suppress("UNCHECKED_CAST")
fun Installation.vehicleLabel(): String {
    val v = vehicle ?: return ""
    return when (v) {
        is String -> v.trim()
        is Map<*, *> -> {
            val make = (v["make"] as? String).orEmpty().trim()
            val model = (v["model"] as? String).orEmpty().trim()
            listOf(make, model).filter { it.isNotBlank() }.joinToString(" - ").trim()
        }
        else -> v.toString().trim()
    }
}

@Suppress("UNCHECKED_CAST")
fun Installation.headquarterLabel(): String {
    val h = headquarter ?: return ""
    return when (h) {
        is String -> h.trim()
        is Map<*, *> -> ((h["name"] as? String).orEmpty()).trim()
        else -> h.toString().trim()
    }
}
