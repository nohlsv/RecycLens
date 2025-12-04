package com.example.recyclens.data.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class DatabaseHelper private constructor(ctx: Context) :
    SQLiteOpenHelper(ctx, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        const val DATABASE_NAME = "recyclens_schema.db"
        const val DATABASE_VERSION = 1

        @Volatile
        private var INSTANCE: DatabaseHelper? = null

        fun getInstance(context: Context): DatabaseHelper =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: DatabaseHelper(context.applicationContext).also {
                    INSTANCE = it
                }
            }
    }

    override fun onCreate(db: SQLiteDatabase) {
        // --- Tables used in your repositories ---

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS waste_category (
                category_id   INTEGER PRIMARY KEY AUTOINCREMENT,
                name          TEXT NOT NULL,
                bin_color     TEXT,
                icon_path     TEXT,
                description   TEXT
            );
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS waste_material (
                material_id   INTEGER PRIMARY KEY AUTOINCREMENT,
                name          TEXT NOT NULL,
                image         TEXT,
                category_id   INTEGER NOT NULL,
                FOREIGN KEY(category_id) REFERENCES waste_category(category_id)
            );
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS game (
                game_id     INTEGER PRIMARY KEY AUTOINCREMENT,
                game_key    TEXT NOT NULL UNIQUE,
                game_name   TEXT NOT NULL,
                description TEXT
            );
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS game_level (
                level_id     INTEGER PRIMARY KEY AUTOINCREMENT,
                game_id      INTEGER NOT NULL,
                level_number INTEGER NOT NULL,
                level_name   TEXT NOT NULL,
                FOREIGN KEY(game_id) REFERENCES game(game_id)
            );
            """.trimIndent()
        )

        // --- Optional: seed some basic data so screens are not empty ---

        db.execSQL(
            """
            INSERT INTO game (game_key, game_name, description) VALUES
            ('trash_sort',    'Trash Sorting',    'Drag items into the correct bins'),
            ('street_cleanup','Street Cleanup',   'Clean the street by sorting trash');
            """.trimIndent()
        )

        db.execSQL(
            """
            INSERT INTO game_level (game_id, level_number, level_name)
            VALUES
            (1, 1, 'Easy'),
            (1, 2, 'Medium'),
            (1, 3, 'Hard'),
            (2, 1, 'Easy'),
            (2, 2, 'Medium'),
            (2, 3, 'Hard');
            """.trimIndent()
        )

        db.execSQL(
            """
            INSERT INTO waste_category (name, bin_color, description) VALUES
            ('Biodegradable',  'green',  'Food scraps and other organic waste'),
            ('Recyclable',     'blue',   'Plastic bottles, cans, paper'),
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // simplest: drop and recreate if you ever bump DATABASE_VERSION
        db.execSQL("DROP TABLE IF EXISTS game_level;")
        db.execSQL("DROP TABLE IF EXISTS game;")
        db.execSQL("DROP TABLE IF EXISTS waste_material;")
        db.execSQL("DROP TABLE IF EXISTS waste_category;")
        onCreate(db)
    }
}
