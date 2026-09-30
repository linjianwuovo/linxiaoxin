package com.linxin.feature.aiclass.ui
import com.linxin.core.designsystem.theme.LxShapes

import android.Manifest
import android.net.Uri
import android.util.Log
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cancel
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.ZoomSuggestionOptions
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.linxin.core.designsystem.component.LxProgressIndicator
import com.linxin.core.designsystem.component.LxDialog
import com.linxin.core.designsystem.component.LxTopBar
import com.linxin.feature.aiclass.domain.AiClassQrPayload
import android.util.Size
import android.view.ScaleGestureDetector
import java.util.concurrent.Executors

private const val SCAN_LOG_TAG = "AiClassScan"
private const val OFFICIAL_AUTO_ZOOM_MAX_RATIO = 4f

@Composable
fun AiClassScanScreen(
    onBack: () -> Unit,
    onScanResult: (AiClassQrPayload) -> Unit,
    modifier: Modifier = Modifier,
    signResult: String? = null,
    signSucceeded: Boolean = false,
    onConsumeSignResult: () -> Unit = {},
) {
    var permissionGranted by remember { mutableStateOf(false) }
    var scanState by remember { mutableStateOf<ScanState>(ScanState.Scanning) }

    // 权限请求
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        permissionGranted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.CAMERA,
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    if (!permissionGranted) {
        CameraPermissionRequest(
            onGranted = { permissionGranted = true },
            onBack = onBack,
        )
        return
    }

    // 扫码签到结果弹窗（首页直达扫码时没有 AI课堂 页承接结果，就地在扫码页展示）
    if (signResult != null) {
        // 签成了就退出：停在扫码页会一直显示"正在签到"，明明已经签上却像没完成
        val finish: () -> Unit = {
            onConsumeSignResult()
            if (signSucceeded) onBack() else scanState = ScanState.Scanning
        }
        LxDialog(
            title = "签到结果",
            message = signResult,
            confirmText = "确定",
            onConfirm = finish,
            onDismissRequest = finish,
        )
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            LxTopBar(
                title = "扫码签到",
                onBack = onBack,
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            // 相机预览
            if (scanState is ScanState.Scanning) {
                CameraPreview(
                    onQrCodeDetected = { rawValue ->
                        if (scanState is ScanState.Scanning) {
                            Log.i(
                                SCAN_LOG_TAG,
                                "QR raw detected, preview=${rawValue.previewForLog()}",
                            )
                            val payload = extractQrPayload(rawValue)
                            if (payload != null) {
                                Log.i(
                                    SCAN_LOG_TAG,
                                    "Token extracted, preview=${payload.token.previewForLog()}, length=${payload.token.length}",
                                )
                                scanState = ScanState.Success
                                onScanResult(payload)
                            } else {
                                Log.w(
                                    SCAN_LOG_TAG,
                                    "QR detected but token extraction failed, preview=${rawValue.previewForLog()}",
                                )
                            }
                        }
                    },
                )
            }

            // 扫描提示
            AnimatedVisibility(
                visible = scanState is ScanState.Scanning,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.fillMaxSize(),
            ) {
                ScanOverlay()
            }

            AnimatedVisibility(
                visible = scanState is ScanState.Scanning,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                Text(
                    text = "将二维码对准框内",
                    style = MiuixTheme.textStyles.body2,
                    color = Color.White,
                    modifier = Modifier
                        .padding(bottom = 80.dp)
                        .background(
                            Color.Black.copy(alpha = 0.5f),
                            LxShapes.medium,
                        )
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                )
            }

            // 成功状态
            if (scanState is ScanState.Success) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MiuixTheme.colorScheme.background),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        // 请求还在飞的时候不能显示成失败
                        val failed = signResult != null && !signSucceeded
                        Icon(
                            imageVector = if (failed) Icons.Default.Cancel else Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = if (failed) MiuixTheme.colorScheme.error else MiuixTheme.colorScheme.primary,
                            modifier = Modifier.size(48.dp),
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = when {
                                signResult == null -> "扫码成功，正在签到..."
                                signSucceeded -> "签到成功"
                                else -> "签到失败"
                            },
                            style = MiuixTheme.textStyles.body2,
                        )
                        if (signResult == null) {
                            Spacer(modifier = Modifier.height(12.dp))
                            LxProgressIndicator(modifier = Modifier.size(24.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CameraPreview(
    onQrCodeDetected: (String) -> Unit,
    onCameraReady: (Camera) -> Unit = {},  // 相机就绪回调，供父组件获取 Camera 实例
) {
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    var scanner: BarcodeScanner? by remember { mutableStateOf(null) }

    DisposableEffect(Unit) {
        onDispose {
            executor.shutdown()
            scanner?.close()
        }
    }

    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx).apply {
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }
            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
            var camera: Camera? = null

            val scaleDetector = ScaleGestureDetector(
                ctx,
                object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    override fun onScale(detector: ScaleGestureDetector): Boolean {
                        val cam = camera ?: return false
                        val zoomState = cam.cameraInfo.zoomState.value ?: return false
                        val maxZoom = minOf(
                            zoomState.maxZoomRatio ?: OFFICIAL_AUTO_ZOOM_MAX_RATIO,
                            OFFICIAL_AUTO_ZOOM_MAX_RATIO,
                        )
                        val currentZoom = zoomState.zoomRatio ?: 1f
                        val newZoom = (currentZoom * detector.scaleFactor)
                            .coerceIn(1f, maxZoom)
                        cam.cameraControl.setZoomRatio(newZoom)
                        return true
                    }
                },
            )
            previewView.setOnTouchListener { _, event ->
                scaleDetector.onTouchEvent(event)
            }

            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()

                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }

                val analysis = ImageAnalysis.Builder()
                    .setTargetResolution(Size(1280, 720))
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                try {
                    cameraProvider.unbindAll()
                    val boundCamera = cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        analysis,
                    )
                    camera = boundCamera
                    onCameraReady(boundCamera)
                    val maxSupportedZoomRatio = minOf(
                        boundCamera.cameraInfo.zoomState.value?.maxZoomRatio ?: OFFICIAL_AUTO_ZOOM_MAX_RATIO,
                        OFFICIAL_AUTO_ZOOM_MAX_RATIO,
                    )
                    val zoomCallback = ZoomSuggestionOptions.ZoomCallback { suggestedZoomRatio ->
                        applyOfficialZoomSuggestion(
                            camera = boundCamera,
                            suggestedZoomRatio = suggestedZoomRatio,
                            maxSupportedZoomRatio = maxSupportedZoomRatio,
                        )
                    }
                    scanner?.close()
                    scanner = BarcodeScanning.getClient(
                        BarcodeScannerOptions.Builder()
                            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                            .setZoomSuggestionOptions(
                                ZoomSuggestionOptions.Builder(zoomCallback)
                                    .setMaxSupportedZoomRatio(maxSupportedZoomRatio)
                                    .build(),
                            )
                            .build(),
                    )
                    analysis.setAnalyzer(executor) { imageProxy ->
                        processImage(
                            imageProxy = imageProxy,
                            scanner = scanner,
                            onResult = onQrCodeDetected,
                        )
                    }
                } catch (e: Exception) {
                    Log.e(SCAN_LOG_TAG, "Camera bind failed", e)
                }
            }, ContextCompat.getMainExecutor(ctx))

            previewView
        },
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
private fun ScanOverlay(
    frameSize: Dp = 240.dp,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(frameSize)
                .border(
                    width = 2.dp,
                    color = Color.White.copy(alpha = 0.92f),
                    shape = LxShapes.large,
                ),
        )

        Text(
            text = "二维码放入框内自动识别",
            style = MiuixTheme.textStyles.button,
            fontWeight = FontWeight.Medium,
            color = Color.White,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 36.dp)
                .background(
                    Color.Black.copy(alpha = 0.45f),
                    LxShapes.medium,
                )
                .padding(horizontal = 14.dp, vertical = 8.dp),
        )
    }
}

@androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
private fun processImage(
    imageProxy: ImageProxy,
    scanner: BarcodeScanner?,
    onResult: (String) -> Unit,
) {
    val mediaImage = imageProxy.image
    if (mediaImage == null || scanner == null) {
        imageProxy.close()
        return
    }

    val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
    scanner.process(inputImage)
        .addOnSuccessListener { barcodes ->
            barcodes.firstNotNullOfOrNull { barcode ->
                barcode.rawValue?.trim()?.takeIf { it.isNotBlank() }
            }?.let {
                Log.i(SCAN_LOG_TAG, "MLKit recognized QR content, preview=${it.previewForLog()}")
                onResult(it)
            }
        }
        .addOnCompleteListener {
            imageProxy.close()
        }
}

private fun applyOfficialZoomSuggestion(
    camera: Camera,
    suggestedZoomRatio: Float,
    maxSupportedZoomRatio: Float,
): Boolean {
    val currentZoom = camera.cameraInfo.zoomState.value?.zoomRatio ?: 1f
    val targetZoom = suggestedZoomRatio.coerceIn(1f, maxSupportedZoomRatio)
    if (targetZoom <= currentZoom + 0.01f) {
        return false
    }
    camera.cameraControl.setZoomRatio(targetZoom)
    Log.i(
        SCAN_LOG_TAG,
        "Official zoom suggestion applied, zoom=$currentZoom->$targetZoom",
    )
    return true
}

/**
 * 从二维码内容提取原始内容与 token。
 * 二维码可能是完整 URL（含 token 参数）或直接是 token 字符串。
 */
private fun extractQrPayload(rawValue: String): AiClassQrPayload? {
    val trimmed = rawValue.trim()

    runCatching { Uri.parse(trimmed) }.getOrNull()?.let { uri ->
        uri.getQueryParameter("token")
            ?.takeIf { it.isNotBlank() }
            ?.let { return AiClassQrPayload(rawValue = trimmed, token = it) }
    }

    if (trimmed.contains("token=")) {
        val token = Regex("[?&]token=([^&#]+)").find(trimmed)?.groupValues?.getOrNull(1)
        if (!token.isNullOrBlank()) {
            return AiClassQrPayload(rawValue = trimmed, token = token)
        }
    }

    if (trimmed.matches(Regex("^[A-Za-z0-9_-]{20,}$"))) {
        return AiClassQrPayload(rawValue = trimmed, token = trimmed)
    }

    return null
}

private fun String.previewForLog(maxLen: Int = 48): String {
    if (isBlank()) return "<blank>"
    return if (length <= maxLen) this else take(maxLen) + "..."
}

@Composable
private fun CameraPermissionRequest(
    onGranted: () -> Unit,
    onBack: () -> Unit,
) {
    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) onGranted() else onBack()
    }

    LaunchedEffect(Unit) {
        launcher.launch(Manifest.permission.CAMERA)
    }

    Scaffold(
        topBar = { LxTopBar(title = "扫码签到", onBack = onBack) },
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "需要相机权限才能扫码签到",
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
    }
}

private sealed interface ScanState {
    data object Scanning : ScanState
    data object Success : ScanState
}
