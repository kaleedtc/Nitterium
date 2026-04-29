package com.kaleedtc.nitterium.ui

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.RssFeed
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.kaleedtc.nitterium.NitteriumApplication
import com.kaleedtc.nitterium.ui.common.viewModelFactory
import com.kaleedtc.nitterium.ui.feature.profile.ProfileScreen
import com.kaleedtc.nitterium.ui.feature.profile.ProfileViewModel
import com.kaleedtc.nitterium.ui.feature.search.SearchScreen
import com.kaleedtc.nitterium.ui.feature.search.SearchViewModel
import com.kaleedtc.nitterium.ui.feature.settings.SettingsScreen
import com.kaleedtc.nitterium.ui.feature.settings.SettingsViewModel
import com.kaleedtc.nitterium.ui.feature.subscriptions.SubscriptionsScreen
import com.kaleedtc.nitterium.ui.feature.subscriptions.SubscriptionsViewModel
import com.kaleedtc.nitterium.ui.navigation.Profile
import com.kaleedtc.nitterium.ui.navigation.Search
import com.kaleedtc.nitterium.ui.navigation.Settings
import com.kaleedtc.nitterium.ui.navigation.Subscriptions
import com.kaleedtc.nitterium.ui.navigation.Feed
import com.kaleedtc.nitterium.ui.feature.feed.FeedScreen
import com.kaleedtc.nitterium.ui.feature.feed.FeedViewModel

import com.kaleedtc.nitterium.ui.common.LocalFullScreenMode

import androidx.compose.ui.res.stringResource
import com.kaleedtc.nitterium.R

