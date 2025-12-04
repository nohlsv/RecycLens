package com.example.recyclens.data.model

import android.content.Context
import com.example.recyclens.data.db.AppDatabase
import kotlinx.coroutines.flow.Flow

class ContentRepository(context: Context) {

    // 1. Get the DAO from the AppDatabase
    private val dao = AppDatabase.getDatabase(context).recyclensDao()

    // 2. FOR THE SCANNER: Get a material by its English name (e.g., "Plastic Bottle")
    suspend fun getMaterialByName(name: String): WasteMaterial? {
        return dao.getMaterialByName(name)
    }

    // 3. FOR THE GAME SELECT: Get all categories (Green vs Blue)
    fun getAllCategories(): Flow<List<WasteCategory>> {
        return dao.getAllCategories()
    }

    // 4. FOR THE GAMES: Get items specific to a bin
    fun getMaterialsByCategory(categoryId: Int): Flow<List<WasteMaterial>> {
        return dao.getMaterialsByCategory(categoryId)
    }
}