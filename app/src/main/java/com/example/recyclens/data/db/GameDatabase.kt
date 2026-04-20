package com.example.recyclens.data.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase

object GameDatabase {

    fun open(context: Context): SQLiteDatabase {
        // Ensure Room copies the packaged asset before game screens query or update it.
        AppDatabase.getInstance(context).openHelper.writableDatabase

        val dbPath = context.getDatabasePath(AppDatabase.DB_NAME)
        return SQLiteDatabase.openDatabase(
            dbPath.path,
            null,
            SQLiteDatabase.OPEN_READWRITE
        )
    }
}
