package com.zoe.ledger

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val Ink = Color(0xFF202126)
private val Muted = Color(0xFF74757D)
private val Brand = Color(0xFF295C49)
private val Canvas = Color(0xFFF8F8F5)
private val ExpenseAccent = Color(0xFFD36A51)
private fun Long.money() = "¥" + (this / 100.0).toString().let { if (it.endsWith(".0")) it.dropLast(2) else it }

@Composable
fun QingJiApp() {
    val state = remember { LedgerState() }
    var tab by remember { mutableStateOf(0) }
    var addOpen by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { state.load() }
    MaterialTheme(colorScheme = lightColorScheme(primary = Brand, surface = Color.White, background = Canvas)) {
        Surface(color = Canvas, modifier = Modifier.fillMaxSize()) {
            if (state.loading) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            else BoxWithConstraints(Modifier.fillMaxSize()) {
                val compact = maxWidth < 700.dp
                if (compact) {
                    Column(Modifier.fillMaxSize()) {
                        Box(Modifier.weight(1f)) { when (tab) { 0 -> Dashboard(state, compact = true); 1 -> Ledger(state, compact = true); else -> Analysis(state, compact = true) } }
                        NavigationBar(containerColor = Color.White) {
                            listOf("概览", "账本", "分析").forEachIndexed { index, label ->
                                NavigationBarItem(selected = tab == index, onClick = { tab = index }, icon = { Text(listOf("◌", "▤", "◔")[index], fontSize = 20.sp) }, label = { Text(label) })
                            }
                        }
                    }
                    FloatingActionButton(onClick = { addOpen = true }, containerColor = Brand, contentColor = Color.White, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 60.dp)) { Text("＋", fontSize = 28.sp) }
                } else Row(Modifier.fillMaxSize()) {
                    NavigationRail(containerColor = Color.White, modifier = Modifier.fillMaxHeight().width(106.dp)) {
                        Spacer(Modifier.height(22.dp)); Text("轻记", color = Brand, fontWeight = FontWeight.Black, letterSpacing = 2.sp); Spacer(Modifier.height(24.dp))
                        listOf("概览", "账本", "分析").forEachIndexed { index, label -> NavigationRailItem(selected = tab == index, onClick = { tab = index }, icon = { Text(listOf("◌", "▤", "◔")[index], fontSize = 20.sp) }, label = { Text(label) }) }
                        Spacer(Modifier.weight(1f)); Text("本地账本", color = Muted, fontSize = 11.sp, modifier = Modifier.padding(bottom = 20.dp))
                    }
                    Box(Modifier.weight(1f).fillMaxHeight()) { when (tab) { 0 -> Dashboard(state); 1 -> Ledger(state); else -> Analysis(state) }; FloatingActionButton(onClick = { addOpen = true }, containerColor = Brand, contentColor = Color.White, modifier = Modifier.align(Alignment.BottomEnd).padding(32.dp)) { Text("＋", fontSize = 28.sp) } }
                }
            }
        }
        if (addOpen) AddEntryDialog(state, onDismiss = { addOpen = false })
    }
}

@Composable private fun PageHeader(title: String, subtitle: String) = Column {
    Text(title, color = Ink, fontSize = 30.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(5.dp)); Text(subtitle, color = Muted, fontSize = 14.sp)
}

@Composable
private fun Dashboard(state: LedgerState, compact: Boolean = false) {
    val summary = state.summary()
    val topCategories = state.topExpenseCategories()
    val recentEntries = state.entriesFor()
        .asReversed()
        .filter { it.type != EntryType.Transfer }
        .sortedByDescending { it.date }
        .take(6)
    val month = state.selectedMonth
    val monthLabel = "${month.substringBeforeLast('-')} 年 ${month.substringAfterLast('-').toInt()} 月"
    val budgetHint = if (summary.budgetCents > 0L) "预算 ${summary.budgetCents.money()}" else "未设置预算"
    val sidePadding = if (compact) 20.dp else 42.dp

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = sidePadding, top = sidePadding, end = sidePadding, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { PageHeader("家庭总览", "$monthLabel · 你和爱人的共同账本") }
        if (compact) {
            item { MetricCard("本月结余", summary.balanceCents.money(), "收入减支出", Brand, Modifier.fillMaxWidth()) }
            item { MetricCard("本月支出", summary.expenseCents.money(), budgetHint, ExpenseAccent, Modifier.fillMaxWidth()) }
            item { TopExpenseCategoriesCard(topCategories, summary.expenseCents, state::categoryFor) }
            item { RecentEntriesCard(recentEntries, state::categoryFor) }
        } else {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(Modifier.weight(2f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            MetricCard("本月结余", summary.balanceCents.money(), "收入减支出", Brand, Modifier.weight(1f))
                            MetricCard("总收入", summary.incomeCents.money(), "本月已入账", Color(0xFF337A5D), Modifier.weight(1f))
                        }
                        RecentEntriesCard(recentEntries, state::categoryFor)
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        MetricCard("本月支出", summary.expenseCents.money(), budgetHint, ExpenseAccent, Modifier.fillMaxWidth())
                        TopExpenseCategoriesCard(topCategories, summary.expenseCents, state::categoryFor)
                        BudgetProgressCard(summary)
                    }
                }
            }
        }
    }
}

