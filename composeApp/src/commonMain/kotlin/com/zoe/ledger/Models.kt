package com.zoe.ledger

import kotlinx.datetime.LocalDate

enum class EntryType { Expense, Income, Transfer }
enum class Owner(val label: String) { Shared("共同"), Me("我"), Partner("爱人") }

data class Category(
    val id: String,
    val name: String,
    val icon: String,
    val color: Long,
    val type: EntryType,
)

data class LedgerEntry(
    val id: String,
    val amountCents: Long,
    val type: EntryType,
    val categoryId: String,
    val owner: Owner,
    val date: LocalDate,
    val note: String = "",
)

data class MonthlyBudget(val limitCents: Long, val monthKey: String)

data class MonthlySummary(
    val incomeCents: Long,
    val expenseCents: Long,
    val budgetCents: Long,
) {
    val balanceCents get() = incomeCents - expenseCents
    val budgetUsedRatio get() = if (budgetCents == 0L) 0f else expenseCents.toFloat() / budgetCents
}

interface LedgerRepository {
    suspend fun entries(): List<LedgerEntry>
    suspend fun categories(): List<Category>
    suspend fun budget(): MonthlyBudget
    suspend fun save(entry: LedgerEntry)
}

