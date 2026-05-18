package com.kaleedtc.nitterium.ui.feature.subscriptions

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.kaleedtc.nitterium.data.model.Subscription

import androidx.compose.ui.res.stringResource
import com.kaleedtc.nitterium.R

@Composable
fun SubscriptionsScreen(
    onNavigateToUser: (String) -> Unit,
    viewModel: SubscriptionsViewModel
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        viewModel.onEvent(SubscriptionsEvent.SubscriptionsExported(uri))
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        viewModel.onEvent(SubscriptionsEvent.SubscriptionsImported(uri))
    }

    LaunchedEffect(viewModel.effect) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is SubscriptionsEffect.ShowSnackbar -> snackbarHostState.showSnackbar(effect.message)
                is SubscriptionsEffect.LaunchExportIntent -> {
                    val date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                    exportLauncher.launch("Nitterium-subscriptions-$date.json")
                }
                is SubscriptionsEffect.LaunchImportIntent -> importLauncher.launch(arrayOf("application/json", "*/*"))
            }
        }
    }

    SubscriptionsContent(
        state = state,
        onEvent = viewModel::onEvent,
        onNavigateToUser = onNavigateToUser,
        snackbarHostState = snackbarHostState
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionsContent(
    state: SubscriptionsState,
    onEvent: (SubscriptionsEvent) -> Unit,
    onNavigateToUser: (String) -> Unit,
    snackbarHostState: SnackbarHostState
) {
    val scrollBehavior =
        TopAppBarDefaults.exitUntilCollapsedScrollBehavior(
            rememberTopAppBarState(),
            canScroll = { true },
        )

    var showMenu by remember { mutableStateOf(false) }
    var subForGroups by remember { mutableStateOf<Subscription?>(null) }

    if (subForGroups != null) {
        GroupAssignmentDialog(
            subscription = subForGroups!!,
            allGroups = state.groups,
            onDismiss = { subForGroups = null },
            onConfirm = { groupIds ->
                onEvent(SubscriptionsEvent.UpdateSubscriptionGroups(subForGroups!!.username, groupIds))
                subForGroups = null
            }
        )
    }

    val listState = rememberLazyListState()
    var draggingItemKey by remember { mutableStateOf<Any?>(null) }
    var itemTranslationY by remember { mutableFloatStateOf(0f) }
    var expectedIndex by remember { mutableStateOf<Int?>(null) }

    val pointerInputModifier = Modifier.pointerInput(Unit) {
        detectDragGesturesAfterLongPress(
            onDragStart = { offset ->
                listState.layoutInfo.visibleItemsInfo
                    .firstOrNull { item -> offset.y.toInt() in item.offset..(item.offset + item.size) }
                    ?.let { item ->
                        draggingItemKey = item.key
                        expectedIndex = item.index
                        itemTranslationY = 0f
                    }
            },
            onDrag = { change, dragAmount ->
                change.consume()
                val draggingKey = draggingItemKey ?: return@detectDragGesturesAfterLongPress
                itemTranslationY += dragAmount.y

                val visibleItems = listState.layoutInfo.visibleItemsInfo
                val draggingItem = visibleItems.firstOrNull { it.key == draggingKey }

                if (draggingItem != null && (expectedIndex == null || draggingItem.index == expectedIndex)) {
                    val currentCenter = draggingItem.offset + itemTranslationY + (draggingItem.size / 2)

                    val targetItem = visibleItems
                        .filter { it.key != draggingKey }
                        .minByOrNull { item ->
                            val itemCenter = item.offset + (item.size / 2)
                            kotlin.math.abs(itemCenter - currentCenter)
                        }

                    if (targetItem != null) {
                        val itemCenter = targetItem.offset + (targetItem.size / 2)
                        val movingDown = draggingItem.index < targetItem.index
                        val movingUp = draggingItem.index > targetItem.index

                        val shouldSwap = (movingDown && currentCenter > itemCenter) || 
                                         (movingUp && currentCenter < itemCenter)

                        if (shouldSwap) {
                            onEvent(SubscriptionsEvent.ReorderSubscriptions(draggingItem.index, targetItem.index))
                            itemTranslationY += draggingItem.offset - targetItem.offset
                            expectedIndex = targetItem.index
                        }
                    }
                }
            },
            onDragEnd = {
                draggingItemKey = null
                itemTranslationY = 0f
                expectedIndex = null
                onEvent(SubscriptionsEvent.SaveSubscriptionOrder)
            },
            onDragCancel = {
                draggingItemKey = null
                itemTranslationY = 0f
                expectedIndex = null
                onEvent(SubscriptionsEvent.SaveSubscriptionOrder)
            }
        )
    }

    if (state.subscriptionToDelete != null) {
        AlertDialog(
            onDismissRequest = { onEvent(SubscriptionsEvent.CancelDelete) },
            title = { Text(stringResource(R.string.unsubscribe_title)) },
            text = { Text(stringResource(R.string.unsubscribe_message, state.subscriptionToDelete.username)) },
            confirmButton = {
                TextButton(
                    onClick = { onEvent(SubscriptionsEvent.ConfirmDelete) }
                ) {
                    Text(stringResource(R.string.unsubscribe_confirm))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { onEvent(SubscriptionsEvent.CancelDelete) }
                ) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets.exclude(WindowInsets.navigationBars),
        topBar = {
            MediumTopAppBar(
                title = { Text(stringResource(R.string.nav_subscriptions)) },
                scrollBehavior = scrollBehavior,
                actions = {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = stringResource(R.string.more_options)
                        )
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.import_label)) },
                            onClick = {
                                showMenu = false
                                onEvent(SubscriptionsEvent.ImportClicked)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.export_label)) },
                            onClick = {
                                showMenu = false
                                onEvent(SubscriptionsEvent.ExportClicked)
                            }
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = { onEvent(SubscriptionsEvent.Refresh) },
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .then(pointerInputModifier),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                itemsIndexed(state.subscriptions, key = { _, sub -> sub.username }) { _, sub ->
                    val isDragging = sub.username == draggingItemKey
                    val modifier = Modifier
                        .animateItem()
                        .then(
                            if (isDragging) {
                                Modifier
                                    .zIndex(1f)
                                    .graphicsLayer {
                                        translationY = itemTranslationY
                                        scaleX = 1.05f
                                        scaleY = 1.05f
                                        shadowElevation = 8.dp.toPx()
                                    }
                            } else {
                                Modifier.zIndex(0f)
                            }
                        )

                    SubscriptionItem(
                        subscription = sub,
                        modifier = modifier,
                        onClick = { onNavigateToUser(sub.username) },
                        onDelete = { onEvent(SubscriptionsEvent.RequestDelete(sub)) },
                        onManageGroups = { subForGroups = sub }
                    )
                }
            }
        }
    }
}