@Composable
private fun TopExpenseCategoriesCard(
    categories: List<CategorySpending>,
    totalExpenseCents: Long,
    categoryFor: (String) -> Category?,
) {
    SurfaceCard(Modifier.fillMaxWidth()) {
        Text("支出分类 Top 3", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(12.dp))
        if (categories.isEmpty()) {
            Text("本月还没有支出", color = Muted, fontSize = 14.sp)
        } else Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            categories.forEach { item ->
                val ratio = if (totalExpenseCents > 0L) {
                    (item.amountCents.toDouble() / totalExpenseCents).coerceIn(0.0, 1.0)
                } else 0.0
                val share = if (ratio > 0.0 && ratio < 0.01) "<1%" else "${(ratio * 100).roundToInt()}%"
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            categoryFor(item.categoryId)?.name ?: "未分类",
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(item.amountCents.money(), fontWeight = FontWeight.SemiBold)
                        Text(share, color = Muted, fontSize = 12.sp)
                    }
                    LinearProgressIndicator(
                        progress = { ratio.toFloat() },
                        color = Brand,
                        trackColor = Color(0xFFE7ECE8),
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(12.dp)),
                    )
                }
            }
        }
    }
}

@Composable
private fun RecentEntriesCard(entries: List<LedgerEntry>, categoryFor: (String) -> Category?) {
    SurfaceCard(Modifier.fillMaxWidth()) {
        Text("最近流水", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        if (entries.isEmpty()) {
            Text("本月还没有流水", color = Muted, fontSize = 14.sp)
        } else {
            entries.forEach { entry ->
                EntryRow(entry, categoryFor(entry.categoryId))
            }
        }
    }
}

@Composable
private fun BudgetProgressCard(summary: MonthlySummary) {
    SurfaceCard(Modifier.fillMaxWidth()) {
        Text("预算进度", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(12.dp))
        if (summary.budgetCents <= 0L) {
            Text("本月未设置预算", color = Muted, fontSize = 14.sp)
        } else {
            Text("${(summary.budgetUsedRatio * 100).toInt()}%", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Brand)
            Text("已用 ${summary.expenseCents.money()} / ${summary.budgetCents.money()}", color = Muted)
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { summary.budgetUsedRatio.coerceIn(0f, 1f) },
                color = Brand,
                trackColor = Color(0xFFE7ECE8),
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(12.dp)),
            )
        }
    }
}

@Composable private fun MetricCard(title: String, amount: String, hint: String, accent: Color, modifier: Modifier = Modifier) = SurfaceCard(modifier) {
    Text(title, color = Muted, fontSize = 13.sp); Spacer(Modifier.height(12.dp)); Text(amount, color = accent, fontSize = 28.sp, fontWeight = FontWeight.Bold); Spacer(Modifier.height(5.dp)); Text(hint, color = Muted, fontSize = 12.sp)
}
@Composable private fun SurfaceCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) = Surface(shape = RoundedCornerShape(20.dp), color = Color.White, modifier = modifier) { Column(Modifier.padding(22.dp), content = content) }

@Composable private fun CategoryLine(category: Category?, cents: Long, total: Long) = Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
    Text(category?.icon ?: "•", modifier = Modifier.background(Color(category?.color ?: 0xFFF0F0F0), RoundedCornerShape(9.dp)).padding(8.dp)); Spacer(Modifier.width(10.dp)); Column(Modifier.weight(1f)) { Text(category?.name ?: "未分类", fontWeight = FontWeight.Medium); Text("${if (total == 0L) 0 else cents * 100 / total}% 的支出", color = Muted, fontSize = 12.sp) }; Text(cents.money(), fontWeight = FontWeight.SemiBold)
}

