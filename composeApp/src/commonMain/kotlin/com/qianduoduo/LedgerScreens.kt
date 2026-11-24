package com.qianduoduo

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlin.math.roundToInt

@Composable
internal fun IconAction(icon: ImageVector, title: String, onClick: () -> Unit) {
    IconButton(onClick) { Icon(icon, contentDescription = title) }
}

@Composable
internal fun ScreenHeader(title: String, onBack: () -> Unit, close: Boolean = false, action: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconAction(if (close) Icons.Default.Close else Icons.AutoMirrored.Filled.ArrowBack, if (close) "关闭" else "返回", onBack)
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        action()
    }
}

@Composable
internal fun LoadFailure(onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("无法打开本地账本")
            TextButton(onRetry) { Text("重试") }
        }
    }
}

@Composable
internal fun TimelineScreen(
    data: LedgerSnapshot,
    period: LedgerPeriod,
    demo: Boolean,
    onPeriod: (LedgerPeriod) -> Unit,
    onFilter: () -> Unit,
    onAccounts: () -> Unit,
    onSearch: () -> Unit,
    onInsights: () -> Unit,
    onExpense: (ExpenseGroup) -> Unit,
    onEdit: (TransactionRecord?) -> Unit,
) {
    val today = today()
    val entries = remember(data.txns, period, today) { data.forPeriod(period, today) }
    val groups = remember(entries) { entries.groupBy { localDateTime(it.at).date } }
    val top = remember(data.categories, entries) { data.expenseBreakdown(entries).take(3) }
    val expense = remember(entries) { entries.sumOf { if (it.type == TxnType.EXPENSE) it.cents else 0L } }
    val income = remember(entries) { entries.sumOf { if (it.type == TxnType.INCOME) it.cents else 0L } }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                text = { Text("记一笔") },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                onClick = { onEdit(null) },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        },
    ) { inset ->
        Column(Modifier.fillMaxSize().padding(inset)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text("钱多多", color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    if (demo) Text("演示账本 · 模拟数据", color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall, maxLines = 1)
                }
                TextButton(onClick = onInsights) { Text("统计") }
                IconAction(Icons.Default.Search, "搜索流水", onSearch)
                IconAction(WalletIcon, "账户", onAccounts)
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (period.selectedMonth(today) != null) {
                    IconAction(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "上一月") { onPeriod(period.shiftMonth(-1, today)) }
                }
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    TextButton(onFilter) {
                        Text(period.label(today), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, Modifier.size(18.dp))
                    }
                }
                if (period.selectedMonth(today) != null) {
                    IconAction(Icons.AutoMirrored.Filled.KeyboardArrowRight, "下一月") { onPeriod(period.shiftMonth(1, today)) }
                }
            }
            if (entries.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("这个时间段还没有账目", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("点右下角「记一笔」开始", color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
            } else LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
                item("summary") { SummaryCard(expense, income) }
                if (top.isNotEmpty()) item("top-categories") { CategoryRanking(top, expense, onExpense) }
                transactionItems(groups, data, today, onEdit = onEdit)
            }
        }
    }
}

