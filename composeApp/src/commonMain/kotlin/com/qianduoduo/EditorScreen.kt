package com.qianduoduo

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

internal data class EditorDraft(
    val kind: TxnType,
    val at: Long,
    val cents: Long? = null,
    val source: String? = null,
    val destination: String? = null,
    val category: String? = null,
    val note: String? = null,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EditorScreen(
    entry: TransactionRecord?,
    draft: EditorDraft?,
    copied: Boolean,
    lastSaved: Long?,
    data: LedgerSnapshot,
    busy: Boolean,
    onClose: () -> Unit,
    onSave: (TransactionRecord, keepOpen: Boolean) -> Unit,
    onCopy: () -> Unit,
    onDelete: (String) -> Unit,
) {
    var type by rememberSaveable { mutableStateOf(entry?.type ?: draft?.kind ?: TxnType.EXPENSE) }
    var amount by rememberSaveable { mutableStateOf((entry?.cents ?: draft?.cents)?.let { Money.format(it, false, false) }.orEmpty()) }
    var category by rememberSaveable { mutableStateOf(entry?.category ?: draft?.category) }
    var expandedRoot by rememberSaveable {
        mutableStateOf(entry?.category?.let { id -> data.categories.find { it.id == id }?.parent ?: id })
    }
    val accounts = data.activeAccounts
    var editedAccount by rememberSaveable { mutableStateOf(entry?.account) }
    var expenseAccount by rememberSaveable { mutableStateOf(draft?.takeIf { it.kind == TxnType.EXPENSE }?.source) }
    var incomeAccount by rememberSaveable { mutableStateOf(draft?.takeIf { it.kind == TxnType.INCOME }?.source) }
    var transferAccount by rememberSaveable { mutableStateOf(draft?.takeIf { it.kind == TxnType.TRANSFER }?.source) }
    val chosenAccount = when (type) {
        TxnType.EXPENSE -> expenseAccount
        TxnType.INCOME -> incomeAccount
        TxnType.TRANSFER -> transferAccount
    }
    val categoryAccount = remember(data, type, category) { data.lastCategoryAccount(type, category) }
    val account = if (entry != null) editedAccount else chosenAccount ?: categoryAccount ?: data.suggestedAccount(type)
    var destination by rememberSaveable { mutableStateOf(entry?.destination ?: draft?.destination) }
    var note by rememberSaveable { mutableStateOf((entry?.note ?: draft?.note).orEmpty()) }
    var timestamp by rememberSaveable { mutableLongStateOf(entry?.at ?: draft?.at ?: nowMillis()) }
    var showCalendar by rememberSaveable { mutableStateOf(false) }
    var askDelete by rememberSaveable { mutableStateOf(false) }
    var askCopy by rememberSaveable { mutableStateOf(false) }
    var showExtra by rememberSaveable { mutableStateOf(entry != null) }
    var showAllCategories by rememberSaveable { mutableStateOf(false) }
    val amountFocus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val date = localDateTime(timestamp).date
    val cents = Money.parse(amount) ?: 0
    val transfer = type == TxnType.TRANSFER
    val sourceValid = account != null && (accounts.any { it.id == account } || (entry != null && account == entry.account))
    val destinationValid = destination != null && destination != account &&
        (accounts.any { it.id == destination } || (entry != null && destination == entry.destination))
    val canSave = !busy && cents > 0 && sourceValid && (!transfer || destinationValid)
    val dirty = entry != null && (type != entry.type || cents != entry.cents || category != entry.category ||
        account != entry.account || (transfer && destination != entry.destination) || note != entry.note.orEmpty() || timestamp != entry.at)

    fun submit(keepOpen: Boolean) {
        if (!canSave) return
        val selected = account ?: return
        onSave(TransactionRecord(entry?.id ?: newId(), type, cents, selected,
            destination.takeIf { transfer }, category.takeUnless { transfer }, note, timestamp), keepOpen)
    }

    Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
        ScreenHeader(when { copied -> "复制账单"; entry == null -> "记一笔"; else -> "编辑这笔" },
            onBack = { if (!busy) onClose() }, close = true) {
            if (entry != null) {
                TextButton(onClick = { if (dirty) askCopy = true else onCopy() }, enabled = !busy) { Text("复制账单") }
                IconButton(onClick = { askDelete = true }, enabled = !busy) {
                    Icon(Icons.Default.Delete, "删除", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
        if (lastSaved != null) Text(
            "已记 ${Money.format(lastSaved)}，可以继续记账",
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
            style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
        )
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                TxnType.entries.forEachIndexed { index, value ->
                    SegmentedButton(
                        selected = type == value,
                        shape = SegmentedButtonDefaults.itemShape(index, TxnType.entries.size),
                        onClick = {
                            if (type != value) {
                                type = value
                                category = null
                                expandedRoot = null
                                showAllCategories = false
                            }
                        },
                    ) { Text(value.label) }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("¥", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
                OutlinedTextField(
                    value = amount,
                    onValueChange = { raw ->
                        val clean = raw.filter { it in '0'..'9' || it == '.' }
                        if (clean.isEmpty() || Money.parse(clean) != null) amount = clean
                    },
                    modifier = Modifier.weight(1f).focusRequester(amountFocus).testTag("editor-amount"),
                    textStyle = MaterialTheme.typography.headlineSmall,
                    placeholder = { Text("0.00", style = MaterialTheme.typography.headlineSmall) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    singleLine = true,
                )
            }
            AccountPicker(
                when (type) {
                    TxnType.EXPENSE -> "付款账户"
                    TxnType.INCOME -> "收款账户"
                    TxnType.TRANSFER -> "转出账户"
                }, accounts, account, "editor-source-account",
                categorySuggested = entry == null && chosenAccount == null && categoryAccount != null,
            ) { id ->
                if (entry != null) editedAccount = id else when (type) {
                    TxnType.EXPENSE -> expenseAccount = id
                    TxnType.INCOME -> incomeAccount = id
                    TxnType.TRANSFER -> transferAccount = id
                }
                if (transfer && destination == id) destination = null
            }
            if (transfer) AccountPicker(
                "转入账户", accounts.filter { it.id != account }, destination, "editor-destination-account",
            ) { destination = it }
            if (accounts.isEmpty()) Text("先在首页的「账户」里添加一个账户，才能记账。", color = MaterialTheme.colorScheme.error)
            if (transfer && accounts.size == 1 && entry == null) {
                Text("转账需要两个不同的账户。", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (!transfer) {
                val recent = remember(data, type) { if (entry == null) data.recentCategories(type) else emptyList() }
                if (recent.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        SectionTitle("最近用过")
                        TileGrid(recent, 3, 8) { item ->
                            FilterChip(
                                selected = category == item.id,
                                onClick = {
                                    category = item.id
                                    expandedRoot = null
                                    showAllCategories = false
                                    focusManager.clearFocus()
                                },
                                modifier = Modifier.fillMaxWidth().testTag("recent-category-${item.id}"),
                                label = { Text(item.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            )
                        }
                    }
                    OutlinedButton(
                        onClick = { showAllCategories = !showAllCategories },
                        modifier = Modifier.fillMaxWidth().testTag("all-categories-toggle"),
                    ) {
                        Text("全部分类", Modifier.weight(1f))
                        Text(data.categories.find { it.id == category }?.name ?: "可不选", style = MaterialTheme.typography.labelSmall)
                        Icon(if (showAllCategories) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, null)
                    }
                }
                if (recent.isEmpty() || showAllCategories) {
                    if (recent.isEmpty()) SectionTitle("分类")
                    val roots = data.categories.filter { it.kind == type && it.parent == null }
                    val selectedRoot = data.categories.find { it.id == category }?.let { it.parent ?: it.id }
                    TileGrid(roots, 5, 8) { root ->
                        CategoryTile(root, selected = selectedRoot == root.id || expandedRoot == root.id) {
                            if (expandedRoot == root.id) expandedRoot = null else {
                                category = root.id
                                expandedRoot = root.id
                            }
                        }
                    }
                    val children = data.categories.filter { expandedRoot != null && it.parent == expandedRoot }
                    if (children.isNotEmpty()) Surface(
                        shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow,
                    ) {
                        Box(Modifier.padding(8.dp)) {
                            TileGrid(children, 4, 8) { child -> CategoryTile(child, category == child.id) {
                                category = child.id
                                expandedRoot = null
                                focusManager.clearFocus()
                            } }
                        }
                    }
                }
            }
            if (entry == null) {
                OutlinedButton(onClick = { showExtra = !showExtra }, modifier = Modifier.fillMaxWidth().testTag("editor-details-toggle")) {
                    Text("日期与备注", modifier = Modifier.weight(1f))
                    Text(if (date == today()) "今天" else dayText(date), style = MaterialTheme.typography.labelSmall)
                    if (note.isNotBlank() && !showExtra) Text(" · 有备注", style = MaterialTheme.typography.labelSmall)
                    Icon(if (showExtra) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (showExtra) "收起日期与备注" else "展开日期与备注")
                }
            }
            if (showExtra) {
                SectionTitle("时间")
                val time = localDateTime(timestamp).time
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconAction(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "前一天") {
                        timestamp = withDate(timestamp, date.plus(-1, DateTimeUnit.DAY))
                    }
                    TextButton(onClick = { showCalendar = true }, modifier = Modifier.weight(1f)) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("${dayText(date)} ${weekNames[date.dayOfWeek.ordinal]}", fontWeight = FontWeight.Medium)
                            Text("${time.hour.toString().padStart(2, '0')}:${time.minute.toString().padStart(2, '0')}",
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    IconAction(Icons.AutoMirrored.Filled.KeyboardArrowRight, "后一天") {
                        timestamp = withDate(timestamp, date.plus(1, DateTimeUnit.DAY))
                    }
                    TextButton(onClick = { timestamp = nowMillis() }) { Text("今天") }
                }
                SectionTitle("备注")
                OutlinedTextField(note, { note = it }, Modifier.fillMaxWidth().testTag("editor-note"),
                    placeholder = { Text("买了什么？") }, singleLine = true)
            }
            Spacer(Modifier.height(4.dp))
        }
        Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (entry == null) OutlinedButton(
                    onClick = { submit(keepOpen = true) },
                    modifier = Modifier.weight(0.9f).heightIn(min = 50.dp).testTag("editor-save-continue"),
                    enabled = canSave,
                ) { Text("保存并再记") }
                Button(
                    onClick = { submit(keepOpen = false) },
                    modifier = Modifier.weight(1.1f).heightIn(min = 50.dp).testTag("editor-save"),
                    enabled = canSave,
                ) {
                    val action = if (entry == null) "记下这笔" else "保存修改"
                    val formatted = Money.format(cents)
                    Text(if (cents > 0 && entry == null && formatted.length <= 10) "$action $formatted" else action)
                }
            }
        }
    }
    LaunchedEffect(Unit) { if (entry == null && !copied) amountFocus.requestFocus() }
    if (showCalendar) DayPicker(date, { showCalendar = false }) { picked ->
        timestamp = withDate(timestamp, picked)
        showCalendar = false
    }
    if (askDelete && entry != null) AlertDialog(
        onDismissRequest = { askDelete = false },
        title = { Text("删除这笔账？") },
        text = { Text("删除后不会出现在流水里。") },
        confirmButton = { TextButton({ askDelete = false; onDelete(entry.id) }) {
            Text("删除", color = MaterialTheme.colorScheme.error)
        } },
        dismissButton = { TextButton({ askDelete = false }) { Text("取消") } },
    )
    if (askCopy) AlertDialog(
        onDismissRequest = { askCopy = false },
        title = { Text("放弃未保存修改？") },
        text = { Text("会用原账单生成新草稿，不会覆盖原账单。") },
        confirmButton = { TextButton(onCopy) { Text("复制原账单") } },
        dismissButton = { TextButton({ askCopy = false }) { Text("继续编辑") } },
    )
}

@Composable
private fun AccountPicker(
    label: String,
    accounts: List<LedgerAccount>,
    selected: String?,
    tag: String,
    categorySuggested: Boolean = false,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val current = accounts.firstOrNull { it.id == selected }
    val value = current?.name ?: if (selected == null) "请选择" else "原账户已删除"
    Box(Modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = { expanded = true }, modifier = Modifier.fillMaxWidth().testTag(tag), enabled = accounts.isNotEmpty(),
        ) {
            Column(Modifier.weight(1f)) {
                Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (categorySuggested) Text("按分类建议", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(value, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 140.dp))
            Icon(Icons.Default.KeyboardArrowDown, contentDescription = null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            accounts.forEach { item ->
                DropdownMenuItem(
                    text = { Text(item.name) },
                    trailingIcon = if (item.id == selected) { { Icon(Icons.Default.Check, "已选") } } else null,
                    onClick = { expanded = false; onSelect(item.id) },
                )
            }
        }
    }
}

@Composable
internal fun SectionTitle(title: String) {
    Text(title, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelLarge)
}

@Composable
private fun CategoryTile(category: LedgerCategory, selected: Boolean, onClick: () -> Unit) {
    Surface(onClick, Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.small, color = androidx.compose.ui.graphics.Color.Transparent) {
        Column(
            Modifier.padding(vertical = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            CategoryIcon(category.icon, size = 44.dp, selected = selected)
            Text(category.name, style = MaterialTheme.typography.labelSmall,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun DayPicker(initial: LocalDate, onClose: () -> Unit, onPick: (LocalDate) -> Unit) {
    var monthKey by rememberSaveable { mutableStateOf(LocalDate(initial.year, initial.monthNumber, 1).toString()) }
    val month = LocalDate.parse(monthKey)
    val next = month.plus(1, DateTimeUnit.MONTH)
    val days = month.dayOfWeek.ordinal
    val grid = List<Int?>(days) { null } + (1..next.plus(-1, DateTimeUnit.DAY).dayOfMonth).toList()
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("选择日期") },
        text = {
            Column {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconAction(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "上个月") {
                        monthKey = month.plus(-1, DateTimeUnit.MONTH).toString()
                    }
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { Text("${month.year}年${month.monthNumber}月") }
                    IconAction(Icons.AutoMirrored.Filled.KeyboardArrowRight, "下个月") { monthKey = next.toString() }
                }
                TileGrid(listOf("一", "二", "三", "四", "五", "六", "日"), 7, 0) {
                    Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                }
                Spacer(Modifier.height(4.dp))
                TileGrid(grid, 7, 2) { day ->
                    if (day != null) {
                        val picked = LocalDate(month.year, month.monthNumber, day)
                        Surface(
                            { onPick(picked) }, Modifier.fillMaxWidth().height(36.dp),
                            shape = androidx.compose.foundation.shape.CircleShape,
                            color = if (picked == initial) MaterialTheme.colorScheme.primaryContainer
                                else androidx.compose.ui.graphics.Color.Transparent,
                            contentColor = if (picked == initial) MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.onSurface,
                        ) { Box(contentAlignment = Alignment.Center) { Text(day.toString(), style = MaterialTheme.typography.bodyMedium) } }
                    } else Spacer(Modifier.height(36.dp))
                }
            }
        },
        confirmButton = { TextButton(onClose) { Text("取消") } },
    )
}
