package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.view.HapticFeedbackConstants
import com.example.data.support.SupportChatMessage
import com.example.ui.PaponViewModel
import com.example.ui.ShopConfig
import com.example.ui.components.DokanPrimaryButton
import com.example.ui.components.DokanSecondaryButton
import com.example.ui.theme.*
import com.example.util.Formatters
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveSupportScreen(
    viewModel: PaponViewModel,
    config: ShopConfig,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current

    val messages by viewModel.supportMessages.collectAsState()
    val isSyncing by viewModel.isSupportSyncing.collectAsState()
    val configuredChatId by viewModel.configuredSupportChatId.collectAsState()

    var inputText by remember { mutableStateOf("") }
    var showChatIdDialog by remember { mutableStateOf(false) }
    var tempChatIdInput by remember(configuredChatId) { mutableStateOf(configuredChatId) }

    val listState = rememberLazyListState()

    // Auto-scroll to bottom when new messages arrive
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    // Periodic sync while on this screen
    LaunchedEffect(Unit) {
        while (true) {
            viewModel.syncSupportMessages()
            delay(10000) // Poll every 10s for incoming replies
        }
    }

    fun handleSend(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return

        if (configuredChatId.isBlank()) {
            showChatIdDialog = true
            return
        }

        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        inputText = ""
        keyboardController?.hide()

        viewModel.sendSupportMessage(trimmed) { success, errMsg ->
            if (!success && errMsg != null) {
                viewModel.showToast(errMsg)
            }
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .imePadding(),
        topBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 4.dp,
                shadowElevation = 2.dp,
                border = BorderStroke(1.dp, MaterialTheme.dokanColors.border)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "পিছনে যান"
                            )
                        }

                        // Avatar with active green indicator
                        Box {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.linearGradient(
                                            listOf(
                                                MaterialTheme.colorScheme.primary,
                                                MaterialTheme.colorScheme.tertiary
                                            )
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SupportAgent,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            // Online Dot
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.dokanColors.success)
                                    .align(Alignment.BottomEnd)
                            )
                        }

                        Spacer(modifier = Modifier.width(Spacing.sm))

                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "দোকান প্রো লাইভ সাপোর্ট",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Default.Verified,
                                    contentDescription = "অফিসিয়াল",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Text(
                                text = if (isSyncing) "মেসেজ সিঙ্ক হচ্ছে..." else "টেলিগ্রাম বট লাইভ কানেক্টেড",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.dokanColors.success
                            )
                        }
                    }

                    // Top Action: Direct WhatsApp / Call fallback
                    Row {
                        IconButton(
                            onClick = {
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW).apply {
                                        data = Uri.parse("https://wa.me/8801700000000") // Fallback official link
                                    }
                                    context.startActivity(intent)
                                } catch (_: Exception) {
                                    viewModel.showToast("হোয়াটসঅ্যাপ অ্যাপ খুঁজে পাওয়া যায়নি")
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Chat,
                                contentDescription = "হোয়াটসঅ্যাপ সাপোর্ট",
                                tint = MaterialTheme.dokanColors.success
                            )
                        }

                        IconButton(
                            onClick = { showChatIdDialog = true }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "টেলিগ্রাম গ্রুপ সেটিংস",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // If Group Chat ID is not configured yet, show a helpful alert banner
            if (configuredChatId.isBlank()) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.md, vertical = Spacing.xs)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(Spacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(Spacing.xs))
                            Text(
                                text = "টেলিগ্রাম গ্রুপ আইডি সেট করুন",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        TextButton(
                            onClick = { showChatIdDialog = true },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text("সেট করুন", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            // Quick Help Suggestion Chips
            val quickChips = listOf(
                "🔑 লাইসেন্স অ্যাক্টিভেশন সমস্যা",
                "💾 ক্লাউড ডাটা ব্যাকআপ সাহায্য",
                "🛒 বিক্রি বা প্রিন্টার সংক্রান্ত",
                "💡 নতুন ফিচারের অনুরোধ"
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.md, vertical = Spacing.xs),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                quickChips.forEach { chipText ->
                    Surface(
                        onClick = { handleSend(chipText) },
                        shape = RoundedCornerShape(Radius.pill),
                        color = MaterialTheme.dokanColors.surfaceAlt,
                        border = BorderStroke(1.dp, MaterialTheme.dokanColors.border)
                    ) {
                        Text(
                            text = chipText,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = 6.dp)
                        )
                    }
                }
            }

            // Chat Messages List
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (messages.isEmpty()) {
                    // Empty Greeting State
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(Spacing.xl),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Forum,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(Spacing.md))
                        Text(
                            text = "দোকান প্রো লাইভ চ্যাটে স্বাগতম!",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(Spacing.xs))
                        Text(
                            text = "আপনার যেকোনো প্রশ্ন, সমস্যা বা সহায়তার জন্য মেসেজ লিখুন। আপনার জন্য টেলিগ্রামে ডেডিকেটেড সাপোর্ট টপিক তৈরি হবে এবং দ্রুত সমাধান প্রদান করা হবে।",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = Spacing.md),
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                        contentPadding = PaddingValues(vertical = Spacing.sm)
                    ) {
                        items(messages, key = { it.id }) { msg ->
                            ChatBubbleRow(msg = msg)
                        }
                    }
                }
            }

            // Bottom Message Input Bar
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp,
                shadowElevation = 8.dp,
                border = BorderStroke(1.dp, MaterialTheme.dokanColors.border)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.md, vertical = Spacing.sm)
                        .navigationBarsPadding(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                ) {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        modifier = Modifier
                            .weight(1f)
                            .defaultMinSize(minHeight = 48.dp),
                        placeholder = {
                            Text(
                                "আপনার সমস্যার কথা লিখুন...",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        },
                        shape = RoundedCornerShape(Radius.pill),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                        ),
                        maxLines = 4
                    )

                    IconButton(
                        onClick = { handleSend(inputText) },
                        enabled = inputText.isNotBlank(),
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(
                                if (inputText.isNotBlank()) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "মেসেজ পাঠান",
                            tint = if (inputText.isNotBlank()) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    // Config Support Group ID Dialog
    if (showChatIdDialog) {
        AlertDialog(
            onDismissRequest = { showChatIdDialog = false },
            title = {
                Text(
                    text = "টেলিগ্রাম সাপোর্ট গ্রুপ কনফিগারেশন",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Text(
                        text = "আপনার যে টেলিগ্রাম গ্রুপে সাপোর্ট মেসেজ যাবে এবং টপিক তৈরি হবে, সেই গ্রুপের Chat ID (যেমন: -1002345678901) এখানে দিন:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = tempChatIdInput,
                        onValueChange = { tempChatIdInput = it.trim() },
                        label = { Text("Telegram Group Chat ID") },
                        placeholder = { Text("-100xxxxxxxxxx") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = "বট: @dokanpro_bot\nটোকেন: 8677361782:AAFs...advY",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            },
            confirmButton = {
                DokanPrimaryButton(
                    text = "সংরক্ষণ করুন",
                    onClick = {
                        viewModel.setSupportChatId(tempChatIdInput)
                        showChatIdDialog = false
                        viewModel.showToast("সাপোর্ট গ্রুপ আইডি সফলভাবে সংরক্ষিত হয়েছে!")
                    }
                )
            },
            dismissButton = {
                DokanSecondaryButton(
                    text = "বাতিল",
                    onClick = { showChatIdDialog = false }
                )
            }
        )
    }
}

@Composable
private fun ChatBubbleRow(msg: SupportChatMessage) {
    val isUser = msg.sender == "user"
    val timeStr = remember(msg.timestamp) {
        SimpleDateFormat("hh:mm a", Locale.ENGLISH).format(Date(msg.timestamp))
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        if (!isUser) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.SupportAgent,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(modifier = Modifier.width(Spacing.xs))
        }

        Surface(
            shape = RoundedCornerShape(
                topStart = Radius.md,
                topEnd = Radius.md,
                bottomStart = if (isUser) Radius.md else 2.dp,
                bottomEnd = if (isUser) 2.dp else Radius.md
            ),
            color = if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.dokanColors.surfaceAlt,
            border = BorderStroke(
                1.dp,
                if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.dokanColors.border
            ),
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm)) {
                if (!isUser) {
                    Text(
                        text = "ডোকান প্রো সাপোর্ট টিম",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                }

                Text(
                    text = msg.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(2.dp))

                Row(
                    modifier = Modifier.align(Alignment.End),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = timeStr,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isUser) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        fontSize = 10.sp
                    )

                    if (isUser) {
                        Spacer(modifier = Modifier.width(2.dp))
                        Icon(
                            imageVector = when {
                                msg.isSending -> Icons.Default.AccessTime
                                msg.isFailed -> Icons.Default.Error
                                else -> Icons.Default.DoneAll
                            },
                            contentDescription = null,
                            tint = if (msg.isFailed) MaterialTheme.dokanColors.danger
                            else MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f),
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }
        }
    }
}
