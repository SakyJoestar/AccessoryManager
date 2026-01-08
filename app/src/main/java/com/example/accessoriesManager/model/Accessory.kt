package com.example.accessoriesManager.model

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName
import java.util.Date

@IgnoreExtraProperties
data class Accessory(
    var name: String = "",
    var price: Double = 0.0,
    var createdAt: Any? = null
)
