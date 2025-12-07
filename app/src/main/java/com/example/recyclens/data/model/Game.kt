// Kotlin
package com.example.recyclens.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "game")
data class Game(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Int = 0,

    @ColumnInfo(name = "game_title")
    val gameTitle: String,

    @ColumnInfo(name = "game_level")
    val gameLevel: String,

    @ColumnInfo(name = "current_score")
    val currentScore: Int = 0
)
