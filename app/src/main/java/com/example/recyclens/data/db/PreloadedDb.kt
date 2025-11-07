// com/example/recyclens/data/db/PreloadedDb.kt
package com.example.recyclens.data.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import java.io.File
import java.io.FileOutputStream

object PreloadedDb {
    private const val DB_NAME = "recyclens.db"

    fun installIfNeeded(context: Context) {
        val dbPath = context.getDatabasePath(DB_NAME)
        if (dbPath.exists()) return

        dbPath.parentFile?.mkdirs()
        context.assets.open("databases/$DB_NAME").use { input ->
            FileOutputStream(dbPath).use { output ->
                input.copyTo(output)
            }
        }
    }

    fun open(context: Context): SQLiteDatabase {
        installIfNeeded(context)
        val dbFile: File = context.getDatabasePath(DB_NAME)
        return SQLiteDatabase.openDatabase(
            dbFile.path,
            null,
            SQLiteDatabase.OPEN_READWRITE
        )
    }
}
