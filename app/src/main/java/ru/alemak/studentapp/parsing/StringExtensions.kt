package ru.alemak.studentapp.parsing

fun String.containsAny(vararg strings: String, ignoreCase: Boolean = true): Boolean {
    return strings.any { this.contains(it, ignoreCase) }
}