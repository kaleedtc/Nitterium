package com.kaleedtc.nitterium.ui.common

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.kaleedtc.nitterium.R

@Composable
fun GroupAssignmentMenu(username: String, onManageGroups: () -> Unit) {
    var expanded by remember(username) { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.subscription_options, username))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.assign_to_groups)) },
                onClick = {
                    expanded = false
                    onManageGroups()
                }
            )
        }
    }
}

@Composable
fun SubscriptionActionButton(
    isSubscribed: Boolean,
    onClick: () -> Unit,
    onManageGroups: () -> Unit
) {
    val haptics = LocalHapticFeedback.current
    // One gesture handler owns both actions so a long press cannot also toggle the subscription.
    Surface(
        shape = FloatingActionButtonDefaults.shape,
        color = FloatingActionButtonDefaults.containerColor,
        tonalElevation = 6.dp,
        shadowElevation = 6.dp
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .combinedClickable(
                    role = Role.Button,
                    onClick = onClick,
                    onLongClickLabel = stringResource(R.string.assign_to_groups),
                    hapticFeedbackEnabled = false,
                    onLongClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onManageGroups()
                    }
                ),
            contentAlignment = androidx.compose.ui.Alignment.Center
        ) {
            Icon(
                imageVector = if (isSubscribed) Icons.Default.PersonRemove else Icons.Default.PersonAdd,
                contentDescription = stringResource(if (isSubscribed) R.string.unsubscribe_confirm else R.string.subscribe)
            )
        }
    }
}
