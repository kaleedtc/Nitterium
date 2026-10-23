package com.kaleedtc.nitterium

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kaleedtc.nitterium.data.repository.UserPreferencesRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException

data class AppUiState(
    val isLoading: Boolean = false,
    val isDynamicColor: Boolean = true,
    val isTrueBlack: Boolean = false,
    val isDarkTheme: Boolean? = null,
    val showNavLabels: Boolean = true,
    val defaultTab: String = "Search",
    val showReleaseNotes: Boolean = false,
    val isSavingReleaseNotes: Boolean = false,
    val releaseNotesSaveFailed: Boolean = false
)

class MainViewModel(
    private val preferencesRepository: UserPreferencesRepository,
    private val versionCode: Long
) : ViewModel() {

    private data class ReleaseNotesUiState(
        val dismissed: Boolean = false,
        val saving: Boolean = false,
        val saveFailed: Boolean = false
    )

    private val releaseNotesUi = MutableStateFlow(ReleaseNotesUiState())
    // Decide once per launch. Settings changes must not interrupt navigation or a restore.
    private val startupReleaseNotes = preferencesRepository.releaseNotesPreferences
        .take(1)
        .shareIn(viewModelScope, SharingStarted.Eagerly, replay = 1)

    fun dismissReleaseNotes(hideFutureUpdates: Boolean) {
        if (releaseNotesUi.value.saving) return
        releaseNotesUi.update { it.copy(saving = true, saveFailed = false) }
        viewModelScope.launch {
            try {
                preferencesRepository.dismissReleaseNotes(versionCode, hideFutureUpdates)
                releaseNotesUi.update { it.copy(dismissed = true) }
            } catch (_: IOException) {
                releaseNotesUi.update { it.copy(saveFailed = true) }
            } finally {
                releaseNotesUi.update { it.copy(saving = false) }
            }
        }
    }

    val uiState: StateFlow<AppUiState> = combine(
        combine(
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
        },
        preferencesRepository.defaultTab,
        startupReleaseNotes,
        releaseNotesUi
    ) { state, defaultTab, releaseNotes, releaseNotesState ->
        state.copy(
            isLoading = false,
            defaultTab = defaultTab,
            showReleaseNotes = releaseNotes.shouldShow(versionCode) && !releaseNotesState.dismissed,
            isSavingReleaseNotes = releaseNotesState.saving,
            releaseNotesSaveFailed = releaseNotesState.saveFailed
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AppUiState(isLoading = true)
    )
}
