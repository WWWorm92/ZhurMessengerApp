package com.pulsemessenger.android.feature.createroom

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun CreateRoomScreen(
    viewModel: CreateRoomViewModel,
    onBack: () -> Unit,
    onRoomCreated: (roomId: Long) -> Unit,
) {
    val created = viewModel.createdRoom
    if (created != null) {
        onRoomCreated(created.id)
        viewModel.reset()
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 16.dp, start = 20.dp, end = 20.dp, bottom = 20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Создать комнату", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            OutlinedButton(onClick = onBack) {
                Text("Отмена")
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Название", fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(
                        value = viewModel.name,
                        onValueChange = { viewModel.name = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Название комнаты") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                    )

                    Text("Описание", fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(
                        value = viewModel.description,
                        onValueChange = { viewModel.description = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Описание комнаты (необязательно)") },
                        maxLines = 3,
                        shape = RoundedCornerShape(12.dp),
                    )

                    Text("Тип комнаты", fontWeight = FontWeight.SemiBold)
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            selected = viewModel.accessType == "public",
                            onClick = { viewModel.accessType = "public" },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                        ) {
                            Text("Публичная")
                        }
                        SegmentedButton(
                            selected = viewModel.accessType == "private",
                            onClick = { viewModel.accessType = "private" },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                        ) {
                            Text("Приватная")
                        }
                    }

                    if (viewModel.accessType == "public") {
                        Text("Ссылка (slug)", fontWeight = FontWeight.SemiBold)
                        OutlinedTextField(
                            value = viewModel.slug,
                            onValueChange = { viewModel.slug = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("Необязательно, сгенерируется автоматически") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                        )
                    }
                }
            }

            if (!viewModel.error.isNullOrBlank()) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        viewModel.error ?: "",
                        modifier = Modifier.padding(14.dp),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = viewModel::create,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            enabled = !viewModel.isCreating && viewModel.name.trim().length >= 2,
            shape = RoundedCornerShape(16.dp),
        ) {
            if (viewModel.isCreating) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary)
            } else {
                Text("Создать комнату", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
