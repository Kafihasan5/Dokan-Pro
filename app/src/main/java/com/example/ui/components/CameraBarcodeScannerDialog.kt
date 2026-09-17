package com.example.ui.components

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.view.HapticFeedbackConstants
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.example.ui.theme.Radius
import com.example.ui.theme.Spacing
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

@Composable
fun CameraBarcodeScannerDialog(
    title: String = "বারকোড স্ক্যানার",
    confirmText: String = "কার্টে যোগ করুন",
    onDismiss: () -> Unit,
    onBarcodeScanned: (String) -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    var isManualMode by remember { mutableStateOf(false) }
    var manualBarcodeText by remember { mutableStateOf("") }
    var isTorchOn by remember { mutableStateOf(false) }
    var cameraInstance by remember { mutableStateOf<Camera?>(null) }
    var isScanned by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight()
                .padding(vertical = 16.dp),
            shape = RoundedCornerShape(Radius.lg),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            shadowElevation = 16.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Spacing.md),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header: Title & Close Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.QrCodeScanner,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(Spacing.xs))
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "বন্ধ করুন",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.sm))

                // Mode Toggle: [ক্যামেরা স্ক্যান] vs [সরাসরি লিখুন]
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Radius.pill))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Surface(
                        onClick = { isManualMode = false },
                        shape = RoundedCornerShape(Radius.pill),
                        color = if (!isManualMode) MaterialTheme.colorScheme.primary else Color.Transparent,
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CameraAlt,
                                contentDescription = null,
                                tint = if (!isManualMode) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "ক্যামেরা স্ক্যান",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (!isManualMode) FontWeight.Bold else FontWeight.Medium,
                                color = if (!isManualMode) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Surface(
                        onClick = { isManualMode = true },
                        shape = RoundedCornerShape(Radius.pill),
                        color = if (isManualMode) MaterialTheme.colorScheme.primary else Color.Transparent,
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Keyboard,
                                contentDescription = null,
                                tint = if (isManualMode) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "সরাসরি লিখুন",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (isManualMode) FontWeight.Bold else FontWeight.Medium,
                                color = if (isManualMode) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.md))

                if (!isManualMode) {
                    // CAMERA SCANNER VIEW
                    if (!hasCameraPermission) {
                        // Permission Request Card
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(260.dp)
                                .clip(RoundedCornerShape(Radius.md))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                                .padding(Spacing.lg),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.VideocamOff,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(Spacing.sm))
                            Text(
                                text = "ক্যামেরা ব্যবহারের অনুমতি প্রয়োজন",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(Spacing.xs))
                            Text(
                                text = "বারকোড সহজে স্ক্যান করতে ক্যামেরা পারমিশন এলাউ করুন।",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(Spacing.md))
                            Button(
                                onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                                shape = RoundedCornerShape(Radius.pill)
                            ) {
                                Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(Spacing.xs))
                                Text("অনুমতি দিন")
                            }
                        }
                    } else {
                        // Live Camera Preview with Scanner Target Box
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(260.dp)
                                .clip(RoundedCornerShape(Radius.md))
                                .background(Color.Black),
                            contentAlignment = Alignment.Center
                        ) {
                            CameraPreviewWithAnalyzer(
                                onBarcodeFound = { code ->
                                    if (!isScanned) {
                                        isScanned = true
                                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                        onBarcodeScanned(code)
                                    }
                                },
                                onCameraReady = { cam ->
                                    cameraInstance = cam
                                }
                            )

                            // Scanner Reticle Overlay
                            ScannerReticleOverlay()

                            // Flashlight Toggle
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(Spacing.sm),
                                contentAlignment = Alignment.TopEnd
                            ) {
                                IconButton(
                                    onClick = {
                                        val newState = !isTorchOn
                                        isTorchOn = newState
                                        cameraInstance?.cameraControl?.enableTorch(newState)
                                    },
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(Color.Black.copy(alpha = 0.5f))
                                ) {
                                    Icon(
                                        imageVector = if (isTorchOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                                        contentDescription = "টর্চ",
                                        tint = if (isTorchOn) Color.Yellow else Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(Spacing.xs))
                        Text(
                            text = "💡 পণ্যের বারকোডটি ফ্রেমের ভেতরে ধরুন",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    // MANUAL BARCODE ENTRY VIEW
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = Spacing.sm),
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                    ) {
                        DokanTextField(
                            value = manualBarcodeText,
                            onValueChange = { manualBarcodeText = it },
                            label = "বারকোড নম্বর লিখুন",
                            placeholder = "যেমন: 8901001",
                            keyboardType = KeyboardType.Number,
                            leadingIcon = Icons.Default.QrCode
                        )

                        Text(
                            text = "দ্রুত ডেমো কোড নির্বাচন:",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                        ) {
                            listOf("8901001", "8901005", "8901016").forEach { code ->
                                SuggestionChip(
                                    onClick = { manualBarcodeText = code },
                                    label = { Text(code, style = MaterialTheme.typography.labelSmall) },
                                    shape = RoundedCornerShape(Radius.pill)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(Spacing.xs))

                        Button(
                            onClick = {
                                if (manualBarcodeText.isNotBlank()) {
                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                    onBarcodeScanned(manualBarcodeText.trim())
                                }
                            },
                            enabled = manualBarcodeText.isNotBlank(),
                            shape = RoundedCornerShape(Radius.pill),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(Spacing.xs))
                            Text(confirmText)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScannerReticleOverlay() {
    val infiniteTransition = rememberInfiniteTransition(label = "laser_anim")
    val laserProgress by infiniteTransition.animateFloat(
        initialValue = 0.1f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "laser_pos"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val reticleWidth = size.width * 0.75f
        val reticleHeight = size.height * 0.65f
        val left = (size.width - reticleWidth) / 2f
        val top = (size.height - reticleHeight) / 2f
        val cornerLength = 24.dp.toPx()
        val strokeWidth = 3.dp.toPx()
        val cornerColor = Color(0xFF22C55E) // Bright Green

        // Corner 1: Top-Left
        drawLine(cornerColor, Offset(left, top), Offset(left + cornerLength, top), strokeWidth)
        drawLine(cornerColor, Offset(left, top), Offset(left, top + cornerLength), strokeWidth)

        // Corner 2: Top-Right
        drawLine(cornerColor, Offset(left + reticleWidth, top), Offset(left + reticleWidth - cornerLength, top), strokeWidth)
        drawLine(cornerColor, Offset(left + reticleWidth, top), Offset(left + reticleWidth, top + cornerLength), strokeWidth)

        // Corner 3: Bottom-Left
        drawLine(cornerColor, Offset(left, top + reticleHeight), Offset(left + cornerLength, top + reticleHeight), strokeWidth)
        drawLine(cornerColor, Offset(left, top + reticleHeight), Offset(left, top + reticleHeight - cornerLength), strokeWidth)

        // Corner 4: Bottom-Right
        drawLine(cornerColor, Offset(left + reticleWidth, top + reticleHeight), Offset(left + reticleWidth - cornerLength, top + reticleHeight), strokeWidth)
        drawLine(cornerColor, Offset(left + reticleWidth, top + reticleHeight), Offset(left + reticleWidth, top + reticleHeight - cornerLength), strokeWidth)

        // Animated red/green laser line
        val laserY = top + (reticleHeight * laserProgress)
        drawLine(
            brush = Brush.horizontalGradient(
                colors = listOf(Color.Transparent, Color(0xFFEF4444), Color.Transparent),
                startX = left,
                endX = left + reticleWidth
            ),
            start = Offset(left, laserY),
            end = Offset(left + reticleWidth, laserY),
            strokeWidth = 2.dp.toPx()
        )
    }
}

@androidx.annotation.OptIn(ExperimentalGetImage::class)
@Composable
private fun CameraPreviewWithAnalyzer(
    onBarcodeFound: (String) -> Unit,
    onCameraReady: (Camera) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }

            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
            val cameraExecutor = Executors.newSingleThreadExecutor()

            val barcodeScannerOptions = BarcodeScannerOptions.Builder()
                .setBarcodeFormats(
                    Barcode.FORMAT_EAN_13,
                    Barcode.FORMAT_EAN_8,
                    Barcode.FORMAT_UPC_A,
                    Barcode.FORMAT_UPC_E,
                    Barcode.FORMAT_CODE_128,
                    Barcode.FORMAT_CODE_39,
                    Barcode.FORMAT_QR_CODE,
                    Barcode.FORMAT_ALL_FORMATS
                )
                .build()
            val barcodeScanner = BarcodeScanning.getClient(barcodeScannerOptions)

            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()

                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                    val mediaImage = imageProxy.image
                    if (mediaImage != null) {
                        val inputImage = InputImage.fromMediaImage(
                            mediaImage,
                            imageProxy.imageInfo.rotationDegrees
                        )
                        barcodeScanner.process(inputImage)
                            .addOnSuccessListener { barcodes ->
                                for (barcode in barcodes) {
                                    val raw = barcode.rawValue
                                    if (!raw.isNullOrBlank()) {
                                        onBarcodeFound(raw)
                                        break
                                    }
                                }
                            }
                            .addOnCompleteListener {
                                imageProxy.close()
                            }
                    } else {
                        imageProxy.close()
                    }
                }

                try {
                    cameraProvider.unbindAll()
                    val camera = cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        imageAnalysis
                    )
                    onCameraReady(camera)
                } catch (_: Exception) {}
            }, ContextCompat.getMainExecutor(ctx))

            previewView
        },
        modifier = Modifier.fillMaxSize()
    )
}
