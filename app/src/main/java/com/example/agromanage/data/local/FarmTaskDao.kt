package com.example.agromanage.data.local

import androidx.room.*

@Dao
interface FarmTaskDao {

    // 1. Φέρνει τις εργασίες για μια ΣΥΓΚΕΚΡΙΜΕΝΗ καλλιέργεια (Το "Ημερολόγιο" για συγκεκριμένη περίοδο/έτος)
    @Query("SELECT * FROM tasks WHERE cultivationId = :cultivationId ORDER BY id DESC")
    suspend fun getTasksForCultivation(cultivationId: Int): List<FarmTask>

    // 2. Αν θέλεις να φέρεις ΟΛΕΣ τις εργασίες ενός χωραφιού (από όλα τα έτη ανακατεμένα)
    @Query("""
        SELECT t.* FROM tasks t 
        INNER JOIN cultivations c ON t.cultivationId = c.id 
        WHERE c.fieldId = :fieldId 
        ORDER BY t.id DESC
    """)
    suspend fun getAllTasksForField(fieldId: Int): List<FarmTask>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: FarmTask): Long

    @Update
    suspend fun updateTask(task: FarmTask)

    @Delete
    suspend fun deleteTask(task: FarmTask)

    @Query("SELECT * FROM tasks WHERE cloudId = :cloudId LIMIT 1")
    suspend fun getTaskByCloudId(cloudId: String): FarmTask?
}