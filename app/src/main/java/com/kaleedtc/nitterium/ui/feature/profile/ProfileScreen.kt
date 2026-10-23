package com.kaleedtc.nitterium.ui.feature.profile

import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kaleedtc.nitterium.ui.common.GroupAssignmentDialog
import com.kaleedtc.nitterium.ui.common.SubscriptionActionButton
import androidx.compose.ui.platform.LocalResources
import com.kaleedtc.nitterium.ui.common.LocalFullScreenMode
import com.kaleedtc.nitterium.ui.common.NitterWebView
import com.kaleedtc.nitterium.ui.common.ErrorScreen
import com.kaleedtc.nitterium.ui.common.NoInternetScreen
import androidx.compose.ui.res.stringResource
import com.kaleedtc.nitterium.R

@Composable
fun ProfileScreen(
    username: String,
    isDarkTheme: Boolean,
    onNavigateBack: () -> Unit,
    onSwitchInstance: () -> Unit,
    viewModel: ProfileViewModel
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current

    LaunchedEffect(username) {
        viewModel.onEvent(ProfileEvent.LoadProfile(username))
    }

    LaunchedEffect(viewModel.effect, resources) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is ProfileEffect.ShowMessage -> snackbarHostState.showSnackbar(resources.getString(effect.resourceId))
                is ProfileEffect.ShowSnackbar -> snackbarHostState.showSnackbar(effect.message)
                is ProfileEffect.NavigateBack -> onNavigateBack()
            }
        }
    }

    ProfileContent(
        state = state,
        isDarkTheme = isDarkTheme,
        onEvent = viewModel::onEvent,
        snackbarHostState = snackbarHostState,
        onNavigateBack = onNavigateBack,
        onSwitchInstance = onSwitchInstance
    )
}

@Composable
fun ProfileContent(
    state: ProfileState,
    isDarkTheme: Boolean,
    onEvent: (ProfileEvent) -> Unit,
    snackbarHostState: SnackbarHostState,
    onNavigateBack: () -> Unit,
    onSwitchInstance: () -> Unit
) {
    var webView: WebView? by remember { mutableStateOf(null) }
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val isFullScreen = LocalFullScreenMode.current.value


    state.groupAssignment?.let { subscription ->
        GroupAssignmentDialog(
            subscription = subscription,
            allGroups = state.groups,
            isSubscribed = state.groupAssignmentIsSubscribed,
            isSaving = state.isSavingGroups,
            onDismiss = { onEvent(ProfileEvent.DismissGroups) },
            onConfirm = { onEvent(ProfileEvent.SaveGroups(it)) }
        )
    }

    if (state.showUnsubscribeDialog) {
        AlertDialog(
            onDismissRequest = { onEvent(ProfileEvent.DismissUnsubscribeDialog) },
            title = { Text(stringResource(R.string.unsubscribe_title)) },
            text = { Text(stringResource(R.string.unsubscribe_message, state.username)) },
            confirmButton = {
                TextButton(
                    onClick = { onEvent(ProfileEvent.ConfirmUnsubscribe) }
                ) {
                    Text(stringResource(R.string.unsubscribe_confirm))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { onEvent(ProfileEvent.DismissUnsubscribeDialog) }
                ) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // Handle Back Press
    BackHandler(enabled = !state.isError && webView?.canGoBack() == true) {
        webView?.goBack()
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets.exclude(WindowInsets.navigationBars),
        topBar = {
            if (!isFullScreen) {
                CenterAlignedTopAppBar(
                    title = { },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.back)
                            )
                        }
                    },
                    scrollBehavior = scrollBehavior
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (state.username.isNotBlank() && !state.isError && !isFullScreen) {
                SubscriptionActionButton(
                    isSubscribed = state.isSubscribed,
                    onClick = { onEvent(ProfileEvent.ToggleSubscription(state.username)) },
                    onManageGroups = { onEvent(ProfileEvent.EditGroups) }
                )
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            if (!state.isConnected) {
                NoInternetScreen(
                    onRetry = { onEvent(ProfileEvent.Refresh) }
                )
            } else if (state.currentUrl.isNotEmpty()) {
                if (state.isError) {
                    ErrorScreen(
                        message = state.errorMessage ?: stringResource(R.string.failed_to_load_profile),
                        onRetry = {
                            onEvent(ProfileEvent.Refresh)
                        },
                        onSwitchInstance = onSwitchInstance
                    )
                } else {
                    NitterWebView(
                        url = state.currentUrl,
                        selectedInstanceUrl = state.selectedInstanceUrl,
                        onAvatarFound = { page, avatar -> onEvent(ProfileEvent.OnAvatarFound(page, avatar)) },
                        frontend = state.frontend,
                        onFrontendDetected = { url, type -> onEvent(ProfileEvent.FrontendDetected(url, type)) },
                        isTrueBlack = state.isTrueBlack,
                        isSiteHeaderEnabled = state.isSiteHeaderEnabled,
                        isBlockDirectXEnabled = state.isBlockDirectXEnabled,
                        darkTheme = isDarkTheme,
                        isRefreshing = state.isRefreshing,
                        isProfileView = true,
                        onRefresh = { 
                            onEvent(ProfileEvent.Refresh)
                            if (!state.isError) {
                                webView?.reload()
                            }
                        },
                        onPageStarted = { onEvent(ProfileEvent.OnPageStarted(it)) },
                        onPageFinished = { url, _ ->
                            onEvent(ProfileEvent.OnPageFinished(url))
                        },
                        onPageError = { code, desc -> onEvent(ProfileEvent.OnPageError(code, desc)) },
                        onWebViewCreated = { 
                            webView = it 
                        }
                    )
                }
            }

            com.kaleedtc.nitterium.ui.common.LoadingIndicator(
                isLoading = state.isLoading
            )
        }
    }
}
