package com.qianduoduo

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.qianduoduo.database.QianDuoDuoDatabase
import java.io.File

internal class DesktopBackAction {
    var action: (() -> Unit)? = null
}

internal val LocalDesktopBackAction = staticCompositionLocalOf<DesktopBackAction?> { null }

@Composable
internal actual fun QianBackHandler(enabled: Boolean, onBack: () -> Unit) {
    val holder = LocalDesktopBackAction.current
    val current = rememberUpdatedState(onBack)
    DisposableEffect(holder, enabled) {
        if (enabled) holder?.action = { current.value() }
        onDispose { holder?.action = null }
    }
}

internal fun openDesktopLedger(file: File): LedgerStore {
    file.parentFile?.let { check(it.isDirectory || it.mkdirs()) { "Cannot create the ledger directory." } }
    val driver = JdbcSqliteDriver("jdbc:sqlite:${file.absolutePath}")
    try {
        val version = driver.executeQuery(
            null, "PRAGMA user_version",
            { cursor -> app.cash.sqldelight.db.QueryResult.Value(if (cursor.next().value) cursor.getLong(0) ?: 0L else 0L) },
            0,
        ).value
        val schema = QianDuoDuoDatabase.Schema
        if (version == 0L) {
            QianDuoDuoDatabase(driver).transaction {
                schema.create(driver)
                driver.execute(null, "PRAGMA user_version = ${schema.version}", 0)
            }
        } else check(version == schema.version) { "Unsupported ledger schema version: $version" }
        return LedgerStore(driver)
    } catch (e: Exception) {
        driver.close()
        throw e
    }
}
