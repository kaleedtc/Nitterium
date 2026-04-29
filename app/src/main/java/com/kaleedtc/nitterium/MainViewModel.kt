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
            preferencesRepository.darkTheme
        ) { a, b, c -> listOf(a, b, c) },
        combine(
            preferencesRepository.showNavLabels,
            preferencesRepository.useSystemFont,
            preferencesRepository.defaultTab
        ) { a, b, c -> listOf(a, b, c) }
    ) { group1, group2 ->
        val dynamic = group1[0] as Boolean
        val trueBlack = group1[1] as Boolean
        val dark = group1[2] as Boolean?
        val showLabels = group2[0] as Boolean
        val useSystemFont = group2[1] as Boolean
        val defaultTab = group2[2] as String

        AppUiState(
            isLoading = false,
            isDynamicColor = dynamic,
            isTrueBlack = trueBlack,
            isDarkTheme = dark,
            showNavLabels = showLabels,
            useSystemFont = useSystemFont,
            defaultTab = defaultTab
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AppUiState(isLoading = true)
    )
}