package com.example.accessoriesmanager.local.converter

import androidx.room.TypeConverter
import com.example.accessoriesmanager.model.InstalledAccessory
import com.google.firebase.Timestamp
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.Date

class Converters {

    private val gson = Gson()

    @TypeConverter
    fun fromTimestamp(ts: Timestamp?): Long? = ts?.toDate()?.time

    @TypeConverter
    fun toTimestamp(millis: Long?): Timestamp? = millis?.let { Timestamp(Date(it)) }

    @TypeConverter
    fun fromInstalledAccessories(list: List<InstalledAccessory>?): String? =
        list?.let { gson.toJson(it) }

    @TypeConverter
    fun toInstalledAccessories(json: String?): List<InstalledAccessory>? =
        json?.let { gson.fromJson(it, object : TypeToken<List<InstalledAccessory>>() {}.type) }

    @TypeConverter
    fun fromStringList(list: List<String>?): String? = list?.let { gson.toJson(it) }

    @TypeConverter
    fun toStringList(json: String?): List<String>? =
        json?.let { gson.fromJson(it, object : TypeToken<List<String>>() {}.type) }
}
