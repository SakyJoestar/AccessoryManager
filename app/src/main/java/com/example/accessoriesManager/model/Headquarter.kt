package com.example.accessoriesManager.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties

@IgnoreExtraProperties
data class Headquarter(
    @DocumentId
    var id: String? = null,
    var name: String? = null,
    var increment: Int = 0,
    var createdAt: Timestamp? = null,
    var updatedAt: Timestamp? = null
)