@Composable
fun GroupAssignmentDialog(
    subscription: Subscription,
    allGroups: List<com.kaleedtc.nitterium.data.model.FeedGroup>,
    onDismiss: () -> Unit,
    onConfirm: (List<String>) -> Unit
) {
    var selectedGroupIds by remember { mutableStateOf(subscription.groupIds) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.assign_to_groups)) },
        text = {
            if (allGroups.isEmpty()) {
                Text(stringResource(R.string.no_groups_created))
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(allGroups) { group ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedGroupIds = if (selectedGroupIds.contains(group.id)) {
                                        selectedGroupIds - group.id
                                    } else {
                                        selectedGroupIds + group.id
                                    }
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = selectedGroupIds.contains(group.id),
                                onCheckedChange = null
                            )
                            Spacer(modifier = Modifier.size(8.dp))
                            Text(group.name)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selectedGroupIds) }) {
                Text(stringResource(R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
fun SubscriptionItem(
    subscription: Subscription,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onManageGroups: () -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (subscription.avatarUrl != null) {
            AsyncImage(
                model = subscription.avatarUrl,
                contentDescription = stringResource(R.string.avatar_description, subscription.username),
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop,
                error = rememberVectorPainter(Icons.Default.AccountCircle),
                placeholder = rememberVectorPainter(Icons.Default.AccountCircle)
            )
        } else {
            Icon(
                imageVector = Icons.Default.AccountCircle,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Text(
            text = "@${subscription.username}",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        IconButton(onClick = onManageGroups) {
            Icon(
                imageVector = Icons.Default.FolderOpen,
                contentDescription = stringResource(R.string.groups),
                tint = MaterialTheme.colorScheme.primary
            )
        }

        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = stringResource(R.string.delete),
                tint = MaterialTheme.colorScheme.error
            )
        }
    }
}
