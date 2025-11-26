package com.example.recyclens.data.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.io.File
import java.io.FileOutputStream

class DatabaseHelper private constructor(ctx: Context) :
    SQLiteOpenHelper(ctx, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "recyclens_schema.db"
        private const val DATABASE_VERSION = 1

        @Volatile
        private var INSTANCE: DatabaseHelper? = null

        fun getInstance(context: Context): DatabaseHelper =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: DatabaseHelper(context.applicationContext).also {
                    // Our Database is bundled as an asset, so we need to copy it
                    it.copyIfNeeded()
                    INSTANCE = it
                }
            }
    }

    private val appContext = ctx.applicationContext

    private fun copyIfNeeded() {
        val dbFile = appContext.getDatabasePath(DATABASE_NAME)
        if (dbFile.exists()) {
            return
        }

        // Make sure the directory exists
        dbFile.parentFile?.mkdirs()

        // Copy the database from the assets folder
        appContext.assets.open("databases/$DATABASE_NAME").use { inputStream ->
            dbFile.outputStream().use { outputStream ->
                inputStream.copyTo(outputStream)
            }
        }
    }

    override fun onCreate(db: SQLiteDatabase) {
        // The database is pre-populated, so we don't need to create tables here.
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Handle database upgrades here if the schema changes in future versions.
    }
}
