package com.pulsemessenger.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

data class ChatListFilterItem<T>(
    val value: T,
    val title: String,
    val count: Int = 0,
)

@Composable
fun <T> ChatListFilterBar(
    items: List<ChatListFilterItem<T>>,
    selected: T,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items.forEach { item ->
            val isSelected = item.value == selected

            Text(
                text = if (item.count > 0) {
                    "${item.title} ${item.count}"
                } else {
                    item.title
                },
                modifier = Modifier
                    .clip(RoundedCornerShape(50.dp))
                    .background(
                        if (isSelected) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.48f)
                        }
                    )
                    .clickable { onSelected(item.value) }
                    .padding(horizontal = 13.dp, vertical = 8.dp),
                color = if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
            )
        }
    }
}