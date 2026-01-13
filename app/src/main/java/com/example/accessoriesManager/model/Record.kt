package com.example.accessoriesManager.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties

@IgnoreExtraProperties
data class Record(
    @DocumentId
    var id: String? = null,

    var createdAt: Timestamp? = null,
    var updatedAt: Timestamp? = null
)