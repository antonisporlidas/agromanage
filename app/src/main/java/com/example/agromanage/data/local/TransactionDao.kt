package com.example.agromanage.data.local

import androidx.room.*

@Dao
interface TransactionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: FarmTransaction)

    @Update
    suspend fun updateTransaction(transaction: FarmTransaction)

    @Delete
    suspend fun deleteTransaction(transaction: FarmTransaction)

    // 1. Γενικό: Φέρνει ΟΛΑ τα οικονομικά του χρήστη (χρήσιμο για να κάνουμε τα "βαριά" φίλτρα στον κώδικα Kotlin)
    @Query("SELECT * FROM transactions WHERE userId = :currentUserId ORDER BY date DESC")
    suspend fun getAllTransactionsForUser(currentUserId: String): List<FarmTransaction>

    // 2. Φίλτρο "Απόλυτο": Φέρνει τα έσοδα/έξοδα ΜΟΝΟ για μια συγκεκριμένη σπορά (π.χ. Καλαμπόκι 2026 Καλοκαίρι)
    @Query("SELECT * FROM transactions WHERE cultivationId = :cultivationId ORDER BY date DESC")
    suspend fun getTransactionsForCultivation(cultivationId: Int): List<FarmTransaction>

    // 3. Φίλτρο "Ιστορικό Χωραφιού": Φέρνει ΟΛΑ τα έσοδα/έξοδα από ΟΛΑ τα έτη και τις περιόδους για ΕΝΑ χωράφι
    @Query("""
        SELECT tr.* FROM transactions tr 
        INNER JOIN cultivations c ON tr.cultivationId = c.id 
        WHERE c.fieldId = :fieldId 
        ORDER BY tr.date DESC
    """)
    suspend fun getTransactionsForField(fieldId: Int): List<FarmTransaction>

    // Βοηθητική συνάρτηση για το Firebase
    @Query("SELECT * FROM transactions WHERE cloudId = :cloudId LIMIT 1")
    suspend fun getTransactionByCloudId(cloudId: String): FarmTransaction?
}