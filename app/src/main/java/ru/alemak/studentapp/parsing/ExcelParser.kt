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
            val file = "schedule${course}.xlsx"
            context.assets.open(file).use { stream ->
                val workbook = WorkbookFactory.create(stream)
                val sheet = workbook.getSheetAt(0)
                val groups = parseGroupsFromHeader(sheet)
                workbook.close()

                groups.mapValues { (_, info) -> info.subgroups.map { it.name } }
            }
        } catch (e: Exception) {
            emptyMap()
        }
    }

    fun parseScheduleForGroup(
        context: Context,
        course: Int,
        groupName: String,
        subgroup: String?
    ): List<ScheduleDay> {
        return try {
            val file = "schedule${course}.xlsx"
            context.assets.open(file).use { input ->
                parseExcelForGroup(input, groupName, subgroup)
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    // ============================================================
    // GROUP HEADER PARSING — НОВЫЙ КОРРЕКТНЫЙ АЛГОРИТМ
    // ============================================================

    data class GroupInfo(
        val groupName: String,
        val subgroups: MutableList<SubgroupInfo>
    )

    data class SubgroupInfo(
        val name: String,
        val column: Int
    )

    private val allowedPrefixes = listOf("И", "У", "П", "ЭТ")

    private fun getCellTextSafe(sheet: Sheet, row: Int, col: Int): String? {
        val cell = sheet.getRow(row)?.getCell(col)
        return cell?.toString()?.trim()?.ifBlank { null }
    }

    /** Распознаём имя группы по строгим правилам */
    private fun extractGroupName(text: String?): String? {
        if (text.isNullOrBlank()) return null
        val cleaned = text.trim().uppercase()

        for (prefix in allowedPrefixes) {
            if (cleaned.startsWith(prefix)) {
                return cleaned.substringBefore("(").trim()
            }
        }
        return null
    }

    /** Подгруппа определяется ТОЛЬКО из строки 4 */
    private fun extractSubgroupFromRow4(text: String?): String? {
        if (text.isNullOrBlank()) return null

        val lower = text.lowercase().trim()

        Regex("(\\d) ?подгруппа").find(lower)?.let {
            return "${it.groupValues[1]} подгруппа"
        }

        // явный "осн" мы теперь НЕ используем, чтобы не добавлять "Основная", если есть другие подгруппы
        return null
    }

    private fun parseGroupsFromHeader(sheet: Sheet): Map<String, GroupInfo> {

        val groups = mutableMapOf<String, GroupInfo>()

        val merged = collectMergedCells(sheet)
        val row3 = sheet.getRow(2) ?: return emptyMap()
        val lastCol = row3.lastCellNum.toInt()

        for (col in 0 until lastCol) {

            val t3 = getCellTextSafe(sheet, 2, col)
            val t4 = getCellTextSafe(sheet, 3, col)

            // 1 — ищем группу по текущей колонке
            var groupName = extractGroupName(t3)

            // 2 — если в этой колонке нет группы — проверяем объединённую ячейку
            if (groupName == null) {
                val merge = findMerge(merged, 2, col)
                if (merge != null && merge.firstRow == 2) {
                    val mergedText = getCellTextSafe(sheet, merge.firstRow, merge.firstColumn)
                    groupName = extractGroupName(mergedText)
                }
            }

            // Если не группа — пропускаем
            if (groupName == null) continue

            // Получаем реальный диапазон колонок группы (учитываем merged)
            val merge = findMerge(merged, 2, col)
            val startCol = merge?.firstColumn ?: col
            val endCol = merge?.lastColumn ?: col

            // Если группа впервые — создаём
            val groupInfo = groups.getOrPut(groupName) {
                GroupInfo(groupName, mutableListOf())
            }

            // 3 — перебираем ВСЕ столбцы внутри группы и ищем явные подгруппы в строке 4
            val foundSubgroups = mutableListOf<Pair<String, Int>>() // name, column
            for (c in startCol..endCol) {
                val subText = getCellTextSafe(sheet, 3, c)
                val subgroup = extractSubgroupFromRow4(subText)
                if (subgroup != null) {
                    // сохраняем порядок слева направо
                    if (foundSubgroups.none { it.first == subgroup }) {
                        foundSubgroups.add(subgroup to c)
                    }
                }
            }

            // 4a — если явные подгруппы найдены — добавляем только их (без "Основная")
            if (foundSubgroups.isNotEmpty()) {
                for ((name, c) in foundSubgroups) {
                    if (groupInfo.subgroups.none { it.name == name }) {
                        groupInfo.subgroups.add(SubgroupInfo(name, c))
                    }
                }
            } else {
                // 4b — если явных подгрупп нет — fallback: добавляем одну "1 подгруппа" на первую колонку диапазона
                val fallbackName = "1 подгруппа"
                if (groupInfo.subgroups.none { it.name == fallbackName }) {
                    groupInfo.subgroups.add(SubgroupInfo(fallbackName, startCol))
                }
            }
        }

        return groups
    }



    private fun parseExcelForGroup(
        inputStream: InputStream,
        groupName: String,
        subgroup: String?
    ): List<ScheduleDay> {

        val workbook = WorkbookFactory.create(inputStream)
        val sheet = workbook.getSheetAt(0)

        val groups = parseGroupsFromHeader(sheet)
        val info = groups[groupName] ?: return emptyList()

        val subgroupColumn =
            info.subgroups.find { it.name == subgroup }?.column
                ?: info.subgroups.first().column

        val merged = collectMergedCells(sheet)
        val result = parseDays(sheet, subgroupColumn, merged)

        workbook.close()
        return result
    }


    data class MergeInfo(
        val firstRow: Int,
        val lastRow: Int,
        val firstColumn: Int,
        val lastColumn: Int
    )

    private fun collectMergedCells(sheet: Sheet): List<MergeInfo> {
        return sheet.mergedRegions.map {
            MergeInfo(it.firstRow, it.lastRow, it.firstColumn, it.lastColumn)
        }
    }

    private fun findMerge(merged: List<MergeInfo>, row: Int, col: Int): MergeInfo? {
        return merged.find {
            row in it.firstRow..it.lastRow &&
                    col in it.firstColumn..it.lastColumn
        }
    }



    private fun parseDays(sheet: Sheet, column: Int, merged: List<MergeInfo>): List<ScheduleDay> {
        val days = listOf(
            "Понедельник" to 4,
            "Вторник" to 18,
            "Среда" to 32,
            "Четверг" to 46,
            "Пятница" to 60,
            "Суббота" to 74
        )

        val weekType = DateUtils.getCurrentWeekType()
        val LAST = 84

        val result = mutableListOf<ScheduleDay>()

        for ((dayName, startRow) in days) {

            if (startRow > LAST) break

            val date = DateUtils.getDateForDay(dayName)
            if (HolidayUtils.isHoliday(date)) {
                val h = HolidayUtils.getHolidayName(date) ?: "Праздничный день"
                result.add(ScheduleDay(dayName, listOf(Lesson("", h, "", "", "праздник"))))
                continue
            }

            val lessons = parseLessonsForDay(sheet, startRow, column, weekType, merged)
            if (lessons.isNotEmpty()) {
                result.add(ScheduleDay(dayName, lessons))
            }
        }

        return result
    }

    private fun parseLessonsForDay(
        sheet: Sheet,
        startRow: Int,
        column: Int,
        weekType: String,
        merged: List<MergeInfo>
    ): List<Lesson> {

        val list = mutableListOf<Lesson>()
        val LAST = 84

        for (i in 0..6) {
            val numRow = startRow + i * 2
            val denRow = numRow + 1
            if (denRow > LAST) break

            val time = getTimeByPair(i + 1)
            val selectedRow = if (weekType == "Числитель") numRow else denRow
            val text = getText(sheet, selectedRow, column, merged)

            if (!text.isNullOrBlank()) {
                parseLessonText(text, time)?.let { list.add(it) }
            }
        }

        return list
    }

    private fun getText(sheet: Sheet, row: Int, col: Int, merged: List<MergeInfo>): String? {
        val raw = getCellTextSafe(sheet, row, col)
        if (!raw.isNullOrBlank() && raw != "-" && raw != "null") return raw

        val merge = findMerge(merged, row, col) ?: return null
        val topLeft = getCellTextSafe(sheet, merge.firstRow, merge.firstColumn)

        return if (!topLeft.isNullOrBlank() && topLeft != "-" && topLeft != "null")
            topLeft else null
    }

    private fun parseLessonText(text: String, time: String): Lesson? {
        val lines = text.split("\n").map { it.trim() }.filter { it.isNotBlank() }
        if (lines.isEmpty()) return null

        var subject = cleanSubject(lines[0])
        var teacher = ""
        var room = ""
        var type = when {
            text.contains("лек", ignoreCase = true) -> "лекция"
            text.contains("практ", ignoreCase = true) -> "практика"
            text.contains("лаб", ignoreCase = true) -> "лабораторная"
            else -> "занятие"
        }

        for (i in lines.lastIndex downTo 1) {
            if (Regex("\\d+").containsMatchIn(lines[i])) {
                room = Regex("\\d+").find(lines[i])!!.value
                teacher = lines.subList(1, i).joinToString(", ")
                break
            }
        }

        return Lesson(time, subject, teacher, room, type)
    }

    private fun cleanSubject(s: String): String =
        s.replace("лекция", "", true)
            .replace("лек", "", true)
            .replace("практика", "", true)
            .replace("практ", "", true)
            .replace("лаб", "", true)
            .trim()

    private fun getTimeByPair(pair: Int) = when (pair) {
        1 -> "8:00-09:25"
        2 -> "09:35-11:00"
        3 -> "12:00-13:25"
        4 -> "13:35-15:00"
        5 -> "15:10-16:35"
        6 -> "17:45-19:10"
        7 -> "19:20-20:45"
        else -> "Неизвестно"
    }
}