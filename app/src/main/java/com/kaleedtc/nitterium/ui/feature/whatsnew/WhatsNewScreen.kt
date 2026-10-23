package com.kaleedtc.nitterium.ui.feature.whatsnew

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.VolunteerActivism
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.kaleedtc.nitterium.R

private data class ReleaseHighlight(
    @param:StringRes val title: Int,
    @param:StringRes val summary: Int,
    val icon: ImageVector
)

// Update these highlights and their strings when preparing each release.
private val releaseHighlights = listOf(
    ReleaseHighlight(R.string.whats_new_instances_title, R.string.whats_new_instances_summary, Icons.Default.Public),
    ReleaseHighlight(R.string.whats_new_subscriptions_title, R.string.whats_new_subscriptions_summary, Icons.Default.Bookmarks),
    ReleaseHighlight(R.string.whats_new_backups_title, R.string.whats_new_backups_summary, Icons.Default.CloudDone),
    ReleaseHighlight(R.string.whats_new_browsing_title, R.string.whats_new_browsing_summary, Icons.Default.Explore)
)

@Composable
fun WhatsNewScreen(
    versionName: String,
    onContinue: (hideFutureUpdates: Boolean) -> Unit,
    onDonate: () -> Unit,
    isSaving: Boolean = false,
    saveFailed: Boolean = false
) {
    var hideFutureUpdates by rememberSaveable { mutableStateOf(false) }
    BackHandler { if (!isSaving) onContinue(hideFutureUpdates) }

    Scaffold(
        bottomBar = {
            Surface(tonalElevation = 2.dp) {
                Box(Modifier.fillMaxWidth().navigationBarsPadding(), contentAlignment = Alignment.Center) {
                    Column(
                        modifier = Modifier.widthIn(max = 600.dp).fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .toggleable(
                                    value = hideFutureUpdates,
                                    enabled = !isSaving,
                                    role = Role.Checkbox,
                                    onValueChange = { hideFutureUpdates = it }
                                )
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Checkbox(checked = hideFutureUpdates, onCheckedChange = null, enabled = !isSaving)
                            Text(stringResource(R.string.whats_new_hide_future), style = MaterialTheme.typography.bodyMedium)
                        }
                        if (saveFailed) {
                            Text(
                                text = stringResource(R.string.whats_new_save_failed),
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Button(
                            onClick = { onContinue(hideFutureUpdates) },
                            enabled = !isSaving,
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(16.dp)
                        ) {
                            Text(stringResource(R.string.whats_new_continue))
                        }
                    }
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                modifier = Modifier.widthIn(max = 600.dp).fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(20.dp)
                            ) {
                                Icon(
                                    Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    modifier = Modifier.padding(16.dp).size(28.dp),
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = stringResource(R.string.whats_new_title),
                                    style = MaterialTheme.typography.headlineLarge,
                                    modifier = Modifier.semantics { heading() }
                                )
                                Text(
                                    text = stringResource(R.string.version, versionName),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        Text(
                            text = stringResource(R.string.whats_new_intro),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                item {
                    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(24.dp)) {
                        Column(Modifier.padding(horizontal = 20.dp)) {
                            releaseHighlights.forEachIndexed { index, highlight ->
                                Row(
                                    modifier = Modifier.padding(vertical = 20.dp),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    Icon(
                                        imageVector = highlight.icon,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(top = 2.dp).size(24.dp)
                                    )
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(stringResource(highlight.title), style = MaterialTheme.typography.titleMedium)
                                        Text(
                                            text = stringResource(highlight.summary),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                if (index < releaseHighlights.lastIndex) HorizontalDivider()
                            }
                        }
                    }
                }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(R.string.whats_new_support_title), style = MaterialTheme.typography.titleLarge)
                        Text(
                            text = stringResource(R.string.whats_new_support_summary),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        FilledTonalButton(onClick = onDonate, enabled = !isSaving) {
                            Icon(Icons.Default.VolunteerActivism, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text(stringResource(R.string.whats_new_donate), modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            }
        }
    }
}
