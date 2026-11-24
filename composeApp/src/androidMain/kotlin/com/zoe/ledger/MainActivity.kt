package com.zoe.ledger

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.qingji.ledger.BuildConfig
import com.qianduoduo.LedgerStore
import com.qianduoduo.QianDuoDuoApp
import com.qianduoduo.database.QianDuoDuoDatabase

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val store = ledgerStore ?: LedgerStore(
            AndroidSqliteDriver(
                QianDuoDuoDatabase.Schema, applicationContext,
                if (BuildConfig.DEMO_LEDGER) "qianduoduo-demo.db" else "qianduoduo.db",
            ),
        ).also { ledgerStore = it }
        setContent { QianDuoDuoApp(store, demo = BuildConfig.DEMO_LEDGER) }
    }

    private companion object {
        // Keep one driver per app process, including during activity recreation.
        var ledgerStore: LedgerStore? = null
    }
}
