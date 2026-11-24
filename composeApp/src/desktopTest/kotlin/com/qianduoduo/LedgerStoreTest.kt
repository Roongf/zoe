package com.qianduoduo

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.qianduoduo.database.QianDuoDuoDatabase
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.*
import kotlinx.datetime.LocalDate

class LedgerStoreTest {
    @Test
    fun demoIsOptInPopulatedAcrossSixMonthsAndOnlySeededOnce() = withLedger { file, store ->
        val now = today()
        val initial = store.snapshot()
        assertTrue(initial.txns.isEmpty())
        assertEquals(3, initial.activeAccounts.size)
        store.seedDemo()
        val demo = store.snapshot()
        assertTrue(demo.txns.size >= 170)
        assertTrue(demo.txns.all { localDateTime(it.at).date <= now })
        assertEquals(6, demo.monthlyTotals(now).count { it.income > 0 && it.expense > 0 })
        assertEquals(5, demo.activeAccounts.size)
        assertTrue(demo.txns.any { it.type == TxnType.TRANSFER })
        assertTrue(demo.txns.any { it.type == TxnType.EXPENSE && it.category == null })
        withReopened(file) { reopened ->
            reopened.seed()
            reopened.seedDemo()
            assertEquals(demo, reopened.snapshot())
            demo.txns.forEach { reopened.deleteTxn(it.id) }
            reopened.seedDemo()
            assertTrue(reopened.snapshot().txns.isEmpty())
        }
    }

    @Test
    fun demoStillShowsUsableDataOnFirstDayAndNeverSeedsFutureEntries() {
        val firstDay = LocalDate(2027, 1, 1)
        val time = dateStartMillis(firstDay) + 1_234L
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        QianDuoDuoDatabase.Schema.create(driver)
        val store = LedgerStore(driver) { time }
        try {
            store.seed()
            store.seedDemo()
            val data = store.snapshot()
            assertTrue(data.txns.all { it.at <= time })
            assertEquals(6, data.monthlyTotals(firstDay).count { it.income > 0 && it.expense > 0 })
            val curry = data.searchTransactions("咖喱饭 微信 餐饮").single()
            assertEquals("午餐", data.categories.single { it.id == curry.category }.name)
            assertEquals(3280L, curry.cents)
            assertEquals(firstDay, localDateTime(curry.at).date)
            assertEquals(3, data.expenseBreakdown(data.forPeriod(LedgerPeriod(), firstDay)).take(3).size)
        } finally {
            store.close()
        }
    }

    @Test
    fun seedsDefaultAccountsAndNestedCategoriesOnlyOnce() = withLedger { _, store ->
        val before = store.snapshot()
        assertEquals(listOf("现金", "微信", "支付宝"), before.activeAccounts.map { it.name })
        assertEquals(39, before.categories.size)
        assertEquals(15, before.categories.count { it.parent == null })
        val lunch = before.categories.single { it.name == "午餐" }
        assertEquals("餐饮", before.categories.single { it.id == lunch.parent }.name)
        assertTrue(before.txns.isEmpty())
        store.seed()
        assertEquals(before, store.snapshot())
    }

    @Test
    fun storesEditsAndSoftDeletionAcrossRestarts() = withLedger { file, store ->
        val account = store.snapshot().activeAccounts.first()
        val entry = record(TxnType.EXPENSE, 29, account.id).copy(note = "  早餐  ")
        store.saveTxn(entry, editing = false)
        store.saveTxn(entry.copy(cents = 1_299), editing = true)
        withReopened(file) { reopened ->
            val data = reopened.snapshot()
            assertEquals(entry.copy(cents = 1_299, note = "早餐"), data.txns.single())
            assertEquals(-1_299L, data.balance(account))
            reopened.deleteTxn(entry.id)
        }
        withReopened(file) { reopened ->
            assertTrue(reopened.snapshot().txns.isEmpty())
            assertEquals(0L, reopened.snapshot().balance(account))
        }
    }

    @Test
    fun transfersUpdateBothAccountsWithoutAffectingNetAssets() = withLedger { _, store ->
        val (cash, wechat) = store.snapshot().activeAccounts
        store.saveTxn(record(TxnType.INCOME, 10_000, cash.id), editing = false)
        val transfer = record(TxnType.TRANSFER, 2_500, cash.id).copy(destination = wechat.id)
        store.saveTxn(transfer, editing = false)
        assertFailsWith<IllegalArgumentException> {
            store.saveTxn(transfer.copy(id = newId(), destination = cash.id), editing = false)
        }
        assertFailsWith<IllegalArgumentException> {
            store.saveTxn(transfer.copy(id = newId(), destination = null), editing = false)
        }
        val data = store.snapshot()
        assertEquals(7_500L, data.balance(cash))
        assertEquals(2_500L, data.balance(wechat))
        assertEquals(10_000L, data.activeAccounts.sumOf(data::balance))
        assertEquals(2, data.txns.size)
        assertTrue(data.expenseBreakdown(data.txns).isEmpty())
    }

