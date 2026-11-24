package com.qianduoduo

import app.cash.sqldelight.db.SqlDriver
import com.qianduoduo.database.Account
import com.qianduoduo.database.Category
import com.qianduoduo.database.QianDuoDuoDatabase
import com.qianduoduo.database.Txn
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

internal class LedgerStore(private val driver: SqlDriver, private val clock: () -> Long = ::nowMillis) {
    private val database = QianDuoDuoDatabase(driver)
    private val query = database.ledgerQueries

    fun seed() = database.transaction {
        val time = clock()
        if (query.activeCategoryCount().executeAsOne() == 0L) {
            for ((kind, seeds) in listOf(TxnType.EXPENSE to expenseSeeds, TxnType.INCOME to incomeSeeds)) {
                seeds.forEachIndexed { order, seed ->
                    val root = newId()
                    query.insertCategory(Category(root, null, seed.name, kind.name, seed.icon, null, order.toLong(), 0, time, time, null))
                    seed.children.forEachIndexed { childOrder, (name, icon) ->
                        query.insertCategory(Category(newId(), root, name, kind.name, icon, null, childOrder.toLong(), 0, time, time, null))
                    }
                }
            }
        }
        if (query.activeAccountCount().executeAsOne() == 0L) {
            listOf(
                Triple("现金", AccountType.CASH, "wallet"),
                Triple("微信", AccountType.OTHER, "wechat"),
                Triple("支付宝", AccountType.OTHER, "alipay"),
            ).forEachIndexed { index, (name, type, icon) ->
                query.insertAccount(Account(newId(), name, type.name, "CNY", 0, icon, null, index.toLong(), 0, time, time, null))
            }
        }
    }

    fun snapshot() = LedgerSnapshot(
        accounts = query.allAccounts().executeAsList().map {
            LedgerAccount(it.uuid, it.name, AccountType.entries.find { type -> type.name == it.type } ?: AccountType.OTHER,
                it.icon, it.initial_balance, it.currency, it.deleted_at == null)
        },
        categories = query.allCategories().executeAsList().map {
            LedgerCategory(it.uuid, it.parent_uuid, it.name, if (it.kind == "INCOME") TxnType.INCOME else TxnType.EXPENSE, it.icon)
        },
        txns = query.visibleTxns().executeAsList().mapNotNull { row ->
            val type = TxnType.entries.find { it.name == row.type } ?: return@mapNotNull null
            TransactionRecord(row.uuid, type, row.amount, row.account_uuid, row.to_account_uuid, row.category_uuid, row.note, row.occurred_at)
        },
    )

    // Only called by the isolated preview package or explicit desktop --demo.
    fun seedDemo() = database.transaction {
        if (query.allTxnCount().executeAsOne() != 0L) return@transaction
        val time = clock()
        val current = localDateTime(time).date
        val month = current.monthStart()
        val snapshot = snapshot()
        val categoryIds = snapshot.categories.associate { it.name to it.id }
        val accounts = snapshot.activeAccounts.associate { it.name to it.id }.toMutableMap()
        fun addBank(name: String, opening: Long, sort: Long) {
            if (name in accounts) return
            val id = newId()
            query.insertAccount(Account(id, name, AccountType.BANK.name, "CNY", opening, "wallet", null, sort, 0, time, time, null))
            accounts[name] = id
        }
        addBank("工资卡", 180_000, 3)
        addBank("储蓄卡", 320_000, 4)

        fun add(date: LocalDate, hour: Int, cents: Long, account: String, category: String?, note: String,
                type: TxnType = TxnType.EXPENSE, destination: String? = null) {
            val at = minOf(dateStartMillis(date) + hour * 3_600_000L, time)
            saveTxn(TransactionRecord(
                newId(), type, cents, accounts.getValue(account), destination?.let(accounts::getValue),
                category?.let(categoryIds::getValue), note, at,
            ), editing = false)
        }
        for (offset in 5 downTo 0) {
            val start = month.plus(-offset, DateTimeUnit.MONTH)
            val days = if (offset == 0) current.dayOfMonth else start.plus(1, DateTimeUnit.MONTH).plus(-1, DateTimeUnit.DAY).dayOfMonth
            val salary = 975_000L + offset % 3 * 15_000L
            add(start, 8, salary, "工资卡", "工资", "本月工资", TxnType.INCOME)
            add(start, 9, 248_000L, "工资卡", "房租", "公寓房租")
            add(start, 10, 86_000L, "工资卡", null, "微信生活费", TxnType.TRANSFER, "微信")
            add(start, 10, 54_000L, "工资卡", null, "支付宝生活费", TxnType.TRANSFER, "支付宝")
            add(start, 11, 150_000L, "工资卡", null, "每月定存", TxnType.TRANSFER, "储蓄卡")
            fun sample(day: Int, hour: Int, cents: Long, wallet: String, category: String?, note: String) {
                if (day <= days) add(LocalDate(start.year, start.monthNumber, day), hour, cents, wallet, category, note)
            }
            sample(2, 20, 12_300L + offset * 490L, "支付宝", "水电燃气", "上月水电")
            sample(4, 18, 8_880, "微信", "日用品", "洗衣液和纸巾")
            sample(6, 13, 15_900L + offset * 680L, "工资卡", "服饰", "换季衣服")
            sample(8, 20, 4_600, "微信", "电影", "周末电影")
            sample(11, 9, 6_950, "支付宝", "书籍", "买两本书")
            sample(14, 16, 31_000L + offset * 1_890L, "工资卡", "数码", "家用电子配件")
            sample(19, 20, 8_980, "微信", "外卖", "朋友来家里吃饭")
            sample(22, 9, 790, "支付宝", null, "临时小额支出")
            sample(24, 10, 13_200L + offset * 1_200L, "工资卡", "运动健身", "瑜伽课")
            if (offset in listOf(1, 3, 5)) sample(17, 16, 52_000L + offset * 7_300L, "工资卡", "旅行", "周末短途车票")
            for (day in 1..days step 3) {
                sample(day, 8, 380L + day % 4 * 170L, "支付宝", "公交地铁", "地铁通勤")
                sample(day, 12, 2_360L + (day + offset) % 5 * 420L, "微信", "午餐", "工作日午餐")
                if (day % 2 == 1) sample(day, 18, 4_800L + day % 5 * 1_280L, "微信", "买菜", "超市买菜")
            }
            if (days >= 12) add(LocalDate(start.year, start.monthNumber, 12), 15,
                32_000L + offset * 2_500L, "工资卡", "其他收入", "周末兼职", TxnType.INCOME)
        }
        add(current, 8, 520, "支付宝", "公交地铁", "今天地铁")
        add(current, 12, 3_280, "微信", "午餐", "咖喱饭午餐")
        add(current, 15, 1_580, "微信", "零食饮料", "燕麦拿铁")
    }

