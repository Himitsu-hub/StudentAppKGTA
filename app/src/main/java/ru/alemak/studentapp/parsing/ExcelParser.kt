package ru.alemak.studentapp.parsing

import android.content.Context
import android.util.Log
import org.apache.poi.ss.usermodel.Sheet
import org.apache.poi.ss.usermodel.WorkbookFactory
import ru.alemak.studentapp.screens.HolidayUtils
import ru.alemak.studentapp.utils.DateUtils
import java.io.InputStream

object ExcelParser {
    private const val TAG = "ExcelParser"

    fun getAvailableGroupsWithSubgroups(context: Context, course: Int): Map<String, List<String>> {
        return try {
            val fileName = "schedule${course}.xlsx"
            context.assets.open(fileName).use { inputStream ->
                getGroupsWithSubgroupsFromExcel(inputStream, course)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка получения списка групп для курса $course: ${e.message}", e)
            emptyMap()
        }
    }


    fun parseScheduleForGroup(context: Context, course: Int, groupName: String, subgroup: String? = null): List<ScheduleDay> {
        return try {
            val fileName = "schedule${course}.xlsx"
            Log.d(TAG, "Парсим расписание (курс $course): $fileName для группы $groupName, подгруппа: $subgroup")

            context.assets.open(fileName).use { inputStream ->
                val result = parseExcelFileForGroup(inputStream, groupName, subgroup)
                Log.d(TAG, "Парсинг завершен, найдено дней: ${result.size}")
                result
            }
        } catch (e: Exception) {
            Log.e(TAG, "ОШИБКА при парсинге курса $course: ${e.message}", e)
            emptyList()
        }
    }


    private fun getGroupsWithSubgroupsFromExcel(inputStream: InputStream, course: Int): Map<String, List<String>> {
        val workbook = WorkbookFactory.create(inputStream)
        val sheet = workbook.getSheetAt(0)
        val groupsMap = mutableMapOf<String, MutableList<String>>()

        // 🔢 Определяем номер по курсу
        val courseSuffix = when (course) {
            1 -> "125"
            2 -> "124"
            3 -> "123"
            4 -> "122"
            else -> "124"
        }

        Log.d(TAG, "=== ПОИСК ГРУПП ДЛЯ КУРСА $course ($courseSuffix) ===")

        val groupsRow = sheet.getRow(2) ?: return emptyMap()
        val groupRanges = mapOf(
            "И-$courseSuffix" to (13..15),   // N–P
            "У-$courseSuffix" to (21..25),   // V–Z
            "П-$courseSuffix" to (29..30),   // AD–AE
            "ЭТ-$courseSuffix" to (31..32)   // AF–AG
        )

        groupRanges.forEach { (group, range) ->
            groupsMap[group] = mutableListOf()
            Log.d(TAG, "=== ОБРАБОТКА ГРУППЫ: $group ===")

            when {
                group.startsWith("И-") -> groupsMap[group]?.addAll(listOf("1 подгруппа", "2 подгруппа"))
                else -> groupsMap[group]?.addAll(listOf("Основная", "3 подгруппа"))
            }
        }

        workbook.close()
        Log.d(TAG, "Итоговый список групп ($courseSuffix): $groupsMap")
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
        val groupColumns = when {
            groupName.startsWith("И-") -> (13..15).toList()   // N–P
            groupName.startsWith("У-") -> (21..25).toList()   // V–Z
            groupName.startsWith("П-") -> (29..30).toList()   // AD–AE
            groupName.startsWith("ЭТ-") -> (31..32).toList()  // AF–AG
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
        val range = when {
            groupName.startsWith("И-") -> 13..15
            groupName.startsWith("У-") -> 21..25
            groupName.startsWith("П-") -> 29..30
            groupName.startsWith("ЭТ-") -> 31..32
            else -> return -1
        }

        Log.d(TAG, "Поиск колонки для '$groupName' ($subgroup) в диапазоне $range")

        val chosenColumn = when {
            groupName.startsWith("И-") -> if (subgroup?.contains("2") == true) 15 else 13
            groupName.startsWith("У-") -> if (subgroup?.contains("3") == true) 25 else 21
            groupName.startsWith("П-") -> if (subgroup?.contains("3") == true) 30 else 29
            groupName.startsWith("ЭТ-") -> if (subgroup?.contains("3") == true) 32 else 31
            else -> range.first
        }

        val finalCol = chosenColumn
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
        val LAST_SCHEDULE_ROW = 84  // После этой строки не парсим — там подписи

        val days = listOf(
            "Понедельник" to 4,
            "Вторник" to 18,
            "Среда" to 32,
            "Четверг" to 46,
            "Пятница" to 60,
            "Суббота" to 74
        )

        days.forEach { (dayName, startRow) ->
            // Если начало дня выше предела — пропускаем
            if (startRow > LAST_SCHEDULE_ROW) {
                Log.d(TAG, "Пропускаем день $dayName — начинается за пределами расписания (строка $startRow)")
                return@forEach
            }

            Log.d(TAG, "Обрабатываем день: $dayName (начинается со строки $startRow)")

            // Получаем дату для дня недели
            val dayDate = DateUtils.getDateForDay(dayName)
            Log.d(TAG, "Дата для дня $dayName: $dayDate")

            // Проверка на праздничный день
            if (HolidayUtils.isHoliday(dayDate)) {
                val holidayName = HolidayUtils.getHolidayName(dayDate) ?: "Праздничный день"
                Log.d(TAG, "Праздничный день: $holidayName")

                // Добавляем день как праздничный
                scheduleDays.add(
                    ScheduleDay(
                        dayName,
                        listOf(
                            Lesson(
                                time = "",
                                subject = holidayName,
                                teacher = "",
                                room = "",
                                type = "праздник"
                            )
                        )
                    )
                )
                return@forEach // Пропускаем дальнейшую обработку расписания для этого дня
            }

            // Если день не праздничный, обрабатываем расписание
            val allLessons = parseLessonsForDayWithMerges(
                sheet,
                startRow,
                groupColumn,
                groupName,
                subgroup,
                currentWeekType,
                mergedCellsInfo
            )

            // Фильтруем пары, которые не выходят за пределы 84-й строки
            val lessons = allLessons.filterIndexed { index, _ ->
                val rowForPair = startRow + index * 2
                rowForPair <= LAST_SCHEDULE_ROW
            }

            Log.d(TAG, "Для дня $dayName найдено валидных пар: ${lessons.size}")

            if (lessons.isNotEmpty()) {
                scheduleDays.add(ScheduleDay(dayName, lessons))
            }
        }

        Log.d(TAG, "Парсинг завершен — обработано ${scheduleDays.size} дней (до строки $LAST_SCHEDULE_ROW)")
        return scheduleDays
    }






    private fun getColumnsForSubgroup(groupName: String, subgroup: String?): Pair<Int, Int> {
        return when {
            groupName.startsWith("И-") -> if (subgroup?.contains("2") == true) 15 to 15 else 13 to 14
            groupName.startsWith("У-") -> if (subgroup?.contains("3") == true) 25 to 25 else 21 to 22
            groupName.startsWith("П-") -> if (subgroup?.contains("3") == true) 30 to 30 else 29 to 29
            groupName.startsWith("ЭТ-") -> if (subgroup?.contains("3") == true) 32 to 32 else 31 to 31
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
        val LAST_SCHEDULE_ROW = 84

        Log.d(TAG, "Колонки для $groupName ($subgroup): числитель=$numeratorColumn, знаменатель=$denominatorColumn")

        for (pairIndex in 0..6) {
            val numeratorRowNum = startRow + pairIndex * 2
            val denominatorRowNum = numeratorRowNum + 1

            // 🔥 Если пара выходит за пределы 84 строки — не читаем
            if (numeratorRowNum > LAST_SCHEDULE_ROW || denominatorRowNum > LAST_SCHEDULE_ROW) {
                Log.d(TAG, "Достигнут предел расписания (строка $numeratorRowNum), дальше не парсим.")
                break
            }

            val pairNumber = pairIndex + 1
            val time = getTimeByPairNumber(pairNumber.toString())
            val selectedColumn = if (currentWeekType == "Числитель") numeratorColumn else denominatorColumn

            val lesson = parseLessonWithMerges(
                sheet,
                numeratorRowNum,
                denominatorRowNum,
                selectedColumn,
                groupName,
                subgroup,
                currentWeekType,
                time,
                mergedCellsInfo
            )

            if (lesson != null) {
                lessons.add(lesson)
            }
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
