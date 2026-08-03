package ru.alemak.studentapp.ui.navigation

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.alemak.studentapp.data.local.UserPreferences
import ru.alemak.studentapp.ui.theme.ThemePrefs

@HiltViewModel
class ThemeViewModel @Inject constructor(
    private val userPreferences: UserPreferences,
    @ApplicationContext context: Context,
) : ViewModel() {

    private val _prefsReady = MutableStateFlow(false)
    val prefsReady: StateFlow<Boolean> = _prefsReady.asStateFlow()

    /**
     * Seed from ThemePrefs (sync SharedPreferences) so first frame matches last chosen theme.
     * DataStore default would flash light theme on every cold start.
     */
    val darkTheme: StateFlow<Boolean> = userPreferences.darkTheme
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            ThemePrefs.isDark(context),
        )

    val onboardingDone: StateFlow<Boolean> = userPreferences.onboardingDone
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    init {
        viewModelScope.launch {
            // Wait until DataStore has emitted at least once
            userPreferences.onboardingDone.first()
            _prefsReady.value = true
        }
    }

    fun toggleDarkTheme() {
        viewModelScope.launch {
            val current = userPreferences.darkTheme.first()
            userPreferences.setDarkTheme(!current)
        }
    }
}
