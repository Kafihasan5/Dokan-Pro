package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.PaponViewModel
import com.example.ui.components.DokanPrimaryButton
import com.example.ui.components.DokanSecondaryButton
import com.example.ui.components.DokanTextField
import com.example.ui.theme.*

@Composable
fun AppActivationScreen(
    viewModel: PaponViewModel
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    var emailInput by remember { mutableStateOf("") }
    val isActivating by viewModel.isActivating.collectAsState()
    val activationError by viewModel.activationError.collectAsState()
    val isDemoUsed by viewModel.isDemoUsed.collectAsState()
    val isDemoExpired by viewModel.isDemoExpired.collectAsState()
    val deviceId = remember { viewModel.getDeviceId() }
    var copiedRecently by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brand900)
    ) {
        // Subtle radial glow drawn in Canvas at the top center
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(Brand500.copy(alpha = 0.32f), Color.Transparent),
                    center = Offset(size.width / 2f, size.height * 0.18f),
                    radius = size.width * 0.85f
                )
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(scrollState)
                .padding(horizontal = Spacing.lg, vertical = Spacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Spacer(modifier = Modifier.height(Spacing.sm))

            // Centered 96dp app mark
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(
                                Brand700,
                                Brand500
                            )
                        )
                    )
                    .border(1.5.dp, Brand500.copy(alpha = 0.6f), RoundedCornerShape(26.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.VerifiedUser,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(52.dp)
                )
            }

            // Title & Subtitle
            Text(
                text = "Dokan-Pro অ্যাক্টিভেশন",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White
            )

            Surface(
                shape = RoundedCornerShape(Radius.pill),
                color = Brand500.copy(alpha = 0.22f),
                border = BorderStroke(1.dp, Brand500.copy(alpha = 0.45f))
            ) {
                Text(
                    text = "Webix Solution Official Software",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Brand500,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                )
            }

            Text(
                text = "অ্যাপটি চালাতে ক্রয়কৃত ইমেইল দিয়ে অ্যাক্টিভ করুন অথবা ১ ঘণ্টার ফ্রি ডেমো টেস্ট করে দেখুন। সক্রিয় হওয়ার পর সম্পূর্ণ অ্যাপটি অফলাইনে চালানো যাবে।",
                fontSize = 13.sp,
                color = Color.White.copy(alpha = 0.82f),
                textAlign = TextAlign.Center,
                lineHeight = 19.sp,
                modifier = Modifier.padding(horizontal = Spacing.sm)
            )

            // 1-Hour Free Demo Card
            if (!isDemoUsed || !isDemoExpired) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(Radius.lg),
                    colors = CardDefaults.cardColors(
                        containerColor = Brand700.copy(alpha = 0.35f)
                    ),
                    border = BorderStroke(1.5.dp, Brand500.copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier.padding(Spacing.md),
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Timer,
                                contentDescription = null,
                                tint = Brand500,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "১ ঘণ্টার ফ্রি ডেমো টেস্ট করুন",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            Surface(
                                shape = RoundedCornerShape(Radius.xs),
                                color = Brand500
                            ) {
                                Text(
                                    text = "ফ্রি ট্রায়াল",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }

                        Text(
                            text = "লাইসেন্স কেনার আগে Dokan-Pro এর সকল ফিচার সরাসরি পরীক্ষা করুন! এতে ৭ দিনের বাস্তবসম্মত পণ্য, বেচাকেনা ও বাকির খাতার ডামি ডাটা সংযুক্ত থাকবে।",
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.78f),
                            lineHeight = 17.sp
                        )

                        DokanPrimaryButton(
                            text = if (isActivating) "যাচাই করা হচ্ছে..." else "১ ঘণ্টার ফ্রি ডেমো শুরু করুন (৭ দিনের ডাটা সহ)",
                            isLoading = isActivating,
                            enabled = !isActivating,
                            onClick = { viewModel.startOneHourDemo() }
                        )
                    }
                }
            } else {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(Radius.md),
                    colors = CardDefaults.cardColors(
                        containerColor = StatusDanger.copy(alpha = 0.15f)
                    ),
                    border = BorderStroke(1.dp, StatusDanger.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier.padding(Spacing.md),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = null,
                            tint = StatusDanger,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(Spacing.sm))
                        Column {
                            Text(
                                text = "১ ঘণ্টার ফ্রি ডেমো মেয়াদ সমাপ্ত",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = StatusDanger
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "আপনার ডিভাইসে ফ্রি ডেমো সেশনটি শেষ হয়েছে। Dokan-Pro নিয়মিত ব্যবহার করতে মাত্র ৳৪৯০ টাকায় আজীবন লাইসেন্স সংগ্রহ করুন।",
                                fontSize = 11.sp,
                                color = Color.White.copy(alpha = 0.85f),
                                lineHeight = 15.sp
                            )
                        }
                    }
                }
            }

            // Email Input Box Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(Radius.lg),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    Text(
                        text = "নিবন্ধিত ইমেইল এড্রেস দিয়ে অ্যাক্টিভ করুন",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    DokanTextField(
                        value = emailInput,
                        onValueChange = {
                            emailInput = it
                            if (activationError != null) viewModel.clearActivationError()
                        },
                        label = "ইমেইল এড্রেস",
                        placeholder = "যেমন: customer@gmail.com",
                        keyboardType = KeyboardType.Email,
                        leadingIcon = Icons.Default.Email,
                        trailingIcon = {
                            if (emailInput.isNotEmpty()) {
                                IconButton(onClick = { emailInput = "" }) {
                                    Icon(
                                        Icons.Default.Clear,
                                        contentDescription = "Clear",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    )

                    // Danger-toned inline banner above button (no toast)
                    AnimatedVisibility(visible = activationError != null) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(Radius.sm),
                            color = StatusDanger.copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, StatusDanger.copy(alpha = 0.35f))
                        ) {
                            Row(
                                modifier = Modifier.padding(Spacing.sm),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ErrorOutline,
                                    contentDescription = null,
                                    tint = StatusDanger,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = activationError ?: "",
                                    fontSize = 12.sp,
                                    color = StatusDanger,
                                    lineHeight = 17.sp
                                )
                            }
                        }
                    }

                    // Activate Button
                    DokanPrimaryButton(
                        text = "অ্যাপ সক্রিয় করুন",
                        isLoading = isActivating,
                        enabled = emailInput.isNotBlank() && !isActivating,
                        onClick = {
                            keyboardController?.hide()
                            viewModel.activateApp(emailInput.trim())
                        }
                    )
                }
            }

            // Purchase Card (Webix Solution Link with 490 Tk price)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(Radius.lg),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.ShoppingCart,
                            contentDescription = null,
                            tint = Brand500,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "লাইসেন্স ক্রয় করুন (আজীবন মেয়াদ)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Surface(
                            shape = RoundedCornerShape(Radius.xs),
                            color = Color(0xFF2E7D32)
                        ) {
                            Text(
                                text = "৳৪৯০ আজীবন",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    Text(
                        text = "Webix Solution ওয়েবসাইট থেকে মাত্র ৪৯০ টাকায় Dokan-Pro এর আজীবন (Lifetime) অফিসিয়াল লাইসেন্স ক্রয় করুন। কোনো মাসিক বা বাৎসরিক চার্জ নেই।",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 17.sp
                    )

                    DokanSecondaryButton(
                        text = "ওয়েবসাইট থেকে লাইসেন্স কিনুন (৳৪৯০)",
                        onClick = {
                            try {
                                val intent = Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse("https://webixsolution.store/product/dokan-pro")
                                )
                                context.startActivity(intent)
                            } catch (_: Exception) {
                                Toast.makeText(
                                    context,
                                    "ওয়েবসাইট ব্রাউজারে খুলুন: webixsolution.store/product/dokan-pro",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    )
                }
            }

            // Monospace pill for device ID & Support Info
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(Radius.pill),
                    color = Color.White.copy(alpha = 0.08f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f)),
                    onClick = {
                        clipboardManager.setText(AnnotatedString(deviceId))
                        copiedRecently = true
                        Toast.makeText(context, "ডিভাইস আইডি কপি হয়েছে", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = if (copiedRecently) "কপি হয়েছে! ✓ $deviceId" else "ডিভাইস আইডি: $deviceId",
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = if (copiedRecently) Gold500 else Color.White.copy(alpha = 0.85f),
                                fontWeight = FontWeight.Medium
                            )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = if (copiedRecently) Icons.Default.Check else Icons.Default.ContentCopy,
                            contentDescription = "Copy Device ID",
                            modifier = Modifier.size(14.dp),
                            tint = if (copiedRecently) Gold500 else Color.White.copy(alpha = 0.7f)
                        )
                    }
                }

                Text(
                    text = "সহায়তার জন্য Webix Solution সাপোর্টে যোগাযোগ করুন",
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.5f)
                )
            }

            Spacer(modifier = Modifier.height(Spacing.lg))
        }
    }
}
