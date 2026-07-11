package com.pulsemessenger.android.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

data class ForwardTarget(
    val scope: String,
    val id: Long,
    val label: String,
    val sublabel: String,
)

data class ForwardPayload(
    val content: String,
    val imageUrl: String? = null,
    val fileUrl: String? = null,
    val fileName: String? = null,
    val fileSize: Long? = null,
    val forwardedFromName: String,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForwardMessagesSheet(
    payloads: List<ForwardPayload>,
    targets: List<ForwardTarget>,
    onDismiss: () -> Unit,
    onForwardTo: (ForwardTarget) -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                    Text("Переслать", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("Выберите диалог или комнату для пересылки.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item {
                Card(
                    modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f)),
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Что будет переслано", fontWeight = FontWeight.SemiBold)
                        payloads.take(3).forEach { payload ->
                            Column {
                                Text((payload.fileName ?: payload.content.ifBlank { "Сообщение" }).take(72), fontWeight = FontWeight.Medium)
                                Text(
                                    "От: ${payload.forwardedFromName}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        if (payloads.size > 3) {
                            Text("И еще ${payloads.size - 3}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            items(targets, key = { "${it.scope}:${it.id}" }) { target ->
                Card(
                    modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    onClick = { onForwardTo(target) },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f)),
                ) {
                    Row(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(target.label, fontWeight = FontWeight.SemiBold)
                            Text(target.sublabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text("->", color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            item { Spacer(modifier = Modifier.height(12.dp)) }
        }
    }
}
