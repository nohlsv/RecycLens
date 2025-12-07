package com.example.recyclens.data

import android.content.Context
import com.example.recyclens.data.db.AppDatabase
import com.example.recyclens.data.model.WasteCategory
import com.example.recyclens.data.model.WasteMaterial
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ContentRepository(context: Context) {

    private val db by lazy { AppDatabase.getInstance(context.applicationContext) }
    private val dao by lazy { db.recycLensDao() }

    suspend fun getWasteMaterialByName(name: String): WasteMaterial? = withContext(Dispatchers.IO) {
        dao.getMaterialByName(name) // ensure DAO method name matches
    }

    suspend fun getAllCategories(): List<WasteCategory> = withContext(Dispatchers.IO) {
        dao.getAllCategories() // ensure this DAO method exists and returns List<WasteCategory>
    }

    suspend fun getMaterialsByCategory(categoryId: Int): List<WasteMaterial> = withContext(Dispatchers.IO) {
        dao.getMaterialsByCategory(categoryId) // ensure this DAO method exists
    }
}
