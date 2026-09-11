package com.pulsemessenger.android.ui

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.Surface
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.concurrent.TimeUnit

private const val HOLD_TO_RECORD_MS = 320L
private const val MAX_VIDEO_SECONDS = 90L

private enum class CapturedMediaKind {
    PHOTO,
    VIDEO,
}

@Composable
fun InAppCameraDialog(
    onDismiss: () -> Unit,
    onPhotoSelected: (Uri) -> Unit,
    onVideoSelected: (Uri) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mainExecutor = remember(context) { ContextCompat.getMainExecutor(context) }
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var videoCapture by remember { mutableStateOf<VideoCapture<Recorder>?>(null) }
    var activeRecording by remember { mutableStateOf<Recording?>(null) }
    var lensFacing by remember { mutableIntStateOf(CameraSelector.LENS_FACING_BACK) }
    var hasFrontCamera by remember { mutableStateOf(false) }
    var hasBackCamera by remember { mutableStateOf(false) }
    var flashEnabled by remember { mutableStateOf(false) }
    var isCapturing by remember { mutableStateOf(false) }
    var isRecording by remember { mutableStateOf(false) }
    var recordingStartedAt by remember { mutableLongStateOf(0L) }
    var recordingSeconds by remember { mutableLongStateOf(0L) }
    var capturedUri by remember { mutableStateOf<Uri?>(null) }
    var capturedFile by remember { mutableStateOf<File?>(null) }
    var capturedKind by remember { mutableStateOf<CapturedMediaKind?>(null) }
    var cameraError by remember { mutableStateOf<String?>(null) }

    val currentCamera = rememberUpdatedState(camera)

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
        if (!granted) cameraError = "Нужен доступ к камере"
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasAudioPermission = granted
        cameraError = if (granted) {
            "Микрофон разрешён. Зажмите кнопку ещё раз для записи видео"
        } else {
            "Для видео со звуком нужен доступ к микрофону"
        }
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    LaunchedEffect(hasCameraPermission) {
        if (!hasCameraPermission || cameraProvider != null) return@LaunchedEffect

        val future = ProcessCameraProvider.getInstance(context)
        future.addListener(
            {
                runCatching { future.get() }
                    .onSuccess { provider ->
                        cameraProvider = provider
                        hasBackCamera = runCatching {
                            provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)
                        }.getOrDefault(false)
                        hasFrontCamera = runCatching {
                            provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)
                        }.getOrDefault(false)

                        if (!hasBackCamera && hasFrontCamera) {
                            lensFacing = CameraSelector.LENS_FACING_FRONT
                        }
                    }
                    .onFailure {
                        cameraError = "Не удалось открыть камеру"
                    }
            },
            mainExecutor,
        )
    }

    LaunchedEffect(cameraProvider, lensFacing, lifecycleOwner) {
        val provider = cameraProvider ?: return@LaunchedEffect
        if (isRecording) return@LaunchedEffect

        val selector = CameraSelector.Builder()
            .requireLensFacing(lensFacing)
            .build()

        if (!runCatching { provider.hasCamera(selector) }.getOrDefault(false)) {
            cameraError = "Эта камера недоступна"
            return@LaunchedEffect
        }

        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }
        val photoCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .setFlashMode(
                if (flashEnabled) ImageCapture.FLASH_MODE_ON
                else ImageCapture.FLASH_MODE_OFF
            )
            .build()

        val recorder = Recorder.Builder()
            .setQualitySelector(
                QualitySelector.from(
                    Quality.HD,
                    FallbackStrategy.lowerQualityOrHigherThan(Quality.SD),
                )
            )
            .build()
        val movieCapture = VideoCapture.withOutput(recorder)

        runCatching {
            provider.unbindAll()
            provider.bindToLifecycle(
                lifecycleOwner,
                selector,
                preview,
                photoCapture,
                movieCapture,
            )
        }.onSuccess { boundCamera ->
            camera = boundCamera
            imageCapture = photoCapture
            videoCapture = movieCapture
            cameraError = null
            if (!boundCamera.cameraInfo.hasFlashUnit()) {
                flashEnabled = false
                photoCapture.flashMode = ImageCapture.FLASH_MODE_OFF
            }
        }.onFailure {
            camera = null
            imageCapture = null
            videoCapture = null
            cameraError = "Не удалось запустить камеру"
        }
    }

    LaunchedEffect(flashEnabled, imageCapture) {
        imageCapture?.flashMode =
            if (flashEnabled) ImageCapture.FLASH_MODE_ON
            else ImageCapture.FLASH_MODE_OFF
    }

    LaunchedEffect(isRecording, flashEnabled, camera) {
        val activeCamera = camera ?: return@LaunchedEffect
        val canTorch = activeCamera.cameraInfo.hasFlashUnit()
        runCatching {
            activeCamera.cameraControl.enableTorch(isRecording && flashEnabled && canTorch)
        }
    }

    LaunchedEffect(isRecording, recordingStartedAt) {
        if (!isRecording) {
            recordingSeconds = 0L
            return@LaunchedEffect
        }

        while (isRecording) {
            recordingSeconds = ((System.currentTimeMillis() - recordingStartedAt) / 1000L)
                .coerceAtLeast(0L)

            if (recordingSeconds >= MAX_VIDEO_SECONDS) {
                activeRecording?.stop()
                break
            }
            delay(200L)
        }
    }

    DisposableEffect(cameraProvider) {
        onDispose {
            runCatching { activeRecording?.stop() }
            runCatching { cameraProvider?.unbindAll() }
        }
    }

    val scaleGestureDetector = remember(context) {
        ScaleGestureDetector(
            context,
            object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScale(detector: ScaleGestureDetector): Boolean {
                    val activeCamera = currentCamera.value ?: return false
                    val zoomState = activeCamera.cameraInfo.zoomState.value ?: return false
                    val nextZoom = (zoomState.zoomRatio * detector.scaleFactor)
                        .coerceIn(zoomState.minZoomRatio, zoomState.maxZoomRatio)
                    activeCamera.cameraControl.setZoomRatio(nextZoom)
                    return true
                }
            },
        )
    }

    fun captureUri(file: File): Uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file,
    )

    fun takePhoto() {
        val capture = imageCapture ?: return
        if (isCapturing || isRecording) return

        isCapturing = true
        cameraError = null

        val directory = File(context.cacheDir, "camera").apply { mkdirs() }
        val file = File.createTempFile(
            "camera_${System.currentTimeMillis()}_",
            ".jpg",
            directory,
        )

        capture.targetRotation = previewView.display?.rotation ?: Surface.ROTATION_0
        val outputOptions = ImageCapture.OutputFileOptions.Builder(file).build()

        capture.takePicture(
            outputOptions,
            mainExecutor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    isCapturing = false
                    capturedFile = file
                    capturedUri = captureUri(file)
                    capturedKind = CapturedMediaKind.PHOTO
                }

                override fun onError(exception: ImageCaptureException) {
                    isCapturing = false
                    file.delete()
                    cameraError = "Не удалось сделать снимок"
                }
            },
        )
    }

    fun startVideo() {
        val capture = videoCapture ?: return
        if (isCapturing || isRecording || activeRecording != null) return

        if (!hasAudioPermission) {
            cameraError = "Разрешите микрофон для записи видео со звуком"
            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }

        cameraError = null
        val directory = File(context.cacheDir, "camera").apply { mkdirs() }
        val file = File.createTempFile(
            "video_${System.currentTimeMillis()}_",
            ".mp4",
            directory,
        )
        capture.targetRotation = previewView.display?.rotation ?: Surface.ROTATION_0

        val outputOptions = FileOutputOptions.Builder(file).build()
        val pendingRecording = capture.output
            .prepareRecording(context, outputOptions)
            .withAudioEnabled()

        val recording = pendingRecording.start(mainExecutor) { event ->
            when (event) {
                is VideoRecordEvent.Start -> {
                    isRecording = true
                    recordingStartedAt = System.currentTimeMillis()
                    recordingSeconds = 0L
                }

                is VideoRecordEvent.Finalize -> {
                    isRecording = false
                    activeRecording = null
                    runCatching { camera?.cameraControl?.enableTorch(false) }

                    if (event.hasError() || file.length() <= 0L) {
                        file.delete()
                        cameraError = "Не удалось записать видео"
                    } else {
                        capturedFile = file
                        capturedUri = captureUri(file)
                        capturedKind = CapturedMediaKind.VIDEO
                    }
                }
            }
        }

        activeRecording = recording
        isRecording = true
        recordingStartedAt = System.currentTimeMillis()
        recordingSeconds = 0L
    }

    fun stopVideo() {
        activeRecording?.stop()
    }

    fun discardCapture() {
        capturedFile?.delete()
        capturedFile = null
        capturedUri = null
        capturedKind = null
    }

    Dialog(
        onDismissRequest = {
            if (!isCapturing && !isRecording) {
                discardCapture()
                onDismiss()
            }
        },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
            val mediaUri = capturedUri
            val mediaKind = capturedKind

            if (mediaUri != null && mediaKind != null) {
                if (mediaKind == CapturedMediaKind.PHOTO) {
                    AsyncImage(
                        model = mediaUri,
                        contentDescription = "Снятое фото",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                    )
                } else {
                    CapturedVideoPreview(
                        uri = mediaUri,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                Row(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .fillMaxWidth()
                        .padding(18.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RoundCameraControl("×") {
                        discardCapture()
                        onDismiss()
                    }
                    Text(
                        text = "Предпросмотр",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(modifier = Modifier.size(48.dp))
                }

                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.70f))
                        .padding(horizontal = 20.dp, vertical = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedButton(
                        onClick = { discardCapture() },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Переснять")
                    }
                    Button(
                        onClick = {
                            val uri = capturedUri ?: return@Button
                            val kind = capturedKind ?: return@Button
                            capturedFile = null
                            capturedUri = null
                            capturedKind = null
                            if (kind == CapturedMediaKind.PHOTO) {
                                onPhotoSelected(uri)
                            } else {
                                onVideoSelected(uri)
                            }
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            if (mediaKind == CapturedMediaKind.PHOTO) {
                                "Использовать фото"
                            } else {
                                "Использовать видео"
                            }
                        )
                    }
                }
            } else if (!hasCameraPermission) {
                CameraPermissionState(
                    message = cameraError ?: "Разрешите Zhuravlik доступ к камере",
                    onRequest = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) },
                    onClose = onDismiss,
                )
            } else {
                AndroidView(
                    factory = { previewView },
                    modifier = Modifier.fillMaxSize(),
                    update = { view ->
                        view.setOnTouchListener { _, event ->
                            scaleGestureDetector.onTouchEvent(event)

                            if (
                                event.action == MotionEvent.ACTION_UP &&
                                !scaleGestureDetector.isInProgress &&
                                !isRecording
                            ) {
                                val point = view.meteringPointFactory.createPoint(event.x, event.y)
                                val action = FocusMeteringAction.Builder(point)
                                    .setAutoCancelDuration(3, TimeUnit.SECONDS)
                                    .build()
                                currentCamera.value?.cameraControl?.startFocusAndMetering(action)
                            }
                            true
                        }
                    },
                )

                Row(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .fillMaxWidth()
                        .padding(18.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (!isRecording) {
                        RoundCameraControl("×", onDismiss)
                    } else {
                        Spacer(modifier = Modifier.size(48.dp))
                    }

                    val hasFlash = camera?.cameraInfo?.hasFlashUnit() == true
                    if (hasFlash) {
                        RoundCameraControl(
                            if (flashEnabled) "⚡" else "⚡̸"
                        ) {
                            flashEnabled = !flashEnabled
                        }
                    } else {
                        Spacer(modifier = Modifier.size(48.dp))
                    }
                }

                if (isRecording) {
                    Card(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 22.dp),
                        shape = RoundedCornerShape(50.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color.Black.copy(alpha = 0.58f),
                        ),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(9.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFFF453A)),
                            )
                            Text(
                                text = formatCameraDuration(recordingSeconds),
                                color = Color.White,
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                    }
                }

                cameraError?.let { error ->
                    Card(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = if (isRecording) 72.dp else 82.dp, start = 20.dp, end = 20.dp),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                        ),
                    ) {
                        Text(
                            text = error,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.42f))
                        .padding(horizontal = 32.dp, vertical = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (!isRecording) {
                        Text(
                            text = "Нажмите для фото · удерживайте для видео",
                            color = Color.White.copy(alpha = 0.78f),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    } else {
                        Text(
                            text = "Отпустите, чтобы закончить запись",
                            color = Color.White.copy(alpha = 0.82f),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val canSwitch = hasFrontCamera && hasBackCamera
                        if (canSwitch && !isRecording) {
                            RoundCameraControl("↻") {
                                flashEnabled = false
                                lensFacing =
                                    if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                                        CameraSelector.LENS_FACING_FRONT
                                    } else {
                                        CameraSelector.LENS_FACING_BACK
                                    }
                            }
                        } else {
                            Spacer(modifier = Modifier.size(48.dp))
                        }

                        Box(
                            modifier = Modifier
                                .size(88.dp)
                                .clip(CircleShape)
                                .border(
                                    width = 4.dp,
                                    color = if (isRecording) Color(0xFFFF453A) else Color.White,
                                    shape = CircleShape,
                                )
                                .pointerInput(
                                    imageCapture,
                                    videoCapture,
                                    hasAudioPermission,
                                ) {
                                    // Keep this gesture coroutine alive while recording.
                                    // isRecording/isCapturing must NOT be pointerInput keys:
                                    // changing them would cancel the coroutine before ACTION_UP,
                                    // which is why the old build required a second tap to stop.
                                    awaitEachGesture {
                                        awaitFirstDown(requireUnconsumed = false)

                                        val releasedQuickly = withTimeoutOrNull(HOLD_TO_RECORD_MS) {
                                            waitForUpOrCancellation()
                                        }

                                        if (releasedQuickly != null) {
                                            if (!isRecording && !isCapturing) {
                                                takePhoto()
                                            }
                                        } else {
                                            if (!isCapturing && !isRecording) {
                                                startVideo()
                                            }

                                            // Whether the pointer is released normally or the
                                            // gesture is cancelled, stop the active Recording.
                                            // activeRecording is assigned synchronously by
                                            // CameraX before the Start event arrives.
                                            waitForUpOrCancellation()
                                            stopVideo()
                                        }
                                    }
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(if (isRecording) 44.dp else 68.dp)
                                    .clip(if (isRecording) RoundedCornerShape(12.dp) else CircleShape)
                                    .background(
                                        if (isRecording) Color(0xFFFF453A)
                                        else if (isCapturing) Color.White.copy(alpha = 0.45f)
                                        else Color.White
                                    ),
                            )
                        }

                        Spacer(modifier = Modifier.size(48.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun CapturedVideoPreview(
    uri: Uri,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val player = remember(uri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(uri))
            repeatMode = Player.REPEAT_MODE_ONE
            prepare()
            playWhenReady = true
        }
    }

    DisposableEffect(player) {
        onDispose { player.release() }
    }

    AndroidView(
        factory = { playerContext ->
            PlayerView(playerContext).apply {
                this.player = player
                useController = true
                setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
            }
        },
        update = { it.player = player },
        modifier = modifier,
    )
}

@Composable
private fun RoundCameraControl(
    text: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.48f))
            .pointerInput(onClick) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val up = waitForUpOrCancellation()
                    if (up != null && down.id == up.id) onClick()
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 24.sp,
        )
    }
}

@Composable
private fun CameraPermissionState(
    message: String,
    onRequest: () -> Unit,
    onClose: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            shape = RoundedCornerShape(26.dp),
        ) {
            Column(
                modifier = Modifier.padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    text = "Камера",
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    OutlinedButton(onClick = onClose) {
                        Text("Закрыть")
                    }
                    Spacer(modifier = Modifier.size(8.dp))
                    Button(onClick = onRequest) {
                        Text("Разрешить")
                    }
                }
            }
        }
    }
}

private fun formatCameraDuration(seconds: Long): String {
    val safe = seconds.coerceAtLeast(0L)
    val minutes = safe / 60L
    val secs = safe % 60L
    return "%02d:%02d".format(minutes, secs)
}