@Composable
internal fun SummaryCard(expense: Long, income: Long) {
    val colors = LocalMoneyColors.current
    val theme = MaterialTheme.colorScheme
    Card(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = theme.primaryContainer, contentColor = theme.onPrimaryContainer),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 14.dp)) {
            Text("本期收支", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SummaryItem("支出", expense, colors.expense, Modifier.weight(1f))
                SummaryItem("收入", income, colors.income, Modifier.weight(1f))
                SummaryItem("结余", income - expense, theme.onPrimaryContainer, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun SummaryItem(label: String, amount: Long, amountColor: Color, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.Start) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(4.dp))
        Text(Money.format(amount, showSymbol = false), color = amountColor, style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun CategoryRanking(values: List<ExpenseTotal>, expense: Long, onExpense: (ExpenseGroup) -> Unit) {
    Card(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow, contentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Column(Modifier.padding(vertical = 8.dp)) {
            Text("支出分类 Top 3", Modifier.padding(start = 12.dp, top = 4.dp, bottom = 2.dp),
                style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            values.forEach { total -> ExpenseBreakdownRow(total, expense) { onExpense(total.group) } }
        }
    }
}

@Composable
internal fun ExpenseBreakdownRow(total: ExpenseTotal, expense: Long, onClick: () -> Unit) {
    val ratio = if (expense > 0) (total.cents.toDouble() / expense).coerceIn(0.0, 1.0) else 0.0
    val percent = if (ratio > 0.0 && ratio < 0.01) "<1%" else "${(ratio * 100).roundToInt()}%"
    Column(
        Modifier.fillMaxWidth().clickable(role = Role.Button, onClickLabel = "查看${total.group.label}明细", onClick = onClick)
            .heightIn(min = 48.dp).padding(horizontal = 12.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterVertically),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(total.group.label, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
            Text(Money.format(total.cents), style = MaterialTheme.typography.bodySmall)
            Text(percent, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Box(
            Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        ) {
            Box(Modifier.fillMaxWidth(ratio.toFloat()).fillMaxHeight()
                .clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.primary))
        }
    }
}

internal fun LazyListScope.transactionItems(
    groups: Map<LocalDate, List<TransactionRecord>>,
    data: LedgerSnapshot,
    today: LocalDate,
    showYear: Boolean = false,
    onEdit: (TransactionRecord) -> Unit,
) {
    val categoryMap = data.categories.associateBy { it.id }
    val accountMap = data.accounts.associateBy { it.id }
    groups.forEach { (day, txns) ->
        item(day.toString()) { DayHeader(day, today, txns, showYear) }
        items(txns, key = { it.id }) { txn ->
            val origin = accountMap[txn.account]?.name
            val destination = accountMap[txn.destination]?.name
            val accounts = if (txn.type == TxnType.TRANSFER) listOfNotNull(origin, destination).joinToString(" → ") else origin
            TransactionRow(txn, categoryMap[txn.category], accounts) { onEdit(txn) }
        }
    }
}

@Composable
private fun DayHeader(date: LocalDate, today: LocalDate, txns: List<TransactionRecord>, showYear: Boolean) {
    val title = when (date) {
        today -> "今天"
        today.plus(-1, DateTimeUnit.DAY) -> "昨天"
        else -> if (showYear) "${date.year}年${dayText(date)}" else dayText(date)
    }
    val colors = LocalMoneyColors.current
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer).padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("$title ${weekNames[date.dayOfWeek.ordinal]}", Modifier.weight(1f),
            style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        val expense = txns.filter { it.type == TxnType.EXPENSE }.sumOf { it.cents }
        val income = txns.filter { it.type == TxnType.INCOME }.sumOf { it.cents }
        if (expense > 0) Text("支出 ${Money.format(expense, false)}", color = colors.expense, style = MaterialTheme.typography.labelSmall)
        if (income > 0) Text("收入 ${Money.format(income, false)}", color = colors.income, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun TransactionRow(txn: TransactionRecord, category: LedgerCategory?, account: String?, onClick: () -> Unit) {
    val time = localDateTime(txn.at).time
    val shortTime = "${time.hour.toString().padStart(2, '0')}:${time.minute.toString().padStart(2, '0')}"
    val sign = when (txn.type) { TxnType.EXPENSE -> "-"; TxnType.INCOME -> "+"; TxnType.TRANSFER -> "" }
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        CategoryIcon(category?.icon)
        Column(Modifier.weight(1f)) {
            val memo = txn.note?.takeIf { it.isNotBlank() }
            val categoryLabel = category?.name ?: txn.type.label
            Text(memo ?: categoryLabel, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
            val detail = listOfNotNull(categoryLabel.takeIf { memo != null }, account?.takeIf { it.isNotBlank() }).joinToString(" · ")
            if (detail.isNotEmpty()) Text(detail, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(sign + Money.format(txn.cents, false), color = LocalMoneyColors.current.forType(txn.type),
                style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(shortTime, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
        }
    }
    HorizontalDivider(Modifier.padding(start = 68.dp), color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
internal fun FilterScreen(current: LedgerPeriod, onSelect: (LedgerPeriod) -> Unit, onClose: () -> Unit) {
    val today = today()
    val selected = current.selectedMonth(today)
    var year by rememberSaveable { mutableIntStateOf(selected?.year ?: today.year) }
    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        ScreenHeader("选择时间段", onClose, close = true)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("快捷区间", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FilterPreset.entries.forEach { preset ->
                val isSelected = current.preset == preset
                val scheme = MaterialTheme.colorScheme
                Row(
                    Modifier.fillMaxWidth().height(46.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .background(if (isSelected) scheme.secondaryContainer else scheme.surfaceContainerLow)
                        .clickable { onSelect(LedgerPeriod(preset)) }.padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(preset.label, Modifier.weight(1f),
                        color = if (isSelected) scheme.onSecondaryContainer else scheme.onSurface)
                    if (isSelected) Icon(Icons.Default.Check, "当前选择", tint = scheme.onSecondaryContainer)
                }
            }
            Spacer(Modifier.height(6.dp))
            Text("按月份", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconAction(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "上一年") { if (year > 1) year-- }
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { Text("${year}年", style = MaterialTheme.typography.titleMedium) }
                IconAction(Icons.AutoMirrored.Filled.KeyboardArrowRight, "下一年") { if (year < 9999) year++ }
            }
            TileGrid((1..12).toList(), 3, 8) { month ->
                ChoiceTile("${month}月", selected?.year == year && selected.monthNumber == month) {
                    onSelect(LedgerPeriod(null, LocalDate(year, month, 1)))
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
internal fun <T> TileGrid(values: List<T>, columns: Int, gap: Int, content: @Composable (T) -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(gap.dp)) {
        values.chunked(columns).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap.dp)) {
                row.forEach { entry -> Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { content(entry) } }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
internal fun ChoiceTile(label: String, selected: Boolean, onClick: () -> Unit) {
    val theme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(10.dp)
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(40.dp),
        shape = shape,
        border = if (selected) androidx.compose.foundation.BorderStroke(1.dp, theme.primary) else null,
        color = if (selected) theme.primaryContainer else theme.surfaceContainerLow,
        contentColor = if (selected) theme.onPrimaryContainer else theme.onSurfaceVariant,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
