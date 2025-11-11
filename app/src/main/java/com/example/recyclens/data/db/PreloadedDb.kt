// com/example/recyclens/data/db/PreloadedDb.kt
package com.example.recyclens.data.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import java.io.File
import java.io.FileOutputStream

object PreloadedDb {
    // ⬇️ Match this to your actual file in assets/databases/
    private const val DB_NAME = "recyclens.db"      // or "recyclensdb.db"
    private const val ASSET_PATH = "databases/$DB_NAME"

    @Volatile private var db: SQLiteDatabase? = null

    /** Call once in Application.onCreate() */
    fun init(context: Context) {
        if (db?.isOpen == true) return
        installIfNeeded(context)
        db = openInternal(context)
        enablePragmas(db)
    }

    /** Get an open database (after init). */
    fun db(): SQLiteDatabase {
        return requireNotNull(db) { "PreloadedDb not initialized. Call PreloadedDb.init(context) first." }
    }

    /** Optional: close on app shutdown */
    fun close() {
        try { db?.close() } catch (_: Throwable) {}
        db = null
    }

    // ---------------- internals ----------------

    private fun enablePragmas(db: SQLiteDatabase?) {
        try {
            db?.execSQL("PRAGMA foreign_keys = ON;")
            // If you like WAL for perf (optional):
            // db?.enableWriteAheadLogging()
        } catch (t: Throwable) {
            Log.w("PreloadedDb", "PRAGMA setup failed", t)
        }
    }

    fun installIfNeeded(context: Context) {
        val dbFile = context.getDatabasePath(DB_NAME)
        if (dbFile.exists()) return

        dbFile.parentFile?.mkdirs()

        // Copy via temp file to avoid partial copies
        val tmp = File(dbFile.parentFile, "$DB_NAME.tmp")
        context.assets.open(ASSET_PATH).use { input ->
            FileOutputStream(tmp).use { output -> input.copyTo(output) }
        }
        if (!tmp.renameTo(dbFile)) {
            // Fallback: delete and copy directly
            FileOutputStream(dbFile).use { output ->
                context.assets.open(ASSET_PATH).use { input -> input.copyTo(output) }
            }
            tmp.delete()
        }
    }

    private fun openInternal(context: Context): SQLiteDatabase {
        val dbFile: File = context.getDatabasePath(DB_NAME)
        return try {
            SQLiteDatabase.openDatabase(
                dbFile.path,
                null,
                SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.NO_LOCALIZED_COLLATORS
            )
        } catch (e: Throwable) {
            Log.w("PreloadedDb", "Open failed, recopying DB from assets…", e)
            // Recopy once if open fails (corrupted/outdated local copy)
            dbFile.delete()
            installIfNeeded(context)
            SQLiteDatabase.openDatabase(
                dbFile.path,
                null,
                SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.NO_LOCALIZED_COLLATORS
            )
        }
    }
}
