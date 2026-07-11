package com.pulsemessenger.android.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.pulsemessenger.android.core.network.PollCreationRequest

@Composable
fun CreatePollDialog(
    onDismiss: () -> Unit,
    onCreate: (PollCreationRequest) -> Unit,
) {
    var question by remember { mutableStateOf("") }
    var options by remember { mutableStateOf(listOf("", "")) }
    var allowMultiple by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Создать опрос") },
        text = {
            Column {
                OutlinedTextField(
                    value = question,
                    onValueChange = { question = it },
                    label = { Text("Вопрос") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text("Варианты", style = MaterialTheme.typography.labelMedium)
                Spacer(modifier = Modifier.height(4.dp))
                LazyColumn(modifier = Modifier.heightIn(max = 200.dp)) {
                    itemsIndexed(options) { index, option ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = option,
                                onValueChange = { newValue ->
                                    options = options.toMutableList().also { it[index] = newValue }
                                },
                                label = { Text("Вариант ${index + 1}") },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                            if (options.size > 2) {
                                IconButton(onClick = {
                                    options = options.toMutableList().also { it.removeAt(index) }
                                }) {
                                    Icon(Icons.Default.Close, contentDescription = "Удалить")
                                }
                            }
                        }
                    }
                }
                TextButton(onClick = { options = options + "" }) {
                    Text("+ Добавить вариант")
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Несколько вариантов", modifier = Modifier.weight(1f))
                    Switch(checked = allowMultiple, onCheckedChange = { allowMultiple = it })
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val validOptions = options.map { it.trim() }.filter { it.isNotBlank() }
                    val q = question.trim()
                    if (q.isNotBlank() && validOptions.size >= 2) {
                        onCreate(PollCreationRequest(question = q, options = validOptions, allowMultiple = allowMultiple))
                    }
                },
                enabled = question.trim().isNotBlank() && options.count { it.trim().isNotBlank() } >= 2,
            ) {
                Text("Отправить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )
}
