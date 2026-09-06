package ru.alemak.studentapp.data.model

/** Local faculty catalog (mirrors iOS FacultyCatalog). */
object FacultyCatalog {
    const val FAE = "fae"
    const val MTF = "mtf"
    const val MASTERS = "masters"

    data class Item(val id: String, val short: String)

    val all: List<Item> = listOf(
        Item(FAE, "АиЭ"),
        Item(MTF, "МТФ"),
        Item(MASTERS, "Маг."),
    )

    fun shortName(id: String): String = when (id) {
        MTF -> "МТФ"
        MASTERS -> "Маг."
        else -> "АиЭ"
    }

    fun fullName(id: String): String = when (id) {
        MTF -> "Механико-технологический факультет"
        MASTERS -> "Магистратура (очное)"
        else -> "Факультет автоматики и электроники"
    }

    fun courses(forId: String): List<Int> = when (forId) {
        MTF -> (1..5).toList()
        MASTERS -> listOf(2)
        else -> (1..4).toList()
    }

    fun normalize(id: String?): String = when (id) {
        MTF -> MTF
        MASTERS -> MASTERS
        else -> FAE
    }
}
