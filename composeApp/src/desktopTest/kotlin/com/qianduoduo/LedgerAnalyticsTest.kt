package com.qianduoduo

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertNull
import kotlinx.datetime.LocalDate

class LedgerAnalyticsTest {
    private val food = LedgerCategory("food", null, "餐饮", TxnType.EXPENSE, "restaurant")
    private val lunch = LedgerCategory("lunch", food.id, "午餐", TxnType.EXPENSE, "lunch")
    private val shopping = LedgerCategory("shopping", null, "购物", TxnType.EXPENSE, "shopping")
    private val travel = LedgerCategory("travel", null, "旅行", TxnType.EXPENSE, "travel")
    private val salary = LedgerCategory("salary", null, "工资", TxnType.INCOME, "salary")
    private val categories = listOf(food, lunch, shopping, travel, salary)
    private val february = LocalDate(2026, 2, 1)

    @Test
    fun categoryTotalsAndDrilldownsReconcileIncludingUncategorized() {
        val data = LedgerSnapshot(categories = categories, txns = listOf(
            entry("root", food.id, 130),
            entry("child", lunch.id, 300),
            entry("no-category", null, 77),
            entry("unknown-category", "missing", 23),
            entry("income", salary.id, 1_200, type = TxnType.INCOME),
            entry("transfer", food.id, 1_000, type = TxnType.TRANSFER),
            entry("previous", lunch.id, 240, date = LocalDate(2026, 1, 31)),
        ))
        val entries = data.forPeriod(LedgerPeriod(null, february), february)
        val (foodTotal, unclassified) = data.expenseBreakdown(entries)
        assertEquals(ExpenseTotal(ExpenseGroup(food.id, food.name, food.icon), 430, 2), foodTotal)
        assertEquals(ExpenseTotal(ExpenseGroup(null, "未分类", null), 100, 2), unclassified)
        assertEquals(listOf("root", "child"), data.expenseEntries(entries, foodTotal.group).map { it.id })
        assertEquals(listOf("no-category", "unknown-category"), data.expenseEntries(entries, unclassified.group).map { it.id })
        for (total in data.expenseBreakdown(entries)) {
            assertEquals(total.cents, data.expenseEntries(entries, total.group).sumOf { it.cents })
        }
    }

    @Test
    fun sixMonthsCrossYearsWithEmptyMonthsAndExcludeTransfers() {
        val data = LedgerSnapshot(txns = listOf(
            entry("too-old", null, 999, date = LocalDate(2025, 8, 31)),
            entry("first", null, 100, date = LocalDate(2025, 9, 1)),
            entry("january", null, 50, date = LocalDate(2026, 1, 31)),
            entry("last", null, 550, date = LocalDate(2026, 2, 28), type = TxnType.INCOME),
            entry("transfer", null, 999, type = TxnType.TRANSFER),
            entry("too-new", null, 999, date = LocalDate(2026, 3, 1)),
        ))
        val months = data.monthlyTotals(LocalDate(2026, 2, 15))
        assertEquals(listOf(9, 10, 11, 12, 1, 2), months.map { it.month.monthNumber })
        assertEquals(listOf(2025, 2025, 2025, 2025, 2026, 2026), months.map { it.month.year })
        assertEquals(listOf(100L, 0L, 0L, 0L, 50L, 0L), months.map { it.expense })
        assertEquals(listOf(0L, 0L, 0L, 0L, 0L, 550L), months.map { it.income })
    }

    @Test
    fun shortcutsUseDistinctCategoriesByTypeWithoutFlatteningChildren() {
        val data = LedgerSnapshot(categories = categories, txns = listOf(
            entry("a", lunch.id, 10),
            entry("b", lunch.id, 11),
            entry("c", null, 12),
            entry("d", salary.id, 13, type = TxnType.INCOME),
            entry("e", shopping.id, 14),
            entry("f", food.id, 15),
            entry("g", travel.id, 16),
        ))
        assertEquals(listOf(lunch, shopping, food), data.recentCategories(TxnType.EXPENSE))
        assertEquals(listOf(salary), data.recentCategories(TxnType.INCOME))
        assertTrue(data.recentCategories(TxnType.TRANSFER).isEmpty())
    }

    @Test
    fun searchesNotesParentCategoriesAndBothTransferAccountsAcrossDates() {
        val oldLunch = entry("lunch", lunch.id, 3200, date = LocalDate(2025, 12, 1)).copy(note = "Cafe 午餐")
        val transfer = entry("transfer", null, 999, type = TxnType.TRANSFER).copy(destination = "bank")
        val data = LedgerSnapshot(
            accounts = listOf(LedgerAccount("cash", "现金", AccountType.CASH, null, 0),
                LedgerAccount("bank", "储蓄卡", AccountType.BANK, null, 0)),
            categories = categories,
            txns = listOf(transfer, oldLunch),
        )
        assertEquals(listOf(oldLunch), data.searchTransactions(" 餐饮 cafe "))
        assertEquals(listOf(transfer), data.searchTransactions("储蓄卡 现金"))
        assertTrue(data.searchTransactions("  ").isEmpty())
        assertTrue(data.searchTransactions("餐饮 储蓄卡").isEmpty())
    }

    @Test
    fun newEntryAccountRespectsTypeAndSkipsDeletedAccounts() {
        val accounts = listOf(
            LedgerAccount("cash", "现金", AccountType.CASH, null, 0),
            LedgerAccount("wx", "微信", AccountType.OTHER, null, 0),
            LedgerAccount("deleted", "旧卡", AccountType.BANK, null, 0, active = false),
        )
        val data = LedgerSnapshot(accounts = accounts, txns = listOf(
            entry("a", lunch.id, 10).copy(account = "deleted"),
            entry("b", lunch.id, 10).copy(account = "wx"),
            entry("c", salary.id, 10, type = TxnType.INCOME).copy(account = "cash"),
        ))
        assertEquals("wx", data.suggestedAccount(TxnType.EXPENSE))
        assertEquals("wx", data.lastCategoryAccount(TxnType.EXPENSE, lunch.id))
        assertNull(data.lastCategoryAccount(TxnType.EXPENSE, shopping.id))
        assertNull(data.lastCategoryAccount(TxnType.INCOME, lunch.id))
        assertNull(data.lastCategoryAccount(TxnType.TRANSFER, lunch.id))
        assertNull(data.lastCategoryAccount(TxnType.EXPENSE, null))
        assertEquals("cash", data.suggestedAccount(TxnType.INCOME))
        assertEquals("cash", data.suggestedAccount(TxnType.TRANSFER))
        assertNull(LedgerSnapshot().suggestedAccount(TxnType.EXPENSE))
    }

    private fun entry(
        id: String,
        category: String?,
        cents: Long,
        date: LocalDate = february,
        type: TxnType = TxnType.EXPENSE,
    ) = TransactionRecord(id, type, cents, "cash", null, category, null, dateStartMillis(date))
}
