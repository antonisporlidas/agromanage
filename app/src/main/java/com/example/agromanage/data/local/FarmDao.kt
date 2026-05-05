package com.example.agromanage.data.local

import androidx.room.*

@Dao
interface FarmDao {

    // ==========================================
    // 1. ΟΙΚΟΠΕΔΑ (FarmField) - Ακριβώς οι δικές σου συναρτήσεις!
    // ==========================================
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertField(field: FarmField): Long

    @Query("SELECT * FROM fields_table WHERE userId = :currentUserId ORDER BY name ASC")
    suspend fun getAllFieldsForUser(currentUserId: String): List<FarmField>

    @Delete
    suspend fun deleteField(field: FarmField): Int

    @Query("SELECT * FROM fields_table WHERE cloudId = :cloudId LIMIT 1")
    suspend fun getFieldByCloudId(cloudId: String): FarmField?

    @Query("SELECT * FROM fields_table WHERE id = :id LIMIT 1")
    suspend fun getFieldById(id: Int): FarmField?

    @Query("SELECT * FROM fields_table WHERE location = :loc AND userId = :currentUserId")
    suspend fun getFieldsByLocationForUser(loc: String, currentUserId: String): List<FarmField>


    // ==========================================
    // 2. ΚΑΛΛΙΕΡΓΕΙΕΣ (Cultivation) - Οι νέες συναρτήσεις
    // ==========================================
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCultivation(cultivation: Cultivation): Long

    // Φέρνει το ιστορικό σποράς για ένα ΣΥΓΚΕΚΡΙΜΕΝΟ χωράφι (για την καρτέλα Χωράφια)
    @Query("SELECT * FROM cultivations WHERE fieldId = :fieldId ORDER BY year DESC, season DESC")
    suspend fun getCultivationsForField(fieldId: Int): List<Cultivation>

    @Delete
    suspend fun deleteCultivation(cultivation: Cultivation): Int

    @Query("SELECT * FROM cultivations WHERE cloudId = :cloudId LIMIT 1")
    suspend fun getCultivationByCloudId(cloudId: String): Cultivation?

    @Query("SELECT * FROM cultivations WHERE id = :id LIMIT 1")
    suspend fun getCultivationById(id: Int): Cultivation?

    // ==========================================
    // 3. ΕΞΥΠΝΑ ΦΙΛΤΡΑ (Για τον Τζίρο και τις Εργασίες)
    // ==========================================
    // Φέρνει ΟΛΕΣ τις καλλιέργειες από ΟΛΑ τα χωράφια του χρήστη ενώνοντας τους πίνακες
    @Query("""
        SELECT c.* FROM cultivations c 
        INNER JOIN fields_table f ON c.fieldId = f.id 
        WHERE f.userId = :currentUserId
    """)
    suspend fun getAllCultivationsForUser(currentUserId: String): List<Cultivation>
}