package com.kaleedtc.nitterium

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kaleedtc.nitterium.data.repository.UserPreferencesRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class AppUiState(
    val isLoading: Boolean = false,
    val isDynamicColor: Boolean = true,
    val isTrueBlack: Boolean = false,
    val isDarkTheme: Boolean? = null,
    val showNavLabels: Boolean = true,
    val useSystemFont: Boolean = false,
    val defaultTab: String = "Search"
)

class MainViewModel(
    preferencesRepository: UserPreferencesRepository
) : ViewModel() {

    val uiState: StateFlow<AppUiState> = combine(
        combine(
            preferencesRepository.dynamicColor,
            preferencesRepository.trueBlack,
            preferencesRepository.darkTheme,
            preferencesRepository.showNavLabels,
            preferencesRepository.useSystemFont
        ) { dynamic, trueBlack, dark, showLabels, useSystemFont ->
            AppUiState(
                isDynamicColor = dynamic,
                isTrueBlack = trueBlack,
                isDarkTheme = dark,
                showNavLabels = showLabels,
                useSystemFont = useSystemFont
            )
        },
        preferencesRepository.defaultTab
    ) { state, defaultTab ->
        state.copy(
            isLoading = false,
            defaultTab = defaultTab
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AppUiState(isLoading = true)
    )
}