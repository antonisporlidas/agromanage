package com.example.agromanage.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "fields_table", indices = [Index(value = ["cloudId"], unique = true)])
data class FarmField (
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String = "",
    val location: String = "",
    val size: Double = 0.0,
    val cropType: String = "",
    val year: String = "",
    val season: String = "",
    val fieldType: String = "",
    val userId: String = "",
    val cloudId: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0
)