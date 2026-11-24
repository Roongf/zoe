package com.qianduoduo

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.dp
import java.awt.GraphicsEnvironment
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.plus
import org.junit.Assume.assumeFalse

@OptIn(ExperimentalTestApi::class)
class LedgerUiTest {
    @Test
    fun recordsAnExpenseAndShowsRankingAccountsAndFilter() = withLedger { store ->
        runComposeUiTest {
            fun clickText(text: String) = onNodeWithText(text, useUnmergedTree = true).performClick()
            fun clickIcon(label: String) = onNodeWithContentDescription(label, useUnmergedTree = true).performClick()
            setContent { QianDuoDuoApp(store) }
            waitUntil(timeoutMillis = 10_000) { onAllNodesWithText("钱多多").fetchSemanticsNodes().isNotEmpty() }
            onNodeWithText("这个时间段还没有账目").assertIsDisplayed()
            clickText("记一笔")
            onNodeWithTag("editor-amount").performTextInput("29.90")
            clickText("餐饮")
            clickText("午餐")
            onNodeWithText("记下这笔 ¥29.90", useUnmergedTree = true).performClick()
            waitUntil(timeoutMillis = 10_000) { onAllNodesWithText("支出分类 Top 3").fetchSemanticsNodes().isNotEmpty() }
            onNodeWithText("餐饮").assertExists()
            clickIcon("账户")
            onNodeWithText("净资产").assertIsDisplayed()
            onAllNodesWithText("-¥29.90").assertCountEquals(2)
            clickIcon("返回")
            clickText(LedgerPeriod().label(today()))
            clickText("上月")
            onNodeWithText("这个时间段还没有账目").assertIsDisplayed()
        }
    }

    @Test
    fun chartsAndHomeRankingDrillDownAndKeepTheSelectedMonthOnReturn() = withLedger { store ->
        store.seed()
        val snapshot = store.snapshot()
        val cash = snapshot.activeAccounts.first()
        val now = today()
        val prior = now.monthStart().plus(-1, DateTimeUnit.MONTH)
        val lunch = snapshot.categories.single { it.name == "午餐" }
        val taxi = snapshot.categories.single { it.name == "打车" }
        store.saveTxn(TransactionRecord(newId(), TxnType.EXPENSE, 1800, cash.id, null, lunch.id, "this-month-lunch", nowMillis()), false)
        store.saveTxn(TransactionRecord(newId(), TxnType.EXPENSE, 1200, cash.id, null, lunch.id, "last-month-lunch", dateStartMillis(prior)), false)
        store.saveTxn(TransactionRecord(newId(), TxnType.EXPENSE, 900, cash.id, null, taxi.id, "this-month-taxi", nowMillis()), false)
        runComposeUiTest {
            fun clickText(text: String) = onNodeWithText(text, useUnmergedTree = true).performClick()
            fun back() = onNodeWithContentDescription("返回", useUnmergedTree = true).performClick()
            setContent { Box(Modifier.size(360.dp, 680.dp)) { QianDuoDuoApp(store) } }
            waitUntil(timeoutMillis = 10_000) { onAllNodesWithText("钱多多").fetchSemanticsNodes().isNotEmpty() }
            clickText("餐饮")
            onNodeWithText("分类明细").assertIsDisplayed()
            onNodeWithText("this-month-lunch", substring = true).assertExists()
            onNodeWithText("this-month-taxi", substring = true).assertDoesNotExist()
            onNodeWithText("last-month-lunch", substring = true).assertDoesNotExist()
            clickText("this-month-lunch")
            onNodeWithText("编辑这笔").assertIsDisplayed()
            onNodeWithContentDescription("关闭", useUnmergedTree = true).performClick()
            onNodeWithText("分类明细").assertIsDisplayed()
            back()
            clickText("统计")
            onNodeWithText("六个月收支").assertIsDisplayed()
            onNodeWithText("本月尚未结束", substring = true).assertIsDisplayed()
            onNodeWithTag("trend-month-${now.monthStart()}").assertIsSelected()
            onNodeWithTag("trend-month-$prior").assertIsDisplayed().performClick().assertIsSelected()
            clickText("餐饮")
            onNodeWithText("last-month-lunch", substring = true).assertExists()
            onNodeWithText("this-month-lunch", substring = true).assertDoesNotExist()
            back()
            onNodeWithTag("trend-month-$prior").assertIsSelected()
            clickText("查看当月流水")
            onNodeWithText(LedgerPeriod(null, prior).label(now)).assertExists()
            onNodeWithText("last-month-lunch", substring = true).assertExists()
        }
    }

