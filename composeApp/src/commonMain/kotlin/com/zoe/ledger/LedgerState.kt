package com.zoe.ledger

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn

class LedgerState(private val repository: LedgerRepository = SampleLedgerRepository()) {
    var entries by mutableStateOf<List<LedgerEntry>>(emptyList()); private set
    var categories by mutableStateOf<List<Category>>(emptyList()); private set
    var budget by mutableStateOf(MonthlyBudget(0, "")); private set
    var selectedMonth by mutableStateOf("2026-09")
    var loading by mutableStateOf(true); private set

    suspend fun load() {
        entries = repository.entries(); categories = repository.categories(); budget = repository.budget(); loading = false
    }
    fun summary() = summaryFor(selectedMonth)
    fun summaryFor(month: String): MonthlySummary {
        val scoped = entries.filter { it.date.toString().startsWith(month) }
        return MonthlySummary(
            scoped.filter { it.type == EntryType.Income }.sumOf { it.amountCents },
            scoped.filter { it.type == EntryType.Expense }.sumOf { it.amountCents },
            if (month == budget.monthKey) budget.limitCents else 0,
        )
    }
    fun categoryFor(id: String) = categories.firstOrNull { it.id == id }
    suspend fun add(amountCents: Long, type: EntryType, categoryId: String, owner: Owner, note: String) {
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
        val item = LedgerEntry("local-${entries.size + 1}", amountCents, type, categoryId, owner, today, note)
        repository.save(item); entries = entries + item
    }
}

