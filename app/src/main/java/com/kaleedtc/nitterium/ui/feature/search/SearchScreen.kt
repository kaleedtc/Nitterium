package com.kaleedtc.nitterium.ui.feature.search

import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.foundation.layout.windowInsetsPadding
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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExpandedFullScreenSearchBar
import androidx.compose.material3.SearchBarValue
import androidx.compose.material3.rememberSearchBarState
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kaleedtc.nitterium.ui.common.GroupAssignmentDialog
import com.kaleedtc.nitterium.ui.common.GroupAssignmentMenu
import com.kaleedtc.nitterium.ui.common.SubscriptionActionButton
import androidx.compose.ui.platform.LocalResources
import com.kaleedtc.nitterium.ui.common.LocalFullScreenMode
import com.kaleedtc.nitterium.ui.common.NitterWebView
import com.kaleedtc.nitterium.ui.common.ErrorScreen
import com.kaleedtc.nitterium.ui.common.NoInternetScreen
import androidx.compose.ui.res.stringResource
import com.kaleedtc.nitterium.R

@Composable
fun SearchScreen(
    initialUsername: String? = null,
    deepLinkUrl: String? = null,
    isDarkTheme: Boolean,
    onSwitchInstance: () -> Unit,
    viewModel: SearchViewModel
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current

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

    LaunchedEffect(viewModel.effect, resources) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is SearchEffect.ShowMessage -> snackbarHostState.showSnackbar(resources.getString(effect.resourceId))
                is SearchEffect.ShowSnackbar -> snackbarHostState.showSnackbar(effect.message)
                is SearchEffect.NavigateToUrl -> { /* Handled by WebView state */ }
            }
        }
    }

    SearchContent(
        state = state,
        isDarkTheme = isDarkTheme,
        onEvent = viewModel::onEvent,
        snackbarHostState = snackbarHostState,
        onSwitchInstance = onSwitchInstance
    )
}

