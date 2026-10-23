package com.kaleedtc.nitterium.ui.feature.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.kaleedtc.nitterium.R
import com.kaleedtc.nitterium.data.model.BackupScope
import com.kaleedtc.nitterium.data.model.NitteriumBackup

@Composable
fun BackupSettings(state: SettingsState, onEvent: (SettingsEvent) -> Unit) {
    var scope by rememberSaveable { mutableStateOf(BackupScope.EVERYTHING) }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(stringResource(R.string.backup_description), style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(R.string.backup_include), style = MaterialTheme.typography.titleMedium)
        Column(Modifier.selectableGroup()) {
            BackupScope.entries.forEach { option ->
                val (title, description) = when (option) {
                    BackupScope.SUBSCRIPTIONS -> R.string.backup_subscriptions to R.string.backup_subscriptions_description
                    BackupScope.SUBSCRIPTIONS_AND_GROUPS -> R.string.backup_subscriptions_groups to R.string.backup_subscriptions_groups_description
                    BackupScope.SETTINGS -> R.string.backup_settings to R.string.backup_settings_description
                    BackupScope.EVERYTHING -> R.string.backup_everything to R.string.backup_everything_description
                }
                Row(
                    modifier = Modifier.fillMaxWidth().selectable(
                        selected = scope == option,
                        enabled = !state.isBackupBusy,
                        role = Role.RadioButton,
                        onClick = { scope = option }
                    ).padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = scope == option, onClick = null, enabled = !state.isBackupBusy)
                    Column(Modifier.padding(start = 12.dp).weight(1f)) {
                        Text(stringResource(title), style = MaterialTheme.typography.bodyLarge)
                        Text(stringResource(description), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        Button(
            onClick = { onEvent(SettingsEvent.ExportBackup(scope)) },
            enabled = !state.isBackupBusy,
            modifier = Modifier.fillMaxWidth()
        ) { Text(stringResource(R.string.backup_create)) }
        HorizontalDivider()
        Text(stringResource(R.string.backup_restore_description), style = MaterialTheme.typography.bodyMedium)
        OutlinedButton(
            onClick = { onEvent(SettingsEvent.ImportBackup) },
            enabled = !state.isBackupBusy,
            modifier = Modifier.fillMaxWidth()
        ) { Text(stringResource(R.string.backup_restore_file)) }
        if (state.isBackupBusy) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Text(stringResource(R.string.backup_working), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun RestoreBackupDialog(backup: NitteriumBackup, busy: Boolean, onEvent: (SettingsEvent) -> Unit) {
    AlertDialog(
        onDismissRequest = { if (!busy) onEvent(SettingsEvent.CancelRestoreBackup) },
        title = { Text(stringResource(R.string.backup_restore_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                backup.subscriptions?.let {
                    Text(pluralStringResource(R.plurals.backup_subscription_count, it.size, it.size))
                }
                backup.groups?.let { Text(pluralStringResource(R.plurals.backup_group_count, it.size, it.size)) }
                if (backup.settings != null) Text(stringResource(R.string.backup_settings_included))
                if (backup.skipped > 0) Text(pluralStringResource(R.plurals.backup_skipped_count, backup.skipped, backup.skipped))
                if (backup.subscriptions != null) Text(stringResource(R.string.backup_merge_note))
                if (backup.settings != null) Text(stringResource(R.string.backup_settings_replace_note))
                if (busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(onClick = { onEvent(SettingsEvent.ConfirmRestoreBackup) }, enabled = !busy) {
                Text(stringResource(R.string.backup_restore_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = { onEvent(SettingsEvent.CancelRestoreBackup) }, enabled = !busy) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
