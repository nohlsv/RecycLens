package com.example.recyclens.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.recyclens.data.model.WasteCategory
import com.example.recyclens.data.model.WasteMaterial

// 1. LIST ENTITIES: Add all your tables here.
@Database(entities = [WasteCategory::class, WasteMaterial::class], version = 1)
abstract class AppDatabase : RoomDatabase() {

    // 2. DAO GETTER: Connects the database to your queries
    abstract fun recyclensDao(): RecycLensDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "recyclens_database"
                )
                    // 3. ASSET IMPORT: This must match your assets/databases folder exactly
                    .createFromAsset("databases/recyclens_schema.db")
                    .fallbackToDestructiveMigration() // Handles changes gracefully
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}