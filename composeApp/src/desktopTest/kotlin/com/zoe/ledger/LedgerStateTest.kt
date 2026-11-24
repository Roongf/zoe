package com.zoe.ledger

import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LedgerStateTest {
    @Test
    fun defaultsToCurrentMonthAndIgnoresPreviousYearAndMonth() = runBlocking {
        val state = stateFor(
            entry("last-year", 9000, date = LocalDate(2025, 1, 1)),
            entry("last-month", 8000, date = LocalDate(2025, 12, 31)),
            entry("this-month", 1200),
        )

        assertEquals("2026-01", state.selectedMonth)
        assertEquals(listOf("this-month"), state.entriesFor().map { it.id })
        assertEquals(1200L, state.summary().expenseCents)
    }

    @Test
    fun ranksThreeExpenseCategoriesUsingEntireMonthForTotal() = runBlocking {
        val state = stateFor(
            entry("lunch", 300, "food"),
            entry("dinner", 450, "food"),
            entry("rent", 1500, "home"),
            entry("train", 500, "travel"),
            entry("book", 500, "books"),
            entry("salary", 20000, "salary", EntryType.Income),
            entry("transfer", 15000, "transfer", EntryType.Transfer),
            entry("old-rent", 70000, "home", date = LocalDate(2025, 12, 31)),
        )

        assertEquals(
            listOf(CategorySpending("home", 1500), CategorySpending("food", 750), CategorySpending("books", 500)),
            state.topExpenseCategories(),
        )
        assertEquals(3250L, state.summary().expenseCents)
    }

    @Test
    fun startsWithAnEmptyRankingAndUpdatesWhenTodaysExpenseIsSaved() = runBlocking {
        val state = stateFor(entry("salary", 20000, "salary", EntryType.Income))

        assertTrue(state.topExpenseCategories().isEmpty())
        assertEquals(0L, state.summary().expenseCents)

        state.add(350, EntryType.Expense, "food", Owner.Shared, "早餐")

        assertEquals(listOf(CategorySpending("food", 350)), state.topExpenseCategories())
        assertEquals(350L, state.summary().expenseCents)
        assertEquals(today, state.entriesFor().last().date)
    }

    private val today = LocalDate(2026, 1, 1)

    private fun entry(
        id: String,
        cents: Long,
        category: String = "food",
        type: EntryType = EntryType.Expense,
        date: LocalDate = today,
    ) = LedgerEntry(id, cents, type, category, Owner.Shared, date)

    private suspend fun stateFor(vararg entries: LedgerEntry): LedgerState {
        val saved = entries.toMutableList()
        val repository = object : LedgerRepository {
            override suspend fun entries() = saved.toList()
            override suspend fun categories() = emptyList<Category>()
            override suspend fun budget() = MonthlyBudget(0, "")
            override suspend fun save(entry: LedgerEntry) {
                saved.add(entry)
            }
        }
        return LedgerState(repository, today = { today }).also { it.load() }
    }
}
