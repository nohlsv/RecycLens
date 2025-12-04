package com.example.recyclens.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo

@Entity(tableName = "waste_category")
data class WasteCategory(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "category_id") val categoryId: Int,

    @ColumnInfo(name = "name") val name: String, // e.g., "Biodegradable"

    @ColumnInfo(name = "bin_color") val binColor: String, // e.g., "Green"

    @ColumnInfo(name = "icon_path") val iconPath: String?,

    @ColumnInfo(name = "description") val description: String?
)