    @Test
    fun newEntryUsesARecentSubcategoryAndKeepsNoteWhenCollapsed() = withLedger { store ->
        store.seed()
        val snapshot = store.snapshot()
        val wechat = snapshot.activeAccounts.single { it.name == "微信" }
        val lunch = snapshot.categories.single { it.name == "午餐" }
        store.saveTxn(TransactionRecord(newId(), TxnType.EXPENSE, 1000, wechat.id, null, lunch.id, null, nowMillis()), false)
        runComposeUiTest {
            setContent { QianDuoDuoApp(store) }
            waitUntil(timeoutMillis = 10_000) { onAllNodesWithText("钱多多").fetchSemanticsNodes().isNotEmpty() }
            onNodeWithText("记一笔", useUnmergedTree = true).performClick()
            onNodeWithTag("editor-amount").assertIsFocused().performTextInput("8.50")
            onNode(hasTestTag("editor-source-account") and hasText("微信")).assertIsDisplayed()
            onNodeWithText("交通").assertDoesNotExist()
            onNodeWithText("最近用过").assertIsDisplayed()
            onNodeWithTag("all-categories-toggle").performClick()
            onNodeWithText("交通").assertExists()
            onNodeWithTag("all-categories-toggle").performClick()
            onNodeWithTag("recent-category-${lunch.id}").performClick().assertIsSelected()
            onNodeWithTag("editor-note").assertDoesNotExist()
            onNodeWithTag("editor-details-toggle").performScrollTo().performClick()
            onNodeWithTag("editor-note").performScrollTo().performTextInput("测试快捷录入")
            onNodeWithTag("editor-details-toggle").performScrollTo().performClick()
            onNodeWithTag("editor-note").assertDoesNotExist()
            onNodeWithText("记下这笔 ¥8.50", useUnmergedTree = true).performClick()
            waitUntil(timeoutMillis = 10_000) { onAllNodesWithText("钱多多").fetchSemanticsNodes().isNotEmpty() }
            val saved = store.snapshot().txns.single { it.cents == 850L }
            assertEquals(lunch.id, saved.category)
            assertEquals(wechat.id, saved.account)
            assertEquals("测试快捷录入", saved.note)
        }
    }

    @Test
    fun populatedDemoSupportsSearchAndEditFromOutsideTheCurrentMonth() = withLedger { store ->
        runComposeUiTest {
            setContent { Box(Modifier.size(360.dp, 680.dp)) { QianDuoDuoApp(store, demo = true) } }
            waitUntil(timeoutMillis = 10_000) { onAllNodesWithText("演示账本 · 模拟数据").fetchSemanticsNodes().isNotEmpty() }
            onNodeWithText("支出分类 Top 3").assertIsDisplayed()
            onNodeWithContentDescription("搜索流水", useUnmergedTree = true).performClick()
            onNodeWithTag("transaction-search").assertIsFocused().performTextInput("咖喱饭")
            onNodeWithText("找到 1 笔 · 全部时间").assertIsDisplayed()
            onNodeWithText("咖喱饭午餐", useUnmergedTree = true).performClick()
            onNodeWithText("编辑这笔").assertIsDisplayed()
            onNodeWithContentDescription("关闭", useUnmergedTree = true).performClick()
            onNodeWithText("找到 1 笔 · 全部时间").assertIsDisplayed()
            onNodeWithContentDescription("清空搜索", useUnmergedTree = true).performClick()
            onNodeWithTag("transaction-search").performTextInput("房租 工资卡")
            onNodeWithText("找到 6 笔 · 全部时间").assertIsDisplayed()
            val prior = today().monthStart().plus(-1, DateTimeUnit.MONTH)
            onNodeWithText("${prior.year}年${prior.monthNumber}月1日", substring = true).assertExists()
        }
    }