@Composable private fun EntryRow(entry: LedgerEntry, category: Category?) = Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
    Text(category?.icon ?: "•", modifier = Modifier.background(Color(category?.color ?: 0xFFF0F0F0), RoundedCornerShape(9.dp)).padding(9.dp)); Spacer(Modifier.width(11.dp)); Column(Modifier.weight(1f)) { Text(entry.note.ifBlank { category?.name ?: "未分类" }, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis); Text("${entry.date} · ${entry.owner.label}", color = Muted, fontSize = 12.sp) }; Text((if (entry.type == EntryType.Income) "+" else "−") + entry.amountCents.money(), color = if (entry.type == EntryType.Income) Brand else Ink, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun Ledger(state: LedgerState, compact: Boolean = false) {
    var filter by remember { mutableStateOf(EntryType.Expense) }
    val list = state.entriesFor().filter { it.type == filter }.sortedByDescending { it.date }
    Column(Modifier.fillMaxSize().padding(if (compact) 20.dp else 42.dp)) {
        PageHeader("账本", "记录每一笔钱，让家庭开销清晰可见"); Spacer(Modifier.height(25.dp))
        SingleChoiceSegmentedButtonRow { listOf(EntryType.Expense to "支出", EntryType.Income to "收入").forEachIndexed { i, (type, label) -> SegmentedButton(selected = filter == type, onClick = { filter = type }, shape = SegmentedButtonDefaults.itemShape(i, 2)) { Text(label) } } }
        Spacer(Modifier.height(18.dp)); SurfaceCard(Modifier.fillMaxWidth().weight(1f)) { LazyColumn { items(list) { EntryRow(it, state.categoryFor(it.categoryId)); HorizontalDivider(color = Color(0xFFF0F0EE)) } } }
    }
}

@Composable
private fun Analysis(state: LedgerState, compact: Boolean = false) {
    val summary = state.summary(); val expense = state.entriesFor().filter { it.type == EntryType.Expense }
    Column(Modifier.fillMaxSize().padding(if (compact) 20.dp else 42.dp)) {
        PageHeader("支出分析", "从消费结构中找到家庭预算的优化空间"); Spacer(Modifier.height(26.dp))
        if (compact) SurfaceCard(Modifier.fillMaxWidth().weight(1f)) { Text("按分类", fontSize = 18.sp, fontWeight = FontWeight.SemiBold); Spacer(Modifier.height(14.dp)); expense.groupBy { it.categoryId }.mapValues { it.value.sumOf(LedgerEntry::amountCents) }.toList().sortedByDescending { it.second }.forEach { (id, amount) -> CategoryLine(state.categoryFor(id), amount, summary.expenseCents); HorizontalDivider(Modifier.padding(top = 10.dp), color = Color(0xFFF0F0EE)) } } else Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            SurfaceCard(Modifier.weight(1f).fillMaxHeight()) { Text("按分类", fontSize = 18.sp, fontWeight = FontWeight.SemiBold); Spacer(Modifier.height(14.dp)); expense.groupBy { it.categoryId }.mapValues { it.value.sumOf(LedgerEntry::amountCents) }.toList().sortedByDescending { it.second }.forEach { (id, amount) -> CategoryLine(state.categoryFor(id), amount, summary.expenseCents); HorizontalDivider(Modifier.padding(top = 10.dp), color = Color(0xFFF0F0EE)) } }
            SurfaceCard(Modifier.weight(1f).fillMaxHeight()) { Text("按成员", fontSize = 18.sp, fontWeight = FontWeight.SemiBold); Spacer(Modifier.height(14.dp)); Owner.entries.forEach { owner -> val amount = expense.filter { it.owner == owner }.sumOf { it.amountCents }; Text(owner.label, fontWeight = FontWeight.Medium); Text("${amount.money()} · ${if (summary.expenseCents == 0L) 0 else amount * 100 / summary.expenseCents}%", color = Muted); Spacer(Modifier.height(20.dp)); LinearProgressIndicator(progress = { if (summary.expenseCents == 0L) 0f else amount.toFloat() / summary.expenseCents }, color = Brand, modifier = Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(10.dp))); Spacer(Modifier.height(20.dp)) } }
        }
    }
}

@Composable
private fun AddEntryDialog(state: LedgerState, onDismiss: () -> Unit) {
    var type by remember { mutableStateOf(EntryType.Expense) }; var amount by remember { mutableStateOf("") }; var note by remember { mutableStateOf("") }; var owner by remember { mutableStateOf(Owner.Shared) }; var categoryId by remember { mutableStateOf("food") }; val scope = rememberCoroutineScope()
    AlertDialog(onDismissRequest = onDismiss, title = { Text("新增一笔") }, text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SingleChoiceSegmentedButtonRow { listOf(EntryType.Expense to "支出", EntryType.Income to "收入").forEachIndexed { i, (value, label) -> SegmentedButton(selected = type == value, onClick = { type = value; categoryId = if (value == EntryType.Income) "salary" else "food" }, shape = SegmentedButtonDefaults.itemShape(i, 2)) { Text(label) } } }
        OutlinedTextField(amount, { amount = it.filter { c -> c.isDigit() || c == '.' } }, label = { Text("金额（元）") }, singleLine = true)
        OutlinedTextField(note, { note = it }, label = { Text("备注") }, singleLine = true)
        Text("归属", fontSize = 13.sp, color = Muted); Row { Owner.entries.forEach { item -> FilterChip(selected = owner == item, onClick = { owner = item }, label = { Text(item.label) }, modifier = Modifier.padding(end = 6.dp)) } }
        Text("分类", fontSize = 13.sp, color = Muted); Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { state.categories.filter { it.type == type }.forEach { item -> FilterChip(selected = categoryId == item.id, onClick = { categoryId = item.id }, label = { Text("${item.icon} ${item.name}") }) } }
    } }, confirmButton = { Button(onClick = { val cents = (amount.toDoubleOrNull()?.times(100))?.toLong() ?: 0; if (cents > 0) { scope.launch { state.add(cents, type, categoryId, owner, note); onDismiss() } } }) { Text("保存") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } })
}
