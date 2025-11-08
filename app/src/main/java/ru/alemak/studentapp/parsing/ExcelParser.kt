package ru.alemak.studentapp.parsing

import android.content.Context
import android.util.Log
import org.apache.poi.ss.usermodel.Sheet
import org.apache.poi.ss.usermodel.WorkbookFactory
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

        // Ищем в 3-й строке (индекс 2) основные группы
        val groupsRow = sheet.getRow(2) ?: return emptyMap()

        // Определяем диапазоны колонок для каждой группы
        val groupRanges = mapOf(
            "И-124" to (13..15),   // N-P
            "У-124" to (21..25),   // V-Z
            "П-124" to (29..30),   // AD-AE
            "ЭТ-124" to (31..32)   // AF-AG
        )

        Log.d(TAG, "Определены диапазоны групп: $groupRanges")

        // Для каждой группы создаем подгруппы на основе диапазона
        groupRanges.forEach { (group, range) ->
            groupsMap[group] = mutableListOf()
            Log.d(TAG, "=== ОБРАБОТКА ГРУППЫ: $group ===")
            Log.d(TAG, "Диапазон колонок: $range")

            // Проверяем, что группа действительно есть в этих колонках
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

            // Создаем подгруппы на основе диапазона
            when (group) {
                "И-124" -> {
                    // И-124: 1 и 2 подгруппы
                    groupsMap[group]?.add("1 подгруппа")
                    groupsMap[group]?.add("2 подгруппа")
                    Log.d(TAG, "Для И-124 созданы подгруппы: 1 подгруппа, 2 подгруппа")
                }
                "У-124", "П-124", "ЭТ-124" -> {
                    // У-124, П-124, ЭТ-124: Основная и 3 подгруппа
                    groupsMap[group]?.add("Основная")
                    groupsMap[group]?.add("3 подгруппа")
                    Log.d(TAG, "Для $group созданы подгруппы: Основная, 3 подгруппа")
                }
            }

            Log.d(TAG, "Итоговые подгруппы для $group: ${groupsMap[group]}")
        }

        workbook.close()

        Log.d(TAG, "Итоговый список групп с подгруппами: $groupsMap")
        return groupsMap
    }

    private fun isValidGroupName(name: String): Boolean {
        return name == "И-124" || name == "У-124" || name == "П-124" || name == "ЭТ-124" ||
                name.matches(Regex("[А-Яа-я]+-\\d+"))
    }

    private fun parseExcelFileForGroup(inputStream: InputStream, groupName: String, subgroup: String?): List<ScheduleDay> {
        val workbook = WorkbookFactory.create(inputStream)
        val sheet = workbook.getSheetAt(0)

        // Находим номер колонки для группы и подгруппы
        val groupColumn = findGroupColumn(sheet, groupName, subgroup)
        if (groupColumn == -1) {
            Log.e(TAG, "Группа $groupName (подгруппа: $subgroup) не найдена в файле")
            workbook.close()
            return emptyList()
        }

        Log.d(TAG, "Группа $groupName (подгруппа: $subgroup) найдена в колонке $groupColumn")

        val scheduleDays = mutableListOf<ScheduleDay>()

        // Фиксированные позиции дней недели
        val days = listOf(
            "Понедельник" to 4,  // A5
            "Вторник" to 18,     // A19
            "Среда" to 32,       // A33
            "Четверг" to 46,     // A47
            "Пятница" to 60,     // A61
            "Суббота" to 74      // A75
        )

        // Для каждого дня недели парсим занятия
        days.forEach { (dayName, startRow) ->
            Log.d(TAG, "Обрабатываем день: $dayName (начинается со строки $startRow)")
            val lessons = parseLessonsForDay(sheet, startRow, groupColumn, groupName, subgroup)
            Log.d(TAG, "Для дня $dayName найдено пар: ${lessons.size}")
            if (lessons.isNotEmpty()) {
                scheduleDays.add(ScheduleDay(dayName, lessons))
            }
        }

        workbook.close()
        Log.d(TAG, "ИТОГО: создано ${scheduleDays.size} дней расписания")
        return scheduleDays
    }

    private fun findGroupColumn(sheet: Sheet, groupName: String, subgroup: String?): Int {
        // Определяем диапазоны колонок для каждой группы
        val groupRanges = mapOf(
            "И-124" to (13..15),   // N-P
            "У-124" to (21..25),   // V-Z
            "П-124" to (29..30),   // AD-AE
            "ЭТ-124" to (31..32)   // AF-AG
        )

        val range = groupRanges[groupName] ?: return -1
        Log.d(TAG, "Поиск группы '$groupName' с подгруппой '$subgroup' в диапазоне $range")

        // Определяем конкретную колонку на основе подгруппы
        return when (groupName) {
            "И-124" -> {
                when (subgroup) {
                    "1 подгруппа" -> 13 // N - основная колонка для 1 подгруппы
                    "2 подгруппа" -> 15 // P - основная колонка для 2 подгруппы
                    else -> 13 // по умолчанию первая подгруппа
                }
            }
            "У-124" -> {
                when (subgroup) {
                    "Основная" -> 21 // V (первая колонка диапазона)
                    "3 подгруппа" -> 25 // Z (последняя колонка диапазона)
                    else -> 21 // по умолчанию основная
                }
            }
            "П-124" -> {
                when (subgroup) {
                    "Основная" -> 29 // AD (первая колонка диапазона)
                    "3 подгруппа" -> 30 // AE (последняя колонка диапазона)
                    else -> 29 // по умолчанию основная
                }
            }
            "ЭТ-124" -> {
                when (subgroup) {
                    "Основная" -> 31 // AF (первая колонка диапазона)
                    "3 подгруппа" -> 32 // AG (последняя колонка диапазона)
                    else -> 31 // по умолчанию основная
                }
            }
            else -> range.first // по умолчанию первая колонка диапазона
        }.also { col ->
            Log.d(TAG, "Для группы '$groupName' подгруппы '$subgroup' выбрана колонка $col")
        }
    }

    private fun parseLessonsForDay(sheet: Sheet, startRow: Int, groupColumn: Int, groupName: String, subgroup: String?): List<Lesson> {
        val lessons = mutableListOf<Lesson>()

        Log.d(TAG, "=== ПАРСИНГ ДНЯ С $startRow СТРОКИ ДЛЯ КОЛОНКИ $groupColumn, ГРУППА $groupName, ПОДГРУППА $subgroup ===")

        // Для группы И-124 с подгруппами нужно объединять данные из обеих колонок
        val columnsToParse = if (groupName == "И-124" && subgroup != null) {
            when (subgroup) {
                "1 подгруппа" -> listOf(13, 15) // N и P - берем обе колонки, но приоритет у N
                "2 подгруппа" -> listOf(15, 13) // P и N - берем обе колонки, но приоритет у P
                else -> listOf(groupColumn)
            }
        } else {
            listOf(groupColumn)
        }

        Log.d(TAG, "Колонки для парсинга: $columnsToParse")

        // Создаем отдельные списки для числителя и знаменателя
        val numeratorLessons = mutableListOf<Lesson>()
        val denominatorLessons = mutableListOf<Lesson>()

        // Для каждого дня 7 пар (шаг 2 строки - числитель и знаменатель)
        for (pairIndex in 0..6) {
            val numeratorRowNum = startRow + pairIndex * 2    // Четные строки - числитель
            val denominatorRowNum = numeratorRowNum + 1       // Нечетные строки - знаменатель
            val pairNumber = pairIndex + 1

            // Обрабатываем ЧИСЛИТЕЛЬ
            val numeratorRow = sheet.getRow(numeratorRowNum)
            if (numeratorRow != null) {
                val pairCell = numeratorRow.getCell(1)?.toString()?.trim()
                val normalizedPairNumber = when {
                    pairCell == null -> null
                    pairCell.contains(".") -> pairCell.substringBefore(".") // Убираем .0
                    else -> pairCell
                }

                // Для числителя проверяем номер пары
                if (normalizedPairNumber == pairNumber.toString()) {
                    val timeCell = numeratorRow.getCell(2)?.toString()?.trim()
                    val time = timeCell ?: getTimeByPairNumber(pairNumber.toString())

                    var lessonText: String? = null
                    for (col in columnsToParse) {
                        val cellLessonText = numeratorRow.getCell(col)?.toString()?.trim()
                        if (!cellLessonText.isNullOrEmpty() && cellLessonText != "null" && cellLessonText != "-" && cellLessonText != " ") {
                            lessonText = cellLessonText
                            Log.d(TAG, "Числитель - Пара $pairNumber, строка $numeratorRowNum, колонка $col: '$lessonText'")
                            break
                        }
                    }

                    if (lessonText != null) {
                        try {
                            val lesson = parseLessonFromText(lessonText, time)
                            lesson?.let {
                                numeratorLessons.add(it)
                                Log.d(TAG, "  -> ДОБАВЛЕНО ЗАНЯТИЕ (числитель): ${lesson.subject}")
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Ошибка парсинга занятия из числителя: '$lessonText'", e)
                        }
                    }
                }
            }

            // Обрабатываем ЗНАМЕНАТЕЛЬ
            val denominatorRow = sheet.getRow(denominatorRowNum)
            if (denominatorRow != null) {
                // Для знаменателя НЕ проверяем номер пары - берем все строки подряд
                // Время берем такое же, как у соответствующей пары числителя
                val timeCell = denominatorRow.getCell(2)?.toString()?.trim()
                val time = timeCell ?: getTimeByPairNumber(pairNumber.toString())

                var lessonText: String? = null
                for (col in columnsToParse) {
                    val cellLessonText = denominatorRow.getCell(col)?.toString()?.trim()
                    if (!cellLessonText.isNullOrEmpty() && cellLessonText != "null" && cellLessonText != "-" && cellLessonText != " ") {
                        lessonText = cellLessonText
                        Log.d(TAG, "Знаменатель - Пара $pairNumber, строка $denominatorRowNum, колонка $col: '$lessonText'")
                        break
                    }
                }

                if (lessonText != null) {
                    try {
                        val lesson = parseLessonFromText(lessonText, time)
                        lesson?.let {
                            denominatorLessons.add(it)
                            Log.d(TAG, "  -> ДОБАВЛЕНО ЗАНЯТИЕ (знаменатель): ${lesson.subject}")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Ошибка парсинга занятия из знаменателя: '$lessonText'", e)
                    }
                }
            }
        }

        // ВРЕМЕННО: объединяем все пары (числитель + знаменатель)
        // Позже можно будет добавить логику выбора только числителя или только знаменателя
        lessons.addAll(numeratorLessons)
        lessons.addAll(denominatorLessons)

        Log.d(TAG, "Числитель: ${numeratorLessons.size} пар, Знаменатель: ${denominatorLessons.size} пар")
        Log.d(TAG, "=== ЗАВЕРШЕНО: найдено ${lessons.size} пар ===")
        return lessons
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

            // Обрабатываем многострочный текст
            val lines = text.split("\n").map { it.trim() }.filter { it.isNotEmpty() }

            if (lines.isNotEmpty()) {
                subject = lines[0]

                // Ищем аудиторию (последняя строка с цифрами)
                for (i in lines.size - 1 downTo 0) {
                    val roomMatch = Regex("\\d+").find(lines[i])
                    if (roomMatch != null) {
                        room = roomMatch.value
                        // Преподаватель - всё кроме первой и последней строки
                        teacher = lines.subList(1, i).joinToString(", ")
                        break
                    }
                }

                // Если не нашли аудиторию, то преподаватель - все строки кроме первой
                if (room.isEmpty() && lines.size > 1) {
                    teacher = lines.subList(1, lines.size).joinToString(", ")
                }
            }

            // Очищаем предмет от указаний типа
            subject = cleanSubject(subject)

            Log.d(TAG, "Результат парсинга: subject='$subject', teacher='$teacher', room='$room', type='$type'")

            return Lesson(
                time = time,
                subject = subject,
                teacher = teacher,
                room = room,
                type = type
            )
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка при парсинге занятия из текста: '$text'", e)
            return null
        }
    }

    private fun cleanSubject(subject: String): String {
        return subject
            .replace("лекция", "", ignoreCase = true)
            .replace("лек", "", ignoreCase = true)
            .replace("практика", "", ignoreCase = true)
            .replace("практ", "", ignoreCase = true)
            .replace("лабораторная", "", ignoreCase = true)
            .replace("лаб", "", ignoreCase = true)
            .replace("  ", " ")
            .trim()
    }

    private fun getTimeByPairNumber(pairNumber: String): String {
        return when (pairNumber) {
            "1" -> "8:00-09:25"
            "2" -> "09:35-11:00"
            "3" -> "12:00-13:25"
            "4" -> "13:35-15:00"
            "5" -> "15:10-16:35"
            "6" -> "17:45-19:10"
            "7" -> "19:20-20:45"
            else -> "Неизвестно"
        }
    }

    // Вспомогательная функция расширения для containsAny
    private fun String.containsAny(vararg strings: String): Boolean {
        return strings.any { this.contains(it, ignoreCase = true) }
    }
}