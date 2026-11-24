package com.qianduoduo

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

private sealed interface Screen {
    data object Timeline : Screen
    data object Accounts : Screen
    data object Filter : Screen
    data class Search(val query: String = "") : Screen
    data class Insights(val endMonth: LocalDate, val selected: LocalDate = endMonth) : Screen
    data class ExpenseDetails(val period: LedgerPeriod, val group: ExpenseGroup, val previous: Screen) : Screen
    data class Editor(
        val record: TransactionRecord?,
        val previous: Screen = Timeline,
        val draft: EditorDraft? = null,
        val copied: Boolean = false,
        val lastSaved: Long? = null,
        val generation: Int = 0,
    ) : Screen
}

@Composable
internal expect fun QianBackHandler(enabled: Boolean, onBack: () -> Unit)

@Composable
internal fun QianDuoDuoApp(store: LedgerStore, demo: Boolean = false, dark: Boolean = isSystemInDarkTheme()) {
    var screen: Screen by remember { mutableStateOf(Screen.Timeline) }
    var filter by remember { mutableStateOf(LedgerPeriod()) }
    var data: LedgerSnapshot? by remember { mutableStateOf(null) }
    var busy by remember { mutableStateOf(false) }
    var startupError by remember { mutableStateOf(false) }
    var loadAttempt by remember { mutableIntStateOf(0) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val goBack = {
        screen = when (val current = screen) {
            is Screen.Editor -> current.previous
            is Screen.ExpenseDetails -> current.previous
            else -> Screen.Timeline
        }
    }

    LaunchedEffect(store, loadAttempt) {
        startupError = false
        try {
            data = withContext(Dispatchers.IO) {
                store.seed()
                if (demo) store.seedDemo()
                store.snapshot()
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            startupError = true
        }
    }

    fun selectPeriod(period: LedgerPeriod) {
        filter = period
        goBack()
    }

    fun mutate(failure: String, done: () -> Unit = {}, change: LedgerStore.() -> Unit) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                data = withContext(Dispatchers.IO) {
                    store.change()
                    store.snapshot()
                }
                done()
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                launch { snackbar.showSnackbar(failure) }
            } finally {
                busy = false
            }
        }
    }

    QianBackHandler(screen != Screen.Timeline && !busy, goBack)
    QianTheme(dark) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            val snapshot = data
            Box(Modifier.fillMaxSize()) {
                if (snapshot == null) {
                    if (startupError) LoadFailure { loadAttempt++ }
                    else CircularProgressIndicator(Modifier.align(Alignment.Center))
                } else when (val current = screen) {
                    Screen.Timeline -> TimelineScreen(
                        snapshot, filter, demo,
                        onPeriod = { filter = it },
                        onFilter = { screen = Screen.Filter },
                        onAccounts = { screen = Screen.Accounts },
                        onSearch = { screen = Screen.Search() },
                        onInsights = {
                            val now = today()
                            screen = Screen.Insights(filter.selectedMonth(now) ?: now.monthStart())
                        },
                        onExpense = { screen = Screen.ExpenseDetails(filter, it, current) },
                        onEdit = { screen = Screen.Editor(it) },
                    )
                    is Screen.Search -> TransactionSearchScreen(
                        snapshot, current.query, demo, goBack,
                        onQuery = { screen = current.copy(query = it) },
                        onEdit = { screen = Screen.Editor(it, current) },
                    )
                    is Screen.Insights -> InsightsScreen(
                        snapshot, current.endMonth, current.selected, goBack,
                        onSelect = { screen = current.copy(selected = it) },
                        onShift = { direction ->
                            screen = Screen.Insights(current.endMonth.plus(6 * direction, DateTimeUnit.MONTH))
                        },
                        onViewMonth = {
                            filter = LedgerPeriod(null, current.selected)
                            screen = Screen.Timeline
                        },
                        onExpense = { screen = Screen.ExpenseDetails(LedgerPeriod(null, current.selected), it, current) },
                    )
                    is Screen.ExpenseDetails -> ExpenseDetailScreen(
                        snapshot, current.period, current.group, goBack,
                        onEdit = { screen = Screen.Editor(it, current) },
                    )
                    Screen.Filter -> FilterScreen(
                        filter, ::selectPeriod, goBack,
                    )
                    Screen.Accounts -> AccountsScreen(
                        snapshot, goBack,
                        onSave = { id, name, type -> mutate("无法保存账户") { saveAccount(id, name, type) } },
                        onDelete = { id -> mutate("无法删除账户") { deleteAccount(id) } },
                    )
                    is Screen.Editor -> key(current.record?.id, current.generation) {
                        EditorScreen(
                            current.record, current.draft, current.copied, current.lastSaved, snapshot, busy, goBack,
                            onSave = { entry, keepOpen ->
                                mutate(
                                    "无法保存这笔账",
                                    done = {
                                        val date = localDateTime(entry.at).date
                                        if (current.record == null && current.previous == Screen.Timeline) {
                                            val (from, until) = filter.range(today())
                                            if ((from != null && date < from) || (until != null && date >= until)) {
                                                filter = LedgerPeriod(null, date.monthStart())
                                            }
                                        }
                                        screen = if (keepOpen) Screen.Editor(
                                            null, current.previous, EditorDraft(entry.type, withDate(nowMillis(), date)),
                                            lastSaved = entry.cents, generation = current.generation + 1,
                                        ) else current.previous
                                    },
                                ) { saveTxn(entry, editing = current.record != null) }
                            },
                            onCopy = {
                                current.record?.let { original ->
                                    screen = Screen.Editor(
                                        null, current.previous,
                                        EditorDraft(original.type, nowMillis(), original.cents, original.account,
                                            original.destination, original.category, original.note),
                                        copied = true, generation = current.generation + 1,
                                    )
                                }
                            },
                            onDelete = { id -> mutate("无法删除这笔账", goBack) { deleteTxn(id) } },
                        )
                    }
                }
                SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
            }
        }
    }
}

internal val weekNames = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
internal fun dayText(date: LocalDate) = "${date.monthNumber}月${date.dayOfMonth}日"
