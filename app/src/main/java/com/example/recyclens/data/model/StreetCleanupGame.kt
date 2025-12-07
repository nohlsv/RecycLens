// Kotlin
package com.example.recyclens.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "street_cleanup_game")
data class StreetCleanupGame(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Int = 0,

    @ColumnInfo(name = "street_icon")
    val streetIcon: String,

    @ColumnInfo(name = "location_name")
    val locationName: String,

    @ColumnInfo(name = "material_name")
    val materialName: String,

    @ColumnInfo(name = "trash_count")
    val trashCount: Int = 0,

    @ColumnInfo(name = "timer")
    val timer: Int = 0
)
