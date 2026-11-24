package com.qianduoduo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.datetime.LocalDate

private fun LocalDate.monthLabel() = "${year}年${monthNumber}月"

@Composable
internal fun InsightsScreen(
    data: LedgerSnapshot,
    endMonth: LocalDate,
    selected: LocalDate,
    onBack: () -> Unit,
    onSelect: (LocalDate) -> Unit,
    onShift: (Int) -> Unit,
    onViewMonth: () -> Unit,
    onExpense: (ExpenseGroup) -> Unit,
) {
    val months = remember(data.txns, endMonth) { data.monthlyTotals(endMonth) }
    val month = months.first { it.month == selected }
    val breakdown = remember(data, selected) {
        data.expenseBreakdown(data.forPeriod(LedgerPeriod(null, selected), selected))
    }
    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        ScreenHeader("统计", onBack)
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            item("trend") { MonthlyTrend(months, selected, onSelect, onShift) }
            item("selected-month") {
                Row(
                    Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(selected.monthLabel(), style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = onViewMonth) { Text("查看当月流水") }
                }
                SummaryCard(month.expense, month.income)
            }
            item("group-heading") {
                Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 10.dp)) {
                    Text("支出构成", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (breakdown.isEmpty()) "所选月份没有支出" else "点分类查看对应流水，转账不计入",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            items(breakdown, key = { "group:${it.group.id.orEmpty()}" }) { total ->
                Box(Modifier.padding(horizontal = 8.dp)) {
                    ExpenseBreakdownRow(total, month.expense) { onExpense(total.group) }
                }
            }
        }
    }
}

@Composable
private fun MonthlyTrend(
    months: List<MonthTotals>,
    selected: LocalDate,
    onSelect: (LocalDate) -> Unit,
    onShift: (Int) -> Unit,
) {
    val colors = LocalMoneyColors.current
    val scheme = MaterialTheme.colorScheme
    val currentMonth = today().monthStart()
    val scale = (months.maxOf { maxOf(it.income, it.expense) }).coerceAtLeast(1L)
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainerLow, contentColor = scheme.onSurface),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text("六个月收支", Modifier.padding(start = 4.dp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconAction(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "前六个月") { onShift(-1) }
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text("${months.first().month.monthLabel()} 至 ${months.last().month.monthLabel()}",
                        style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                }
                IconAction(Icons.AutoMirrored.Filled.KeyboardArrowRight, "后六个月") { onShift(1) }
            }
            Row(Modifier.padding(start = 4.dp, bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TrendLegend("收入", colors.income)
                TrendLegend("支出", colors.expense)
            }
            Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                months.forEach { totals ->
                    val active = totals.month == selected
                    Column(
                        Modifier.weight(1f).clip(MaterialTheme.shapes.small)
                            .background(if (active) scheme.secondaryContainer else Color.Transparent)
                            .selectable(selected = active, role = Role.Tab, onClick = { onSelect(totals.month) })
                            .testTag("trend-month-${totals.month}")
                            .semantics {
                                contentDescription = "${totals.month.monthLabel()}，收入 ${Money.format(totals.income)}，支出 ${Money.format(totals.expense)}"
                            }.padding(vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Row(
                            Modifier.fillMaxWidth().height(108.dp),
                            horizontalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterHorizontally),
                            verticalAlignment = Alignment.Bottom,
                        ) {
                            TrendBar(totals.income, scale, colors.income)
                            TrendBar(totals.expense, scale, colors.expense)
                        }
                        HorizontalDivider(Modifier.fillMaxWidth().padding(horizontal = 4.dp), color = scheme.outlineVariant)
                        Spacer(Modifier.height(4.dp))
                        Text("${totals.month.monthNumber}月${if (totals.month == currentMonth) "*" else ""}",
                            color = if (active) scheme.onSecondaryContainer else scheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal)
                    }
                }
            }
            Text(
                if (months.all { it.income == 0L && it.expense == 0L }) "这六个月还没有收支记录" else "点月份查看当月收支和分类",
                modifier = Modifier.padding(start = 4.dp, top = 8.dp),
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
            )
            if (months.any { it.month == currentMonth }) Text(
                "* 本月尚未结束，与历史整月对比时请留意。",
                modifier = Modifier.padding(start = 4.dp, top = 4.dp),
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TrendBar(value: Long, scale: Long, color: Color) {
    val fraction = (value.toDouble() / scale).coerceIn(0.0, 1.0).toFloat()
    Box(
        Modifier.width(9.dp).height(if (value > 0) (108.dp * fraction).coerceAtLeast(2.dp) else 0.dp)
            .background(color, RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)),
    )
}

@Composable
private fun TrendLegend(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(Modifier.size(8.dp).background(color, CircleShape))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
internal fun ExpenseDetailScreen(
    data: LedgerSnapshot,
    period: LedgerPeriod,
    group: ExpenseGroup,
    onBack: () -> Unit,
    onEdit: (TransactionRecord) -> Unit,
) {
    val now = today()
    val entries = remember(data, period, group, now) { data.expenseEntries(data.forPeriod(period, now), group) }
    val groups = remember(entries) { entries.groupBy { localDateTime(it.at).date } }
    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        ScreenHeader("分类明细", onBack)
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            item("detail-summary") {
                Card(
                    Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        CategoryIcon(group.icon)
                        Column(Modifier.weight(1f)) {
                            Text(group.label, style = MaterialTheme.typography.titleMedium)
                            Text("${period.label(now)} · ${entries.size} 笔支出", style = MaterialTheme.typography.bodySmall)
                            Text(
                                Money.format(entries.sumOf { it.cents }),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }
            if (entries.isEmpty()) item("detail-empty") {
                Text("这个时间段没有该分类的支出", Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            transactionItems(groups, data, now, onEdit = onEdit)
        }
    }
}
