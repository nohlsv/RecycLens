package com.example.recyclens.data.db

import androidx.room.Dao
import androidx.room.Query
import com.example.recyclens.data.model.WasteCategory
import com.example.recyclens.data.model.WasteMaterial
import kotlinx.coroutines.flow.Flow

@Dao
interface RecycLensDao {
    // 1. Get all categories (Green/Blue Bins)
    @Query("SELECT * FROM waste_category")
    fun getAllCategories(): Flow<List<WasteCategory>>

    // 2. Get a specific material by its English name (for the Scanner)
    // We use name_en because the AI model detects in English
    @Query("SELECT * FROM waste_material WHERE name_en LIKE :detectedName LIMIT 1")
    suspend fun getMaterialByName(detectedName: String): WasteMaterial?

    // 3. Get all materials for a specific bin (for the Games)
    @Query("SELECT * FROM waste_material WHERE category_id = :categoryId")
    fun getMaterialsByCategory(categoryId: Int): Flow<List<WasteMaterial>>
}