    @Test
    fun transferRequiresADestinationAndKeepsTheManuallyChosenExpenseAccount() = withLedger { store ->
        runComposeUiTest {
            fun clickText(text: String) = onNodeWithText(text, useUnmergedTree = true).performClick()
            val save = "记下这笔 ¥40.00"
            setContent { Box(Modifier.size(360.dp, 680.dp)) { QianDuoDuoApp(store) } }
            waitUntil(timeoutMillis = 10_000) { onAllNodesWithText("钱多多").fetchSemanticsNodes().isNotEmpty() }
            clickText("记一笔")
            onNodeWithTag("editor-amount").performTextInput("40")
            onNodeWithTag("editor-source-account").performClick()
            clickText("支付宝")
            clickText("转账")
            onNodeWithText(save).assertIsNotEnabled()
            clickText("支出")
            onNode(hasTestTag("editor-source-account") and hasText("支付宝")).assertIsDisplayed()
            clickText("转账")
            onNodeWithTag("editor-destination-account").performClick()
            clickText("微信")
            onNodeWithText(save).assertIsEnabled()
            clickText(save)
            waitUntil(timeoutMillis = 10_000) { onAllNodesWithText("钱多多").fetchSemanticsNodes().isNotEmpty() }
            val snapshot = store.snapshot()
            val saved = snapshot.txns.single()
            assertEquals(TxnType.TRANSFER, saved.type)
            assertEquals("现金", snapshot.accounts.single { it.id == saved.account }.name)
            assertEquals("微信", snapshot.accounts.single { it.id == saved.destination }.name)
        }
    }

    @Test
    fun aCategorySuggestsItsOwnAccountButCannotReplaceAnExplicitChoice() = withLedger { store ->
        store.seed()
        val data = store.snapshot()
        val lunch = data.categories.single { it.name == "午餐" }
        val taxi = data.categories.single { it.name == "打车" }
        val wx = data.activeAccounts.single { it.name == "微信" }
        val alipay = data.activeAccounts.single { it.name == "支付宝" }
        val time = nowMillis()
        store.saveTxn(TransactionRecord(newId(), TxnType.EXPENSE, 2000, wx.id, null, lunch.id, null, time - 1_000), false)
        store.saveTxn(TransactionRecord(newId(), TxnType.EXPENSE, 2000, alipay.id, null, taxi.id, null, time), false)
        runComposeUiTest {
            setContent { Box(Modifier.size(360.dp, 680.dp)) { QianDuoDuoApp(store) } }
            waitUntil(timeoutMillis = 10_000) { onAllNodesWithText("钱多多").fetchSemanticsNodes().isNotEmpty() }
            onNodeWithText("记一笔", useUnmergedTree = true).performClick()
            onNode(hasTestTag("editor-source-account") and hasText("支付宝")).assertIsDisplayed()
            onNodeWithTag("recent-category-${lunch.id}").performClick()
            onNode(hasTestTag("editor-source-account") and hasText("微信")).assertIsDisplayed()
            onNodeWithText("按分类建议").assertIsDisplayed()
            onNodeWithTag("editor-source-account").performClick()
            onNodeWithText("现金", useUnmergedTree = true).performClick()
            onNodeWithTag("recent-category-${taxi.id}").performClick()
            onNode(hasTestTag("editor-source-account") and hasText("现金")).assertIsDisplayed()
            onNodeWithText("按分类建议").assertDoesNotExist()
        }
    }

