package ru.alemak.studentapp.parsing

data class ScheduleDay(
    val dayName: String,
    val lessons: List<Lesson>
)

data class Lesson(
    val time: String,
    val subject: String,
    val teacher: String,
    val room: String,
    val type: String // лекция/практика/лабораторная
)