    @Test
    fun managesAccountsWithoutDeletingHistoricalTransactions() = withLedger { file, store ->
        store.saveAccount(null, "  工资卡  ", AccountType.BANK)
        val account = store.snapshot().activeAccounts.single { it.name == "工资卡" }
        store.saveTxn(record(TxnType.INCOME, 200, account.id), editing = false)
        store.saveAccount(account.id, "储蓄卡", AccountType.BANK)
        withReopened(file) { reopened ->
            assertEquals("储蓄卡", reopened.snapshot().activeAccounts.single { it.id == account.id }.name)
            reopened.deleteAccount(account.id)
        }
        val state = store.snapshot()
        assertFalse(state.activeAccounts.any { it.id == account.id })
        assertEquals("储蓄卡", state.accounts.single { it.id == account.id }.name)
        assertEquals(account.id, state.txns.single().account)
    }

    @Test
    fun currentMonthExcludesEarlierAndLaterMonthsAndRanksParentCategories() = withLedger { _, store ->
        val today = LocalDate(2026, 1, 15)
        val state = store.snapshot()
        val cash = state.activeAccounts.first()
        fun expense(amount: Long, category: String?, date: LocalDate = today) {
            val id = state.categories.singleOrNull { it.name == category }?.id
            store.saveTxn(record(TxnType.EXPENSE, amount, cash.id, date).copy(category = id), editing = false)
        }
        expense(120, "早餐")
        expense(580, "午餐")
        expense(400, "打车")
        expense(350, "书籍", LocalDate(2026, 1, 1))
        expense(250, null)
        expense(10_001, "购物", LocalDate(2026, 2, 1))
        expense(20_002, "购物", LocalDate(2025, 12, 31))
        store.saveTxn(record(TxnType.INCOME, 10_000, cash.id, today), editing = false)
        val data = store.snapshot()
        val month = data.forPeriod(LedgerPeriod(), today)
        assertEquals(6, month.size)
        assertEquals(
            listOf("餐饮" to 700L, "交通" to 400L, "学习" to 350L),
            data.expenseBreakdown(month).take(3).map { it.group.label to it.cents },
        )
        assertEquals(20_002L, data.forPeriod(LedgerPeriod().shiftMonth(-1, today), today).single().cents)
        assertEquals(10_001L, data.forPeriod(LedgerPeriod().shiftMonth(1, today), today).single().cents)
        assertEquals(8, data.forPeriod(LedgerPeriod(FilterPreset.ALL), today).size)
    }

    @Test
    fun localDatesUseHalfOpenRangesAndPreserveTimeWhenEdited() {
        val today = LocalDate(2028, 2, 29)
        assertEquals(LocalDate(2028, 2, 23) to LocalDate(2028, 3, 1), LedgerPeriod(FilterPreset.LAST_7_DAYS).range(today))
        assertEquals(LocalDate(2028, 1, 31) to LocalDate(2028, 3, 1), LedgerPeriod(FilterPreset.LAST_30_DAYS).range(today))
        val before = dateStartMillis(today) + 13_647_000
        val after = localDateTime(withDate(before, LocalDate(2028, 3, 1)))
        assertEquals(localDateTime(before).time, after.time)
        assertEquals(LocalDate(2028, 3, 1), after.date)
    }

    @Test
    fun preservesIntegerCentsAndRejectsUnsupportedInputs() {
        assertEquals(29L, Money.parse("0.29"))
        assertEquals(2_910L, Money.parse("29.1"))
        assertEquals(999_999_999_999_999L, Money.parse("9999999999999.99"))
        assertEquals("-¥1,234.56", Money.format(-123_456))
        listOf("", "1.234", "1..2", "-0.29", "10000000000000", "1e3").forEach { assertNull(Money.parse(it)) }
    }

    private fun record(type: TxnType, amount: Long, account: String, date: LocalDate = LocalDate(2026, 1, 15)) =
        TransactionRecord(newId(), type, amount, account, null, null, null, dateStartMillis(date))

    private fun withLedger(test: (File, LedgerStore) -> Unit) {
        val dir = createTempDirectory("zoe-qdd-").toFile()
        try {
            val file = File(dir, "qianduoduo.db")
            withReopened(file) {
                it.seed()
                test(file, it)
            }
        } finally {
            dir.deleteRecursively()
        }
    }

    private fun withReopened(file: File, test: (LedgerStore) -> Unit) {
        val store = openDesktopLedger(file)
        try {
            test(store)
        } finally {
            store.close()
        }
    }
}
