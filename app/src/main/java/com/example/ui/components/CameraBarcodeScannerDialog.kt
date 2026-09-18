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
import android.media.AudioManager
import android.media.ToneGenerator
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.style.TextOverflow
import com.example.data.entity.Product
import com.example.ui.CartItem
import com.example.ui.ShopConfig
import com.example.ui.theme.amountTextStyle
import com.example.ui.theme.dokanColors
import com.example.util.Formatters
import java.util.concurrent.Executors
import android.os.Handler
import android.os.Looper
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.TimeUnit
import android.util.Size
import android.view.MotionEvent
import androidx.camera.core.FocusMeteringAction
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.drawscope.Stroke

@Composable
fun CameraBarcodeScannerDialog(
    title: String = "বারকোড স্ক্যানার",
    confirmText: String = "কার্টে যোগ করুন",
    enableContinuousScan: Boolean = false,
    cartItemCount: Int? = null,
    cartTotalText: String? = null,
    products: List<Product> = emptyList(),
    cartItems: List<CartItem> = emptyList(),
    config: ShopConfig = ShopConfig(),
    onDismiss: () -> Unit,
    onBarcodeScanned: (String) -> Unit,
    onQtyChange: ((productId: Long, newQty: Double) -> Unit)? = null,
    onOpenWeightDialog: ((Product) -> Unit)? = null
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
    var isContinuousMode by remember { mutableStateOf(enableContinuousScan) }
    var lastScannedBarcode by remember { mutableStateOf<String?>(null) }
    var lastScannedTimestamp by remember { mutableLongStateOf(0L) }
    var lastCode by remember { mutableStateOf("") }
    var scannedHistory by remember { mutableStateOf<List<Long>>(emptyList()) }
    var resetScanTrigger by remember { mutableIntStateOf(0) }

    val scannedProduct = remember(lastScannedBarcode, products) {
        if (!lastScannedBarcode.isNullOrBlank()) {
            val code = lastScannedBarcode!!.trim()
            products.find { it.barcode?.trim().equals(code, ignoreCase = true) }
        } else null
    }

    val inCartItem = remember(scannedProduct, cartItems) {
        if (scannedProduct != null) {
            cartItems.find { it.productId == scannedProduct.id }
        } else null
    }

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
                .fillMaxWidth(0.95f)
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
                    .verticalScroll(rememberScrollState())
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

                Spacer(modifier = Modifier.height(Spacing.xs))

                if (!isManualMode) {
                    // LIVE CAMERA SCANNER VIEW
                    if (!hasCameraPermission) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(230.dp)
                                .clip(RoundedCornerShape(Radius.md))
                                .clipToBounds()
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
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
                                text = "বারকোড বা QR কোড সহজে স্ক্যান করতে ক্যামেরা পারমিশন এলাউ করুন।",
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
                                .height(230.dp)
                                .clip(RoundedCornerShape(Radius.md))
                                .clipToBounds()
                                .background(Color.Black),
                            contentAlignment = Alignment.Center
                        ) {
                            CameraPreviewWithAnalyzer(
                                resetTrigger = resetScanTrigger,
                                onBarcodeFound = { code ->
                                    val trimmedCode = code.trim()
                                    val currentTime = System.currentTimeMillis()
                                    if (currentTime - lastScannedTimestamp >= 500L) {
                                        lastScannedTimestamp = currentTime
                                        lastCode = trimmedCode
                                        lastScannedBarcode = trimmedCode

                                        val found = products.find { it.barcode?.trim().equals(trimmedCode, ignoreCase = true) }
                                        if (found != null && !scannedHistory.contains(found.id)) {
                                            scannedHistory = listOf(found.id) + scannedHistory
                                        }

                                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                        try {
                                            val toneGen = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
                                            toneGen.startTone(ToneGenerator.TONE_PROP_BEEP, 130)
                                        } catch (_: Exception) {}

                                        onBarcodeScanned(trimmedCode)

                                        if (!isContinuousMode) {
                                            // Non-continuous mode: user can still see and confirm before dismiss
                                        }
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
                            text = "💡 বারকোড বা QR কোডটি ফ্রেমের ভেতরে ধরুন (ফোকাস করতে ট্যাপ করুন)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Continuous Mode Toggle Switch (when enabled for this dialog)
                    if (enableContinuousScan) {
                        Spacer(modifier = Modifier.height(Spacing.xs))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(Radius.sm))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                                .padding(horizontal = Spacing.sm, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AllInclusive,
                                    contentDescription = null,
                                    tint = if (isContinuousMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(Spacing.xs))
                                Column {
                                    Text(
                                        text = "একটানা অটো-স্ক্যান মোড",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = if (isContinuousMode) "ক্যামেরা চালু থাকবে, পরপর পণ্য স্ক্যান করুন" else "একটি স্ক্যান করার পর বন্ধ হবে",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Switch(
                                checked = isContinuousMode,
                                onCheckedChange = { isContinuousMode = it },
                                modifier = Modifier.scale(0.8f)
                            )
                        }
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
                                    val code = manualBarcodeText.trim()
                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                    try {
                                        val toneGen = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
                                        toneGen.startTone(ToneGenerator.TONE_PROP_BEEP, 120)
                                    } catch (_: Exception) {}

                                    val found = products.find { it.barcode?.trim().equals(code, ignoreCase = true) }
                                    if (found != null && !scannedHistory.contains(found.id)) {
                                        scannedHistory = listOf(found.id) + scannedHistory
                                    }

                                    onBarcodeScanned(code)
                                    lastScannedBarcode = code
                                    manualBarcodeText = ""
                                    if (!isContinuousMode) {
                                        // let user see scanned product
                                    }
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

                // --------------------------------------------------------------
                // SCANNED PRODUCT & QUANTITY CARD (সাথে সাথে পণ্য ও পরিমাণ প্রদর্শন)
                // --------------------------------------------------------------
                if (scannedProduct != null) {
                    Spacer(modifier = Modifier.height(Spacing.sm))
                    Card(
                        shape = RoundedCornerShape(Radius.md),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                        ),
                        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(Spacing.sm)) {
                            // Header: Checkmark + Name + Line Total in Cart
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF16A34A)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = scannedProduct.nameBn,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = "বারকোড: ${scannedProduct.barcode ?: lastScannedBarcode}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            if (lastCode.isNotEmpty()) {
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Surface(
                                                    onClick = {
                                                        lastCode = ""
                                                        resetScanTrigger++
                                                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                                    },
                                                    shape = RoundedCornerShape(Radius.xs),
                                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.Refresh,
                                                            contentDescription = "পুনরায় স্ক্যান",
                                                            tint = MaterialTheme.colorScheme.primary,
                                                            modifier = Modifier.size(11.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(2.dp))
                                                        Text(
                                                            text = "পুনরায় স্ক্যান",
                                                            fontSize = 9.sp,
                                                            color = MaterialTheme.colorScheme.primary,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                val displayTotalPoisha = inCartItem?.lineTotalPoisha ?: scannedProduct.salePricePoisha
                                Text(
                                    text = Formatters.formatMoney(
                                        displayTotalPoisha,
                                        config.useBengaliNumerals,
                                        config.currencySymbol
                                    ),
                                    style = amountTextStyle(17.sp),
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            // Unit price & Stock Available
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "একক মূল্য: ${Formatters.formatMoney(scannedProduct.salePricePoisha, config.useBengaliNumerals, config.currencySymbol)} / ${scannedProduct.unitName}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "মজুদ স্টক: ${Formatters.formatQty(scannedProduct.stockQty, scannedProduct.unitName, config.useBengaliNumerals)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (scannedProduct.stockQty <= scannedProduct.minStock) MaterialTheme.dokanColors.warning else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = if (scannedProduct.stockQty <= scannedProduct.minStock) FontWeight.Bold else FontWeight.Normal
                                )
                            }

                            Spacer(modifier = Modifier.height(Spacing.xs))
                            HorizontalDivider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                            Spacer(modifier = Modifier.height(Spacing.xs))

                            // QUANTITY CONTROLLER ROW RIGHT HERE!
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "কার্টে পরিমাণ:",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    // Minus button
                                    Surface(
                                        onClick = {
                                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                            val currentQ = inCartItem?.qty ?: 1.0
                                            val step = if (currentQ <= 0.05) 0.001 else if (currentQ <= 0.25) 0.05 else if (currentQ <= 1.0) 0.25 else 1.0
                                            val newQ = (currentQ - step).coerceAtLeast(0.0)
                                            val rounded = kotlin.math.round(newQ * 1000) / 1000.0
                                            onQtyChange?.invoke(scannedProduct.id, rounded)
                                        },
                                        shape = RoundedCornerShape(Radius.xs),
                                        color = MaterialTheme.dokanColors.surfaceAlt,
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(Icons.Default.Remove, contentDescription = "কমান", modifier = Modifier.size(16.dp))
                                        }
                                    }

                                    // Quantity pill
                                    Surface(
                                        onClick = {
                                            if (onOpenWeightDialog != null) {
                                                onOpenWeightDialog(scannedProduct)
                                            }
                                        },
                                        shape = RoundedCornerShape(Radius.xs),
                                        color = MaterialTheme.colorScheme.surface,
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                                        modifier = Modifier.padding(horizontal = 6.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                        ) {
                                            Text(
                                                text = inCartItem?.let {
                                                    Formatters.formatWeightDetailed(it.qty, it.unitName, config.useBengaliNumerals)
                                                } ?: "১ ${scannedProduct.unitName}",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            if (scannedProduct.unitName.trim() == "কেজি") {
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Icon(
                                                    imageVector = Icons.Default.Edit,
                                                    contentDescription = "ওজন পরিবর্তন",
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(12.dp)
                                                )
                                            }
                                        }
                                    }

                                    // Plus button
                                    Surface(
                                        onClick = {
                                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                            val currentQ = inCartItem?.qty ?: 1.0
                                            val step = if (currentQ < 0.05) 0.001 else if (currentQ < 0.25) 0.05 else if (currentQ < 1.0) 0.25 else 1.0
                                            val newQ = currentQ + step
                                            val rounded = kotlin.math.round(newQ * 1000) / 1000.0
                                            onQtyChange?.invoke(scannedProduct.id, rounded)
                                        },
                                        shape = RoundedCornerShape(Radius.xs),
                                        color = MaterialTheme.dokanColors.surfaceAlt,
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(Icons.Default.Add, contentDescription = "বাড়ান", modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else if (lastScannedBarcode != null && products.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(Spacing.sm))
                    Surface(
                        shape = RoundedCornerShape(Radius.md),
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(Spacing.sm),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(Spacing.xs))
                            Column {
                                Text(
                                    text = "বারকোড / QR: $lastScannedBarcode",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.error
                                )
                                Text(
                                    text = "দোকানের তালিকায় এই কোডের কোনো পণ্য পাওয়া যায়নি!",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // Recent Scanned Items in Continuous Session
                val otherScannedProducts = remember(scannedHistory, products, scannedProduct) {
                    scannedHistory.filter { it != scannedProduct?.id }.mapNotNull { id -> products.find { it.id == id } }
                }
                if (otherScannedProducts.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(Spacing.xs))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "পূর্ববর্তী স্ক্যানকৃত পণ্যসমূহ:",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                    ) {
                        otherScannedProducts.forEach { prevProd ->
                            val prevCartItem = cartItems.find { it.productId == prevProd.id }
                            Surface(
                                onClick = {
                                    lastScannedBarcode = prevProd.barcode
                                },
                                shape = RoundedCornerShape(Radius.pill),
                                color = MaterialTheme.dokanColors.surfaceAlt,
                                border = BorderStroke(1.dp, MaterialTheme.dokanColors.border)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${prevProd.nameBn}: ${prevCartItem?.let { Formatters.formatWeightDetailed(it.qty, it.unitName, config.useBengaliNumerals) } ?: "১টি"}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                }

                // Live Cart Summary Strip (shown in continuous mode when cart details are provided)
                if (isContinuousMode && cartItemCount != null) {
                    Spacer(modifier = Modifier.height(Spacing.sm))
                    Surface(
                        shape = RoundedCornerShape(Radius.md),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "কার্টে মোট: $cartItemCount টি আইটেম",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                if (!cartTotalText.isNullOrBlank()) {
                                    Text(
                                        text = "মোট বিল: $cartTotalText",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Button(
                                onClick = onDismiss,
                                shape = RoundedCornerShape(Radius.pill),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Icon(Icons.Default.DoneAll, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("সম্পন্ন")
                            }
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
        val reticleWidth = size.width * 0.78f
        val reticleHeight = size.height * 0.70f
        val left = (size.width - reticleWidth) / 2f
        val top = (size.height - reticleHeight) / 2f
        val cornerLength = minOf(16.dp.toPx(), reticleHeight * 0.35f)
        val strokeWidth = 2.5.dp.toPx()
        val cornerColor = Color(0xFF22C55E) // Bright Green

        // Dimmed mask outside the reticle box so the user clearly sees that only the center frame is active
        val scrimColor = Color.Black.copy(alpha = 0.45f)
        // Top darkened strip
        drawRect(scrimColor, Offset(0f, 0f), Size(size.width, top))
        // Bottom darkened strip
        drawRect(scrimColor, Offset(0f, top + reticleHeight), Size(size.width, size.height - (top + reticleHeight)))
        // Left darkened strip
        drawRect(scrimColor, Offset(0f, top), Size(left, reticleHeight))
        // Right darkened strip
        drawRect(scrimColor, Offset(left + reticleWidth, top), Size(size.width - (left + reticleWidth), reticleHeight))

        // Guide border around the reticle box
        drawRect(
            color = Color.White.copy(alpha = 0.25f),
            topLeft = Offset(left, top),
            size = Size(reticleWidth, reticleHeight),
            style = Stroke(width = 1.dp.toPx())
        )

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
        drawLine(cornerColor, Offset(left + reticleWidth, top + reticleHeight), Offset(left + reticleWidth, top + cornerLength), strokeWidth)

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
    resetTrigger: Int = 0,
    onBarcodeFound: (String) -> Unit,
    onCameraReady: (Camera) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val currentOnBarcodeFound by rememberUpdatedState(onBarcodeFound)

    // Lock to enforce:
    // 1) Exactly 1 beep + 1 cart entry per barcode presentation
    // 2) Never continuously add while held pointing at the same barcode
    val activeBarcodeLock = remember { AtomicReference<String?>(null) }
    val lastScanTimestamp = remember { AtomicLong(0L) }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
        }
    }

    LaunchedEffect(resetTrigger) {
        activeBarcodeLock.set(null)
        lastScanTimestamp.set(0L)
    }

    AndroidView(
        factory = { ctx ->
            // Use COMPATIBLE implementation mode so PreviewView uses TextureView instead of SurfaceView.
            // SurfaceView punches through the window layer and cannot be clipped by Compose Modifier.clip/clipToBounds.
            // TextureView respects Compose clipping, rounded corners, and dialog boundaries cleanly.
            val previewView = PreviewView(ctx).apply {
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                scaleType = PreviewView.ScaleType.FILL_CENTER
                clipToOutline = true
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }

            var boundCamera: Camera? = null

            // Tap-to-focus on camera viewfinder
            previewView.setOnTouchListener { v, event ->
                if (event.action == MotionEvent.ACTION_UP) {
                    try {
                        val factory = previewView.meteringPointFactory
                        val point = factory.createPoint(event.x, event.y)
                        val action = FocusMeteringAction.Builder(
                            point,
                            FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE
                        ).setAutoCancelDuration(3, TimeUnit.SECONDS).build()
                        boundCamera?.cameraControl?.startFocusAndMetering(action)
                        v.performClick()
                    } catch (_: Exception) {}
                }
                true
            }

            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

            // ML Kit barcode scanner supporting ALL formats (QR codes, EAN-13, UPC, Code 128, Code 39, Data Matrix, etc.)
            val barcodeScannerOptions = BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
                .build()
            val barcodeScanner = BarcodeScanning.getClient(barcodeScannerOptions)

            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()

                // Target 720p resolution for high definition, crystal clear QR/barcode recognition
                val preview = Preview.Builder()
                    .setTargetResolution(Size(1280, 720))
                    .build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }

                val imageAnalysis = ImageAnalysis.Builder()
                    .setTargetResolution(Size(1280, 720))
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                    val mediaImage = imageProxy.image
                    if (mediaImage != null) {
                        val rotationDegrees = imageProxy.imageInfo.rotationDegrees
                        val inputImage = InputImage.fromMediaImage(
                            mediaImage,
                            rotationDegrees
                        )

                        barcodeScanner.process(inputImage)
                            .addOnSuccessListener { barcodes ->
                                if (barcodes.isNullOrEmpty()) {
                                    val now = System.currentTimeMillis()
                                    if (now - lastScanTimestamp.get() > 1200L) {
                                        activeBarcodeLock.set(null)
                                    }
                                    return@addOnSuccessListener
                                }

                                // Extract all detected values cleanly (both rawValue and displayValue for QR codes)
                                val candidates = barcodes.mapNotNull { b ->
                                    val raw = b.rawValue?.trim()?.ifBlank { null }
                                        ?: b.displayValue?.trim()?.ifBlank { null }
                                    if (raw != null) Pair(b, raw) else null
                                }

                                if (candidates.isEmpty()) return@addOnSuccessListener

                                // If multiple barcodes/QRs are detected, pick the one closest to center
                                val chosen = if (candidates.size == 1) {
                                    candidates.first()
                                } else {
                                    val imgW = if (rotationDegrees == 90 || rotationDegrees == 270) inputImage.height else inputImage.width
                                    val imgH = if (rotationDegrees == 90 || rotationDegrees == 270) inputImage.width else inputImage.height
                                    candidates.minByOrNull { (b, _) ->
                                        val box = b.boundingBox
                                        if (box != null && imgW > 0 && imgH > 0) {
                                            val cx = box.centerX().toFloat() / imgW.toFloat() - 0.5f
                                            val cy = box.centerY().toFloat() / imgH.toFloat() - 0.5f
                                            cx * cx + cy * cy
                                        } else {
                                            0f
                                        }
                                    } ?: candidates.first()
                                }

                                val raw = chosen.second
                                val now = System.currentTimeMillis()

                                // ATOMIC REPEATED SCAN LOCK:
                                // If camera is currently pointed at this same barcode, do not trigger again within 1.5s
                                val currentLocked = activeBarcodeLock.get()
                                if (currentLocked == raw && (now - lastScanTimestamp.get()) < 1500L) {
                                    return@addOnSuccessListener
                                }

                                activeBarcodeLock.set(raw)
                                lastScanTimestamp.set(now)

                                mainHandler.post {
                                    currentOnBarcodeFound(raw)
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
                    boundCamera = camera
                    onCameraReady(camera)
                } catch (_: Exception) {}
            }, ContextCompat.getMainExecutor(ctx))

            previewView
        },
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds()
    )
}

/**
 * Compact, always-on camera barcode scanner frame designed to sit right at the top
 * of product addition forms.
 */
@Composable
fun CompactCameraBarcodeScanner(
    currentBarcode: String,
    existingProducts: List<Product> = emptyList(),
    config: ShopConfig = ShopConfig(),
    onBarcodeScanned: (String) -> Unit,
    modifier: Modifier = Modifier
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

    var isTorchOn by remember { mutableStateOf(false) }
    var cameraInstance by remember { mutableStateOf<Camera?>(null) }
    var lastCode by remember { mutableStateOf("") }
    var lastScannedTimestamp by remember { mutableLongStateOf(0L) }
    var lastScannedBarcode by remember { mutableStateOf<String?>(null) }
    var resetScanTrigger by remember { mutableIntStateOf(0) }
    var isCameraActive by remember { mutableStateOf(true) }
    val displayedBarcode = lastScannedBarcode ?: if (currentBarcode.isNotBlank()) currentBarcode else null

    Column(modifier = modifier.fillMaxWidth()) {
        if (!hasCameraPermission) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp),
                shape = RoundedCornerShape(Radius.md),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(Spacing.sm),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "বারকোড বা QR স্ক্যান করতে ক্যামেরা ব্যবহারের অনুমতি দিন",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Button(
                        onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                        shape = RoundedCornerShape(Radius.pill),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text("অনুমতি দিন", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        } else if (!isCameraActive) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp),
                shape = RoundedCornerShape(Radius.md),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = Spacing.md),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.VideocamOff,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(Spacing.sm))
                        Column {
                            Text(
                                text = "ক্যামেরা স্ক্যানার পজ করা আছে",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "ক্যামেরা চালু করতে ডানপাশের বাটনে চাপুন",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    IconButton(onClick = { isCameraActive = true }) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "চালু করুন",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        } else {
            // Live Compact Camera Frame (140dp)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .clip(RoundedCornerShape(Radius.md))
                    .clipToBounds()
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                CameraPreviewWithAnalyzer(
                    resetTrigger = resetScanTrigger,
                    onBarcodeFound = { code ->
                        val trimmed = code.trim()
                        val currentTime = System.currentTimeMillis()
                        if (currentTime - lastScannedTimestamp >= 500L) {
                            lastScannedTimestamp = currentTime
                            lastCode = trimmed
                            lastScannedBarcode = trimmed

                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            try {
                                val toneGen = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
                                toneGen.startTone(ToneGenerator.TONE_PROP_BEEP, 130)
                            } catch (_: Exception) {}

                            onBarcodeScanned(trimmed)
                        }
                    },
                    onCameraReady = { cam ->
                        cameraInstance = cam
                    }
                )

                // Compact Reticle Overlay
                ScannerReticleOverlay()

                // Top Floating Status & Control Strip
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Live Status Pill
                    Surface(
                        shape = RoundedCornerShape(Radius.pill),
                        color = Color.Black.copy(alpha = 0.55f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF22C55E))
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "লাইভ স্ক্যানার",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Torch & Pause Controls
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(
                            onClick = {
                                val newState = !isTorchOn
                                isTorchOn = newState
                                cameraInstance?.cameraControl?.enableTorch(newState)
                            },
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.55f))
                        ) {
                            Icon(
                                imageVector = if (isTorchOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                                contentDescription = "টর্চ",
                                tint = if (isTorchOn) Color.Yellow else Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        IconButton(
                            onClick = { isCameraActive = false },
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.55f))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Pause,
                                contentDescription = "পজ",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                // Success Badge
                if (displayedBarcode != null) {
                    Surface(
                        shape = RoundedCornerShape(Radius.pill),
                        color = Color(0xFF16A34A),
                        shadowElevation = 4.dp,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 6.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "✓ বারকোড: $displayedBarcode",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "💡 পণ্যের বারকোডটি ফ্রেমের সামনে ধরলে কোডটি নিজে থেকেই বসে যাবে",
            style = MaterialTheme.typography.labelSmall,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        val existingProduct = remember(displayedBarcode, existingProducts) {
            if (!displayedBarcode.isNullOrBlank()) {
                existingProducts.find { it.barcode == displayedBarcode }
            } else null
        }
        if (existingProduct != null) {
            Spacer(modifier = Modifier.height(6.dp))
            Surface(
                shape = RoundedCornerShape(Radius.sm),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Inventory2,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Column {
                        Text(
                            text = "দোকানে বিদ্যমান: ${existingProduct.nameBn}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "বর্তমান মজুদ: ${Formatters.formatQty(existingProduct.stockQty, existingProduct.unitName, config.useBengaliNumerals)} • মূল্য: ${Formatters.formatMoney(existingProduct.salePricePoisha, config.useBengaliNumerals, config.currencySymbol)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
