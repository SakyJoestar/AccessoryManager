package com.example.accessoriesManager.model

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties

@IgnoreExtraProperties
data class Headquarter(
    @DocumentId
    var id: String? = null,
    var name: String = "",
    var increment: Int = 0
)