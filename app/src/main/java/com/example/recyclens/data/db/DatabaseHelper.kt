package com.example.recyclens.data.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class DatabaseHelper private constructor(ctx: Context) :

    SQLiteOpenHelper(ctx, DB_NAME, null, DB_VERSION) {

    companion object {
        private const val DB_NAME = "recyclens_schema.db"
        private const val DB_VERSION = 1

        @Volatile
        private var INSTANCE: DatabaseHelper? = null

        fun get(ctx: Context): DatabaseHelper =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: DatabaseHelper(ctx.applicationContext).also {
                    it.copyIfNeeded()
                    INSTANCE = it
                }
            }
    }

    private val appContext = ctx.applicationContext

    private fun copyIfNeeded() {
        val dbFile = appContext.getDatabasePath(DB_NAME)
        if (dbFile.exists()) return

        dbFile.parentFile?.mkdirs()
        appContext.assets.open(DB_NAME).use { input ->
            dbFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
    }

    override fun onCreate(db: SQLiteDatabase) {
        // Do nothing here because DB is already pre-populated
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Handle upgrades later if needed
    }
}
