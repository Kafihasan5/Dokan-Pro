package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.entity.Expense
import com.example.data.entity.ExpenseCategory
import com.example.ui.PaponViewModel
import com.example.ui.ShopConfig
import com.example.ui.components.*
import com.example.ui.theme.*
import com.example.util.Formatters

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ExpensesScreen(
    viewModel: PaponViewModel,
    config: ShopConfig,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val expenses by viewModel.expenses.collectAsState()
    val expenseCategories by viewModel.expenseCategories.collectAsState()

    var showAddBottomSheet by remember { mutableStateOf(false) }
    var expenseToDelete by remember { mutableStateOf<Expense?>(null) }

    val totalExpensePoisha = remember(expenses) { expenses.sumOf { it.amountPoisha } }

    // Horizontal category summary computed from loaded data
    val categoryTotals = remember(expenses) {
        expenses.groupBy { it.categoryName }.mapValues { entry ->
            entry.value.sumOf { it.amountPoisha }
        }
    }

    // Group expenses by Bengali formatted date
    val groupedExpenses = remember(expenses) {
        expenses.sortedByDescending { it.expenseDate }.groupBy { exp ->
            Formatters.formatBengaliDate(exp.expenseDate)
        }
    }

    DokanScreenScaffold(
        title = "দোকানের খরচ ব্যবস্থাপনা",
        onBack = onBack,
        floatingAction = {
            ExtendedFloatingActionButton(
                onClick = { showAddBottomSheet = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("নতুন খরচ", style = MaterialTheme.typography.labelLarge) },
                containerColor = MaterialTheme.dokanColors.danger,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(Radius.pill),
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }
    ) {
        // 1) Single surfaceAlt summary strip at the top
        Surface(
            color = MaterialTheme.dokanColors.surfaceAlt,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "সর্বমোট খরচ",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = Formatters.formatMoney(totalExpensePoisha, config.useBengaliNumerals, config.currencySymbol),
                            style = amountTextStyle(20.sp),
                            color = MaterialTheme.dokanColors.danger
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "মোট এন্ট্রি",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${if (config.useBengaliNumerals) Formatters.toBengaliDigits(expenses.size.toString()) else expenses.size} টি",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                HorizontalDivider(thickness = 1.dp, color = MaterialTheme.dokanColors.border)
            }
        }

        // Horizontal Category Summary Chips
        if (categoryTotals.isNotEmpty()) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                items(categoryTotals.entries.toList()) { entry ->
                    Surface(
                        shape = RoundedCornerShape(Radius.pill),
                        color = MaterialTheme.dokanColors.surfaceAlt,
                        border = BorderStroke(1.dp, MaterialTheme.dokanColors.border)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = entry.key,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(Spacing.xs))
                            Text(
                                text = Formatters.formatMoney(entry.value, config.useBengaliNumerals, config.currencySymbol),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.dokanColors.danger
                            )
                        }
                    }
                }
            }
        }

        // Expenses List or EmptyState
        if (expenses.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                EmptyState(
                    icon = Icons.Default.ReceiptLong,
                    title = "কোনো খরচ এন্ট্রি করা হয়নি",
                    message = "দোকানের দৈনন্দিন খরচ রেকর্ড করতে নিচের বাটনে চাপ দিন।",
                    actionLabel = "নতুন খরচ",
                    onAction = { showAddBottomSheet = true }
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(
                    start = Spacing.lg,
                    end = Spacing.lg,
                    bottom = 120.dp
                ),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                groupedExpenses.forEach { (dateStr, dateExpenses) ->
                    stickyHeader {
                        Surface(
                            color = MaterialTheme.colorScheme.background,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = dateStr,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(vertical = Spacing.xs)
                            )
                        }
                    }

                    items(dateExpenses, key = { it.id }) { expense ->
                        ExpenseRowCard(
                            expense = expense,
                            config = config,
                            onDeleteClick = { expenseToDelete = expense }
                        )
                    }
                }
            }
        }
    }

    // Delete Confirmation Dialog
    expenseToDelete?.let { exp ->
        DokanConfirmDialog(
            title = "খরচ মুছুন",
            message = "এই খরচের রেকর্ডটি স্থায়ীভাবে মুছে ফেলতে চান?",
            confirmLabel = "মুছুন",
            onConfirm = {
                viewModel.deleteExpense(exp.id)
                expenseToDelete = null
            },
            onDismiss = { expenseToDelete = null },
            isDestructive = true
        )
    }

    // Modal Bottom Sheet: Add Expense
    if (showAddBottomSheet) {
        AddExpenseBottomSheet(
            categories = expenseCategories,
            onDismiss = { showAddBottomSheet = false },
            onSave = { catId, catName, amount, note ->
                viewModel.addExpense(catId, catName, amount, note) {
                    showAddBottomSheet = false
                }
            }
        )
    }
}

