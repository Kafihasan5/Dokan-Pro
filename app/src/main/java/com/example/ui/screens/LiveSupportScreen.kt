package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import com.example.ui.theme.*
import kotlinx.coroutines.delay
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
    val view = LocalView.current
    val keyboardController = LocalSoftwareKeyboardController.current

    val messages by viewModel.supportMessages.collectAsState()
    val isSyncing by viewModel.isSupportSyncing.collectAsState()

    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    val imeBottom = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
    val isKeyboardOpen = imeBottom > 0.dp

    DisposableEffect(Unit) {
        com.example.data.support.TelegramSupportManager.isLiveSupportActive = true
        onDispose {
            com.example.data.support.TelegramSupportManager.isLiveSupportActive = false
        }
    }

    // Auto-scroll to bottom when new messages arrive or keyboard opens
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    LaunchedEffect(isKeyboardOpen) {
        if (isKeyboardOpen && messages.isNotEmpty()) {
            delay(100)
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    // Periodic sync every 3s while active on this screen for instant incoming replies
    LaunchedEffect(Unit) {
        viewModel.markAllNotificationsRead()
        viewModel.syncSupportMessages()
        while (true) {
            delay(3000)
            viewModel.syncSupportMessages()
        }
    }

    fun handleSend(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return

        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        inputText = ""

        viewModel.sendSupportMessage(trimmed) { success, errMsg ->
            if (!success && errMsg != null) {
                viewModel.showToast(errMsg)
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
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
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "পিছনে যান"
                        )
                    }

                    Spacer(modifier = Modifier.width(Spacing.xs))

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
                            text = if (isSyncing) "মেসেজ সিঙ্ক হচ্ছে..." else "অনলাইন সাপোর্ট অ্যাক্টিভ",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.dokanColors.success
                        )
                    }
                }
            }
        },
        bottomBar = {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (isKeyboardOpen) Modifier.imePadding()
                        else Modifier
                    ),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp,
                shadowElevation = 8.dp,
                border = BorderStroke(1.dp, MaterialTheme.dokanColors.border)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (isKeyboardOpen) Modifier
                            else Modifier.navigationBarsPadding()
                        )
                        .padding(horizontal = Spacing.md, vertical = Spacing.sm),
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
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
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
                            text = "আপনার যেকোনো প্রশ্ন, সমস্যা বা সহায়তার জন্য মেসেজ লিখুন। আমাদের সাপোর্ট প্রতিনিধি দ্রুত আপনার প্রশ্নের উত্তর প্রদান করবেন।",
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
        }
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
                        text = "দোকান প্রো সাপোর্ট টিম",
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
