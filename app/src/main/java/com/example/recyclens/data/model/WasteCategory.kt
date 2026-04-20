package com.example.recyclens.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "waste_category")
data class WasteCategory(

    @PrimaryKey
    @ColumnInfo(name = "category_id")
    val categoryId: Int,

    @ColumnInfo(name = "name_en")
    val nameEn: String?,

    @ColumnInfo(name = "name_tl")
    val nameTl: String?,

    @ColumnInfo(name = "description_en")
    val descriptionEn: String?,

    @ColumnInfo(name = "description_tl")
    val descriptionTl: String?,

    @ColumnInfo(name = "icon_path")
    val iconPath: String?
)
