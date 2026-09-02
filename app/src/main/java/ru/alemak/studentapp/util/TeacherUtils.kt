package ru.alemak.studentapp.util

import ru.alemak.studentapp.data.model.Teacher

object TeacherUtils {
    /**
     * Hierarchy for the teachers list:
     * rector → vice-rectors → deans → heads of chairs → professors →
     * docents → senior lecturers → lecturers → assistants → other staff.
     */
    fun leadershipPriority(teacher: Teacher): Int {
        val pos = teacher.position.lowercase().replace('ё', 'е')
        return when {
            "ректор" in pos && "проректор" !in pos -> 0
            "проректор" in pos -> 1
            "декан" in pos -> 2
            "заведующий кафедр" in pos || "зав. кафедр" in pos || "зав кафедр" in pos ||
                "и.о. заведующ" in pos -> 3
            "профессор" in pos -> 4
            "доцент" in pos -> 5
            "старший преподаватель" in pos || "ст. преп" in pos || "ст.преп" in pos -> 6
            "преподаватель" in pos -> 7
            "ассистент" in pos || "асс." in pos -> 8
            "инженер" in pos || "методист" in pos || "лаборант" in pos ||
                "специалист" in pos || "секретар" in pos -> 9
            else -> 10
        }
    }

    fun extractDepartment(teacher: Teacher): String {
        val pos = teacher.position
        val match = Regex("""[Кк]афедр\w*\s+[«"]?([^»";,\n]+)""", RegexOption.IGNORE_CASE)
            .find(pos)
        val raw = match?.groupValues?.get(1)?.trim()?.trimEnd('»', '"', ' ') ?: ""
        if (raw.length > 3 && !raw.lowercase().startsWith("кандидат")) {
            return normalizeDept(raw)
        }
        val match2 = Regex("""[Кк]афедр\w*\s+([А-Яа-яёЁ][А-Яа-яёЁ\s\-]+)""").find(pos)
        val raw2 = match2?.groupValues?.get(1)?.trim().orEmpty()
        return if (raw2.length > 3) normalizeDept(raw2) else ""
    }

    fun departments(teachers: List<Teacher>): List<String> {
        val depts = teachers.map { extractDepartment(it) }.filter { it.isNotEmpty() }.toSortedSet()
        val result = depts.toMutableList()
        if (teachers.any { extractDepartment(it).isEmpty() }) result.add("Другие")
        return result
    }

    fun sort(teachers: List<Teacher>): List<Teacher> =
        teachers.sortedWith(compareBy({ leadershipPriority(it) }, { it.name }))

    private fun normalizeDept(raw: String): String {
        val lower = raw.lowercase()
        return when {
            "машиностр" in lower && "технолог" !in lower -> "Машиностроение"
            "гидропневм" in lower || "гидропривод" in lower -> "Гидропневмоавтоматика и гидропривод"
            "робототехн" in lower -> "Робототехника и комплексная автоматизация"
            "пм и сапр" in lower || "сапр" in lower -> "ПМ и САПР"
            "приборостроен" in lower -> "Приборостроение"
            "электротехн" in lower -> "Электротехника"
            "лазерн" in lower || "оптико-электрон" in lower -> "Лазерные и оптико-электронные системы"
            "безопасност" in lower || "бжде" in lower -> "Безопасность жизнедеятельности"
            "экономик" in lower || "гуманитарн" in lower -> "Экономика и гуманитарные науки"
            "менеджмент" in lower -> "Менеджмент"
            "технолог" in lower && "машиностр" in lower -> "Технология машиностроения"
            "иностран" in lower -> "Иностранные языки"
            else -> raw
        }
    }
}
