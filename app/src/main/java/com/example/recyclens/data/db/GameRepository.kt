//package com.example.recyclens.data.db
//
//import android.content.Context
//import android.database.Cursor
//
//data class Game(
//    val id: Long,
//    val key: String,
//    val name: String,
//    val description: String?
//)
//
//data class GameLevel(
//    val id: Long,
//    val gameId: Long,
//    val levelNumber: Int,
//    val levelName: String
//)
//
//class GameRepository(ctx: Context) {
//
//    // Use the singleton DatabaseHelper
//    private val db = DatabaseHelper.getInstance(ctx).readableDatabase
//
//    fun getGameByKey(key: String): Game? {
//        var result: Game? = null
//
//        db.rawQuery(
//            "SELECT game_id, game_key, game_name, description FROM game WHERE game_key = ?",
//            arrayOf(key)
//        ).use { cursor: Cursor ->
//            if (cursor.moveToFirst()) {
//                result = Game(
//                    id = cursor.getLong(0),
//                    key = cursor.getString(1),
//                    name = cursor.getString(2),
//                    description = cursor.getString(3)
//                )
//            }
//        }
//
//        return result
//    }
//
//    fun getLevelsForGame(gameId: Long): List<GameLevel> {
//        val list = mutableListOf<GameLevel>()
//
//        db.rawQuery(
//            "SELECT level_id, game_id, level_number, level_name FROM game_level WHERE game_id = ? ORDER BY level_number",
//            arrayOf(gameId.toString())
//        ).use { cursor: Cursor ->
//            while (cursor.moveToNext()) {
//                list.add(
//                    GameLevel(
//                        id = cursor.getLong(0),
//                        gameId = cursor.getLong(1),
//                        levelNumber = cursor.getInt(2),
//                        levelName = cursor.getString(3)
//                    )
//                )
//            }
//        }
//
//        return list
//    }
//}
