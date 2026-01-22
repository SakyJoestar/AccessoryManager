package com.example.accessoriesManager.model

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties

@IgnoreExtraProperties
data class InstalledAccessory(
    val accessoryId: String? = null,   // id del Accessory (campo normal, no @DocumentId)
    val name: String? = null,          // snapshot para UI
    val unitPrice: Int = 0,            // 👈 precio unitario
    val quantity: Int = 1,
    val isPaid: Boolean = false,
    val total: Long = 0                // puedes mantenerlo por facilidad/consulta
) {
    fun computedTotal(): Int = unitPrice * quantity
}
