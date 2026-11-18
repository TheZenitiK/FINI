package com.example.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Size as AndroidSize
import android.view.MotionEvent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.FlashOff
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.core.logging.AppLogger
import com.example.ui.i18n.LocalStrings
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.InvertedLuminanceSource
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import java.util.EnumMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrScannerScreen(
    onCodeScanned: (String) -> Unit,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val strings = LocalStrings.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var torchEnabled by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            hasCameraPermission = granted
            AppLogger.log("QR", "Разрешение на камеру: $granted")
        }
    )

    val galleryPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val decoded = decodeQrFromGalleryUri(context, uri)
            if (!decoded.isNullOrBlank()) {
                AppLogger.log("QR", "QR-код распознан из галереи (${decoded.length} симв.)")
                onCodeScanned(decoded)
            } else {
                AppLogger.log("QR", "Не удалось найти QR-код на выбранном изображении")
                snackbarHostState.showSnackbar(
                    if (strings.isRussian) "QR-код не найден на выбранном изображении"
                    else "No QR code found in the selected image"
                )
            }
        }
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(if (strings.isRussian) "Сканировать QR-код" else "Scan QR Code") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = strings.back
                        )
                    }
                },
                actions = {
                    if (hasCameraPermission) {
                        IconButton(
                            onClick = { torchEnabled = !torchEnabled },
                            modifier = Modifier.testTag("qr_torch_button")
                        ) {
                            Icon(
                                imageVector = if (torchEnabled) Icons.Rounded.FlashOn else Icons.Rounded.FlashOff,
                                contentDescription = if (strings.isRussian) "Фонарик" else "Flashlight"
                            )
                        }
                    }
                    IconButton(
                        onClick = {
                            galleryPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        modifier = Modifier.testTag("qr_gallery_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PhotoLibrary,
                            contentDescription = if (strings.isRussian) "Выбрать из галереи" else "Pick from gallery"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        }
    ) { innerPadding ->
        if (hasCameraPermission) {
            CameraQrPreview(
                torchEnabled = torchEnabled,
                onPickFromGallery = {
                    galleryPickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                onCodeDetected = onCodeScanned,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Rounded.CameraAlt,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(56.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = if (strings.isRussian) "Нужен доступ к камере" else "Camera Permission Needed",
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (strings.isRussian) {
                        "Разрешите использование камеры, чтобы сканировать QR-коды серверов VLESS и подписок, либо выберите скриншот из галереи"
                    } else {
                        "Allow camera access to scan server or subscription QR codes, or pick an image from your gallery"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(20.dp))
                Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                    Text(if (strings.isRussian) "Разрешить камеру" else "Allow Camera")
                }
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(
                    onClick = {
                        galleryPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }
                ) {
                    Icon(Icons.Rounded.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (strings.isRussian) "Выбрать из галереи" else "Choose from Gallery")
                }
            }
        }
    }
}

@SuppressLint("ClickableViewAccessibility", "UnsafeOptInUsageError")
@Composable
private fun CameraQrPreview(
    torchEnabled: Boolean,
    onPickFromGallery: () -> Unit,
    onCodeDetected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val strings = LocalStrings.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mainExecutor = ContextCompat.getMainExecutor(context)
    val executor = remember { Executors.newSingleThreadExecutor() }

    val mlKitScanner = remember {
        BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build()
        )
    }
    val zxingReader = remember { QRCodeReader() }
    val zxingHints = remember {
        EnumMap<DecodeHintType, Any>(DecodeHintType::class.java).apply {
            put(DecodeHintType.POSSIBLE_FORMATS, listOf(BarcodeFormat.QR_CODE))
            put(DecodeHintType.TRY_HARDER, true)
            put(DecodeHintType.ALSO_INVERTED, true)
        }
    }
    val handledOnce = remember { AtomicBoolean(false) }
    val isDisposed = remember { AtomicBoolean(false) }
    var boundCamera by remember { mutableStateOf<Camera?>(null) }
    var boundProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var previewViewRef by remember { mutableStateOf<PreviewView?>(null) }

    LaunchedEffect(torchEnabled, boundCamera) {
        runCatching { boundCamera?.cameraControl?.enableTorch(torchEnabled) }
    }

    DisposableEffect(Unit) {
        onDispose {
            isDisposed.set(true)
            runCatching { previewViewRef?.setOnTouchListener(null) }
            previewViewRef = null
            boundCamera = null
            runCatching { boundProvider?.unbindAll() }
            boundProvider = null
            runCatching { mlKitScanner.close() }
            runCatching { executor.shutdown() }
        }
    }

    Box(modifier = modifier) {
        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx).apply {
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }
                previewViewRef = previewView

                previewView.setOnTouchListener { v, event ->
                    if (event.action == MotionEvent.ACTION_UP) {
                        val camera = boundCamera ?: return@setOnTouchListener false
                        val point = previewView.meteringPointFactory.createPoint(event.x, event.y)
                        val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE)
                            .setAutoCancelDuration(3, TimeUnit.SECONDS)
                            .build()
                        runCatching { camera.cameraControl.startFocusAndMetering(action) }
                        v.performClick()
                    }
                    true
                }

                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                cameraProviderFuture.addListener({
                    if (isDisposed.get()) return@addListener
                    val cameraProvider = runCatching { cameraProviderFuture.get() }.onFailure { err ->
                        AppLogger.log("QR", "Ошибка инициализации камеры: ${err.localizedMessage ?: err.javaClass.simpleName}")
                    }.getOrNull() ?: return@addListener
                    if (isDisposed.get()) return@addListener
                    boundProvider = cameraProvider

                    val resolutionSelector = ResolutionSelector.Builder()
                        .setResolutionStrategy(
                            ResolutionStrategy(
                                AndroidSize(1280, 720),
                                ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                            )
                        )
                        .build()

                    val preview = Preview.Builder()
                        .setResolutionSelector(resolutionSelector)
                        .build()
                        .also { it.surfaceProvider = previewView.surfaceProvider }

                    val analysis = ImageAnalysis.Builder()
                        .setResolutionSelector(resolutionSelector)
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()

                    analysis.setAnalyzer(executor) { imageProxy ->
                        if (handledOnce.get()) {
                            imageProxy.close()
                            return@setAnalyzer
                        }
                        val mediaImage = imageProxy.image
                        if (mediaImage != null) {
                            val inputImage = InputImage.fromMediaImage(
                                mediaImage,
                                imageProxy.imageInfo.rotationDegrees
                            )
                            mlKitScanner.process(inputImage)
                                .addOnSuccessListener(executor) { barcodes ->
                                    val mlValue = barcodes.firstNotNullOfOrNull { it.rawValue?.trim()?.takeIf { v -> v.isNotBlank() } }
                                    val finalValue = mlValue ?: decodeImageProxyWithZxing(imageProxy, zxingReader, zxingHints)
                                    if (!finalValue.isNullOrBlank() && handledOnce.compareAndSet(false, true)) {
                                        AppLogger.log(
                                            "QR",
                                            "QR распознан (${if (mlValue != null) "MLKit" else "ZXing"}, ${finalValue.length} симв.)"
                                        )
                                        mainExecutor.execute { onCodeDetected(finalValue) }
                                    }
                                    imageProxy.close()
                                }
                                .addOnFailureListener(executor) {
                                    val fallback = decodeImageProxyWithZxing(imageProxy, zxingReader, zxingHints)
                                    if (!fallback.isNullOrBlank() && handledOnce.compareAndSet(false, true)) {
                                        AppLogger.log("QR", "QR распознан (ZXing fallback, ${fallback.length} симв.)")
                                        mainExecutor.execute { onCodeDetected(fallback) }
                                    }
                                    imageProxy.close()
                                }
                        } else {
                            val fallback = decodeImageProxyWithZxing(imageProxy, zxingReader, zxingHints)
                            if (!fallback.isNullOrBlank() && handledOnce.compareAndSet(false, true)) {
                                AppLogger.log("QR", "QR распознан (ZXing, ${fallback.length} симв.)")
                                mainExecutor.execute { onCodeDetected(fallback) }
                            }
                            imageProxy.close()
                        }
                    }

                    runCatching {
                        if (isDisposed.get()) return@addListener
                        cameraProvider.unbindAll()
                        val camera = cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            analysis
                        )
                        if (isDisposed.get()) {
                            cameraProvider.unbindAll()
                            return@addListener
                        }
                        boundCamera = camera
                        previewView.post {
                            val cx = previewView.width / 2f
                            val cy = previewView.height / 2f
                            if (cx > 0f && cy > 0f) {
                                val pt = previewView.meteringPointFactory.createPoint(cx, cy)
                                val focusAction = FocusMeteringAction.Builder(pt, FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE)
                                    .setAutoCancelDuration(4, TimeUnit.SECONDS)
                                    .build()
                                runCatching { camera.cameraControl.startFocusAndMetering(focusAction) }
                            }
                        }
                    }.onFailure { err ->
                        AppLogger.log("QR", "Не удалось запустить камеру: ${err.localizedMessage ?: err.javaClass.simpleName}")
                    }
                }, mainExecutor)
                previewView
            },
            modifier = Modifier.fillMaxSize()
        )

        val primaryColor = MaterialTheme.colorScheme.primary
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
        ) {
            val boxSize = size.minDimension * 0.66f
            val left = (size.width - boxSize) / 2f
            val top = (size.height - boxSize) / 2f
            drawRect(color = Color.Black.copy(alpha = 0.52f))
            drawRoundRect(
                color = Color.Transparent,
                topLeft = Offset(left, top),
                size = Size(boxSize, boxSize),
                cornerRadius = CornerRadius(24.dp.toPx(), 24.dp.toPx()),
                blendMode = BlendMode.Clear
            )
            drawRoundRect(
                color = primaryColor,
                topLeft = Offset(left, top),
                size = Size(boxSize, boxSize),
                cornerRadius = CornerRadius(24.dp.toPx(), 24.dp.toPx()),
                style = Stroke(width = 3.dp.toPx())
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 28.dp, start = 24.dp, end = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color.Black.copy(alpha = 0.65f)
            ) {
                Text(
                    text = if (strings.isRussian) {
                        "Наведите камеру на QR-код (нажмите на экран для фокуса)"
                    } else {
                        "Point camera at QR code (tap screen to focus)"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }

            FilledTonalButton(
                onClick = onPickFromGallery,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.testTag("qr_bottom_gallery_button")
            ) {
                Icon(Icons.Rounded.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (strings.isRussian) "Выбрать фото с QR-кодом" else "Pick QR from Gallery")
            }
        }
    }
}

