package com.kaleedtc.nitterium.ui.feature.groups

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kaleedtc.nitterium.R
import com.kaleedtc.nitterium.data.model.FeedGroup

import androidx.core.graphics.toColorInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedGroupsScreen(
    viewModel: FeedGroupsViewModel,
    onNavigateToGroup: (String, String) -> Unit,
    onNavigateToAll: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showAddDialog by remember { mutableStateOf(false) }
    var groupToEdit by remember { mutableStateOf<FeedGroup?>(null) }

    LaunchedEffect(viewModel.effect) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is FeedGroupsEffect.NavigateToGroupFeed -> onNavigateToGroup(effect.groupId, effect.groupName)
                FeedGroupsEffect.NavigateToAllFeed -> onNavigateToAll()
            }
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.groups)) }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_group))
            }
        }
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(150.dp),
            contentPadding = padding,
            modifier = Modifier.fillMaxSize().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // "All" group
            item {
                GroupCard(
                    name = stringResource(R.string.all),
                    count = state.totalSubscriptionsCount,
                    icon = Icons.Default.RssFeed,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    onClick = { viewModel.onEvent(FeedGroupsEvent.OpenAllFeed) }
                )
            }

            items(state.groups) { group ->
                val count = state.subscriptionCounts[group.id] ?: 0
                val color = group.color?.let { Color(it.toColorInt()) } 
                    ?: MaterialTheme.colorScheme.surfaceVariant
                
                GroupCard(
                    name = group.name,
                    count = count,
                    icon = getIconForName(group.icon),
                    color = color,
                    onClick = { viewModel.onEvent(FeedGroupsEvent.OpenGroup(group.id, group.name)) },
                    onLongClick = { groupToEdit = group }
                )
            }
        }

        if (showAddDialog) {
            GroupDialog(
                initialGroup = null,
                onDismiss = { showAddDialog = false },
                onConfirm = { name, icon, color ->
                    viewModel.onEvent(FeedGroupsEvent.AddGroup(name, color = color, icon = icon))
                    showAddDialog = false
                },
                onDelete = null
            )
        }

        if (groupToEdit != null) {
            GroupDialog(
                initialGroup = groupToEdit,
                onDismiss = { groupToEdit = null },
                onConfirm = { name, icon, color ->
                    viewModel.onEvent(FeedGroupsEvent.UpdateGroup(groupToEdit!!.id, name, icon = icon, color = color))
                    groupToEdit = null
                },
                onDelete = {
                    viewModel.onEvent(FeedGroupsEvent.DeleteGroup(groupToEdit!!.id))
                    groupToEdit = null
                }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GroupCard(
    name: String,
    count: Int,
    icon: ImageVector,
    color: Color,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(color),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = contentColorFor(color)
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    Text(
                        text = "($count)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun GroupDialog(
    initialGroup: FeedGroup?,
    onDismiss: () -> Unit,
    onConfirm: (String, String, String) -> Unit,
    onDelete: (() -> Unit)?
) {
    var name by remember { mutableStateOf(initialGroup?.name ?: "") }
    var selectedIcon by remember { mutableStateOf(initialGroup?.icon ?: "folder") }
    var selectedColor by remember { mutableStateOf(initialGroup?.color ?: "#607D8B") } // Blue Grey default

    val icons = listOf(
        "music" to Icons.Default.MusicNote,
        "news" to Icons.Default.Newspaper,
        "politics" to Icons.Default.Gavel,
        "tech" to Icons.Default.Laptop,
        "sports" to Icons.Default.SportsBasketball,
        "movies" to Icons.Default.Movie,
        "gaming" to Icons.Default.Gamepad,
        "code" to Icons.Default.Code,
        "science" to Icons.Default.Science,
        "school" to Icons.Default.School,
        "art" to Icons.Default.Palette,
        "food" to Icons.Default.Restaurant,
        "travel" to Icons.Default.Flight,
        "finance" to Icons.Default.AttachMoney,
        "fitness" to Icons.Default.FitnessCenter,
        "pets" to Icons.Default.Pets,
        "cars" to Icons.Default.DirectionsCar,
        "camera" to Icons.Default.CameraAlt,
        "folder" to Icons.Default.Folder
    )

    val colors = listOf(
        "#F44336", "#E91E63", "#9C27B0", "#673AB7", 
        "#3F51B5", "#2196F3", "#03A9F4", "#00BCD4", 
        "#009688", "#4CAF50", "#8BC34A", "#CDDC39", 
        "#FFEB3B", "#FFC107", "#FF9800", "#FF5722", 
        "#795548", "#9E9E9E", "#607D8B"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (initialGroup == null) R.string.add_group else R.string.edit_group)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.group_name)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Text(
                    text = stringResource(R.string.select_icon),
                    style = MaterialTheme.typography.titleSmall
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(40.dp),
                        modifier = Modifier.height(100.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(icons) { (id, icon) ->
                            IconButton(
                                onClick = { selectedIcon = id },
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(if (selectedIcon == id) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                            ) {
                                Icon(icon, contentDescription = null)
                            }
                        }
                    }
                }

                Text(
                    text = stringResource(R.string.select_color),
                    style = MaterialTheme.typography.titleSmall
                )
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(30.dp),
                    modifier = Modifier.height(100.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(colors) { hex ->
                        val color = Color(hex.toColorInt())
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(color)
                                .border(
                                    width = if (selectedColor == hex) 2.dp else 0.dp,
                                    color = if (selectedColor == hex) MaterialTheme.colorScheme.primary else Color.Transparent,
                                    shape = CircleShape
                                )
                                .clickable { selectedColor = hex }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (initialGroup != null && onDelete != null) {
                    TextButton(
                        onClick = onDelete,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text(stringResource(R.string.delete))
                    }
                }
                TextButton(
                    onClick = { if (name.isNotBlank()) onConfirm(name, selectedIcon, selectedColor) },
                    enabled = name.isNotBlank()
                ) {
                    Text(stringResource(R.string.ok))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

fun getIconForName(name: String?): ImageVector {
    return when (name) {
        "music" -> Icons.Default.MusicNote
        "news" -> Icons.Default.Newspaper
        "politics" -> Icons.Default.Gavel
        "tech" -> Icons.Default.Laptop
        "sports" -> Icons.Default.SportsBasketball
        "movies" -> Icons.Default.Movie
        "gaming" -> Icons.Default.Gamepad
        "code" -> Icons.Default.Code
        "science" -> Icons.Default.Science
        "school" -> Icons.Default.School
        "art" -> Icons.Default.Palette
        "food" -> Icons.Default.Restaurant
        "travel" -> Icons.Default.Flight
        "finance" -> Icons.Default.AttachMoney
        "fitness" -> Icons.Default.FitnessCenter
        "pets" -> Icons.Default.Pets
        "cars" -> Icons.Default.DirectionsCar
        "camera" -> Icons.Default.CameraAlt
        else -> Icons.Default.Folder
    }
}
