package com.zoe.ledger

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.qianduoduo.DesktopBackAction
import com.qianduoduo.LedgerStore
import com.qianduoduo.LoadFailure
import com.qianduoduo.LocalDesktopBackAction
import com.qianduoduo.QianDuoDuoApp
import com.qianduoduo.QianTheme
import com.qianduoduo.openDesktopLedger
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

fun main(args: Array<String>) = application {
    val demo = "--demo" in args
    val compact = "--compact" in args
    val darkOverride = when {
        "--dark" in args -> true
        "--light" in args -> false
        else -> null
    }
    val backAction = remember { DesktopBackAction() }
    val file = remember { File(System.getProperty("user.home"), if (demo) ".zoe/qianduoduo-demo.db" else ".zoe/qianduoduo.db") }
    Window(
        onCloseRequest = ::exitApplication,
        title = if (demo) "钱多多·体验" else "钱多多",
        state = rememberWindowState(width = if (compact) 390.dp else 480.dp, height = 780.dp),
        onPreviewKeyEvent = { event ->
            val action = backAction.action
            if (event.type == KeyEventType.KeyUp && event.key == Key.Escape && action != null) {
                action()
                true
            } else false
        },
    ) {
        val dark = darkOverride ?: isSystemInDarkTheme()
        var attempt by remember { mutableIntStateOf(0) }
        var result by remember { mutableStateOf<Result<LedgerStore>?>(null) }
        LaunchedEffect(attempt) {
            result = withContext(Dispatchers.IO) { runCatching { openDesktopLedger(file) } }
        }
        val store = result?.getOrNull()
        if (store != null) {
            DisposableEffect(store) { onDispose { store.close() } }
            CompositionLocalProvider(LocalDesktopBackAction provides backAction) { QianDuoDuoApp(store, demo = demo, dark = dark) }
        } else {
            QianTheme(dark) {
                Surface(Modifier.fillMaxSize()) {
                    if (result?.isFailure == true) LoadFailure { attempt++ }
                }
            }
        }
    }
}