    fun saveAccount(id: String?, name: String, type: AccountType) {
        require(name.isNotBlank())
        val time = clock()
        if (id == null) {
            query.insertAccount(Account(newId(), name.trim(), type.name, "CNY", 0, type.icon, null, 0, 0, time, time, null))
        } else {
            query.updateAccount(name.trim(), type.name, time, id)
        }
    }

    fun deleteAccount(id: String) {
        val time = clock()
        query.deleteAccount(time, time, id)
    }

    fun saveTxn(record: TransactionRecord, editing: Boolean) {
        val transfer = record.type == TxnType.TRANSFER
        require(record.cents > 0 && record.account.isNotBlank())
        require(!transfer || (record.destination != null && record.destination != record.account))
        val time = clock()
        val to = record.destination.takeIf { transfer }
        val category = record.category.takeUnless { transfer }
        val note = record.note?.trim()?.ifEmpty { null }
        if (editing) {
            query.updateTxn(record.type.name, record.cents, record.account, to, category, note, record.at, time, record.id)
        } else {
            query.insertTxn(Txn(record.id, record.type.name, record.cents, record.account, to, category,
                note, record.at, null, time, time, null))
        }
    }

    fun deleteTxn(id: String) {
        val time = clock()
        query.deleteTxn(time, time, id)
    }

    fun close() = driver.close()
}

private data class Seed(val name: String, val icon: String, val children: List<Pair<String, String>> = emptyList())
private val expenseSeeds = listOf(
    Seed("餐饮", "restaurant", listOf("早餐" to "breakfast", "午餐" to "lunch", "晚餐" to "dinner",
        "买菜" to "groceries", "外卖" to "takeout", "零食饮料" to "snack")),
    Seed("交通", "transport", listOf("公交地铁" to "transit", "打车" to "taxi", "加油" to "fuel", "停车" to "parking")),
    Seed("购物", "shopping", listOf("日用品" to "daily", "服饰" to "clothing", "数码" to "digital")),
    Seed("居住", "home", listOf("房租" to "rent", "水电燃气" to "utilities", "物业" to "property")),
    Seed("娱乐", "entertainment", listOf("电影" to "movie", "游戏" to "game", "旅行" to "travel")),
    Seed("医疗健康", "medical", listOf("看病" to "doctor", "买药" to "medicine", "运动健身" to "fitness")),
    Seed("学习", "education", listOf("书籍" to "book", "课程" to "course")),
    Seed("通讯", "phone"), Seed("人情往来", "gift"), Seed("其他支出", "more"),
)
private val incomeSeeds = listOf(
    Seed("工资", "salary"), Seed("奖金", "bonus"), Seed("投资收益", "investment"),
    Seed("红包", "redpacket"), Seed("其他收入", "more"),
)
