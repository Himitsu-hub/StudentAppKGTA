package ru.alemak.studentapp.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.userDataStore: DataStore<Preferences> by preferencesDataStore("user_prefs")

data class UserSelection(
    val course: Int = 1,
    val group: String? = null,
    val subgroup: String? = null,
)

@Singleton
class UserPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val store = context.userDataStore

    val selection: Flow<UserSelection> = store.data.map { prefs ->
        UserSelection(
            course = prefs[Keys.COURSE] ?: 1,
            group = prefs[Keys.GROUP],
            subgroup = prefs[Keys.SUBGROUP],
        )
    }

    suspend fun save(course: Int, group: String?, subgroup: String?) {
        store.edit { prefs ->
            prefs[Keys.COURSE] = course
            if (group != null) prefs[Keys.GROUP] = group else prefs.remove(Keys.GROUP)
            if (subgroup != null) prefs[Keys.SUBGROUP] = subgroup else prefs.remove(Keys.SUBGROUP)
        }
    }

    suspend fun getSelectedCourse(): Int =
        store.data.map { it[Keys.COURSE] ?: 1 }.first()

    /** Last known server version string for a course (empty = never seen). */
    suspend fun getScheduleVersion(course: Int): String =
        store.data.map { it[scheduleVersionKey(course)].orEmpty() }.first()

    suspend fun setScheduleVersion(course: Int, version: String) {
        store.edit { prefs ->
            prefs[scheduleVersionKey(course)] = version
        }
    }

    private fun scheduleVersionKey(course: Int) =
        stringPreferencesKey("schedule_version_$course")

    private object Keys {
        val COURSE = intPreferencesKey("selected_course")
        val GROUP = stringPreferencesKey("selected_group")
        val SUBGROUP = stringPreferencesKey("selected_subgroup")
    }
}
