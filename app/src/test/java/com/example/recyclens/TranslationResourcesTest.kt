package com.example.recyclens

import com.example.recyclens.data.db.AppDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class TranslationResourcesTest {

    @Test
    fun valuesAndValuesTlContainTheSameStringKeys() {
        val values = stringNamesFrom(projectFile("src/main/res/values/strings.xml"))
        val valuesTl = stringNamesFrom(projectFile("src/main/res/values-tl/strings.xml"))

        assertEquals("English and Filipino string keys should stay in sync.", values, valuesTl)
    }

    @Test
    fun packagedDatabaseAssetExistsAtTheConfiguredPath() {
        val assetFile = projectFile("src/main/assets/${AppDatabase.DB_ASSET_PATH.removePrefix("databases/")}")
        val fallbackAssetFile = projectFile(AppDatabase.DB_ASSET_PATH.let { "src/main/assets/$it" })

        assertTrue(
            "Expected packaged database asset at ${fallbackAssetFile.path}",
            assetFile.exists() || fallbackAssetFile.exists()
        )
    }

    @Test
    fun legacyLanguageSuffixKeysAreRetiredExceptToggleLabels() {
        val names = stringNamesFrom(projectFile("src/main/res/values/strings.xml"))

        val allowList = setOf("label_en", "label_tl")
        val legacySuffixKeys = names.filter {
            (it.endsWith("_en") || it.endsWith("_tl")) && it !in allowList
        }

        assertTrue(
            "Legacy *_en/*_tl keys should be retired. Found: ${legacySuffixKeys.sorted()}",
            legacySuffixKeys.isEmpty()
        )
    }

    private fun stringNamesFrom(file: File): Set<String> {
        val document = DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(file)
        val nodes = document.getElementsByTagName("string")
        return buildSet {
            for (index in 0 until nodes.length) {
                val node = nodes.item(index)
                val name = node.attributes?.getNamedItem("name")?.nodeValue
                if (!name.isNullOrBlank()) add(name)
            }
        }
    }

    private fun projectFile(relativePath: String): File {
        val candidates = listOf(
            File(relativePath),
            File("app/$relativePath")
        )

        return candidates.firstOrNull(File::exists)
            ?: candidates.first()
    }
}
