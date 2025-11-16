package com.example.recyclens.data.db

import android.content.Context

data class Game(
    val id: Long,
    val key: String,
    val name: String,
    val description: String?
)

data class GameLevel(
    val id: Long,
    val gameId: Long,
    val levelNumber: Int,
    val levelName: String
)

class GameRepository(ctx: Context) {

    private val db = DatabaseHelper.get(ctx).readableDatabase

    fun getGameByKey(key: String): Game? {
        val cursor = db.rawQuery(
            "SELECT game_id, game_key, game_name, description FROM game WHERE game_key = ?",
            arrayOf(key)
        )
        cursor.use {
            if (it.moveToFirst()) {
                return Game(
                    id = it.getLong(0),
                    key = it.getString(1),
                    name = it.getString(2),
                    description = it.getString(3)
                )
            }
        }
        return null
    }

    fun getLevelsForGame(gameId: Long): List<GameLevel> {
        val list = mutableListOf<GameLevel>()
        val cursor = db.rawQuery(
            "SELECT level_id, game_id, level_number, level_name FROM game_level WHERE game_id = ? ORDER BY level_number",
            arrayOf(gameId.toString())
        )
        cursor.use {
            while (it.moveToNext()) {
                list.add(
                    GameLevel(
                        id = it.getLong(0),
                        gameId = it.getLong(1),
                        levelNumber = it.getInt(2),
                        levelName = it.getString(3)
                    )
                )
            }
        }
        return list
    }
}