private fun decodeImageProxyWithZxing(
    imageProxy: ImageProxy,
    reader: QRCodeReader,
    hints: Map<DecodeHintType, Any>
): String? {
    val plane = imageProxy.planes.firstOrNull() ?: return null
    val buffer = plane.buffer
    val rowStride = plane.rowStride
    val pixelStride = plane.pixelStride
    val width = imageProxy.width
    val height = imageProxy.height
    if (width <= 0 || height <= 0) return null

    val yBytes = ByteArray(width * height)
    val rowBuffer = ByteArray(rowStride)
    buffer.rewind()
    for (row in 0 until height) {
        val bytesToRead = minOf(rowStride, buffer.remaining())
        if (bytesToRead <= 0) break
        buffer.get(rowBuffer, 0, bytesToRead)
        if (pixelStride == 1) {
            System.arraycopy(rowBuffer, 0, yBytes, row * width, minOf(width, bytesToRead))
        } else {
            for (col in 0 until width) {
                val idx = col * pixelStride
                if (idx < bytesToRead) {
                    yBytes[row * width + col] = rowBuffer[idx]
                }
            }
        }
    }

    val source = PlanarYUVLuminanceSource(yBytes, width, height, 0, 0, width, height, false)
    val normal = runCatching {
        reader.decode(BinaryBitmap(HybridBinarizer(source)), hints)?.text
    }.also { reader.reset() }.getOrNull()
    if (!normal.isNullOrBlank()) return normal

    return runCatching {
        reader.decode(BinaryBitmap(HybridBinarizer(InvertedLuminanceSource(source))), hints)?.text
    }.also { reader.reset() }.getOrNull()
}

