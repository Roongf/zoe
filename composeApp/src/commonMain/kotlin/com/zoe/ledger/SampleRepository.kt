package com.zoe.ledger

import kotlinx.datetime.LocalDate

class SampleLedgerRepository : LedgerRepository {
    private val categoryData = listOf(
        Category("food", "餐饮", "🍜", 0xFFFFEEE8, EntryType.Expense),
        Category("home", "居家", "⌂", 0xFFEAF2FF, EntryType.Expense),
        Category("travel", "出行", "◉", 0xFFE9F8EF, EntryType.Expense),
        Category("fun", "娱乐", "✦", 0xFFF5EDFF, EntryType.Expense),
        Category("health", "健康", "♡", 0xFFFFEDF0, EntryType.Expense),
        Category("salary", "工资", "↗", 0xFFE7F8F0, EntryType.Income),
        Category("bonus", "奖金", "★", 0xFFFFF5DA, EntryType.Income),
    )
    private val entriesData = mutableListOf(
        LedgerEntry("e1", 12800, EntryType.Expense, "food", Owner.Shared, LocalDate(2026, 9, 10), "超市采购"),
        LedgerEntry("e2", 6800, EntryType.Expense, "food", Owner.Me, LocalDate(2026, 9, 9), "工作日午餐"),
        LedgerEntry("e3", 220000, EntryType.Expense, "home", Owner.Shared, LocalDate(2026, 9, 8), "九月房租"),
        LedgerEntry("e4", 3600, EntryType.Expense, "travel", Owner.Partner, LocalDate(2026, 9, 8), "地铁通勤"),
        LedgerEntry("e5", 1699, EntryType.Expense, "fun", Owner.Shared, LocalDate(2026, 9, 6), "流媒体订阅"),
        LedgerEntry("e6", 2800, EntryType.Expense, "health", Owner.Me, LocalDate(2026, 9, 4), "跑步装备"),
        LedgerEntry("e7", 1850000, EntryType.Income, "salary", Owner.Me, LocalDate(2026, 9, 5), "工资"),
        LedgerEntry("e8", 1520000, EntryType.Income, "salary", Owner.Partner, LocalDate(2026, 9, 5), "工资"),
    )
    override suspend fun entries() = entriesData.toList()
    override suspend fun categories() = categoryData
    override suspend fun budget() = MonthlyBudget(650000, "2026-09")
    override suspend fun save(entry: LedgerEntry) { entriesData.add(entry) }
}

