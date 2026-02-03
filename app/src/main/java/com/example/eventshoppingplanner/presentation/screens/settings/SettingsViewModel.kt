package com.example.eventshoppingplanner.presentation.screens.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.eventshoppingplanner.data.preferences.AppPreference
import com.example.eventshoppingplanner.data.preferences.ThemeMode
import com.example.eventshoppingplanner.data.preferences.ThemePreference
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext context: Context
) : ViewModel() {

    private val themePreference = ThemePreference(context)
    private val appPreference = AppPreference(context)

    private val _themeMode = MutableStateFlow(ThemeMode.SYSTEM)
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _executeModeDragEnabled = MutableStateFlow(false)
    val executeModeDragEnabled: StateFlow<Boolean> = _executeModeDragEnabled.asStateFlow()

    private val _cardScale = MutableStateFlow(AppPreference.DEFAULT_CARD_SCALE)
    val cardScale: StateFlow<Float> = _cardScale.asStateFlow()

    init {
        viewModelScope.launch {
            themePreference.themeMode.collect { mode ->
                _themeMode.value = mode
            }
        }
        viewModelScope.launch {
            appPreference.executeModeDragEnabled.collect { enabled ->
                _executeModeDragEnabled.value = enabled
            }
        }
        viewModelScope.launch {
            appPreference.cardScale.collect { scale ->
                _cardScale.value = scale
            }
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            themePreference.setThemeMode(mode)
        }
    }

    fun setExecuteModeDragEnabled(enabled: Boolean) {
        viewModelScope.launch {
            appPreference.setExecuteModeDragEnabled(enabled)
        }
    }

    fun setCardScale(scale: Float) {
        viewModelScope.launch {
            appPreference.setCardScale(scale)
        }
    }

    fun resetCardScale() {
        viewModelScope.launch {
            appPreference.setCardScale(AppPreference.DEFAULT_CARD_SCALE)
        }
    }
}