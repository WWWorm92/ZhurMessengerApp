package com.pulsemessenger.android.feature.chat

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

internal data class DmOutgoingBubblePalette(
    val container: Color,
    val content: Color,
)

@Composable
internal fun dmOutgoingBubblePalette(name: String): DmOutgoingBubblePalette {
    // Use the active Material theme rather than the system setting:
    // the user may select dark/light mode manually inside the app.
    val darkTheme = MaterialTheme.colorScheme.background.luminance() < 0.45f

    return when (name) {
        "default", "blue" -> if (darkTheme) {
            DmOutgoingBubblePalette(
                container = Color(0xFF244B66),
                content = Color(0xFFF2F8FC),
            )
        } else {
            DmOutgoingBubblePalette(
                container = Color(0xFFD5EEFF),
                content = Color(0xFF102A3B),
            )
        }

        "green" -> if (darkTheme) {
            DmOutgoingBubblePalette(
                container = Color(0xFF28543A),
                content = Color(0xFFF0FAF3),
            )
        } else {
            DmOutgoingBubblePalette(
                container = Color(0xFFD8F3DF),
                content = Color(0xFF173522),
            )
        }

        "purple" -> if (darkTheme) {
            DmOutgoingBubblePalette(
                container = Color(0xFF513E6E),
                content = Color(0xFFF8F2FF),
            )
        } else {
            DmOutgoingBubblePalette(
                container = Color(0xFFE9DCFA),
                content = Color(0xFF322145),
            )
        }

        "orange" -> if (darkTheme) {
            DmOutgoingBubblePalette(
                container = Color(0xFF6A4526),
                content = Color(0xFFFFF6ED),
            )
        } else {
            DmOutgoingBubblePalette(
                container = Color(0xFFFBE3C8),
                content = Color(0xFF45260E),
            )
        }

        "graphite" -> if (darkTheme) {
            DmOutgoingBubblePalette(
                container = Color(0xFF3B4652),
                content = Color(0xFFF4F7FA),
            )
        } else {
            DmOutgoingBubblePalette(
                container = Color(0xFFD8DFE7),
                content = Color(0xFF202832),
            )
        }

        else -> if (darkTheme) {
            DmOutgoingBubblePalette(
                container = Color(0xFF244B66),
                content = Color(0xFFF2F8FC),
            )
        } else {
            DmOutgoingBubblePalette(
                container = Color(0xFFD5EEFF),
                content = Color(0xFF102A3B),
            )
        }
    }
}

@Composable
internal fun dmChatWallpaper(name: String): Brush = when (name) {
    "clean" -> Brush.verticalGradient(
        listOf(
            MaterialTheme.colorScheme.background,
            MaterialTheme.colorScheme.background,
        )
    )

    "dots" -> Brush.linearGradient(
        listOf(
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.34f),
            MaterialTheme.colorScheme.background,
        )
    )

    "gradient" -> Brush.linearGradient(
        listOf(
            MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.09f),
            MaterialTheme.colorScheme.background,
        )
    )

    "night" -> Brush.verticalGradient(
        listOf(
            Color(0xFF142434),
            Color(0xFF223A4E),
        )
    )

    else -> Brush.verticalGradient(
        listOf(
            MaterialTheme.colorScheme.background,
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.16f),
        )
    )
}
