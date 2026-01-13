package com.example.accessoriesManager.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties

@IgnoreExtraProperties
data class Vehicle(
    @DocumentId
    var id: String? = null,
    var modelo: String? = null,
    var make: String? = null,
    var createdAt: Timestamp? = null,
    var updatedAt: Timestamp? = null
)