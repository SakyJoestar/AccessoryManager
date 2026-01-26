package com.example.accessoriesManager.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties

@IgnoreExtraProperties
data class Installation(
    @DocumentId
    var id: String? = null,
    var order: Int?= null,
    val serie: String? = null,
    val plate: String? = null,
    val warehouse: String? = null,
    val condition: String? = null,
    val date: Timestamp? = null,
    val headquarter: Headquarter? = null,
    val increment: Long? = null,
    val vehicle: Vehicle? = null,
    val accessories: List<InstalledAccessory>? = null,
    val state: String? = null,
    val totalWorked: Long? = null,
    val totalPaid: Long? = null,
    val totalUnpaid: Long? = null,
    var createdAt: Timestamp? = null,
    var updatedAt: Timestamp? = null
)