@Composable
fun SearchContent(
    state: SearchState,
    isDarkTheme: Boolean,
    onEvent: (SearchEvent) -> Unit,
    snackbarHostState: SnackbarHostState,
    onSwitchInstance: () -> Unit
) {
    var webView: WebView? by remember { mutableStateOf(null) }
    val focusManager = LocalFocusManager.current
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val isFullScreen = LocalFullScreenMode.current.value

    val searchBarState = rememberSearchBarState(
        initialValue = if (state.isSearchBarActive) SearchBarValue.Expanded else SearchBarValue.Collapsed
    )
    val textFieldState = rememberTextFieldState(state.searchQuery)
    val currentOnEvent by rememberUpdatedState(onEvent)

    // Keep the state-based Material components connected to the feature's MVI state.
    LaunchedEffect(state.searchQuery) {
        if (textFieldState.text.toString() != state.searchQuery) {
            textFieldState.setTextAndPlaceCursorAtEnd(state.searchQuery)
        }
    }
    LaunchedEffect(textFieldState) {
        snapshotFlow { textFieldState.text.toString() }
            .distinctUntilChanged()
            .collect { currentOnEvent(SearchEvent.UpdateQuery(it)) }
    }
    LaunchedEffect(state.isSearchBarActive) {
        val target = if (state.isSearchBarActive) SearchBarValue.Expanded else SearchBarValue.Collapsed
        if (searchBarState.targetValue != target) {
            if (state.isSearchBarActive) searchBarState.animateToExpanded()
            else searchBarState.animateToCollapsed()
        }
    }
    LaunchedEffect(searchBarState) {
        snapshotFlow { searchBarState.targetValue == SearchBarValue.Expanded }
            .distinctUntilChanged()
            .collect { currentOnEvent(SearchEvent.ToggleSearchBar(it)) }
    }


    state.groupAssignment?.let { subscription ->
        GroupAssignmentDialog(
            subscription = subscription,
            allGroups = state.groups,
            isSubscribed = state.groupAssignmentIsSubscribed,
            isSaving = state.isSavingGroups,
            onDismiss = { onEvent(SearchEvent.DismissGroups) },
            onConfirm = { onEvent(SearchEvent.SaveGroups(it)) }
        )
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
                                if (state.currentUsername != null && !state.isError) {
                                    GroupAssignmentMenu(state.currentUsername) { onEvent(SearchEvent.EditGroups) }
                                }
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
                        SubscriptionActionButton(
                            isSubscribed = state.isSubscribed,
                            onClick = { onEvent(SearchEvent.ToggleSubscription(state.currentUsername)) },
                            onManageGroups = { onEvent(SearchEvent.EditGroups) }
                        )
                    }
                }
            ) { innerPadding ->
                // WebView Content
                Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
                    if (state.isError) {
                        ErrorScreen(
                            message = state.errorMessage ?: stringResource(R.string.failed_to_load_content),
                            onRetry = { 
                                // Do NOT call webView.reload() here. The WebView might be dead/detached.
                                // Resetting the error state will cause NitterWebView to be recomposed and created fresh.
                                onEvent(SearchEvent.Refresh)
                            },
                            onSwitchInstance = onSwitchInstance
                        )
                    } else {
                        NitterWebView(
                            url = state.currentUrl,
                            selectedInstanceUrl = state.selectedInstanceUrl,
                            onAvatarFound = { page, avatar -> onEvent(SearchEvent.OnAvatarFound(page, avatar)) },
                            frontend = state.frontend,
                            onFrontendDetected = { url, type -> onEvent(SearchEvent.FrontendDetected(url, type)) },
                            isTrueBlack = state.isTrueBlack,
                            isSiteHeaderEnabled = state.isSiteHeaderEnabled,
                            isBlockDirectXEnabled = state.isBlockDirectXEnabled,
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
                            onPageFinished = { url, _ ->
                                onEvent(SearchEvent.OnPageFinished(url)) 
                            },
                            onPageError = { code, desc -> onEvent(SearchEvent.OnPageError(code, desc)) },
                            onWebViewCreated = { 
                                webView = it 
                            }
                        )
                    }
                }
            }
        } else if (!state.isLoading) {
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
        if ((state.currentUrl.isEmpty() || state.isSearchBarActive) && !state.isLoading && !isFullScreen) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
            ) {
                val colors = SearchBarDefaults.colors()
                val inputField: @Composable () -> Unit = {
                    SearchBarDefaults.InputField(
                        textFieldState = textFieldState,
                        searchBarState = searchBarState,
                        onSearch = { query ->
                            onEvent(SearchEvent.PerformSearch(query))
                            focusManager.clearFocus()
                        },
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
                            if (textFieldState.text.isNotEmpty()) {
                                IconButton(onClick = { textFieldState.setTextAndPlaceCursorAtEnd("") }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear")
                                }
                            }
                        },
                        colors = colors.inputFieldColors
                    )
                }
                SearchBar(
                    state = searchBarState,
                    inputField = inputField,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .windowInsetsPadding(SearchBarDefaults.windowInsets),
                    colors = colors
                )
                ExpandedFullScreenSearchBar(
                    state = searchBarState,
                    inputField = inputField,
                    colors = colors
                ) {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val query = textFieldState.text.toString()
                        if (query.isNotEmpty()) {
                            item {
                                ListItem(
                                    onClick = {
                                        onEvent(SearchEvent.PerformSearch(query))
                                        focusManager.clearFocus()
                                    },
                                    leadingContent = { Icon(Icons.Default.Search, null) }
                                ) {
                                    Text(stringResource(R.string.search_for, query))
                                }
                            }
                        }
                    }
                }
            }
        }

        // Loading Indicator (Global)
        com.kaleedtc.nitterium.ui.common.LoadingIndicator(
            isLoading = state.isLoading
        )
    }
}
