package com.example.agromanage.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    // Προσθέσαμε το Cultivation στη λίστα με τα Entities
    entities = [FarmField::class, Cultivation::class, FarmTask::class, FarmTransaction::class],
    version = 4, // v4: προσθέσαμε *CloudId foreign keys για cross-device sync
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun farmDao(): FarmDao
    abstract fun taskDao(): FarmTaskDao
    abstract fun transactionDao(): TransactionDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "farm_database"
                )
                    // Διαγράφει την παλιά βάση αυτόματα στο κινητό για να μην κρασάρει με τη νέα δομή
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}