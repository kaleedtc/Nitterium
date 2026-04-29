package com.kaleedtc.nitterium.ui.feature.feed

import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kaleedtc.nitterium.R
import com.kaleedtc.nitterium.ui.common.ErrorScreen
import com.kaleedtc.nitterium.ui.common.LocalFullScreenMode
import com.kaleedtc.nitterium.ui.common.NitterWebView
import com.kaleedtc.nitterium.ui.common.NoInternetScreen
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextAlign

@Composable
fun FeedScreen(
    isDarkTheme: Boolean,
    viewModel: FeedViewModel
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel.effect) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is FeedEffect.ShowSnackbar -> snackbarHostState.showSnackbar(effect.message)
            }
        }
    }

    FeedContent(
        state = state,
        isDarkTheme = isDarkTheme,
        onEvent = viewModel::onEvent,
        snackbarHostState = snackbarHostState
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedContent(
    state: FeedState,
    isDarkTheme: Boolean,
    onEvent: (FeedEvent) -> Unit,
    snackbarHostState: SnackbarHostState
) {
    var webView: WebView? by remember { mutableStateOf(null) }
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val isFullScreen = LocalFullScreenMode.current.value

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
                    title = { Text(stringResource(R.string.nav_feed)) },
                    scrollBehavior = scrollBehavior
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            if (!state.hasSubscriptions) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Filled.RssFeed,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.no_subscriptions_feed),
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else if (!state.isConnected) {
                NoInternetScreen(
                    onRetry = { onEvent(FeedEvent.Refresh) }
                )
            } else if (state.currentUrl.isNotEmpty()) {
                if (state.isError) {
                    ErrorScreen(
                        message = stringResource(R.string.failed_to_load_content),
                        onRetry = {
                            onEvent(FeedEvent.Refresh)
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
                        isProfileView = false,
                        isFeedView = true,
                        onRefresh = { 
                            onEvent(FeedEvent.Refresh)
                            if (!state.isError) {
                                webView?.reload()
                            }
                        },
                        onPageStarted = { onEvent(FeedEvent.OnPageStarted(it)) },
                        onPageFinished = { url, _ ->
                            onEvent(FeedEvent.OnPageFinished(url))
                        },
                        onPageError = { code, _ -> 
                            // Only trigger error if main frame failed
                            if (code != -2 && code != -10) { // ignore unknown host and timeout errors that are sometimes flaky
                                onEvent(FeedEvent.OnPageError) 
                            }
                        },
                        onWebViewCreated = { webView = it }
                    )
                }
            }

            com.kaleedtc.nitterium.ui.common.LoadingIndicator(
                isLoading = state.isLoading
            )
        }
    }
}
