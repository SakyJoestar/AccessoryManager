package com.example.accessoriesManager.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties

@IgnoreExtraProperties
data class Installation(
    @DocumentId
    var id: String? = null,
    var order: Int? = null,
    val serie: String? = null,
    val plate: String? = null,
    val warehouse: String? = null,
    val condition: String? = null,
    val date: Timestamp? = null,

    // ✅ Retrocompatible: antes era Headquarter (map), ahora quieres texto
    val headquarter: Any? = null,
    val headquarterId: String? = null,

    val increment: Long? = null,

    // ✅ Retrocompatible: antes era Vehicle (map), ahora quieres texto
    val vehicle: Any? = null,
    val vehicleId: String? = null,

    val accessories: List<InstalledAccessory>? = null,
    val state: String? = null,
    val totalWorked: Long? = null,
    val totalPaid: Long? = null,
    val totalUnpaid: Long? = null,
    val comment: String? = null,
    var createdAt: Timestamp? = null,
    var updatedAt: Timestamp? = null
)