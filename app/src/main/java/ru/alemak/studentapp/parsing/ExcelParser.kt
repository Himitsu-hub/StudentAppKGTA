package ru.alemak.studentapp.parsing

import android.content.Context
import android.util.Log
import org.apache.poi.ss.usermodel.Sheet
import org.apache.poi.ss.usermodel.WorkbookFactory
import ru.alemak.studentapp.utils.DateUtils
import java.io.InputStream

object ExcelParser {
    private const val TAG = "ExcelParser"

    // Получаем список доступных групп с подгруппами
    fun getAvailableGroupsWithSubgroups(context: Context): Map<String, List<String>> {
        return try {
            context.assets.open("schedule.xlsx").use { inputStream ->
                getGroupsWithSubgroupsFromExcel(inputStream)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка получения списка групп: ${e.message}", e)
            emptyMap()
        }
    }

    // Парсим расписание для конкретной группы и подгруппы
    fun parseScheduleForGroup(context: Context, groupName: String, subgroup: String? = null): List<ScheduleDay> {
        return try {
            Log.d(TAG, "Парсим расписание для группы: $groupName, подгруппа: $subgroup")

            context.assets.open("schedule.xlsx").use { inputStream ->
                val result = parseExcelFileForGroup(inputStream, groupName, subgroup)
                Log.d(TAG, "Парсинг завершен, найдено дней: ${result.size}")
                result.forEach { day ->
                    Log.d(TAG, "${day.dayName}: ${day.lessons.size} пар")
                }
                result
            }
        } catch (e: Exception) {
            Log.e(TAG, "ОШИБКА при парсинге: ${e.message}", e)
            emptyList()
        }
    }

    private fun getGroupsWithSubgroupsFromExcel(inputStream: InputStream): Map<String, List<String>> {
        val workbook = WorkbookFactory.create(inputStream)
        val sheet = workbook.getSheetAt(0)
        val groupsMap = mutableMapOf<String, MutableList<String>>()

        Log.d(TAG, "=== ПОИСК ГРУПП И ПОДГРУПП В ФАЙЛЕ ===")

        val groupsRow = sheet.getRow(2) ?: return emptyMap()
        val groupRanges = mapOf(
            "И-124" to (13..15),   // N-P
            "У-124" to (21..25),   // V-Z
            "П-124" to (29..30),   // AD-AE
            "ЭТ-124" to (31..32)   // AF-AG
        )

        Log.d(TAG, "Определены диапазоны групп: $groupRanges")

        groupRanges.forEach { (group, range) ->
            groupsMap[group] = mutableListOf()
            Log.d(TAG, "=== ОБРАБОТКА ГРУППЫ: $group ===")
            Log.d(TAG, "Диапазон колонок: $range")

            var groupFound = false
            for (col in range) {
                val groupCell = groupsRow.getCell(col)?.toString()?.trim()
                if (groupCell == group) {
                    groupFound = true
                    break
                }
            }

            if (!groupFound) {
                Log.w(TAG, "Группа $group не найдена в своем диапазоне $range")
            }

            when (group) {
                "И-124" -> groupsMap[group]?.addAll(listOf("1 подгруппа", "2 подгруппа"))
                "У-124", "П-124", "ЭТ-124" -> groupsMap[group]?.addAll(listOf("Основная", "3 подгруппа"))
            }

            Log.d(TAG, "Итоговые подгруппы для $group: ${groupsMap[group]}")
        }

        workbook.close()
        Log.d(TAG, "Итоговый список групп с подгруппами: $groupsMap")
        return groupsMap
    }

    private fun parseExcelFileForGroup(inputStream: InputStream, groupName: String, subgroup: String?): List<ScheduleDay> {
        val workbook = WorkbookFactory.create(inputStream)
        val sheet = workbook.getSheetAt(0)

        val mergedCellsInfo = findMergedCellsForGroup(sheet, groupName)
        val groupColumn = findGroupColumn(sheet, groupName, subgroup)
        if (groupColumn == -1) {
            Log.e(TAG, "Группа $groupName (подгруппа: $subgroup) не найдена в файле")
            workbook.close()
            return emptyList()
        }

        Log.d(TAG, "Группа $groupName (подгруппа: $subgroup) найдена в колонке $groupColumn (${toExcelColumn(groupColumn)})")
        val scheduleDays = parseScheduleWithMerges(sheet, groupName, subgroup, groupColumn, mergedCellsInfo)

        workbook.close()
        Log.d(TAG, "ИТОГО: создано ${scheduleDays.size} дней расписания")
        return scheduleDays
    }

    private fun findMergedCellsForGroup(sheet: Sheet, groupName: String): List<MergedCellInfo> {
        val mergedCells = mutableListOf<MergedCellInfo>()
        val groupColumns = when (groupName) {
            "И-124" -> (13..15).toList()
            "У-124" -> (21..25).toList()
            "П-124" -> (29..30).toList()
            "ЭТ-124" -> (31..32).toList()
            else -> emptyList()
        }

        if (groupColumns.isEmpty()) {
            Log.w(TAG, "Неизвестная группа: $groupName")
            return mergedCells
        }

        for (mergedRegion in sheet.mergedRegions) {
            val intersectsWithGroup = groupColumns.any { col ->
                col >= mergedRegion.firstColumn && col <= mergedRegion.lastColumn
            }
            if (intersectsWithGroup) {
                mergedCells.add(
                    MergedCellInfo(
                        firstRow = mergedRegion.firstRow,
                        lastRow = mergedRegion.lastRow,
                        firstColumn = mergedRegion.firstColumn,
                        lastColumn = mergedRegion.lastColumn,
                        rowCount = mergedRegion.lastRow - mergedRegion.firstRow + 1,
                        colCount = mergedRegion.lastColumn - mergedRegion.firstColumn + 1
                    )
                )
            }
        }

        Log.d(TAG, "Найдено объединенных ячеек для $groupName: ${mergedCells.size}")
        mergedCells.forEach {
            Log.d(TAG, "Объединение: строки ${it.firstRow}-${it.lastRow}, колонки ${it.firstColumn}-${it.lastColumn} (${it.rowCount}x${it.colCount})")
        }
        return mergedCells
    }

    data class MergedCellInfo(
        val firstRow: Int,
        val lastRow: Int,
        val firstColumn: Int,
        val lastColumn: Int,
        val rowCount: Int,
        val colCount: Int
    )

    private fun findGroupColumn(sheet: Sheet, groupName: String, subgroup: String?): Int {
        val groupRanges = mapOf(
            "И-124" to (13..15),
            "У-124" to (21..25),
            "П-124" to (29..30),
            "ЭТ-124" to (31..32)
        )

        val range = groupRanges[groupName] ?: return -1
        Log.d(TAG, "Поиск группы '$groupName' с подгруппой '$subgroup' в диапазоне $range")

        val chosenColumn = when (groupName) {
            "И-124" -> when (subgroup) {
                "1 подгруппа" -> 13
                "2 подгруппа" -> 15
                else -> 13
            }
            "У-124" -> when (subgroup) {
                "Основная" -> 21
                "3 подгруппа" -> 25
                else -> 21
            }
            "П-124" -> when (subgroup) {
                "Основная" -> 29
                "3 подгруппа" -> 30
                else -> 29
            }
            "ЭТ-124" -> when (subgroup) {
                "Основная" -> 31
                "3 подгруппа" -> 32
                else -> 31
            }
            else -> range.first
        }

        val testRow = sheet.getRow(4)
        val candidateCols = range.filter { col ->
            testRow?.getCell(col)?.toString()?.trim()?.isNotEmpty() == true
        }
        val finalCol = if (candidateCols.isNotEmpty()) {
            if (subgroup?.contains("2") == true && candidateCols.size > 1) candidateCols.last() else candidateCols.first()
        } else {
            chosenColumn
        }

        Log.d(TAG, "Для '$groupName' ($subgroup) выбрана колонка $finalCol (${toExcelColumn(finalCol)})")
        return finalCol
    }

    private fun parseScheduleWithMerges(
        sheet: Sheet,
        groupName: String,
        subgroup: String?,
        groupColumn: Int,
        mergedCellsInfo: List<MergedCellInfo>
    ): List<ScheduleDay> {
        val scheduleDays = mutableListOf<ScheduleDay>()
        val currentWeekType = getCurrentWeekType()

        val days = listOf(
            "Понедельник" to 4,
            "Вторник" to 18,
            "Среда" to 32,
            "Четверг" to 46,
            "Пятница" to 60,
            "Суббота" to 74
        )

        days.forEach { (dayName, startRow) ->
            Log.d(TAG, "Обрабатываем день: $dayName (начинается со строки $startRow)")
            val lessons = parseLessonsForDayWithMerges(
                sheet, startRow, groupColumn, groupName, subgroup,
                currentWeekType, mergedCellsInfo
            )
            Log.d(TAG, "Для дня $dayName найдено пар: ${lessons.size}")
            if (lessons.isNotEmpty()) {
                scheduleDays.add(ScheduleDay(dayName, lessons))
            }
        }

        return scheduleDays
    }

    private fun getColumnsForSubgroup(groupName: String, subgroup: String?): Pair<Int, Int> {
        return when (groupName) {
            "И-124" -> when (subgroup) {
                "1 подгруппа" -> 13 to 14
                "2 подгруппа" -> 15 to 15
                else -> 13 to 14
            }
            "У-124" -> when (subgroup) {
                "Основная" -> 21 to 22
                "3 подгруппа" -> 25 to 25
                else -> 21 to 22
            }
            "П-124" -> when (subgroup) {
                "Основная" -> 29 to 29
                "3 подгруппа" -> 30 to 30
                else -> 29 to 29
            }
            "ЭТ-124" -> when (subgroup) {
                "Основная" -> 31 to 31
                "3 подгруппа" -> 32 to 32
                else -> 31 to 31
            }
            else -> 13 to 14
        }
    }

    private fun parseLessonsForDayWithMerges(
        sheet: Sheet,
        startRow: Int,
        groupColumn: Int,
        groupName: String,
        subgroup: String?,
        currentWeekType: String,
        mergedCellsInfo: List<MergedCellInfo>
    ): List<Lesson> {
        val lessons = mutableListOf<Lesson>()
        val (numeratorColumn, denominatorColumn) = getColumnsForSubgroup(groupName, subgroup)
        Log.d(TAG, "Колонки для $groupName ($subgroup): числитель=$numeratorColumn, знаменатель=$denominatorColumn")

        for (pairIndex in 0..6) {
            val numeratorRowNum = startRow + pairIndex * 2
            val denominatorRowNum = numeratorRowNum + 1
            val pairNumber = pairIndex + 1
            val time = getTimeByPairNumber(pairNumber.toString())
            val selectedColumn = if (currentWeekType == "Числитель") numeratorColumn else denominatorColumn

            val lesson = parseLessonWithMerges(
                sheet, numeratorRowNum, denominatorRowNum, selectedColumn,
                groupName, subgroup, currentWeekType, time, mergedCellsInfo
            )
            if (lesson != null) lessons.add(lesson)
        }
        return lessons
    }

    private fun parseLessonWithMerges(
        sheet: Sheet,
        numeratorRowNum: Int,
        denominatorRowNum: Int,
        groupColumn: Int,
        groupName: String,
        subgroup: String?,
        currentWeekType: String,
        time: String,
        mergedCellsInfo: List<MergedCellInfo>
    ): Lesson? {
        val numeratorText = getCellText(sheet, numeratorRowNum, groupColumn)
        val denominatorText = getCellText(sheet, denominatorRowNum, groupColumn)
        Log.d(TAG, "Строки: числитель=${toExcelCell(numeratorRowNum, groupColumn)}, знаменатель=${toExcelCell(denominatorRowNum, groupColumn)}")
        Log.d(TAG, "Тексты: числитель='${numeratorText}', знаменатель='${denominatorText}'")

        val numeratorMergeInfo = findMergeInfoForCell(mergedCellsInfo, numeratorRowNum, groupColumn)
        val denominatorMergeInfo = findMergeInfoForCell(mergedCellsInfo, denominatorRowNum, groupColumn)

        // Горизонтальное объединение
        val hasHorizontalMerge = mergedCellsInfo.any { merge ->
            numeratorRowNum in merge.firstRow..merge.lastRow &&
                    denominatorRowNum in merge.firstRow..merge.lastRow &&
                    merge.firstColumn <= 13 && merge.lastColumn >= 15
        }

        if (hasHorizontalMerge) {
            val text = if (currentWeekType == "Числитель") numeratorText ?: denominatorText else denominatorText ?: numeratorText
            return text?.let { parseLessonFromText(it, time) }
        }

        // Вертикальное объединение
        if (numeratorMergeInfo?.isVertical() == true || denominatorMergeInfo?.isVertical() == true) {
            val merge = numeratorMergeInfo ?: denominatorMergeInfo
            val text = numeratorText ?: denominatorText
            return text?.let { parseLessonFromText(it, time) }
        }

        // Выбор по типу недели
        var text = if (currentWeekType == "Числитель") numeratorText else denominatorText

        // Фолбэк для 1 подгруппы
        if (subgroup?.contains("1") == true && currentWeekType == "Знаменатель" && text.isNullOrBlank()) {
            val altText = getCellText(sheet, denominatorRowNum, 13)
            if (!altText.isNullOrBlank()) text = altText
        }

        if (text.isNullOrBlank()) return null
        return parseLessonFromText(text, time)
    }

    private fun findMergeInfoForCell(mergedCellsInfo: List<MergedCellInfo>, row: Int, column: Int): MergedCellInfo? {
        return mergedCellsInfo.find { merge ->
            row in merge.firstRow..merge.lastRow && column in merge.firstColumn..merge.lastColumn
        }
    }

    private fun MergedCellInfo.isVertical(): Boolean = rowCount >= 2
    private fun MergedCellInfo.isHorizontalBetweenSubgroups(): Boolean = colCount > 1 && rowCount == 1
    private fun MergedCellInfo.isFullMerge(): Boolean = rowCount >= 2 && colCount >= 2

    private fun getCellText(sheet: Sheet, rowNum: Int, colNum: Int): String? {
        val row = sheet.getRow(rowNum) ?: return null
        val cell = row.getCell(colNum)
        var text = cell?.toString()?.trim()
        if (!text.isNullOrEmpty() && text != "null" && text != "-" && text != " ") return text

        for (region in sheet.mergedRegions) {
            if (rowNum in region.firstRow..region.lastRow && colNum in region.firstColumn..region.lastColumn) {
                val topLeftCell = sheet.getRow(region.firstRow)?.getCell(region.firstColumn)
                text = topLeftCell?.toString()?.trim()
                if (!text.isNullOrEmpty() && text != "null" && text != "-") return text
            }
        }
        return null
    }

    private fun parseLessonFromText(text: String, time: String): Lesson? {
        var subject = text
        var teacher = ""
        var room = ""
        var type = "занятие"

        try {
            type = when {
                text.containsAny("лек", "лекция") -> "лекция"
                text.containsAny("практ", "практика") -> "практика"
                text.containsAny("лаб", "лабораторная") -> "лабораторная"
                else -> "занятие"
            }

            val lines = text.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
            if (lines.isNotEmpty()) {
                subject = lines[0]
                for (i in lines.size - 1 downTo 0) {
                    val roomMatch = Regex("\\d+").find(lines[i])
                    if (roomMatch != null) {
                        room = roomMatch.value
                        teacher = lines.subList(1, i).joinToString(", ")
                        break
                    }
                }
                if (room.isEmpty() && lines.size > 1) teacher = lines.subList(1, lines.size).joinToString(", ")
            }

            subject = cleanSubject(subject)
            return Lesson(time, subject, teacher, room, type)
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка при парсинге занятия из текста: '$text'", e)
            return null
        }
    }

    private fun cleanSubject(subject: String): String {
        return subject.replace("лекция", "", ignoreCase = true)
            .replace("лек", "", ignoreCase = true)
            .replace("практика", "", ignoreCase = true)
            .replace("практ", "", ignoreCase = true)
            .replace("лабораторная", "", ignoreCase = true)
            .replace("лаб", "", ignoreCase = true)
            .replace("  ", " ")
            .trim()
    }

    private fun getTimeByPairNumber(pairNumber: String) = when (pairNumber) {
        "1" -> "8:00-09:25"
        "2" -> "09:35-11:00"
        "3" -> "12:00-13:25"
        "4" -> "13:35-15:00"
        "5" -> "15:10-16:35"
        "6" -> "17:45-19:10"
        "7" -> "19:20-20:45"
        else -> "Неизвестно"
    }

    fun getCurrentWeekType(): String = DateUtils.getCurrentWeekType()

    private fun String.containsAny(vararg strings: String) = strings.any { this.contains(it, ignoreCase = true) }

    // ==================== HELPERS ====================
    private fun toExcelColumn(col: Int): String {
        var c = col
        var colStr = ""
        do {
            colStr = ('A' + (c % 26)) + colStr
            c = c / 26 - 1
        } while (c >= 0)
        return colStr
    }

    private fun toExcelCell(row: Int, col: Int): String = "${toExcelColumn(col)}${row + 1}"
}
