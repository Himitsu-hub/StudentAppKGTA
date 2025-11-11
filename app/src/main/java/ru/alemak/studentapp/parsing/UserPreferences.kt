package ru.alemak.studentapp.parsing

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore by preferencesDataStore("user_preferences")

class UserPreferences(private val context: Context) {
    companion object {
        private val SELECTED_COURSE = intPreferencesKey("selected_course")
        private val SELECTED_GROUP = stringPreferencesKey("selected_group")
        private val SELECTED_SUBGROUP = stringPreferencesKey("selected_subgroup")
    }

    val selectedCourse: Flow<Int> = context.dataStore.data.map { it[SELECTED_COURSE] ?: 1 }
    val selectedGroup: Flow<String?> = context.dataStore.data.map { it[SELECTED_GROUP] }
    val selectedSubgroup: Flow<String?> = context.dataStore.data.map { it[SELECTED_SUBGROUP] }

    suspend fun saveSelection(course: Int, group: String?, subgroup: String?) {
        context.dataStore.edit { prefs ->
            prefs[SELECTED_COURSE] = course
            if (group != null) prefs[SELECTED_GROUP] = group
            if (subgroup != null) prefs[SELECTED_SUBGROUP] = subgroup
        }
    }
}
