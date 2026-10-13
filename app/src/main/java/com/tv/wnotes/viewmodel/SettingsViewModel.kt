package com.tv.wnotes.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tv.wnotes.data.pref.PreferenceManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val preferenceManager = PreferenceManager(application)

    /** null means the user hasn't chosen yet, so the system theme is used. */
    val darkMode: StateFlow<Boolean?> = preferenceManager.darkMode
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun setDarkMode(enabled: Boolean) {
        viewModelScope.launch { preferenceManager.saveDarkMode(enabled) }
    }
}
