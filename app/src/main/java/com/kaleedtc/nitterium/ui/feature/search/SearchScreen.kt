package com.kaleedtc.nitterium.ui.feature.search

import android.annotation.SuppressLint
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kaleedtc.nitterium.ui.common.LocalFullScreenMode
import com.kaleedtc.nitterium.ui.common.NitterWebView
import com.kaleedtc.nitterium.ui.common.ErrorScreen
import com.kaleedtc.nitterium.ui.common.NoInternetScreen
import androidx.compose.ui.res.stringResource
import com.kaleedtc.nitterium.R

class SearchJsInterface(private val onAvatarFound: (String) -> Unit) {
    @JavascriptInterface
    @Suppress("unused")
    fun postAvatarUrl(url: String) {
        onAvatarFound(url)
    }
}

@Composable
fun SearchScreen(
    initialUsername: String? = null,
    deepLinkUrl: String? = null,
    isDarkTheme: Boolean,
    viewModel: SearchViewModel
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(initialUsername) {
        if (initialUsername != null) {
            viewModel.loadUsername(initialUsername)
        }
    }

    LaunchedEffect(deepLinkUrl) {
        if (deepLinkUrl != null) {
            viewModel.onEvent(SearchEvent.ProcessDeepLink(deepLinkUrl))
        }
    }

    LaunchedEffect(viewModel.effect) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is SearchEffect.ShowSnackbar -> snackbarHostState.showSnackbar(effect.message)
                is SearchEffect.NavigateToUrl -> { /* Handled by WebView state */ }
            }
        }
    }

    SearchContent(
        state = state,
        isDarkTheme = isDarkTheme,
        onEvent = viewModel::onEvent,
        snackbarHostState = snackbarHostState
    )
}

