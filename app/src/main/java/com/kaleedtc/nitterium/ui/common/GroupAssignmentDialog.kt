package com.kaleedtc.nitterium.ui.common

import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.kaleedtc.nitterium.R
import com.kaleedtc.nitterium.data.model.Subscription

@Composable
fun GroupAssignmentDialog(
    subscription: Subscription,
    allGroups: List<com.kaleedtc.nitterium.data.model.FeedGroup>,
    onDismiss: () -> Unit,
    onConfirm: (List<String>) -> Unit,
    isSubscribed: Boolean = true,
    isSaving: Boolean = false
) {
    var selectedGroupIds by rememberSaveable(subscription.username) { mutableStateOf(subscription.groupIds) }

    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
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
                                .toggleable(
                                    value = selectedGroupIds.contains(group.id),
                                    enabled = !isSaving,
                                    role = Role.Checkbox,
                                    onValueChange = {
                                        selectedGroupIds = if (selectedGroupIds.contains(group.id)) {
                                            selectedGroupIds - group.id
                                        } else {
                                            selectedGroupIds + group.id
                                        }
                                    }
                                )
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
            TextButton(
                enabled = !isSaving && allGroups.isNotEmpty(),
                onClick = { onConfirm(selectedGroupIds.filter { id -> allGroups.any { it.id == id } }) }
            ) {
                Text(stringResource(if (isSubscribed) R.string.save else R.string.subscribe_and_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSaving) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

