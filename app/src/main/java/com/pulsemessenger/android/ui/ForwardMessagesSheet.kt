package com.pulsemessenger.android.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ForwardFullscreenImageViewer(
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

    val scope = rememberCoroutineScope()

    val scales = remember(images) { mutableStateMapOf<Int, Float>() }
    val offsets = remember(images) { mutableStateMapOf<Int, Offset>() }

    var dismissOffsetY by remember { mutableStateOf(0f) }
    var controlsVisible by remember { mutableStateOf(true) }

    val currentScale = scales[pagerState.currentPage] ?: 1f
    val backgroundAlpha = (1f - (dismissOffsetY / 600f)).coerceIn(0.25f, 1f)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
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
                                if (dismissOffsetY > 170f) {
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
                        .pointerInput(page) {
                            detectTapGestures(
                                onTap = {
                                    controlsVisible = !controlsVisible
                                },
                                onDoubleTap = {
                                    val current = scales[page] ?: 1f
                                    if (current > 1.2f) {
                                        scales[page] = 1f
                                        offsets[page] = Offset.Zero
                                    } else {
                                        scales[page] = 2.4f
                                        offsets[page] = Offset.Zero
                                    }
                                }
                            )
                        }
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

                                        offsets[page] = if (newScale <= 1.01f) {
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

            if (controlsVisible) {
                ViewerTopBar(
                    currentIndex = pagerState.currentPage,
                    total = images.size,
                    onDismiss = onDismiss,
                    modifier = Modifier.align(Alignment.TopCenter)
                )

                if (images.size > 1) {
                    ViewerThumbStrip(
                        images = images,
                        currentIndex = pagerState.currentPage,
                        onImageClick = { index ->
                            scope.launch {
                                pagerState.animateScrollToPage(index)
                            }
                        },
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )
                }
            }
        }
    }
}

@Composable
private fun ViewerTopBar(
    currentIndex: Int,
    total: Int,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(start = 12.dp, end = 12.dp, top = 8.dp)
            .graphicsLayer {
                alpha = 1f
            },
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        IconButton(
            onClick = onDismiss,
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.42f))
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Закрыть",
                tint = Color.White
            )
        }

        if (total > 1) {
            Card(
                shape = RoundedCornerShape(50.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.Black.copy(alpha = 0.42f)
                )
            ) {
                Text(
                    text = "${currentIndex + 1} / $total",
                    color = Color.White,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun ViewerThumbStrip(
    images: List<Any>,
    currentIndex: Int,
    onImageClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(bottom = 14.dp, start = 12.dp, end = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        itemsIndexed(images) { index, image ->
            val selected = index == currentIndex

            Box(
                modifier = Modifier
                    .size(if (selected) 58.dp else 48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (selected) Color.White.copy(alpha = 0.9f)
                        else Color.White.copy(alpha = 0.22f)
                    )
                    .padding(if (selected) 2.dp else 1.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onImageClick(index) }
            ) {
                AsyncImage(
                    model = image,
                    contentDescription = "Миниатюра",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}