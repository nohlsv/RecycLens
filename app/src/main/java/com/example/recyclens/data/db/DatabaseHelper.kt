package com.example.recyclens.data.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log

class DatabaseHelper private constructor(ctx: Context) :
    SQLiteOpenHelper(ctx, DB_NAME, null, DB_VERSION) {

    companion object {
        private const val DB_NAME = "recyclens.db"
        private const val DB_VERSION = 1
        @Volatile private var INSTANCE: DatabaseHelper? = null

        fun get(ctx: Context): DatabaseHelper =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: DatabaseHelper(ctx.applicationContext).also { INSTANCE = it }
            }
    }

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.execSQL("PRAGMA foreign_keys = ON;")
        // Optional: enable WAL for better performance on many writes
        // db.enableWriteAheadLogging()
    }

    override fun onCreate(db: SQLiteDatabase) {
        // ===== CREATE TABLES (no AUTOINCREMENT anywhere) =====
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS waste_category (
              category_id          INTEGER PRIMARY KEY,
              category_name        TEXT NOT NULL UNIQUE,
              bin_color            TEXT NOT NULL,
              bin_iconPath         TEXT,
              category_description TEXT
            );
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS waste_material (
              material_id    INTEGER PRIMARY KEY,
              material_name  TEXT NOT NULL,
              material_img   TEXT,      -- keep NULL for now (no images yet)
              material_type  TEXT,
              category_id    INTEGER NOT NULL,
              FOREIGN KEY (category_id)
                REFERENCES waste_category (category_id)
                ON UPDATE CASCADE
                ON DELETE RESTRICT
            );
        """.trimIndent())
        db.execSQL("""CREATE INDEX IF NOT EXISTS idx_material_category ON waste_material(category_id);""")

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS capture_image (
              capture_id         INTEGER PRIMARY KEY,
              capture_resolution TEXT,
              capture_framerate  INTEGER
            );
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS upload_image (
              upload_id         INTEGER PRIMARY KEY,
              upload_resolution TEXT,
              upload_size       INTEGER,
              file_path         TEXT NOT NULL
            );
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS waste_classifier (
              process_id            INTEGER PRIMARY KEY,
              capture_id            INTEGER NOT NULL,
              waste_classification  TEXT NOT NULL,
              FOREIGN KEY (capture_id)
                REFERENCES capture_image (capture_id)
                ON UPDATE CASCADE
                ON DELETE CASCADE
            );
        """.trimIndent())
        db.execSQL("""CREATE INDEX IF NOT EXISTS idx_classifier_capture ON waste_classifier(capture_id);""")

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS game (
              game_id        INTEGER PRIMARY KEY,
              game_title     TEXT NOT NULL,
              game_timer     INTEGER,
              current_score  INTEGER DEFAULT 0
            );
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS game_item (
              item_id      INTEGER PRIMARY KEY,
              category_id  INTEGER NOT NULL,
              image_path   TEXT,
              FOREIGN KEY (category_id)
                REFERENCES waste_category (category_id)
                ON UPDATE CASCADE
                ON DELETE RESTRICT
            );
        """.trimIndent())
        db.execSQL("""CREATE INDEX IF NOT EXISTS idx_game_item_category ON game_item(category_id);""")

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS game_result (
              result_id     INTEGER PRIMARY KEY,
              game_id       INTEGER NOT NULL,
              total_score   INTEGER DEFAULT 0,
              correct_count INTEGER DEFAULT 0,
              wrong_count   INTEGER DEFAULT 0,
              FOREIGN KEY (game_id)
                REFERENCES game (game_id)
                ON UPDATE CASCADE
                ON DELETE CASCADE
            );
        """.trimIndent())
        db.execSQL("""CREATE INDEX IF NOT EXISTS idx_result_game ON game_result(game_id);""")

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS trash_sorting_game (
              trash_id       INTEGER PRIMARY KEY,
              category_name  TEXT NOT NULL,
              material_name  TEXT NOT NULL
            );
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS street_cleanup_game (
              street_id     INTEGER PRIMARY KEY,
              location_name TEXT NOT NULL,
              material_name TEXT,
              trash_count   INTEGER DEFAULT 0,
              timer         INTEGER DEFAULT 0
            );
        """.trimIndent())

        db.execSQL("""CREATE TABLE IF NOT EXISTS locale ( language TEXT PRIMARY KEY );""")

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS text_to_speech (
              tts_id      INTEGER PRIMARY KEY,
              language    TEXT,
              pitch       REAL DEFAULT 1.0,
              speechRate  REAL DEFAULT 1.0,
              initStatus  INTEGER DEFAULT 0,
              FOREIGN KEY (language)
                REFERENCES locale (language)
                ON UPDATE CASCADE
                ON DELETE SET NULL
            );
        """.trimIndent())

        // ===== OPTIONAL: minimal safe seed (no images/material rows) =====
        db.beginTransaction()
        try {
            db.execSQL("INSERT OR IGNORE INTO locale(language) VALUES ('en-US');")
            db.execSQL("INSERT OR IGNORE INTO locale(language) VALUES ('fil-PH');")

            db.execSQL("""
                INSERT OR IGNORE INTO waste_category (category_id, category_name, bin_color)
                VALUES (1, 'Recyclable', 'blue')
            """)
            db.execSQL("""
                INSERT OR IGNORE INTO waste_category (category_id, category_name, bin_color)
                VALUES (2, 'Biodegradable', 'green')
            """)
            db.execSQL("""
                INSERT OR IGNORE INTO waste_category (category_id, category_name, bin_color)
                VALUES (3, 'Residual', 'gray')
            """)
            db.execSQL("""
                INSERT OR IGNORE INTO game (game_id, game_title, game_timer, current_score)
                VALUES (1, 'Trash Sorting', 60, 0)
            """)
            db.execSQL("""
                INSERT OR IGNORE INTO game (game_id, game_title, game_timer, current_score)
                VALUES (2, 'Street Cleanup', 90, 0)
            """)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }

        Log.d("DatabaseHelper", "Schema created.")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Add ALTER TABLE / data migrations here when you bump DB_VERSION.
    }
}
