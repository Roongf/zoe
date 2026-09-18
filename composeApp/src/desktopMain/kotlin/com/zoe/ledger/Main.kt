package com.zoe.ledger

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "轻记 · 家庭账本") { QingJiApp() }
}
