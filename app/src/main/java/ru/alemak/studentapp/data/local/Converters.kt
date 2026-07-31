package ru.alemak.studentapp.data.local

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import ru.alemak.studentapp.data.model.ScheduleDay

class Converters {
    private val gson = Gson()

    @TypeConverter
    fun fromScheduleDays(value: List<ScheduleDay>?): String =
        gson.toJson(value.orEmpty())

    @TypeConverter
    fun toScheduleDays(value: String?): List<ScheduleDay> {
        if (value.isNullOrBlank()) return emptyList()
        val type = object : TypeToken<List<ScheduleDay>>() {}.type
        return gson.fromJson(value, type) ?: emptyList()
    }

    @TypeConverter
    fun fromStringList(value: List<String>?): String =
        gson.toJson(value.orEmpty())

    @TypeConverter
    fun toStringList(value: String?): List<String> {
        if (value.isNullOrBlank()) return emptyList()
        val type = object : TypeToken<List<String>>() {}.type
        return gson.fromJson(value, type) ?: emptyList()
    }

    @TypeConverter
    fun fromStringMap(value: Map<String, List<String>>?): String =
        gson.toJson(value.orEmpty())

    @TypeConverter
    fun toStringMap(value: String?): Map<String, List<String>> {
        if (value.isNullOrBlank()) return emptyMap()
        val type = object : TypeToken<Map<String, List<String>>>() {}.type
        return gson.fromJson(value, type) ?: emptyMap()
    }
}
