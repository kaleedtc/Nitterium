package com.kaleedtc.nitterium

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kaleedtc.nitterium.data.repository.UserPreferencesRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class AppUiState(
    val isDynamicColor: Boolean = true,
    val isTrueBlack: Boolean = false,
    val isDarkTheme: Boolean? = null,
    val showNavLabels: Boolean = true
)

class MainViewModel(
    preferencesRepository: UserPreferencesRepository
) : ViewModel() {

    val uiState: StateFlow<AppUiState> = combine(
        preferencesRepository.dynamicColor,
        preferencesRepository.trueBlack,
        preferencesRepository.darkTheme,
        preferencesRepository.showNavLabels
    ) { dynamic, trueBlack, dark, showLabels ->
        AppUiState(
            isDynamicColor = dynamic,
            isTrueBlack = trueBlack,
            isDarkTheme = dark,
            showNavLabels = showLabels
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AppUiState()
    )
}