package com.example.recyclens.data.db

import android.content.Context

data class Game(
    val id: Long,
    val title: String,
    val level: String?,
    val timer: Int?,
    val currentScore: Int?
)

class GameRepository(ctx: Context) {

    private val context = ctx.applicationContext

    fun getGame(title: String, level: String): Game? {
        var result: Game? = null

        GameDatabase.open(context).use { db ->
            db.rawQuery(
                "SELECT game_id, game_title, game_level, game_timer, current_score FROM game WHERE game_title = ? AND game_level = ? LIMIT 1",
                arrayOf(title, level)
            ).use { cursor ->
                if (cursor.moveToFirst()) {
                    result = Game(
                        id = cursor.getLong(0),
                        title = cursor.getString(1),
                        level = cursor.getString(2),
                        timer = cursor.getInt(3).takeIf { !cursor.isNull(3) },
                        currentScore = cursor.getInt(4).takeIf { !cursor.isNull(4) }
                    )
                }
            }
        }

        return result
    }

    fun getGamesByTitle(title: String): List<Game> {
        val list = mutableListOf<Game>()

        GameDatabase.open(context).use { db ->
            db.rawQuery(
                "SELECT game_id, game_title, game_level, game_timer, current_score FROM game WHERE game_title = ? ORDER BY game_id",
                arrayOf(title)
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    list.add(
                        Game(
                            id = cursor.getLong(0),
                            title = cursor.getString(1),
                            level = cursor.getString(2),
                            timer = cursor.getInt(3).takeIf { !cursor.isNull(3) },
                            currentScore = cursor.getInt(4).takeIf { !cursor.isNull(4) }
                        )
                    )
                }
            }
        }

        return list
    }
}
