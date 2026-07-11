package com.pulsemessenger.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import kotlin.math.roundToInt

@Composable
fun FullscreenImageViewer(
    imageModel: Any,
    imageModels: List<Any> = listOf(imageModel),
    initialIndex: Int = 0,
    onDismiss: () -> Unit,
) {
    val images = imageModels.ifEmpty { listOf(imageModel) }
    val startIndex = initialIndex.coerceIn(0, images.lastIndex)

    val pagerState = rememberPagerState(
        initialPage = startIndex,
        pageCount = { images.size }
    )

    val scales = remember(images) { mutableStateMapOf<Int, Float>() }
    val offsets = remember(images) { mutableStateMapOf<Int, Offset>() }

    var dismissOffsetY by remember { mutableStateOf(0f) }

    val currentScale = scales[pagerState.currentPage] ?: 1f
    val backgroundAlpha = (1f - (dismissOffsetY / 600f)).coerceIn(0.35f, 1f)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = backgroundAlpha))
                .pointerInput(pagerState.currentPage, currentScale) {
                    if (currentScale <= 1.05f) {
                        detectVerticalDragGestures(
                            onVerticalDrag = { change, dragAmount ->
                                if (dragAmount > 0f) {
                                    dismissOffsetY += dragAmount
                                    change.consume()
                                }
                            },
                            onDragEnd = {
                                if (dismissOffsetY > 160f) {
                                    onDismiss()
                                } else {
                                    dismissOffsetY = 0f
                                }
                            },
                            onDragCancel = {
                                dismissOffsetY = 0f
                            }
                        )
                    }
                }
        ) {
            HorizontalPager(
                state = pagerState,
                userScrollEnabled = currentScale <= 1.05f && dismissOffsetY == 0f,
                modifier = Modifier
                    .fillMaxSize()
                    .offset {
                        IntOffset(
                            x = 0,
                            y = dismissOffsetY.roundToInt()
                        )
                    }
            ) { page ->
                val scale = scales[page] ?: 1f
                val offset = offsets[page] ?: Offset.Zero

                AsyncImage(
                    model = images[page],
                    contentDescription = "Фото",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp)
                        .pointerInput(page) {
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false)

                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Main)
                                    val pressedCount = event.changes.count { it.pressed }

                                    val currentPageScale = scales[page] ?: 1f
                                    val shouldHandleImageGesture =
                                        pressedCount >= 2 || currentPageScale > 1f

                                    if (shouldHandleImageGesture) {
                                        val zoom = event.calculateZoom()
                                        val pan = event.calculatePan()

                                        val oldScale = scales[page] ?: 1f
                                        val oldOffset = offsets[page] ?: Offset.Zero

                                        val newScale = (oldScale * zoom).coerceIn(1f, 5f)
                                        scales[page] = newScale

                                        offsets[page] = if (newScale <= 1f) {
                                            Offset.Zero
                                        } else {
                                            oldOffset + pan
                                        }

                                        event.changes.forEach { change ->
                                            change.consume()
                                        }
                                    }

                                    if (event.changes.none { it.pressed }) {
                                        break
                                    }
                                }
                            }
                        }
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            translationX = offset.x
                            translationY = offset.y
                        }
                )
            }

            if (images.size > 1) {
                Text(
                    text = "${pagerState.currentPage + 1} / ${images.size}",
                    color = Color.White,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 28.dp)
                        .background(Color.Black.copy(alpha = 0.35f))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }

            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 18.dp, end = 12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Закрыть",
                    tint = Color.White
                )
            }
        }
    }
}