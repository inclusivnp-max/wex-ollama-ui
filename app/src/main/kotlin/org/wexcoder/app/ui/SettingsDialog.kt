package org.wexcoder.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.wexcoder.app.AppState

@Composable
fun SettingsDialog(state: AppState) {
    if (!state.showSettingsDialog) return
    var host by remember { mutableStateOf(state.ollamaHost) }
    var ctxSize by remember { mutableStateOf(state.contextSize.toString()) }
    var repeatPenalty by remember { mutableStateOf(state.repeatPenalty.toString()) }
    var topP by remember { mutableStateOf(state.topP.toString()) }
    var topK by remember { mutableStateOf(state.topK.toString()) }
    var autoApprove by remember { mutableStateOf(state.autoApproveTools) }

    AlertDialog(
        onDismissRequest = { state.showSettingsDialog = false },
        title = { Text("Настройки") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it },
                    label = { Text("Ollama Host URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = ctxSize,
                    onValueChange = { ctxSize = it },
                    label = { Text("Контекст (num_ctx, токенов)") },
                    supportingText = { Text("qwen2.5-coder обучен на 32768 — выше не поднимай") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = repeatPenalty,
                    onValueChange = { repeatPenalty = it },
                    label = { Text("repeat_penalty") },
                    supportingText = { Text("1.0 — выкл, 1.1 — норма, выше — жёстче против повторов") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = topP,
                    onValueChange = { topP = it },
                    label = { Text("top_p") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = topK,
                    onValueChange = { topK = it },
                    label = { Text("top_k") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Автоподтверждение операций агента")
                        Text(
                            "Выкл — спрашивать перед правкой файлов и запуском команд",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = autoApprove, onCheckedChange = { autoApprove = it })
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                state.saveSettingsConfig(
                    host = host.trim(),
                    ctx = ctxSize.toIntOrNull() ?: 16384,
                    repeatPenalty = repeatPenalty.toDoubleOrNull() ?: 1.1,
                    topP = topP.toDoubleOrNull() ?: 0.9,
                    topK = topK.toIntOrNull() ?: 40,
                    autoApproveTools = autoApprove,
                )
                state.showSettingsDialog = false
            }) {
                Text("Сохранить")
            }
        },
        dismissButton = {
            TextButton(onClick = { state.showSettingsDialog = false }) {
                Text("Отмена")
            }
        }
    )
}
