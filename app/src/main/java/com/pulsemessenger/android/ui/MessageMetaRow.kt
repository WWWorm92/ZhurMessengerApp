package com.pulsemessenger.android.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp

@Composable
fun MessageMetaRow(
    time: String,
    isMine: Boolean = false,
    isRead: Boolean = false,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = time,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.82f),
            fontSize = 11.sp,
            lineHeight = 11.sp
        )

        if (isMine) {
            Text(
                text = if (isRead) "✓✓" else "✓",
                style = MaterialTheme.typography.bodySmall.copy(
                    letterSpacing = (-4).sp
                ),
                color = if (isRead) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.82f)
                },
                fontSize = 12.sp,
                lineHeight = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}