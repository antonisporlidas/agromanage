package com.example.agromanage.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "tasks", indices = [Index(value = ["cloudId"], unique = true)])
data class FarmTask(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val fieldId: Int = 0,
    val title: String = "",
    val date: String = "",
    val cost: Double = 0.0,
    val cloudId: String = "",
    val cultivationId: Int = 0,
    val cultivationCloudId: String = "", // <-- Cloud id της σποράς για cross-device sync
)