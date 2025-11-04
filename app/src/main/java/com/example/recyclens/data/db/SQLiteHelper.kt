package com.example.recyclens.data.db

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class SQLiteHelper private constructor(context: Context) :
    SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS waste_category(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL UNIQUE
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS waste_material(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                category_id INTEGER NOT NULL,
                recyclable INTEGER NOT NULL DEFAULT 0, -- 0/1
                description TEXT,
                example_aliases TEXT,
                FOREIGN KEY(category_id) REFERENCES waste_category(id)
            )
        """.trimIndent())

        db.execSQL("""CREATE INDEX IF NOT EXISTS idx_material_name ON waste_material(name)""")
        db.execSQL("""CREATE INDEX IF NOT EXISTS idx_material_alias ON waste_material(example_aliases)""")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldV: Int, newV: Int) {
        // Simple strategy: full rebuild (fine during development)
        db.execSQL("DROP TABLE IF EXISTS waste_material")
        db.execSQL("DROP TABLE IF EXISTS waste_category")
        onCreate(db)
    }

    // --- Optional convenience if you still use these elsewhere ---
    fun insertCategory(name: String): Long {
        val cv = ContentValues().apply { put("name", name) }
        return writableDatabase.insert("waste_category", null, cv)
    }

    fun insertMaterial(
        name: String,
        categoryId: Long,
        description: String?,
        recyclable: Boolean,
        exampleAliases: String?
    ): Long {
        val cv = ContentValues().apply {
            put("name", name)
            put("category_id", categoryId)
            put("description", description)
            put("recyclable", if (recyclable) 1 else 0)
            put("example_aliases", exampleAliases)
        }
        return writableDatabase.insert("waste_material", null, cv)
    }

    fun getAllMaterials(): Cursor =
        readableDatabase.rawQuery(
            """
            SELECT m.id, m.name, m.description, m.recyclable, m.example_aliases, c.name AS category
            FROM waste_material m
            JOIN waste_category c ON c.id = m.category_id
            ORDER BY m.name
            """.trimIndent(),
            null
        )

    companion object {
        private const val DB_NAME = "recyclens.db"
        private const val DB_VERSION = 2  // ⬅ bump when schema changes

        @Volatile private var INSTANCE: SQLiteHelper? = null
        fun getInstance(ctx: Context): SQLiteHelper =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: SQLiteHelper(ctx.applicationContext).also { INSTANCE = it }
            }
    }
}