private suspend fun decodeQrFromGalleryUri(context: android.content.Context, uri: Uri): String? =
    withContext(Dispatchers.IO) {
        // 1. Try ML Kit InputImage first (handles rotated/skewed/dark-theme QR reliably)
        val mlResult = runCatching {
            val scanner = BarcodeScanning.getClient(
                BarcodeScannerOptions.Builder()
                    .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                    .build()
            )
            try {
                val inputImage = InputImage.fromFilePath(context, uri)
                val barcodes = suspendCancellableCoroutine { cont ->
                    scanner.process(inputImage)
                        .addOnSuccessListener { result ->
                            if (cont.isActive) cont.resume(result)
                        }
                        .addOnFailureListener { error ->
                            if (cont.isActive) cont.resumeWithException(error)
                        }
                        .addOnCanceledListener {
                            cont.cancel()
                        }
                }
                barcodes.firstNotNullOfOrNull { it.rawValue?.trim()?.takeIf { v -> v.isNotBlank() } }
            } finally {
                scanner.close()
            }
        }.getOrNull()
        if (!mlResult.isNullOrBlank()) return@withContext mlResult

        // 2. Fallback to ZXing on downsampled Bitmap to prevent OOM on large gallery photos
        runCatching {
            val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, boundsOpts)
            }
            val maxDim = maxOf(boundsOpts.outWidth, boundsOpts.outHeight)
            var sampleSize = 1
            while (maxDim > 0 && (maxDim / sampleSize) > 1440) {
                sampleSize *= 2
            }
            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val bitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOpts)
            } ?: return@runCatching null
            try {
                val width = bitmap.width
                val height = bitmap.height
                val pixels = IntArray(width * height)
                bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
                val source = RGBLuminanceSource(width, height, pixels)
                val reader = QRCodeReader()
                val hints = EnumMap<DecodeHintType, Any>(DecodeHintType::class.java).apply {
                    put(DecodeHintType.POSSIBLE_FORMATS, listOf(BarcodeFormat.QR_CODE))
                    put(DecodeHintType.TRY_HARDER, true)
                    put(DecodeHintType.ALSO_INVERTED, true)
                }
                val normal = runCatching {
                    reader.decode(BinaryBitmap(HybridBinarizer(source)), hints)?.text
                }.also { reader.reset() }.getOrNull()
                if (!normal.isNullOrBlank()) return@runCatching normal

                runCatching {
                    reader.decode(BinaryBitmap(HybridBinarizer(InvertedLuminanceSource(source))), hints)?.text
                }.also { reader.reset() }.getOrNull()
            } finally {
                bitmap.recycle()
            }
        }.getOrNull()
    }
