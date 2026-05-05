package com.example.agromanage.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "transactions",
    indices = [Index(value = ["cloudId"], unique = true)] // Για να μην έχουμε διπλότυπα από το Firebase
)
data class FarmTransaction(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val cloudId: String = "",
    val fieldId: Int? = null, // ΣΗΜΑΝΤΙΚΟ: null σημαίνει "Γενικό Έξοδο/Έσοδο"
    val fieldCloudId: String? = null, // Cloud id του χωραφιού για cross-device sync
    val fieldName: String = "Γενικό", // Αποθηκεύουμε το όνομα για να φαίνεται όμορφα στη λίστα
    val amount: Double = 0.0,
    val type: String = "Έσοδο", // "Έσοδο" ή "Έξοδο"
    val description: String = "", // π.χ. "Πώληση Ντομάτας" ή "Πετρέλαιο"
    val date: String = "",
    val year: String = "", // π.χ. "2026" για τα φίλτρα
    val season: String = "", // π.χ. "Καλοκαιρινή" για τα φίλτρα
    val userId: String = "",
    val cultivationId: Int? = null,
    val cultivationCloudId: String? = null, // Cloud id της σποράς για cross-device sync
)