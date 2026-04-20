package com.example.recyclens.data.db

object GameDbContract {
    const val GAME_TITLE_STREET_CLEANUP = "Street Cleanup"
    const val GAME_TITLE_TRASH_SORTING = "Trash Sorting"

    const val LEVEL_EASY = "Easy"
    const val LEVEL_MEDIUM = "Medium"
    const val LEVEL_HARD = "Hard"

    fun levelNameForLevelNumber(level: Int): String {
        return when (level) {
            1 -> LEVEL_EASY
            2 -> LEVEL_MEDIUM
            3 -> LEVEL_HARD
            else -> LEVEL_EASY
        }
    }
}