    @Test
    fun saveAndContinueKeepsDateAndTypeButStartsAnotherRecord() = withLedger { store ->
        val date = today().plus(-1, DateTimeUnit.DAY)
        runComposeUiTest {
            fun clickText(text: String) = onNodeWithText(text, useUnmergedTree = true).performClick()
            setContent { Box(Modifier.size(360.dp, 680.dp)) { QianDuoDuoApp(store) } }
            waitUntil(timeoutMillis = 10_000) { onAllNodesWithText("钱多多").fetchSemanticsNodes().isNotEmpty() }
            clickText("记一笔")
            clickText("收入")
            onNodeWithTag("editor-amount").performTextInput("12.30")
            clickText("工资")
            onNodeWithTag("editor-details-toggle").performScrollTo().performClick()
            onNodeWithContentDescription("前一天", useUnmergedTree = true).performScrollTo().performClick()
            onNodeWithTag("editor-note").performScrollTo().performTextInput("只属于第一笔")
            onNodeWithTag("editor-save-continue").performClick()
            waitUntil(timeoutMillis = 10_000) {
                onAllNodesWithText("已记 ¥12.30", substring = true).fetchSemanticsNodes().isNotEmpty()
            }
            onNodeWithTag("editor-amount").assertIsFocused()
            onNodeWithTag("editor-save").assertIsNotEnabled()
            onNodeWithTag("editor-note").assertDoesNotExist()
            onNodeWithTag("editor-amount").performTextInput("5")
            onNodeWithTag("editor-save").performClick()
            waitUntil(timeoutMillis = 10_000) { onAllNodesWithText("钱多多").fetchSemanticsNodes().isNotEmpty() }
            val first = store.snapshot().txns.single { it.cents == 1230L }
            val second = store.snapshot().txns.single { it.cents == 500L }
            assertEquals(listOf(date, date), listOf(first, second).map { localDateTime(it.at).date })
            assertEquals(listOf(TxnType.INCOME, TxnType.INCOME), listOf(first.type, second.type))
            assertEquals("只属于第一笔", first.note)
            assertNull(second.note)
            assertNull(second.category)
        }
    }

    @Test
    fun copyingAnOldRecordDoesNotSaveUntilConfirmedAndReturnsToTheSearch() = withLedger { store ->
        store.seed()
        val data = store.snapshot()
        val lunch = data.categories.single { it.name == "午餐" }
        val wx = data.activeAccounts.single { it.name == "微信" }
        val prior = today().monthStart().plus(-1, DateTimeUnit.MONTH)
        val original = TransactionRecord(newId(), TxnType.EXPENSE, 1250, wx.id, null, lunch.id, "例行工作餐", dateStartMillis(prior))
        store.saveTxn(original, false)
        runComposeUiTest {
            fun clickText(text: String) = onNodeWithText(text, useUnmergedTree = true).performClick()
            setContent { Box(Modifier.size(360.dp, 680.dp)) { QianDuoDuoApp(store) } }
            waitUntil(timeoutMillis = 10_000) { onAllNodesWithText("钱多多").fetchSemanticsNodes().isNotEmpty() }
            onNodeWithContentDescription("搜索流水", useUnmergedTree = true).performClick()
            onNodeWithTag("transaction-search").performTextInput("工作餐")
            clickText("例行工作餐")
            onNodeWithTag("editor-amount").performTextReplacement("99")
            clickText("复制账单")
            onNodeWithText("放弃未保存修改？").assertIsDisplayed()
            clickText("复制原账单")
            onNodeWithTag("editor-amount").assertTextEquals("12.50")
            onNode(hasTestTag("editor-source-account") and hasText("微信")).assertIsDisplayed()
            assertEquals(listOf(original), store.snapshot().txns)
            onNodeWithTag("editor-amount").performTextReplacement("13.30")
            onNodeWithTag("editor-save").performClick()
            waitUntil(timeoutMillis = 10_000) { onAllNodesWithText("找到 2 笔 · 全部时间").fetchSemanticsNodes().isNotEmpty() }
            val saved = store.snapshot().txns
            assertEquals(original, saved.single { it.id == original.id })
            val copy = saved.single { it.id != original.id }
            assertEquals(1330L, copy.cents)
            assertEquals(today(), localDateTime(copy.at).date)
            assertEquals(original.note, copy.note)
            assertEquals(original.account, copy.account)
            assertEquals(original.category, copy.category)
        }
    }

    private fun withLedger(test: (LedgerStore) -> Unit) {
        assumeFalse("Run with Xvfb when no desktop display is available.", GraphicsEnvironment.isHeadless())
        val dir = createTempDirectory("zoe-qdd-ui-").toFile()
        val store = openDesktopLedger(File(dir, "qianduoduo.db"))
        try {
            test(store)
        } finally {
            store.close()
            dir.deleteRecursively()
        }
    }
}
