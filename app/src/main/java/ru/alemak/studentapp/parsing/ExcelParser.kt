package ru.alemak.studentapp.parsing

import android.content.Context
import org.apache.poi.ss.usermodel.Sheet
import org.apache.poi.ss.usermodel.WorkbookFactory
import ru.alemak.studentapp.screens.HolidayUtils
import ru.alemak.studentapp.utils.DateUtils
import java.io.InputStream

object ExcelParser {

    fun getAvailableGroupsWithSubgroups(context: Context, course: Int): Map<String, List<String>> {
        return try {
            val fileName = "schedule${course}.xlsx"
            context.assets.open(fileName).use { inputStream ->
                getGroupsWithSubgroupsFromExcel(inputStream, course)
            }
        } catch (e: Exception) {
            emptyMap()
        }
    }

    fun parseScheduleForGroup(context: Context, course: Int, groupName: String, subgroup: String? = null): List<ScheduleDay> {
        return try {
            val fileName = "schedule${course}.xlsx"
            context.assets.open(fileName).use { inputStream ->
                parseExcelFileForGroup(inputStream, groupName, subgroup)
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun getGroupsWithSubgroupsFromExcel(inputStream: InputStream, course: Int): Map<String, List<String>> {
        val workbook = WorkbookFactory.create(inputStream)
        val sheet = workbook.getSheetAt(0)
        val groupsMap = mutableMapOf<String, MutableList<String>>()

        val courseSuffix = when (course) {
            1 -> "125"
            2 -> "124"
            3 -> "123"
            4 -> "122"
            else -> "124"
        }

        val groupsRow = sheet.getRow(2) ?: return emptyMap()
        val groupRanges = mapOf(
            "И-$courseSuffix" to (13..15),
            "У-$courseSuffix" to (21..25),
            "П-$courseSuffix" to (29..30),
            "ЭТ-$courseSuffix" to (31..32)
        )

        groupRanges.forEach { (group, _) ->
            groupsMap[group] = mutableListOf()
            when {
                group.startsWith("И-") -> groupsMap[group]?.addAll(listOf("1 подгруппа", "2 подгруппа"))
                else -> groupsMap[group]?.addAll(listOf("Основная", "3 подгруппа"))
            }
        }

        workbook.close()
        return groupsMap
    }

    private fun parseExcelFileForGroup(inputStream: InputStream, groupName: String, subgroup: String?): List<ScheduleDay> {
        val workbook = WorkbookFactory.create(inputStream)
        val sheet = workbook.getSheetAt(0)
        val mergedCellsInfo = findMergedCellsForGroup(sheet, groupName)
        val groupColumn = findGroupColumn(sheet, groupName, subgroup)
        if (groupColumn == -1) {
            workbook.close()
            return emptyList()
        }
        val scheduleDays = parseScheduleWithMerges(sheet, groupName, subgroup, groupColumn, mergedCellsInfo)
        workbook.close()
        return scheduleDays
    }

    private fun findMergedCellsForGroup(sheet: Sheet, groupName: String): List<MergedCellInfo> {
        val mergedCells = mutableListOf<MergedCellInfo>()
        val groupColumns = when {
            groupName.startsWith("И-") -> (13..15).toList()
            groupName.startsWith("У-") -> (21..25).toList()
            groupName.startsWith("П-") -> (29..30).toList()
            groupName.startsWith("ЭТ-") -> (31..32).toList()
            else -> emptyList()
        }

        if (groupColumns.isEmpty()) return mergedCells

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
        return when {
            groupName.startsWith("И-") -> if (subgroup?.contains("2") == true) 15 else 13
            groupName.startsWith("У-") -> if (subgroup?.contains("3") == true) 25 else 21
            groupName.startsWith("П-") -> if (subgroup?.contains("3") == true) 30 else 29
            groupName.startsWith("ЭТ-") -> if (subgroup?.contains("3") == true) 32 else 31
            else -> -1
        }
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
        val LAST_SCHEDULE_ROW = 84

        val days = listOf(
            "Понедельник" to 4,
            "Вторник" to 18,
            "Среда" to 32,
            "Четверг" to 46,
            "Пятница" to 60,
            "Суббота" to 74
        )

        days.forEach { (dayName, startRow) ->
            if (startRow > LAST_SCHEDULE_ROW) return@forEach
            val dayDate = DateUtils.getDateForDay(dayName)

            if (HolidayUtils.isHoliday(dayDate)) {
                val holidayName = HolidayUtils.getHolidayName(dayDate) ?: "Праздничный день"
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
                return@forEach
            }

            val allLessons = parseLessonsForDayWithMerges(
                sheet,
                startRow,
                groupColumn,
                groupName,
                subgroup,
                currentWeekType,
                mergedCellsInfo
            )

            val lessons = allLessons.filterIndexed { index, _ ->
                val rowForPair = startRow + index * 2
                rowForPair <= LAST_SCHEDULE_ROW
            }

            if (lessons.isNotEmpty()) {
                scheduleDays.add(ScheduleDay(dayName, lessons))
            }
        }

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

        for (pairIndex in 0..6) {
            val numeratorRowNum = startRow + pairIndex * 2
            val denominatorRowNum = numeratorRowNum + 1

            if (numeratorRowNum > LAST_SCHEDULE_ROW || denominatorRowNum > LAST_SCHEDULE_ROW) break

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

        val numeratorMergeInfo = findMergeInfoForCell(mergedCellsInfo, numeratorRowNum, groupColumn)
        val denominatorMergeInfo = findMergeInfoForCell(mergedCellsInfo, denominatorRowNum, groupColumn)

        val hasHorizontalMerge = mergedCellsInfo.any { merge ->
            numeratorRowNum in merge.firstRow..merge.lastRow &&
                    denominatorRowNum in merge.firstRow..merge.lastRow &&
                    merge.firstColumn <= 13 && merge.lastColumn >= 15
        }

        if (hasHorizontalMerge) {
            val text = if (currentWeekType == "Числитель") numeratorText ?: denominatorText else denominatorText ?: numeratorText
            return text?.let { parseLessonFromText(it, time) }
        }

        if (numeratorMergeInfo?.isVertical() == true || denominatorMergeInfo?.isVertical() == true) {
            val text = numeratorText ?: denominatorText
            return text?.let { parseLessonFromText(it, time) }
        }

        var text = if (currentWeekType == "Числитель") numeratorText else denominatorText

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
}
