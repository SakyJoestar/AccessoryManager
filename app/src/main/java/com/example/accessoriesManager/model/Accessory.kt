package com.example.accessoriesManager.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties

@IgnoreExtraProperties
data class Accessory(
    @DocumentId
    var id: String = "",
    var name: String = "",
    var price: Long = 0L,
    var createdAt: Timestamp? = null,
    var updatedAt: Timestamp? = null
)
