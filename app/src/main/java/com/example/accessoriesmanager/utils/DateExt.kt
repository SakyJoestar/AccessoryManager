package com.example.accessoriesmanager.utils

import com.google.firebase.Timestamp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

fun Timestamp?.toLocalDate(zoneId: ZoneId = ZoneId.systemDefault()): LocalDate? {
    if (this == null) return null
    return Instant.ofEpochSecond(seconds, nanoseconds.toLong())
        .atZone(zoneId)
        .toLocalDate()
}