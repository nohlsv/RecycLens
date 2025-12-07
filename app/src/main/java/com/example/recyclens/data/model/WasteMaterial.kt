package com.example.recyclens.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "waste_material")
data class WasteMaterial(

    @PrimaryKey
    @ColumnInfo(name = "material_id")
    val materialId: Int,

    @ColumnInfo(name = "name_en")
    val nameEn: String?,

    @ColumnInfo(name = "name_tl")
    val nameTl: String?,

    @ColumnInfo(name = "category_id")
    val categoryId: Int,

    @ColumnInfo(name = "image_path")
    val image: String?
)