// ==============================================================================
// EXPENSE ROW CARD
// ==============================================================================
@Composable
private fun ExpenseRowCard(
    expense: Expense,
    config: ShopConfig,
    onDeleteClick: () -> Unit
) {
    val categoryIcon = getCategoryIcon(expense.categoryName)

    Surface(
        shape = RoundedCornerShape(Radius.md),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .softShadow(1, RoundedCornerShape(Radius.md))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 36dp circular category icon tile on soft tone
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.dokanColors.dangerContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = categoryIcon,
                    contentDescription = null,
                    tint = MaterialTheme.dokanColors.danger,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.width(Spacing.md))

            // Details
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = expense.categoryName,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
                if (!expense.note.isNullOrBlank()) {
                    Text(
                        text = expense.note,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = Formatters.formatDateTime(expense.expenseDate, config.useBengaliNumerals),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }

            Spacer(modifier = Modifier.width(Spacing.sm))

            // Amount in amountTextStyle(17.sp) in danger colour
            Text(
                text = "-${Formatters.formatMoney(expense.amountPoisha, config.useBengaliNumerals, config.currencySymbol)}",
                style = amountTextStyle(17.sp),
                color = MaterialTheme.dokanColors.danger
            )

            Spacer(modifier = Modifier.width(Spacing.xs))

            IconButton(
                onClick = onDeleteClick,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = "Delete Expense",
                    tint = MaterialTheme.dokanColors.danger.copy(alpha = 0.7f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

// ==============================================================================
// ADD EXPENSE BOTTOM SHEET (ModalBottomSheet with sections)
// ==============================================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddExpenseBottomSheet(
    categories: List<ExpenseCategory>,
    onDismiss: () -> Unit,
    onSave: (Long, String, Long, String?) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var amountText by remember { mutableStateOf("") }
    var noteText by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(categories.firstOrNull()) }

    val amtDouble = amountText.toDoubleOrNull() ?: 0.0
    val poisha = (amtDouble * 100).toLong()
    val isValid = poisha > 0 && selectedCategory != null

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = Radius.lg, topEnd = Radius.lg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "নতুন খরচ এন্ট্রি",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "বাতিল")
                }
            }

            HorizontalDivider(color = MaterialTheme.dokanColors.border)

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.lg, vertical = Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.lg)
            ) {
                // Section: খরচের বিবরণ
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    SectionHeader(title = "খরচের বিবরণ")

                    // Amount TextField with ৳ prefix
                    DokanTextField(
                        value = amountText,
                        onValueChange = { amountText = it.filter { c -> c.isDigit() || c == '.' } },
                        label = "টাকার পরিমাণ (৳) *",
                        placeholder = "যেমন: ৫০০",
                        keyboardType = KeyboardType.Decimal,
                        prefix = {
                            Text(
                                text = "৳ ",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.dokanColors.danger
                            )
                        }
                    )

                    // Category Selection Chips
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        Text(
                            text = "ক্যাটাগরি নির্বাচন করুন *",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                            items(categories) { cat ->
                                val isSelected = selectedCategory?.id == cat.id
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { selectedCategory = cat },
                                    label = { Text(cat.nameBn, style = MaterialTheme.typography.labelMedium) },
                                    shape = RoundedCornerShape(Radius.pill),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.dokanColors.dangerContainer,
                                        selectedLabelColor = MaterialTheme.dokanColors.danger
                                    )
                                )
                            }
                        }
                    }

                    // Note / Description TextField
                    DokanTextField(
                        value = noteText,
                        onValueChange = { noteText = it },
                        label = "বিবরণ / নোট (ঐচ্ছিক)",
                        placeholder = "যেমন: চলতি মাসের দোকান ভাড়া",
                        keyboardType = KeyboardType.Text
                    )
                }
            }

            // Bottom Save Action Strip
            Surface(
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.dokanColors.border),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(Spacing.lg),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md)
                ) {
                    DokanSecondaryButton(
                        text = "বাতিল",
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    )

                    DokanDangerButton(
                        text = "খরচ সংরক্ষণ",
                        onClick = {
                            if (isValid && selectedCategory != null) {
                                onSave(selectedCategory!!.id, selectedCategory!!.nameBn, poisha, noteText.ifBlank { null })
                            }
                        },
                        enabled = isValid,
                        modifier = Modifier.weight(1.5f)
                    )
                }
            }
        }
    }
}

private fun getCategoryIcon(categoryName: String): ImageVector {
    return when {
        categoryName.contains("ভাড়া") -> Icons.Default.Storefront
        categoryName.contains("বিদ্যুৎ") || categoryName.contains("কারেন্ট") -> Icons.Default.ElectricBolt
        categoryName.contains("পরিবহন") || categoryName.contains("গাড়ি") -> Icons.Default.LocalShipping
        categoryName.contains("বেতন") || categoryName.contains("স্টাফ") -> Icons.Default.Badge
        categoryName.contains("নাস্তা") || categoryName.contains("খাবার") -> Icons.Default.Fastfood
        categoryName.contains("চা") -> Icons.Default.Coffee
        else -> Icons.Default.ReceiptLong
    }
}
