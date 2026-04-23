package com.example.recyclens.data

import android.content.Context
import androidx.annotation.StringRes
import com.example.recyclens.R

object WasteCatalog {

    data class WasteSpec(
        val dbNameEn: String,
        val dbNameTl: String,
        @StringRes val labelResId: Int,
        val categoryId: Int,
        val resNames: Set<String> = emptySet(),
        val aliases: Set<String> = emptySet()
    )

    private const val BIO_CATEGORY_ID = 1
    private const val NON_BIO_CATEGORY_ID = 2

    private val specs = listOf(
        WasteSpec(
            dbNameEn = "Banana Peel",
            dbNameTl = "Balat ng Saging",
            labelResId = R.string.item_banana_peel,
            categoryId = BIO_CATEGORY_ID,
            resNames = setOf("ic_trash_banana"),
            aliases = setOf("banana", "banana peel", "banana peels")
        ),
        WasteSpec(
            dbNameEn = "Fruit",
            dbNameTl = "Prutas",
            labelResId = R.string.item_fruit,
            categoryId = BIO_CATEGORY_ID,
            resNames = setOf("ic_trash_fruit"),
            aliases = setOf("fruit", "apple", "apple core", "mango", "mango peel", "orange")
        ),
        WasteSpec(
            dbNameEn = "Vegetable",
            dbNameTl = "Gulay",
            labelResId = R.string.item_vegetable,
            categoryId = BIO_CATEGORY_ID,
            aliases = setOf("vegetable", "ampalaya", "kangkong", "okra", "eggplant", "talong")
        ),
        WasteSpec(
            dbNameEn = "Fruit and Vegetable Peels",
            dbNameTl = "Balat ng prutas at gulay",
            labelResId = R.string.item_fruit_vegetable_peels,
            categoryId = BIO_CATEGORY_ID,
            aliases = setOf("fruit vegetable peels", "fruit and vegetable peels", "peels", "vegetable peels")
        ),
        WasteSpec(
            dbNameEn = "Leaf",
            dbNameTl = "Dahon",
            labelResId = R.string.item_leaf,
            categoryId = BIO_CATEGORY_ID,
            resNames = setOf("ic_trash_leaf"),
            aliases = setOf("leaf", "leaves")
        ),
        WasteSpec(
            dbNameEn = "Grass",
            dbNameTl = "Damo",
            labelResId = R.string.item_grass,
            categoryId = BIO_CATEGORY_ID,
            resNames = setOf("ic_trash_grass"),
            aliases = setOf("grass", "bermuda grass")
        ),
        WasteSpec(
            dbNameEn = "Stationery Paper",
            dbNameTl = "Papel",
            labelResId = R.string.item_paper,
            categoryId = BIO_CATEGORY_ID,
            resNames = setOf("ic_trash_paper"),
            aliases = setOf("paper", "stationery paper", "bond paper", "construction paper", "intermediate pad")
        ),
        WasteSpec(
            dbNameEn = "Tissue",
            dbNameTl = "Tisyu",
            labelResId = R.string.item_tissue,
            categoryId = BIO_CATEGORY_ID,
            resNames = setOf("ic_trash_tissue"),
            aliases = setOf("tissue", "tissue core", "tissue roll")
        ),
        WasteSpec(
            dbNameEn = "Plastic Cup",
            dbNameTl = "Plastik na Baso",
            labelResId = R.string.item_plastic_cup,
            categoryId = NON_BIO_CATEGORY_ID,
            resNames = setOf("ic_trash_plastic_cup"),
            aliases = setOf("plastic cup", "plastic", "cup")
        ),
        WasteSpec(
            dbNameEn = "Bottle",
            dbNameTl = "Bote",
            labelResId = R.string.item_plastic_bottle,
            categoryId = NON_BIO_CATEGORY_ID,
            resNames = setOf("ic_trash_bottle"),
            aliases = setOf("bottle", "pet bottle", "plastic bottle")
        ),
        WasteSpec(
            dbNameEn = "Candy Wrapper",
            dbNameTl = "Balot ng Kendi",
            labelResId = R.string.item_candy_wrapper,
            categoryId = NON_BIO_CATEGORY_ID,
            resNames = setOf("ic_trash_wrapper"),
            aliases = setOf("candy wrapper", "snack wrapper", "plastic wrapper", "wrapper")
        ),
        WasteSpec(
            dbNameEn = "Styrofoam Box",
            dbNameTl = "Styro na Lalagyan",
            labelResId = R.string.item_styrofoam_box,
            categoryId = NON_BIO_CATEGORY_ID,
            resNames = setOf("ic_trash_styro"),
            aliases = setOf("styrofoam", "styrofoam box", "styrofoam cup", "styrofoam tray", "styro", "tray", "foam")
        ),
        WasteSpec(
            dbNameEn = "Can",
            dbNameTl = "Lata",
            labelResId = R.string.item_can,
            categoryId = NON_BIO_CATEGORY_ID,
            aliases = setOf("can", "tin can", "lata")
        )
    )

    fun findByResName(resName: String?): WasteSpec? {
        val normalized = normalize(resName) ?: return null
        return specs.firstOrNull { normalized in it.resNames.map(::normalize).filterNotNull().toSet() }
    }

    fun findByDbName(name: String?): WasteSpec? {
        val normalized = normalize(name) ?: return null
        return specs.firstOrNull { spec ->
            normalized == normalize(spec.dbNameEn) ||
                normalized == normalize(spec.dbNameTl) ||
                normalized in spec.aliases.map(::normalize).filterNotNull().toSet()
        }
    }

    fun findByPredictionLabel(label: String?): WasteSpec? {
        val normalized = normalize(label) ?: return null
        return specs.firstOrNull { spec ->
            normalized == normalize(spec.dbNameEn) ||
                normalized in spec.aliases.map(::normalize).filterNotNull().toSet()
        }
    }

    fun localizedLabel(context: Context, spec: WasteSpec?): String? {
        return spec?.let { context.getString(it.labelResId) }
    }

    fun localizedLabelForResName(context: Context, resName: String?): String? {
        return localizedLabel(context, findByResName(resName))
    }

    fun filipinoNameForEnglish(name: String?): String? {
        return findByDbName(name)?.dbNameTl
    }

    fun englishNameForAnyName(name: String?): String? {
        return findByDbName(name)?.dbNameEn
    }

    fun categoryIdForLabel(label: String?): Int? {
        return findByPredictionLabel(label)?.categoryId
    }

    private fun normalize(value: String?): String? {
        return value
            ?.trim()
            ?.lowercase()
            ?.replace("_", " ")
            ?.replace("-", " ")
            ?.replace(Regex("\\s+"), " ")
            ?.takeIf { it.isNotBlank() }
    }
}
