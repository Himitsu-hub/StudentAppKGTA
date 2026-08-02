package ru.alemak.studentapp.ui.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.alemak.studentapp.data.local.UserPreferences

@HiltViewModel
class ThemeViewModel @Inject constructor(
    private val userPreferences: UserPreferences,
) : ViewModel() {

    private val _prefsReady = MutableStateFlow(false)
    val prefsReady: StateFlow<Boolean> = _prefsReady.asStateFlow()

    val darkTheme: StateFlow<Boolean> = userPreferences.darkTheme
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

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
