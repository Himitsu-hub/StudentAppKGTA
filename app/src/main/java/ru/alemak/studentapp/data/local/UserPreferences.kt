package ru.alemak.studentapp.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
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
import ru.alemak.studentapp.ui.theme.ThemePrefs

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

    /** true = dark theme */
    val darkTheme: Flow<Boolean> = store.data.map { it[Keys.DARK_THEME] == true }

    /** First-launch wizard completed (group chosen). */
    val onboardingDone: Flow<Boolean> = store.data.map { prefs ->
        prefs[Keys.ONBOARDING_DONE] == true || !prefs[Keys.GROUP].isNullOrBlank()
    }

    suspend fun save(course: Int, group: String?, subgroup: String?) {
        store.edit { prefs ->
            prefs[Keys.COURSE] = course
            if (group != null) prefs[Keys.GROUP] = group else prefs.remove(Keys.GROUP)
            if (subgroup != null) prefs[Keys.SUBGROUP] = subgroup else prefs.remove(Keys.SUBGROUP)
            if (!group.isNullOrBlank()) {
                prefs[Keys.ONBOARDING_DONE] = true
            }
        }
    }

    suspend fun setDarkTheme(enabled: Boolean) {
        store.edit { prefs -> prefs[Keys.DARK_THEME] = enabled }
        // Persist + switch launcher alias for next cold-start splash.
        // Does NOT call setDefaultNightMode (that recreated Activity and crashed).
        ThemePrefs.setDark(context, enabled)
    }

    suspend fun setOnboardingDone(done: Boolean) {
        store.edit { prefs -> prefs[Keys.ONBOARDING_DONE] = done }
    }

    suspend fun getSelectedCourse(): Int =
        store.data.map { it[Keys.COURSE] ?: 1 }.first()

    suspend fun getScheduleVersion(course: Int): String =
        store.data.map { it[scheduleVersionKey(course)].orEmpty() }.first()

    suspend fun setScheduleVersion(course: Int, version: String) {
        store.edit { prefs ->
            prefs[scheduleVersionKey(course)] = version
        }
    }

    suspend fun getNewsVersion(): String =
        store.data.map { it[Keys.NEWS_VERSION].orEmpty() }.first()

    suspend fun setNewsVersion(version: String) {
        store.edit { prefs -> prefs[Keys.NEWS_VERSION] = version }
    }

    private fun scheduleVersionKey(course: Int) =
        stringPreferencesKey("schedule_version_$course")

    private object Keys {
        val COURSE = intPreferencesKey("selected_course")
        val GROUP = stringPreferencesKey("selected_group")
        val SUBGROUP = stringPreferencesKey("selected_subgroup")
        val DARK_THEME = booleanPreferencesKey("dark_theme")
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        val NEWS_VERSION = stringPreferencesKey("news_feed_version")
    }
}
