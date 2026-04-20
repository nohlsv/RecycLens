package com.example.recyclens

import com.example.recyclens.data.db.AppDatabase
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.sql.DriverManager

class DatabaseAssetSmokeTest {

    @Test
    fun scannerLookupQueryReturnsRowsFromPackagedDatabase() {
        val dbFile = packagedDatabaseFile()
        assertTrue("Packaged database is missing at ${dbFile.path}", dbFile.exists())

        queryInt(
            dbFile,
            """
            SELECT COUNT(*)
            FROM waste_material wm
            JOIN waste_category wc ON wm.category_id = wc.category_id
            WHERE wm.name_en IS NOT NULL
              AND wm.name_tl IS NOT NULL
              AND wm.image_path IS NOT NULL
              AND wm.image_path <> ''
            """.trimIndent()
        ) { count ->
            assertTrue("Expected at least one scanner-usable material row in packaged DB", count > 0)
        }
    }

    @Test
    fun gameTablesContainLocalizedLevelsAndStreetConfig() {
        val dbFile = packagedDatabaseFile()
        assertTrue("Packaged database is missing at ${dbFile.path}", dbFile.exists())

        queryInt(
            dbFile,
            """
            SELECT COUNT(*)
            FROM game
            WHERE game_title IN ('Street Cleanup', 'Trash Sorting')
              AND game_level IN ('Easy', 'Medium', 'Hard')
            """.trimIndent()
        ) { count ->
            assertTrue("Expected game rows for Street Cleanup / Trash Sorting level records", count > 0)
        }

        queryInt(
            dbFile,
            "SELECT COUNT(*) FROM street_cleanup_game"
        ) { count ->
            assertTrue("Expected street_cleanup_game rows for level configs", count > 0)
        }
    }

    private fun queryInt(dbFile: File, sql: String, assertion: (Int) -> Unit) {
        val url = "jdbc:sqlite:${dbFile.absolutePath.replace('\\', '/')}"
        DriverManager.getConnection(url).use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery(sql).use { resultSet ->
                    assertTrue("Query returned no rows: $sql", resultSet.next())
                    assertion(resultSet.getInt(1))
                }
            }
        }
    }

    private fun packagedDatabaseFile(): File {
        val candidates = listOf(
            File("src/main/assets/${AppDatabase.DB_ASSET_PATH}"),
            File("app/src/main/assets/${AppDatabase.DB_ASSET_PATH}")
        )

        return candidates.firstOrNull(File::exists) ?: candidates.last()
    }
}
