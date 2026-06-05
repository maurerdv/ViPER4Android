package com.llsl.viper4android.ui.screens.preset

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.llsl.viper4android.R
import com.llsl.viper4android.data.model.Preset

@Composable
fun PresetDialog(
    presets: List<Preset>,
    onSave: (String) -> Unit,
    onLoad: (Long) -> Unit,
    onDelete: (Long) -> Unit,
    onRename: (Long, String) -> Unit,
    onUpdate: (Long) -> Unit,
    onClearAll: () -> Unit,
    onDismiss: () -> Unit,
) {
    var showSaveInput by remember { mutableStateOf(false) }
    var saveInputName by remember { mutableStateOf("") }
    var renamingId by remember { mutableLongStateOf(-1L) }
    var renameInputName by remember { mutableStateOf("") }
    var pendingDeletePreset by remember { mutableStateOf<Preset?>(null) }
    var showClearAllConfirm by remember { mutableStateOf(false) }
    var showUpdateConfirm by remember { mutableStateOf(false) }
    var updateTargetPreset by remember { mutableStateOf<Preset?>(null) }

    fun commitPendingDelete() {
        pendingDeletePreset?.let { onDelete(it.id) }
        pendingDeletePreset = null
    }

    val visiblePresets =
        remember(presets, pendingDeletePreset) {
            if (pendingDeletePreset != null) {
                presets.filter { it.id != pendingDeletePreset!!.id }
            } else {
                presets
            }
        }

    if (showSaveInput) {
        AlertDialog(
            onDismissRequest = { showSaveInput = false },
            title = { Text(stringResource(R.string.preset_save_title)) },
            text = {
                OutlinedTextField(
                    value = saveInputName,
                    onValueChange = { saveInputName = it },
                    label = { Text(stringResource(R.string.preset_name_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (saveInputName.isNotBlank()) {
                            onSave(saveInputName.trim())
                            saveInputName = ""
                            showSaveInput = false
                        }
                    },
                    enabled = saveInputName.isNotBlank(),
                ) {
                    Text(stringResource(R.string.action_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveInput = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
        return
    }

    if (renamingId >= 0) {
        AlertDialog(
            onDismissRequest = { renamingId = -1L },
            title = { Text(stringResource(R.string.preset_rename_title)) },
            text = {
                OutlinedTextField(
                    value = renameInputName,
                    onValueChange = { renameInputName = it },
                    label = { Text(stringResource(R.string.preset_name_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (renameInputName.isNotBlank()) {
                            onRename(renamingId, renameInputName.trim())
                            renamingId = -1L
                        }
                    },
                    enabled = renameInputName.isNotBlank(),
                ) {
                    Text(stringResource(R.string.action_rename))
                }
            },
            dismissButton = {
                TextButton(onClick = { renamingId = -1L }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
        return
    }

    if (showClearAllConfirm) {
        AlertDialog(
            onDismissRequest = { showClearAllConfirm = false },
            title = { Text(stringResource(R.string.preset_clear_all_title)) },
            text = {
                Text(stringResource(R.string.preset_clear_all_confirm, presets.size))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onClearAll()
                        showClearAllConfirm = false
                    },
                    colors =
                        ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                ) {
                    Text(stringResource(R.string.preset_clear_all))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllConfirm = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
        return
    }

    if (showUpdateConfirm && updateTargetPreset != null) {
        val target = updateTargetPreset!!
        AlertDialog(
            onDismissRequest = { showUpdateConfirm = false },
            title = { Text(stringResource(R.string.preset_update_title)) },
            text = {
                Text(stringResource(R.string.preset_update_confirm, target.name))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onUpdate(target.id)
                        showUpdateConfirm = false
                    },
                    colors =
                        ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                ) {
                    Text(stringResource(R.string.action_update))
                }
            },
            dismissButton = {
                TextButton(onClick = { showUpdateConfirm = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
        return
    }

    AlertDialog(
        onDismissRequest = {
            commitPendingDelete()
            onDismiss()
        },
        title = { Text(stringResource(R.string.menu_presets)) },
        text = {
            Column {
                if (visiblePresets.isEmpty() && pendingDeletePreset == null) {
                    Text(
                        text = stringResource(R.string.preset_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 300.dp),
                    ) {
                        items(visiblePresets, key = { it.id }) { preset ->
                            PresetItem(
                                preset = preset,
                                onLoad = {
                                    commitPendingDelete()
                                    onLoad(preset.id)
                                },
                                onDelete = {
                                    commitPendingDelete()
                                    pendingDeletePreset = preset
                                },
                                onRename = {
                                    renameInputName = preset.name
                                    renamingId = preset.id
                                },
                                onUpdate = {
                                    updateTargetPreset = preset
                                    showUpdateConfirm = true
                                },
                            )
                            HorizontalDivider()
                        }
                        pendingDeletePreset?.let { deleted ->
                            item(key = "deleted_${deleted.id}") {
                                DeletedPresetItem(
                                    preset = deleted,
                                    onRestore = {
                                        pendingDeletePreset = null
                                    },
                                )
                                HorizontalDivider()
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                showSaveInput = true
                saveInputName = ""
            }) {
                Text(stringResource(R.string.preset_save_current))
            }
        },
        dismissButton = {
            Row {
                if (presets.isNotEmpty()) {
                    TextButton(
                        onClick = {
                            commitPendingDelete()
                            showClearAllConfirm = true
                        },
                        colors =
                            ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.error,
                            ),
                    ) {
                        Text(stringResource(R.string.preset_clear_all))
                    }
                }
                TextButton(onClick = {
                    commitPendingDelete()
                    onDismiss()
                }) {
                    Text(stringResource(R.string.action_close))
                }
            }
        },
    )
}

@Composable
private fun PresetItem(
    preset: Preset,
    onLoad: () -> Unit,
    onDelete: () -> Unit,
    onRename: () -> Unit,
    onUpdate: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onLoad)
                .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = preset.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(if (preset.fxType == 1) R.string.tab_headphone else R.string.tab_speaker),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row {
            IconButton(onClick = onRename) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onUpdate) {
                Icon(
                    Icons.Default.Sync,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun DeletedPresetItem(
    preset: Preset,
    onRestore: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = preset.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            )
            Text(
                text = stringResource(R.string.label_deleted),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
            )
        }
        IconButton(onClick = onRestore) {
            Icon(
                Icons.Default.Restore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
