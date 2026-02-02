package com.example.accessoriesManager.model

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName

@IgnoreExtraProperties
data class InstalledAccessory(
    val accessoryId: String? = null,   // id del Accessory (campo normal, no @DocumentId)
    val name: String? = null,          // snapshot para UI
    val price: Long = 0L,
    @get:PropertyName("paid")
    @set:PropertyName("paid")
    var isPaid: Boolean = false,
)
