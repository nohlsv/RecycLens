package com.example.recyclens.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo
import androidx.room.ForeignKey

// 1. ANNOTATE: Tells Room this class maps to the 'waste_material' table
@Entity(
    tableName = "waste_material",
    // Links this material to its category (Biodegradable or Non-Bio)
    foreignKeys = [
        ForeignKey(
            entity = WasteCategory::class,
            parentColumns = ["category_id"],
            childColumns = ["category_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class WasteMaterial(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "material_id") val materialId: Int,

    // 2. NEW BILINGUAL COLUMNS
    @ColumnInfo(name = "name_en") val nameEn: String,
    @ColumnInfo(name = "name_tl") val nameTl: String,

    @ColumnInfo(name = "description_en") val descriptionEn: String?,
    @ColumnInfo(name = "description_tl") val descriptionTl: String?,

    @ColumnInfo(name = "image_path") val imagePath: String?,
    @ColumnInfo(name = "category_id") val categoryId: Int
) {
    // 3. HELPER FUNCTIONS: For easy language switching in your UI
    fun getName(isTagalog: Boolean): String {
        return if (isTagalog) nameTl else nameEn
    }

    fun getDescription(isTagalog: Boolean): String {
        // Returns the description or an empty string if the DB entry is null
        return if (isTagalog) descriptionTl ?: "" else descriptionEn ?: ""
    }
}