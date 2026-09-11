package com.pulsemessenger.android.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * Small live camera preview used as the first tile in the attachment gallery.
 * It intentionally does not request CAMERA permission by itself; tapping it
 * opens the full in-app camera, which owns the permission flow.
 */
@Composable
fun CameraMiniPreviewTile(
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember(context) { ContextCompat.getMainExecutor(context) }
    val hasCameraPermission =
        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED

    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    var provider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var boundPreview by remember { mutableStateOf<Preview?>(null) }
    var previewAvailable by remember { mutableStateOf(false) }

    LaunchedEffect(hasCameraPermission, lifecycleOwner) {
        if (!hasCameraPermission) {
            previewAvailable = false
            return@LaunchedEffect
        }

        val future = ProcessCameraProvider.getInstance(context)
        future.addListener(
            {
                runCatching {
                    val cameraProvider = future.get()
                    provider = cameraProvider

                    val selector = when {
                        cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) ->
                            CameraSelector.DEFAULT_BACK_CAMERA
                        cameraProvider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) ->
                            CameraSelector.DEFAULT_FRONT_CAMERA
                        else -> null
                    } ?: run {
                        previewAvailable = false
                        return@addListener
                    }

                    // Only unbind our previous mini-preview. Never call
                    // unbindAll() here, because the full camera owns its own
                    // use cases when opened.
                    boundPreview?.let { old ->
                        runCatching { cameraProvider.unbind(old) }
                    }

                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }

                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        selector,
                        preview,
                    )
                    boundPreview = preview
                    previewAvailable = true
                }.onFailure {
                    previewAvailable = false
                }
            },
            executor,
        )
    }

    DisposableEffect(lifecycleOwner) {
        onDispose {
            val cameraProvider = provider
            val preview = boundPreview
            if (cameraProvider != null && preview != null) {
                runCatching { cameraProvider.unbind(preview) }
            }
            boundPreview = null
            previewAvailable = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(4.dp))
            .background(Color.Black),
    ) {
        if (hasCameraPermission) {
            AndroidView(
                factory = { previewView },
                modifier = Modifier.fillMaxSize(),
            )
        }

        if (!hasCameraPermission || !previewAvailable) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (hasCameraPermission) "Камера…" else "Камера",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        // A subtle label keeps the tile obvious without covering the preview.
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(7.dp)
                .clip(RoundedCornerShape(50.dp))
                .background(Color.Black.copy(alpha = 0.50f))
                .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Text(
                text = "Камера",
                color = Color.White,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
            )
        }

        // PreviewView is a real Android View. Keep a Compose click layer above
        // it so the tile opens the full camera reliably on every device.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(onClick = onClick),
        )
    }
}
