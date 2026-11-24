package com.qianduoduo

import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.todayIn
import kotlin.random.Random

internal enum class TxnType(val label: String) { EXPENSE("支出"), INCOME("收入"), TRANSFER("转账") }
internal enum class AccountType(val label: String, val icon: String) {
    CASH("现金", "wallet"), BANK("银行卡", "wallet"), CREDIT("信用卡", "wallet"), OTHER("其他", "wechat")
}
internal enum class FilterPreset(val label: String) {
    THIS_MONTH("本月"), LAST_MONTH("上月"), LAST_7_DAYS("近 7 天"), LAST_30_DAYS("近 30 天"),
    THIS_YEAR("今年"), ALL("全部")
}

internal data class LedgerPeriod(val preset: FilterPreset? = FilterPreset.THIS_MONTH, val month: LocalDate? = null) {
    fun range(today: LocalDate): Pair<LocalDate?, LocalDate?> {
        val current = LocalDate(today.year, today.monthNumber, 1)
        val yearStart = LocalDate(today.year, 1, 1)
        val anchor = month?.let { LocalDate(it.year, it.monthNumber, 1) } ?: current
        return when (preset) {
            FilterPreset.THIS_MONTH -> current to current.plus(1, DateTimeUnit.MONTH)
            FilterPreset.LAST_MONTH -> current.plus(-1, DateTimeUnit.MONTH) to current
            FilterPreset.LAST_7_DAYS -> today.plus(-6, DateTimeUnit.DAY) to today.plus(1, DateTimeUnit.DAY)
            FilterPreset.LAST_30_DAYS -> today.plus(-29, DateTimeUnit.DAY) to today.plus(1, DateTimeUnit.DAY)
            FilterPreset.THIS_YEAR -> yearStart to yearStart.plus(1, DateTimeUnit.YEAR)
            FilterPreset.ALL -> null to null
            null -> anchor to anchor.plus(1, DateTimeUnit.MONTH)
        }
    }

    fun selectedMonth(today: LocalDate): LocalDate? = when (preset) {
        null, FilterPreset.THIS_MONTH, FilterPreset.LAST_MONTH -> range(today).first
        else -> null
    }

    fun label(today: LocalDate): String = selectedMonth(today)?.let { "${it.year}年${it.monthNumber}月" } ?: preset?.label.orEmpty()
    fun shiftMonth(by: Int, today: LocalDate): LedgerPeriod {
        val start = selectedMonth(today) ?: LocalDate(today.year, today.monthNumber, 1)
        return LedgerPeriod(null, start.plus(by, DateTimeUnit.MONTH))
    }
}

internal fun nowMillis() = Clock.System.now().toEpochMilliseconds()
internal fun today() = Clock.System.todayIn(TimeZone.currentSystemDefault())
internal fun localDateTime(millis: Long) = Instant.fromEpochMilliseconds(millis).toLocalDateTime(TimeZone.currentSystemDefault())
internal fun dateStartMillis(date: LocalDate) = date.atStartOfDayIn(TimeZone.currentSystemDefault()).toEpochMilliseconds()
internal fun withDate(millis: Long, date: LocalDate): Long =
    LocalDateTime(date, localDateTime(millis).time).toInstant(TimeZone.currentSystemDefault()).toEpochMilliseconds()

internal data class LedgerAccount(
    val id: String, val name: String, val type: AccountType, val icon: String?,
    val initialBalance: Long, val currency: String = "CNY", val active: Boolean = true,
)
internal data class LedgerCategory(val id: String, val parent: String?, val name: String, val kind: TxnType, val icon: String?)
internal data class TransactionRecord(
    val id: String, val type: TxnType, val cents: Long, val account: String, val destination: String?,
    val category: String?, val note: String?, val at: Long,
)
internal data class LedgerSnapshot(
    val accounts: List<LedgerAccount> = emptyList(),
    val categories: List<LedgerCategory> = emptyList(),
    val txns: List<TransactionRecord> = emptyList(),
) {
    val activeAccounts get() = accounts.filter { it.active }
    fun forPeriod(period: LedgerPeriod, today: LocalDate): List<TransactionRecord> {
        val (start, end) = period.range(today)
        if (start == null && end == null) return txns
        val from = start?.let(::dateStartMillis) ?: Long.MIN_VALUE
        val until = end?.let(::dateStartMillis) ?: Long.MAX_VALUE
        return txns.filter { it.at >= from && it.at < until }
    }

    fun balance(account: LedgerAccount) = account.initialBalance + txns.sumOf { txn ->
        when (txn.type) {
            TxnType.EXPENSE -> if (txn.account == account.id) -txn.cents else 0L
            TxnType.INCOME -> if (txn.account == account.id) txn.cents else 0L
            TxnType.TRANSFER -> (if (txn.destination == account.id) txn.cents else 0L) -
                (if (txn.account == account.id) txn.cents else 0L)
        }
    }

}

internal object Money {
    private val pattern = Regex("^[0-9]{0,13}(\\.[0-9]{0,2})?$")
    fun parse(input: String): Long? {
        val value = input.trim().replace(",", "").replace("¥", "").replace(" ", "")
        if (value.isEmpty() || !pattern.matches(value)) return null
        val units = value.substringBefore('.').ifEmpty { "0" }.toLong()
        val fraction = value.substringAfter('.', "").padEnd(2, '0').toLong()
        return units * 100 + fraction
    }

    fun format(cents: Long, showSymbol: Boolean = true, group: Boolean = true): String {
        val raw = cents.toString().removePrefix("-").padStart(3, '0')
        val units = raw.dropLast(2)
        val integer = if (group) units.reversed().chunked(3).joinToString(",").reversed() else units
        return "${if (cents < 0) "-" else ""}${if (showSymbol) "¥" else ""}$integer.${raw.takeLast(2)}"
    }
}

internal fun newId(): String = Random.nextBytes(16).also {
    it[6] = ((it[6].toInt() and 0x0F) or 0x40).toByte()
    it[8] = ((it[8].toInt() and 0x3F) or 0x80).toByte()
}.joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
    .let { "${it.take(8)}-${it.substring(8, 12)}-${it.substring(12, 16)}-${it.substring(16, 20)}-${it.takeLast(12)}" }