@SuppressLint("JavascriptInterface")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchContent(
    state: SearchState,
    isDarkTheme: Boolean,
    onEvent: (SearchEvent) -> Unit,
    snackbarHostState: SnackbarHostState
) {
    var webView: WebView? by remember { mutableStateOf(null) }
    val focusManager = LocalFocusManager.current
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val isFullScreen = LocalFullScreenMode.current.value

    val jsInterface = remember { 
        SearchJsInterface { url -> 
            onEvent(SearchEvent.OnAvatarFound(url)) 
        } 
    }

    if (state.showUnsubscribeDialog && state.currentUsername != null) {
        AlertDialog(
            onDismissRequest = { onEvent(SearchEvent.DismissUnsubscribeDialog) },
            title = { Text(stringResource(R.string.unsubscribe_title)) },
            text = { Text(stringResource(R.string.unsubscribe_message, state.currentUsername)) },
            confirmButton = {
                TextButton(
                    onClick = { onEvent(SearchEvent.ConfirmUnsubscribe) }
                ) {
                    Text(stringResource(R.string.unsubscribe_confirm))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { onEvent(SearchEvent.DismissUnsubscribeDialog) }
                ) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // Handle Back Press
    // Safeguard: Do not access webView.canGoBack() if we are in an error state (Renderer might be gone)
    BackHandler(enabled = state.isSearchBarActive || (!state.isError && webView?.canGoBack() == true)) {
        if (state.isSearchBarActive) {
            onEvent(SearchEvent.ToggleSearchBar(false))
        } else if (state.isError) {
             onEvent(SearchEvent.ClearError)
             // Force reload current URL instead of trying to go back on a dead WebView
             onEvent(SearchEvent.LoadUrl(state.currentUrl))
        } else {
            webView?.goBack()
        }
    }

    // Root Container
    Box(modifier = Modifier.fillMaxSize()) {

        // --- Layer 1: Main Content ---
        if (!state.isConnected) {
            NoInternetScreen(
                onRetry = { onEvent(SearchEvent.Refresh) }
            )
        } else if (state.currentUrl.isNotEmpty()) {
            // RESULTS MODE: Uses Scaffold with Scrollable Top Bar
            Scaffold(
                modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
                contentWindowInsets = ScaffoldDefaults.contentWindowInsets.exclude(WindowInsets.navigationBars),
                topBar = {
                    // Only show TopBar if SearchOverlay isn't active (to prevent double headers) AND not in full screen
                    if (!state.isSearchBarActive && !isFullScreen) {
                        CenterAlignedTopAppBar(
                            title = {
                                Text(
                                    text = state.searchQuery.ifEmpty { stringResource(R.string.app_name) },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            actions = {
                                IconButton(onClick = { onEvent(SearchEvent.ToggleSearchBar(true)) }) {
                                    Icon(Icons.Default.Search, contentDescription = stringResource(R.string.nav_search))
                                }
                            },
                            scrollBehavior = scrollBehavior
                        )
                    }
                },
                snackbarHost = { SnackbarHost(snackbarHostState) },
                floatingActionButton = {
                    if (state.currentUsername != null && !state.isError && !state.isSearchBarActive && !isFullScreen) {
                        FloatingActionButton(
                            onClick = { onEvent(SearchEvent.ToggleSubscription(state.currentUsername)) }
                        ) {
                            Icon(
                                imageVector = if (state.isSubscribed) Icons.Filled.PersonRemove else Icons.Filled.PersonAdd,
                                contentDescription = stringResource(R.string.subscribe)
                            )
                        }
                    }
                }
            ) { innerPadding ->
                // WebView Content
                Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
                    if (state.isError) {
                        ErrorScreen(
                            onRetry = { 
                                // Do NOT call webView.reload() here. The WebView might be dead/detached.
                                // Resetting the error state will cause NitterWebView to be recomposed and created fresh.
                                onEvent(SearchEvent.Refresh)
                            }
                        )
                    } else {
                        NitterWebView(
                            url = state.currentUrl,
                            isTrueBlack = state.isTrueBlack,
                            isSiteHeaderEnabled = state.isSiteHeaderEnabled,
                            darkTheme = isDarkTheme,
                            isRefreshing = state.isRefreshing,
                            isProfileView = state.currentUsername != null,
                            onRefresh = { 
                                onEvent(SearchEvent.Refresh)
                                if (!state.isError) {
                                    webView?.reload()
                                }
                            },
                            onPageStarted = { onEvent(SearchEvent.OnPageStarted(it)) },
                            onPageFinished = { url, view -> 
                                onEvent(SearchEvent.OnPageFinished(url)) 
                                view.evaluateJavascript(
                                    """
                                    (function() {
                                        var avatar = document.querySelector('a.profile-card-avatar');
                                        var url = null;
                                        if (avatar) {
                                             url = avatar.href; 
                                        }
                                        if (!url) {
                                            var meta = document.querySelector('meta[property="og:image"]');
                                            if (meta) url = meta.content;
                                        }
                                        if (url && window.AndroidSearch) {
                                            AndroidSearch.postAvatarUrl(url);
                                        }
                                    })()
                                    """.trimIndent(),
                                    null
                                )
                            },
                            onPageError = { code, desc -> onEvent(SearchEvent.OnPageError(code, desc)) },
                            onWebViewCreated = { 
                                it.addJavascriptInterface(jsInterface, "AndroidSearch")
                                webView = it 
                            }
                        )
                    }
                }
            }
        } else {
            // HOME MODE: Centered welcome content
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Welcome Content
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = null,
                    modifier = Modifier.size(80.dp),
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.search_nitter),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = stringResource(R.string.search_hint),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Snackbar host for Home mode
            Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 80.dp)) {
                SnackbarHost(snackbarHostState)
            }
        }

        // --- Layer 2: Search Bar Overlay ---
        // Show SearchBar if:
        // 1. We are Home (currentUrl empty) -> Always show (Inactive/Active)
        // 2. OR We are actively searching (isSearchBarActive) -> Show (Active)
        // AND not in full screen
        if ((state.currentUrl.isEmpty() || state.isSearchBarActive) && !isFullScreen) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
            ) {
                val onActiveChange: (Boolean) -> Unit = { active -> onEvent(SearchEvent.ToggleSearchBar(active)) }
                val colors1 = SearchBarDefaults.colors()
                SearchBar(
                    inputField = {
                        SearchBarDefaults.InputField(
                            query = state.searchQuery,
                            onQueryChange = { q -> onEvent(SearchEvent.UpdateQuery(q)) },
                            onSearch = { q ->
                                        onEvent(SearchEvent.PerformSearch(q))
                                        focusManager.clearFocus()
                                    },
                            expanded = state.isSearchBarActive,
                            onExpandedChange = onActiveChange,
                            enabled = true,
                            placeholder = { Text(stringResource(R.string.search_placeholder)) },
                            leadingIcon = {
                                        if (state.isSearchBarActive) {
                                            IconButton(onClick = { onEvent(SearchEvent.ToggleSearchBar(false)) }) {
                                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                                            }
                                        } else {
                                            Icon(Icons.Default.Search, contentDescription = stringResource(R.string.nav_search))
                                        }
                                    },
                            trailingIcon = {
                                        if (state.searchQuery.isNotEmpty()) {
                                            IconButton(onClick = { onEvent(SearchEvent.UpdateQuery("")) }) {
                                                Icon(Icons.Default.Close, contentDescription = "Clear")
                                            }
                                        }
                                    },
                            colors = colors1.inputFieldColors,
                            interactionSource = null,
                        )
                    },
                    expanded = state.isSearchBarActive,
                    onExpandedChange = onActiveChange,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = if (state.isSearchBarActive) 0.dp else 16.dp),
                    shape = SearchBarDefaults.inputFieldShape,
                    colors = colors1,
                    tonalElevation = SearchBarDefaults.TonalElevation,
                    shadowElevation = SearchBarDefaults.ShadowElevation,
                    windowInsets = SearchBarDefaults.windowInsets,
                    content = {
                        LazyColumn(
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            if (state.searchQuery.isNotEmpty()) {
                                item {
                                    ListItem(
                                        headlineContent = { Text(stringResource(R.string.search_for, state.searchQuery)) },
                                        leadingContent = { Icon(Icons.Default.Search, null) },
                                        modifier = Modifier.clickable {
                                            onEvent(SearchEvent.PerformSearch(state.searchQuery))
                                            focusManager.clearFocus()
                                        }
                                    )
                                }
                            }
                        }
                    },
                )
            }
        }

        // Loading Indicator (Global)
        com.kaleedtc.nitterium.ui.common.LoadingIndicator(
            isLoading = state.isLoading
        )
    }
    }