@Composable
fun NitteriumApp(
    app: NitteriumApplication,
    isDarkTheme: Boolean,
    initialIntentUrl: String? = null,
    showNavLabels: Boolean = true,
    useSystemFont: Boolean = false,
    defaultTab: String = "Search"
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val isFullScreen = LocalFullScreenMode.current.value

    val currentTab = rememberSaveable { mutableStateOf(defaultTab) }

    LaunchedEffect(currentDestination) {
        if (currentDestination?.hierarchy?.any { it.hasRoute<Search>() } == true) {
            currentTab.value = "Search"
        } else if (currentDestination?.hierarchy?.any { it.hasRoute<Subscriptions>() } == true) {
            currentTab.value = "Subscriptions"
        } else if (currentDestination?.hierarchy?.any { it.hasRoute<Feed>() } == true) {
            currentTab.value = "Feed"
        } else if (currentDestination?.hierarchy?.any { it.hasRoute<Settings>() } == true) {
            currentTab.value = "Settings"
        }
    }

    val isSubscriptionsFlow = currentTab.value == "Subscriptions"
    val isFeedFlow = currentTab.value == "Feed"
    val isSettingsFlow = currentTab.value == "Settings"
    val isSearchFlow = currentTab.value == "Search"

    var deepLinkHandled by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            if (!isFullScreen) {
                NavigationBar {
                    NavigationBarItem(
                        selected = isSearchFlow,
                        onClick = {
                            if (!isSearchFlow) {
                                currentTab.value = "Search"
                                navController.navigate(Search()) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                        icon = {
                            Icon(
                                imageVector = if (isSearchFlow) Icons.Filled.Search else Icons.Outlined.Search,
                                contentDescription = stringResource(R.string.nav_search)
                            )
                        },
                        label = if (showNavLabels) {
                            { Text(stringResource(R.string.nav_search)) }
                        } else null
                    )
                    NavigationBarItem(
                        selected = isSubscriptionsFlow,
                        onClick = {
                            if (!isSubscriptionsFlow) {
                                currentTab.value = "Subscriptions"
                                navController.navigate(Subscriptions) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                        icon = {
                            Icon(
                                imageVector = if (isSubscriptionsFlow) Icons.AutoMirrored.Filled.List else Icons.AutoMirrored.Outlined.List,
                                contentDescription = stringResource(R.string.nav_subscriptions)
                            )
                        },
                        label = if (showNavLabels) {
                            { Text(stringResource(R.string.nav_subscriptions)) }
                        } else null
                    )
                    NavigationBarItem(
                        selected = isFeedFlow,
                        onClick = {
                            if (!isFeedFlow) {
                                currentTab.value = "Feed"
                                navController.navigate(Feed) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                        icon = {
                            Icon(
                                imageVector = if (isFeedFlow) Icons.Filled.RssFeed else Icons.Outlined.RssFeed,
                                contentDescription = stringResource(R.string.nav_feed)
                            )
                        },
                        label = if (showNavLabels) {
                            { Text(stringResource(R.string.nav_feed)) }
                        } else null
                    )
                    NavigationBarItem(
                        selected = isSettingsFlow,
                        onClick = {
                            if (!isSettingsFlow) {
                                currentTab.value = "Settings"
                                navController.navigate(Settings) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                        icon = {
                            Icon(
                                imageVector = if (isSettingsFlow) Icons.Filled.Settings else Icons.Outlined.Settings,
                                contentDescription = stringResource(R.string.nav_settings)
                            )
                        },
                        label = if (showNavLabels) {
                            { Text(stringResource(R.string.nav_settings)) }
                        } else null
                    )
                }
            }
        }
    ) { innerPadding ->
        val layoutDirection = LocalLayoutDirection.current
        
        val startDestination = when (defaultTab) {
            "Search" -> Search()
            "Subscriptions" -> Subscriptions
            "Feed" -> Feed
            "Settings" -> Settings
            else -> Search()
        }

        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(
                start = innerPadding.calculateLeftPadding(layoutDirection),
                end = innerPadding.calculateRightPadding(layoutDirection),
                bottom = innerPadding.calculateBottomPadding(),
                top = 0.dp // Ignore top padding to allow drawing behind status bar
            ),
            enterTransition = { slideInHorizontally { it } + fadeIn() },
            exitTransition = { slideOutHorizontally { -it } + fadeOut() },
            popEnterTransition = { slideInHorizontally { -it } + fadeIn() },
            popExitTransition = { slideOutHorizontally { it } + fadeOut() }
        ) {
            composable<Search> {
                val viewModel: SearchViewModel = viewModel(
                    factory = viewModelFactory {
                        SearchViewModel(
                            app.userPreferencesRepository,
                            app.subscriptionRepository,
                            app.connectivityMonitor
                        )
                    }
                )

                val deepLinkToPass =
                    if (!deepLinkHandled && initialIntentUrl != null) initialIntentUrl else null

                SearchScreen(
                    deepLinkUrl = deepLinkToPass,
                    isDarkTheme = isDarkTheme,
                    viewModel = viewModel
                )

            }

            composable<Profile> { backStackEntry ->
                val profile: Profile = backStackEntry.toRoute()
                val viewModel: ProfileViewModel = viewModel(
                    factory = viewModelFactory {
                        ProfileViewModel(
                            app.userPreferencesRepository,
                            app.subscriptionRepository,
                            app.connectivityMonitor
                        )
                    }
                )

                ProfileScreen(
                    username = profile.username,
                    isDarkTheme = isDarkTheme,
                    onNavigateBack = { navController.popBackStack() },
                    viewModel = viewModel
                )
            }

            composable<Subscriptions> {
                val viewModel: SubscriptionsViewModel = viewModel(
                    factory = viewModelFactory {
                        SubscriptionsViewModel(app.subscriptionRepository)
                    }
                )
                SubscriptionsScreen(
                    onNavigateToUser = { username ->
                        navController.navigate(Profile(username = username))
                    },
                    viewModel = viewModel
                )
            }
            composable<Feed> {
                val viewModel: FeedViewModel = viewModel(
                    factory = viewModelFactory {
                        FeedViewModel(
                            app.userPreferencesRepository,
                            app.subscriptionRepository,
                            app.connectivityMonitor
                        )
                    }
                )
                FeedScreen(
                    isDarkTheme = isDarkTheme,
                    viewModel = viewModel
                )
            }
            composable<Settings> {
                val viewModel: SettingsViewModel = viewModel(
                    factory = viewModelFactory {
                        SettingsViewModel(app.userPreferencesRepository, app)
                    }
                )
                SettingsScreen(viewModel = viewModel)
            }
        }
    }
}