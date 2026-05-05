package com.example.agromanage.data.local

import com.google.firebase.Timestamp

data class MarketPost(
    val id: String = "",
    val farmerId: String = "",
    val farmerName: String = "",
    val farmerEmail: String = "", // Το κρατάμε κρυφό μέχρι ο έμπορος να πατήσει "Ενδιαφέρομαι"
    val farmerPhone: String = "",
    val cropType: String = "", // π.χ. Ντομάτες, Ελιές
    val quantity: String = "", // π.χ. 500 κιλά
    val priceMin: Double = 0.0, // Ελάχιστη τιμή (Range)
    val priceMax: Double = 0.0, // Μέγιστη τιμή (Range)
    val description: String = "", // π.χ. "Άριστης ποιότητας, βιολογικές"
    val date: String = "",                          // Για εμφάνιση (dd/MM/yyyy)
    val dateTimestamp: Timestamp? = null,           // Για σωστή χρονολογική ταξινόμηση στο Firestore
    val imageUrl: String = "",
    // Λίστα με τα UIDs των εμπόρων που πάτησαν "Ενδιαφέρομαι"
    val interestedMerchants: List<String> = emptyList()
)