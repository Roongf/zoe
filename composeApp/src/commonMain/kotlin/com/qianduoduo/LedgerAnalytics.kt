package com.qianduoduo

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

internal fun LocalDate.monthStart() = LocalDate(year, monthNumber, 1)

internal data class ExpenseGroup(val id: String?, val label: String, val icon: String?)

internal data class ExpenseTotal(val group: ExpenseGroup, val cents: Long, val count: Int)

internal data class MonthTotals(val month: LocalDate, val income: Long, val expense: Long)

private fun Map<String, LedgerCategory>.expenseRoot(entry: TransactionRecord): LedgerCategory? =
    get(entry.category)?.takeIf { it.kind == TxnType.EXPENSE }?.let { get(it.parent) ?: it }

internal fun LedgerSnapshot.expenseBreakdown(entries: List<TransactionRecord>): List<ExpenseTotal> {
    val byId = categories.associateBy { it.id }
    return entries.asSequence()
        .filter { it.type == TxnType.EXPENSE }
        .groupBy { byId.expenseRoot(it) }
        .map { (root, records) ->
            ExpenseTotal(ExpenseGroup(root?.id, root?.name ?: "未分类", root?.icon), records.sumOf { it.cents }, records.size)
        }
        .sortedWith(compareByDescending<ExpenseTotal> { it.cents }.thenBy { it.group.label }.thenBy { it.group.id })
}

internal fun LedgerSnapshot.expenseEntries(entries: List<TransactionRecord>, group: ExpenseGroup): List<TransactionRecord> {
    val byId = categories.associateBy { it.id }
    return entries.filter { it.type == TxnType.EXPENSE && byId.expenseRoot(it)?.id == group.id }
}

internal fun LedgerSnapshot.monthlyTotals(endMonth: LocalDate): List<MonthTotals> {
    val start = endMonth.monthStart().plus(-5, DateTimeUnit.MONTH)
    val from = dateStartMillis(start)
    val until = dateStartMillis(endMonth.monthStart().plus(1, DateTimeUnit.MONTH))
    val income = LongArray(6)
    val expense = LongArray(6)
    for (entry in txns) {
        if (entry.type == TxnType.TRANSFER || entry.at < from || entry.at >= until) continue
        val date = localDateTime(entry.at).date
        val index = (date.year - start.year) * 12 + date.monthNumber - start.monthNumber
        if (entry.type == TxnType.INCOME) income[index] += entry.cents else expense[index] += entry.cents
    }
    return List(6) { index -> MonthTotals(start.plus(index, DateTimeUnit.MONTH), income[index], expense[index]) }
}

internal fun LedgerSnapshot.recentCategories(kind: TxnType): List<LedgerCategory> {
    if (kind == TxnType.TRANSFER) return emptyList()
    val byId = categories.associateBy { it.id }
    // The snapshot already comes from SQLite in newest occurrence first order.
    return txns.asSequence()
        .filter { it.type == kind }
        .mapNotNull { byId[it.category]?.takeIf { category -> category.kind == kind } }
        .distinctBy { it.id }
        .take(3)
        .toList()
}

internal fun LedgerSnapshot.suggestedAccount(kind: TxnType): String? {
    val active = activeAccounts
    val ids = active.mapTo(mutableSetOf()) { it.id }
    return txns.firstOrNull { it.type == kind && it.account in ids }?.account ?: active.firstOrNull()?.id
}

internal fun LedgerSnapshot.lastCategoryAccount(kind: TxnType, categoryId: String?): String? {
    if (categoryId == null || kind == TxnType.TRANSFER) return null
    val active = activeAccounts.mapTo(mutableSetOf()) { it.id }
    return txns.firstOrNull { it.type == kind && it.category == categoryId && it.account in active }?.account
}

internal fun LedgerSnapshot.searchTransactions(query: String): List<TransactionRecord> {
    val terms = query.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (terms.isEmpty()) return emptyList()
    val categoryById = categories.associateBy { it.id }
    val accountById = accounts.associateBy { it.id }
    return txns.filter { entry ->
        val category = categoryById[entry.category]
        val fields = listOfNotNull(
            entry.note, category?.name, categoryById[category?.parent]?.name,
            accountById[entry.account]?.name, accountById[entry.destination]?.name,
        )
        terms.all { term -> fields.any { it.contains(term, ignoreCase = true) } }
    }
}
