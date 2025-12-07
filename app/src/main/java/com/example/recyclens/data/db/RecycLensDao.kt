package com.example.recyclens.data.db

import androidx.room.Dao
import androidx.room.Query
import com.example.recyclens.data.model.WasteCategory
import com.example.recyclens.data.model.WasteMaterial

@Dao
interface RecycLensDao {

    @Query("SELECT * FROM waste_material WHERE name_en = :name OR name_tl = :name LIMIT 1")
    suspend fun getMaterialByName(name: String): WasteMaterial?

    @Query("SELECT * FROM waste_category WHERE category_id = :id LIMIT 1")
    suspend fun getCategoryById(id: Int): WasteCategory?

    @Query("SELECT * FROM waste_category ORDER BY category_id")
    suspend fun getAllCategories(): List<WasteCategory>

    @Query("SELECT * FROM waste_material WHERE category_id = :categoryId ORDER BY material_id")
    suspend fun getMaterialsByCategory(categoryId: Int): List<WasteMaterial>
}
