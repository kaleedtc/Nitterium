package com.kaleedtc.nitterium.ui.feature.profile

import android.annotation.SuppressLint
import android.webkit.JavascriptInterface
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
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
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
import com.kaleedtc.nitterium.ui.common.LocalFullScreenMode
import com.kaleedtc.nitterium.ui.common.NitterWebView
import com.kaleedtc.nitterium.ui.common.ErrorScreen
import com.kaleedtc.nitterium.ui.common.NoInternetScreen
import androidx.compose.ui.res.stringResource
import com.kaleedtc.nitterium.R

class ProfileJsInterface(private val onAvatarFound: (String) -> Unit) {
    @JavascriptInterface
    @Suppress("unused")
    fun postAvatarUrl(url: String) {
        onAvatarFound(url)
    }
}

@Composable
fun ProfileScreen(
    username: String,
    isDarkTheme: Boolean,
    onNavigateBack: () -> Unit,
    viewModel: ProfileViewModel
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(username) {
        viewModel.onEvent(ProfileEvent.LoadProfile(username))
    }

    LaunchedEffect(viewModel.effect) {
        viewModel.effect.collect { effect ->
            when (effect) {
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
        onNavigateBack = onNavigateBack
    )
}

@SuppressLint("JavascriptInterface")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileContent(
    state: ProfileState,
    isDarkTheme: Boolean,
    onEvent: (ProfileEvent) -> Unit,
    snackbarHostState: SnackbarHostState,
    onNavigateBack: () -> Unit
) {
    var webView: WebView? by remember { mutableStateOf(null) }
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val isFullScreen = LocalFullScreenMode.current.value

    val jsInterface = remember { 
        ProfileJsInterface { url -> 
            onEvent(ProfileEvent.OnAvatarFound(url)) 
        } 
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
            if (!state.isError && !isFullScreen) {
                FloatingActionButton(
                    onClick = { onEvent(ProfileEvent.ToggleSubscription(state.username)) }
                ) {
                    Icon(
                        imageVector = if (state.isSubscribed) Icons.Filled.PersonRemove else Icons.Filled.PersonAdd,
                        contentDescription = stringResource(R.string.subscribe)
                    )
                }
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
                        message = stringResource(R.string.failed_to_load_profile),
                        onRetry = {
                            onEvent(ProfileEvent.Refresh)
                        }
                    )
                } else {
                    NitterWebView(


                        url = state.currentUrl,
                        isTrueBlack = state.isTrueBlack,
                        isSiteHeaderEnabled = state.isSiteHeaderEnabled,
                        isBlockDirectXEnabled = state.isBlockDirectXEnabled,
                        useSystemFont = state.useSystemFont,
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
                        onPageFinished = { url, view ->
                            onEvent(ProfileEvent.OnPageFinished(url))
                            view.evaluateJavascript(
                                """
                                (function() {
                                    var url = null;
                                    var avatar = document.querySelector('a.profile-card-avatar');
                                    if (avatar) {
                                         url = avatar.href; 
                                    }
                                    if (!url) {
                                        var mainTweetAvatar = document.querySelector('.main-tweet .tweet-avatar img');
                                        if (mainTweetAvatar) url = mainTweetAvatar.src;
                                    }
                                    if (!url) {
                                        var anyAvatar = document.querySelector('.tweet-avatar img');
                                        if (anyAvatar) url = anyAvatar.src;
                                    }
                                    if (!url && window.location.pathname.indexOf('/status/') === -1) {
                                        var meta = document.querySelector('meta[property="og:image"]');
                                        if (meta) url = meta.content;
                                    }
                                    if (url && window.AndroidProfile) {
                                        AndroidProfile.postAvatarUrl(url);
                                    }
                                })()
                                """.trimIndent(),
                                null
                            )
                        },
                        onPageError = { code, desc -> onEvent(ProfileEvent.OnPageError(code, desc)) },
                        onWebViewCreated = { 
                            it.addJavascriptInterface(jsInterface, "AndroidProfile")
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