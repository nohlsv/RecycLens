package com.example.recyclens.data

import android.content.Context
import android.database.Cursor
import android.util.Log
// NOTE: Verify this import path for your DatabaseHelper
import com.example.recyclens.data.db.DatabaseHelper
import com.example.recyclens.data.model.WasteCategory
import com.example.recyclens.data.model.WasteMaterial

class ContentRepository(context: Context) {

    private val dbHelper = DatabaseHelper.get(context)

    // Helper function to safely read String from cursor (handles NULL columns)
    private fun Cursor.getStringOrNull(columnName: String): String? {
        val index = getColumnIndex(columnName)
        return if (index >= 0 && !isNull(index)) getString(index) else null
    }

    // --- FETCH CATEGORIES ---
    fun getAllCategories(): List<WasteCategory> {
        val categories = mutableListOf<WasteCategory>()
        val db = dbHelper.readableDatabase
        val query = "SELECT category_id, name, bin_color, icon_path, description FROM waste_category"

        db.rawQuery(query, null).use { cursor ->
            // Get column indices once for efficiency
            val idIndex = cursor.getColumnIndexOrThrow("category_id")
            val nameIndex = cursor.getColumnIndexOrThrow("name")

            while (cursor.moveToNext()) {
                val category = WasteCategory(
                    categoryId = cursor.getInt(idIndex),
                    name = cursor.getString(nameIndex),
                    binColor = cursor.getStringOrNull("bin_color"),
                    iconPath = cursor.getStringOrNull("icon_path"),
                    description = cursor.getStringOrNull("description")
                )
                categories.add(category)
            }
        }
        return categories
    }

    // --- FETCH MATERIALS ---
    fun getAllMaterials(): List<WasteMaterial> {
        val materials = mutableListOf<WasteMaterial>()
        val db = dbHelper.readableDatabase
        val query = "SELECT material_id, name, image, category_id FROM waste_material"

        db.rawQuery(query, null).use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow("material_id")
            val nameIndex = cursor.getColumnIndexOrThrow("name")
            val categoryIdIndex = cursor.getColumnIndexOrThrow("category_id")

            while (cursor.moveToNext()) {
                val material = WasteMaterial(
                    materialId = cursor.getInt(idIndex),
                    name = cursor.getString(nameIndex),
                    image = cursor.getStringOrNull("image"),
                    categoryId = cursor.getInt(categoryIdIndex)
                )
                materials.add(material)
            }
        }
        return materials
    }
}