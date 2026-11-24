package com.zoe.ledger

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn

private fun LocalDate.monthKey() = toString().substringBeforeLast('-')

internal data class CategorySpending(val categoryId: String, val amountCents: Long)

class LedgerState(
    private val repository: LedgerRepository = SampleLedgerRepository(),
    private val today: () -> LocalDate = { Clock.System.todayIn(TimeZone.currentSystemDefault()) },
) {
    var entries by mutableStateOf<List<LedgerEntry>>(emptyList()); private set
    var categories by mutableStateOf<List<Category>>(emptyList()); private set
    var budget by mutableStateOf(MonthlyBudget(0, "")); private set
    var selectedMonth by mutableStateOf(today().monthKey())
    var loading by mutableStateOf(true); private set

    suspend fun load() {
        entries = repository.entries(); categories = repository.categories(); budget = repository.budget(); loading = false
    }
    fun entriesFor(month: String = selectedMonth) = entries.filter { it.date.monthKey() == month }

    fun summary() = summaryFor(selectedMonth)
    fun summaryFor(month: String): MonthlySummary {
        val scoped = entriesFor(month)
        return MonthlySummary(
            scoped.filter { it.type == EntryType.Income }.sumOf { it.amountCents },
            scoped.filter { it.type == EntryType.Expense }.sumOf { it.amountCents },
            if (month == budget.monthKey) budget.limitCents else 0,
        )
    }

    internal fun topExpenseCategories(): List<CategorySpending> = entriesFor()
        .filter { it.type == EntryType.Expense }
        .groupingBy { it.categoryId }
        .fold(0L) { total, entry -> total + entry.amountCents }
        .mapNotNull { (id, total) -> if (total > 0L) CategorySpending(id, total) else null }
        .sortedWith(compareByDescending<CategorySpending> { it.amountCents }.thenBy { it.categoryId })
        .take(3)

    fun categoryFor(id: String) = categories.firstOrNull { it.id == id }
    suspend fun add(amountCents: Long, type: EntryType, categoryId: String, owner: Owner, note: String) {
        val item = LedgerEntry("local-${entries.size + 1}", amountCents, type, categoryId, owner, today(), note)
        repository.save(item); entries = entries + item
    }
}
