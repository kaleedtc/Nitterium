package com.kaleedtc.nitterium.ui.feature.feed

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.kaleedtc.nitterium.R
import com.kaleedtc.nitterium.data.model.FeedItem
import com.kaleedtc.nitterium.ui.common.ErrorScreen
import com.kaleedtc.nitterium.ui.common.LoadingIndicator
import com.kaleedtc.nitterium.ui.common.LocalFullScreenMode
import com.kaleedtc.nitterium.ui.common.NoInternetScreen
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun FeedScreen(
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
        onEvent = viewModel::onEvent,
        snackbarHostState = snackbarHostState
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedContent(
    state: FeedState,
    onEvent: (FeedEvent) -> Unit,
    snackbarHostState: SnackbarHostState
) {
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val isFullScreen = LocalFullScreenMode.current.value

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
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (state.hasSubscriptions && state.isConnected) {
                FloatingActionButton(onClick = { onEvent(FeedEvent.Refresh) }) {
                    Icon(imageVector = Icons.Filled.Refresh, contentDescription = "Refresh")
                }
            }
        }
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
            } else if (state.isError && state.items.isEmpty()) {
                ErrorScreen(
                    message = stringResource(R.string.failed_to_load_content),
                    onRetry = { onEvent(FeedEvent.Refresh) }
                )
            } else {
                val pullToRefreshState = rememberPullToRefreshState()
                
                PullToRefreshBox(
                    isRefreshing = state.isRefreshing || (state.isLoading && state.items.isNotEmpty()),
                    onRefresh = { onEvent(FeedEvent.Refresh) },
                    state = pullToRefreshState,
                    modifier = Modifier.fillMaxSize()
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 80.dp)
                    ) {
                        items(state.items, key = { it.id }) { item ->
                            val avatarUrl = state.avatars[item.authorUsername.lowercase()]
                            FeedItemCard(item = item, avatarUrl = avatarUrl)
                            HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        }
                    }
                }
            }

            if (state.isLoading && state.items.isEmpty()) {
                LoadingIndicator(isLoading = true)
            }
        }
    }
}

@Composable
fun FeedProfilePicture(username: String, authorName: String, avatarUrl: String?) {
    val context = LocalContext.current
    
    val backgroundColor = remember(username) {
        val colors = listOf(
            Color(0xFFEF5350), Color(0xFFEC407A), Color(0xFFAB47BC),
            Color(0xFF7E57C2), Color(0xFF5C6BC0), Color(0xFF42A5F5),
            Color(0xFF26A69A), Color(0xFF66BB6A), Color(0xFFFFA726)
        )
        colors[username.lowercase().hashCode().coerceAtLeast(0) % colors.size]
    }

    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(backgroundColor),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = authorName.take(1).uppercase(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )

        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(avatarUrl)
                .crossfade(true)
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
fun FeedItemCard(item: FeedItem, avatarUrl: String?) {
    val context = LocalContext.current
    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy \u2022 h:mm a", Locale.getDefault()) }
    val dateString = dateFormat.format(Date(item.pubDate))

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                try {
                    val intent = Intent(Intent.ACTION_VIEW, item.link.toUri())
                    context.startActivity(intent)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            .padding(16.dp)
    ) {
        if (item.retweetedBy != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 36.dp, bottom = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Repeat,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.retweeted_by, item.retweetedBy),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Row(modifier = Modifier.fillMaxWidth()) {
            FeedProfilePicture(
                username = item.authorUsername, 
                authorName = item.author,
                avatarUrl = avatarUrl
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = item.author,
                        style = MaterialTheme.typography.titleMedium.copy(
                            textDirection = TextDirection.Content
                        ),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = dateString,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Text(
                    text = "@${item.authorUsername}",
                    style = MaterialTheme.typography.bodySmall.copy(
                        textDirection = TextDirection.Ltr
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                if (item.contentText.isNotEmpty()) {
                    Text(
                        text = item.contentText,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            textDirection = TextDirection.Content
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                if (item.imageUrls.isNotEmpty()) {
                    val firstImage = item.imageUrls.first()
                    AsyncImage(
                        model = firstImage,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 300.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                }
            }
        }
    }
}
