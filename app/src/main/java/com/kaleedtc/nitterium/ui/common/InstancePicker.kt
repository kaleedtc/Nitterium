package com.kaleedtc.nitterium.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kaleedtc.nitterium.R
import com.kaleedtc.nitterium.data.repository.UserPreferencesRepository
import com.kaleedtc.nitterium.data.security.UrlPolicy
import java.io.IOException
import kotlinx.coroutines.launch

@Composable
fun InstancePicker(preferences: UserPreferencesRepository, onDismiss: () -> Unit) {
    val selected by preferences.instanceUrl.collectAsStateWithLifecycle(initialValue = "")
    val custom by preferences.customInstances.collectAsStateWithLifecycle(initialValue = emptySet())
    val builtIn = stringArrayResource(R.array.default_instance_urls).toList()
    val instances = remember(builtIn, custom, selected) { UrlPolicy.instanceChoices(builtIn, custom, selected) }
    val scope = rememberCoroutineScope()
    var saving by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text(stringResource(R.string.switch_instance)) },
        text = {
            Column {
                LazyColumn(Modifier.heightIn(max = 360.dp).selectableGroup().testTag("instance_choices")) {
                    items(instances, key = { it }) { instance ->
                        val isSelected = instance.trimEnd('/') == selected.trimEnd('/')
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .selectable(
                                    selected = isSelected,
                                    enabled = !saving && !isSelected,
                                    role = Role.RadioButton,
                                    onClick = {
                                        saving = true
                                        failed = false
                                        scope.launch {
                                            try {
                                                preferences.setInstanceUrl(instance)
                                                onDismiss()
                                            } catch (_: IOException) {
                                                failed = true
                                            } finally {
                                                saving = false
                                            }
                                        }
                                    }
                                ).padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = isSelected, onClick = null, enabled = !saving)
                            Text(instance, Modifier.padding(start = 8.dp))
                        }
                    }
                }
                if (failed) Text(stringResource(R.string.instance_switch_failed), color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss, enabled = !saving) { Text(stringResource(R.string.cancel)) } }
    )
}
