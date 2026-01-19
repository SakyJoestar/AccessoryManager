package com.example.accessoriesManager.model

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties

@IgnoreExtraProperties
data class InstalledAccessory(
    @DocumentId
    val accessoryId: String? = null,   // id del Accessory
    val name: String? = null,           // snapshot para UI
    val quantity: Int = 1,              // número de piezas
    val isPaid: Boolean = false,         // ¿pagado?
)