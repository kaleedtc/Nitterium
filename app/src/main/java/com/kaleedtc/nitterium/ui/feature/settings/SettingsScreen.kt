package com.kaleedtc.nitterium.ui.feature.settings

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kaleedtc.nitterium.R
import androidx.core.net.toUri

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(viewModel.effect) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is SettingsEffect.NavigateToUrl -> {
                    val intent = Intent(Intent.ACTION_VIEW, effect.url.toUri())
                    context.startActivity(intent)
                }
            }
        }
    }

    SettingsContent(
        state = state,
        onEvent = viewModel::onEvent
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsContent(
    state: SettingsState,
    onEvent: (SettingsEvent) -> Unit
) {
    val scrollBehavior =
        TopAppBarDefaults.exitUntilCollapsedScrollBehavior(
            rememberTopAppBarState(),
            canScroll = { true },
        )

    var selectedTabIndex by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf(
        stringResource(R.string.app_settings),
        stringResource(R.string.instance_options)
    )

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets.exclude(WindowInsets.navigationBars),
        topBar = {
            MediumTopAppBar(
                title = { Text(stringResource(R.string.nav_settings)) },
                scrollBehavior = scrollBehavior
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            SecondaryTabRow(selectedTabIndex = selectedTabIndex) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = { Text(title) }
                    )
                }
            }

            Box(modifier = Modifier.weight(1f)) {
                when (selectedTabIndex) {
                    0 -> AppSettingsList(state, onEvent)
                    1 -> InstanceSettingsList(state, onEvent)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSettingsList(
    state: SettingsState,
    onEvent: (SettingsEvent) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = stringResource(R.string.network),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))

            val (expanded, setExpanded) = remember { mutableStateOf(false) }

            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = setExpanded,
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = state.instanceUrl,
                    onValueChange = { onEvent(SettingsEvent.UpdateInstanceUrl(it)) },
                    label = { Text(stringResource(R.string.nitter_instance_url)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable),
                    singleLine = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
                )

                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { setExpanded(false) }
                ) {
                    state.availableInstances.forEach { instance ->
                        DropdownMenuItem(
                            text = { Text(instance) },
                            onClick = {
                                onEvent(SettingsEvent.UpdateInstanceUrl(instance))
                                setExpanded(false)
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        item {
            Text(
                text = stringResource(R.string.appearance),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))

            SettingsSwitch(
                label = stringResource(R.string.dynamic_color),
                checked = state.isDynamicColor,
                onCheckedChange = { onEvent(SettingsEvent.UpdateDynamicColor(it)) }
            )

            SettingsSwitch(
                label = stringResource(R.string.true_black),
                checked = state.isTrueBlack,
                onCheckedChange = { onEvent(SettingsEvent.UpdateTrueBlack(it)) }
            )

            SettingsSwitch(
                label = stringResource(R.string.show_nav_labels),
                checked = state.isNavLabelsEnabled,
                onCheckedChange = { onEvent(SettingsEvent.UpdateNavLabels(it)) }
            )

            SettingsSwitch(
                label = stringResource(R.string.block_direct_x),
                checked = state.isBlockDirectXEnabled,
                onCheckedChange = { onEvent(SettingsEvent.UpdateBlockDirectX(it)) },
                icon = {
                    SettingsInfoIcon(
                        title = stringResource(R.string.block_direct_x),
                        description = stringResource(R.string.block_direct_x_description)
                    )
                }
            )

            Spacer(modifier = Modifier.height(16.dp))
            Text(text = stringResource(R.string.theme_mode), style = MaterialTheme.typography.bodyLarge)
        }

        item {
            ThemeOption(
                label = stringResource(R.string.system_default),
                selected = state.isDarkTheme == null,
                onClick = { onEvent(SettingsEvent.UpdateDarkTheme(null)) }
            )
        }
        item {
            ThemeOption(
                label = stringResource(R.string.light),
                selected = state.isDarkTheme == false,
                onClick = { onEvent(SettingsEvent.UpdateDarkTheme(false)) }
            )
        }
        item {
            ThemeOption(
                label = stringResource(R.string.dark),
                selected = state.isDarkTheme == true,
                onClick = { onEvent(SettingsEvent.UpdateDarkTheme(true)) }
            )
            Spacer(modifier = Modifier.height(24.dp))
        }

        item {
            HorizontalDivider()
            Spacer(modifier = Modifier.height(24.dp))
        }

        item {
            Text(
                text = stringResource(R.string.about),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        item {
            SettingsItem(
                label = stringResource(R.string.version, state.appVersion),
                icon = Icons.Default.Info
            )
        }

        item {
            val appUrl = stringResource(R.string.app_github_url)
            SettingsLink(
                label = stringResource(R.string.app_page),
                icon = Icons.Default.Public,
                onClick = { onEvent(SettingsEvent.OpenUrl(appUrl)) }
            )
        }

        item {
            val developerUrl = stringResource(R.string.developer_github_url)
            SettingsLink(
                label = stringResource(R.string.developer_page),
                icon = Icons.Default.Code,
                onClick = { onEvent(SettingsEvent.OpenUrl(developerUrl)) }
            )
        }
    }
}

@Composable
fun SettingsItem(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(end = 16.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun InstanceSettingsList(
    state: SettingsState,
    onEvent: (SettingsEvent) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = stringResource(R.string.global_settings_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            Text(
                text = stringResource(R.string.privacy_filtering),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))

            SettingsSwitch(
                label = stringResource(R.string.hide_tweet_stats),
                checked = state.instanceSettings.hideTweetStats,
                onCheckedChange = { onEvent(SettingsEvent.UpdateInstanceSetting("hideTweetStats", it)) }
            )
            SettingsSwitch(
                label = stringResource(R.string.hide_profile_banner),
                checked = state.instanceSettings.hideBanner,
                onCheckedChange = { onEvent(SettingsEvent.UpdateInstanceSetting("hideBanner", it)) }
            )
            SettingsSwitch(
                label = stringResource(R.string.hide_pinned_tweets),
                checked = state.instanceSettings.hidePins,
                onCheckedChange = { onEvent(SettingsEvent.UpdateInstanceSetting("hidePins", it)) }
            )
            
            Spacer(modifier = Modifier.height(24.dp))
        }

        item {
            Text(
                text = stringResource(R.string.media_playback),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))

            SettingsSwitch(
                label = stringResource(R.string.hls_playback),
                checked = state.instanceSettings.hlsPlayback,
                onCheckedChange = { onEvent(SettingsEvent.UpdateInstanceSetting("hlsPlayback", it)) }
            )
            SettingsSwitch(
                label = stringResource(R.string.proxy_videos),
                checked = state.instanceSettings.proxyVideos,
                onCheckedChange = { onEvent(SettingsEvent.UpdateInstanceSetting("proxyVideos", it)) },
                icon = {
                    SettingsInfoIcon(
                        title = stringResource(R.string.proxy_videos),
                        description = stringResource(R.string.proxy_videos_description)
                    )
                }
            )
            SettingsSwitch(
                label = stringResource(R.string.mute_videos),
                checked = state.instanceSettings.muteVideos,
                onCheckedChange = { onEvent(SettingsEvent.UpdateInstanceSetting("muteVideos", it)) }
            )
             SettingsSwitch(
                label = stringResource(R.string.autoplay_gifs),
                checked = state.instanceSettings.autoplayGifs,
                onCheckedChange = { onEvent(SettingsEvent.UpdateInstanceSetting("autoplayGifs", it)) }
            )
            
            Spacer(modifier = Modifier.height(24.dp))
        }

        item {
            Text(
                text = stringResource(R.string.navigation),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            
            SettingsSwitch(
                label = stringResource(R.string.infinite_scroll),
                checked = state.instanceSettings.infiniteScroll,
                onCheckedChange = { onEvent(SettingsEvent.UpdateInstanceSetting("infiniteScroll", it)) }
            )
        }
    }
}

@Composable
fun SettingsLink(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(end = 16.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        Icon(
            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
fun SettingsInfoIcon(title: String, description: String) {
    var showDialog by remember { mutableStateOf(false) }

    IconButton(onClick = { showDialog = true }) {
        Icon(Icons.Default.Info, contentDescription = stringResource(R.string.info))
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            confirmButton = {
                TextButton(onClick = { showDialog = false }) { 
                    Text(stringResource(R.string.ok)) 
                }
            },
            title = { Text(title) },
            text = { Text(description) }
        )
    }
}

@Composable
fun SettingsSwitch(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    icon: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(
                value = checked,
                onValueChange = onCheckedChange,
                role = Role.Switch
            )
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        if (icon != null) {
            icon()
        }
        Switch(
            checked = checked,
            onCheckedChange = null
        )
    }
}

@Composable
fun ThemeOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(
                value = selected,
                onValueChange = { onClick() },
                role = Role.RadioButton
            )
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = null
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(start = 16.dp)
        )
    }
}
