package com.example.agromanage.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

// 2. Η Καλλιέργεια (Το Ιστορικό Σποράς)
@Entity(tableName = "cultivations")
data class Cultivation(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val cloudId: String = "",
    val fieldId: Int = 0, // <-- Τοπικό Room id του χωραφιού (για JOINs)
    val fieldCloudId: String = "", // <-- Cloud id του χωραφιού (για cross-device sync)
    val cropType: String = "",
    val year: String = "",
    val season: String = ""
)